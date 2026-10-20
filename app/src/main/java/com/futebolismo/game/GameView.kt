package com.futebolismo.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

// hat: 0 boné, 1 tranças, 2 maria-chiquinhas loiras, 3 cacheado, 4 marinheiro, 5 cabelo curto, 6 laço, 7 carteiro, 8 árbitro
class Ch(val name: String, val hat: Int, val skin: Int, val hair: Int, val wide: Float, val spd: Float, val say: String, val type: Int)

val CH = listOf(
    Ch("Chaves", 0, 0xFFF1C27D.toInt(), 0xFF2E8B3A.toInt(), 1f, 1.08f, "Foi sem querer querendo!", 0),
    Ch("Chiquinha", 1, 0xFFF1C27D.toInt(), 0xFF6B3A1E.toInt(), 1f, 1.0f, "Ai, ai, ai, ai!", 1),
    Ch("Popis", 2, 0xFFF5D0A9.toInt(), 0xFFF4D03F.toInt(), 1f, 0.95f, "Ai, que lindo!", 2),
    Ch("Seu Madruga", 3, 0xFFE8B58A.toInt(), 0xFF111111.toInt(), 1.05f, 0.9f, "Era só o que me faltava!", 3),
    Ch("Quico", 4, 0xFFF5D0A9.toInt(), 0xFFFFFFFF.toInt(), 1.15f, 1.0f, "Cala a boca!", 0),
    Ch("Nhonho", 5, 0xFFF5D0A9.toInt(), 0xFF4A2A12.toInt(), 1.35f, 0.85f, "Sou o craque!", 1),
    Ch("Paty", 6, 0xFFF1C27D.toInt(), 0xFF111111.toInt(), 1f, 1.0f, "Que fofo!", 2),
    Ch("Jaiminho", 7, 0xFFE8B58A.toInt(), 0xFF1F3A7A.toInt(), 1f, 0.95f, "Foi um grande prazer!", 3),
    Ch("Seu Barriga", 8, 0xFFF1C27D.toInt(), 0xFF222222.toInt(), 1.5f, 0.7f, "Gooool!", 0)
)

class P(val ch: Int, val team: Int, val gk: Boolean, val hx: Float, val hy: Float) {
    var x = hx; var y = hy; var vx = 0f; var vy = 0f
    var fx = if (team == 0) 1f else -1f; var fy = 0f
    var boost = 0f; var aiT = 0f
}

class GameView(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback, Runnable {
    val W = 1200f; val H = 640f; val GY1 = 230f; val GY2 = 410f
    val dn = resources.displayMetrics.density
    val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    val rf = RectF(); val path = Path()
    var th: Thread? = null
    @Volatile var running = false
    var sw = 1f; var sh = 1f; var sc = 1f; var ox = 0f; var oy = 0f
    var state = 0; var t = 0f; var endT = 0f
    @Volatile var tapped = false
    val ps = ArrayList<P>()
    var bx = 600f; var by = 320f; var bvx = 0f; var bvy = 0f
    var owner: P? = null; var ownT = 0f; var lastK: P? = null; var kLock = 0f
    val score = intArrayOf(0, 0)
    var clock = 120f; var celebT = 0f; var scorer: P? = null; var scTeam = 0
    var joyId = -1; var jx0 = 0f; var jy0 = 0f; var jx = 0f; var jy = 0f
    @Volatile var shootP = false
    @Volatile var passP = false
    var ctl: P? = null
    var mvx = 0f; var mvy = 0f
    var refx = 600f

    init { holder.addCallback(this); setup() }

    // ---------- ciclo ----------
    override fun surfaceCreated(h: SurfaceHolder) { running = true; th = Thread(this); th!!.start() }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
        sw = w.toFloat(); sh = hh.toFloat(); sc = min(sw / (W + 120f), sh / H)
        ox = (sw - (W + 120f) * sc) / 2f + 60f * sc; oy = (sh - H * sc) / 2f
    }
    override fun surfaceDestroyed(h: SurfaceHolder) { running = false; th?.join() }
    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val n = System.nanoTime(); val dt = min(0.04f, (n - last) / 1e9f); last = n
            val c = holder.lockCanvas() ?: continue
            try { update(dt); render(c) } finally { holder.unlockCanvasAndPost(c) }
        }
    }

    // ---------- setup ----------
    fun setup() {
        ps.clear()
        val hx = floatArrayOf(380f, 520f, 520f); val hyy = floatArrayOf(320f, 170f, 470f)
        for (i in 0..2) ps.add(P(i, 0, false, hx[i], hyy[i]))
        ps.add(P(3, 0, true, 55f, 320f))
        for (i in 0..2) ps.add(P(4 + i, 1, false, W - hx[i], hyy[i]))
        ps.add(P(7, 1, true, W - 55f, 320f))
        score[0] = 0; score[1] = 0; clock = 120f
        kickoff()
    }
    fun kickoff() {
        for (p in ps) { p.x = p.hx; p.y = p.hy; p.vx = 0f; p.vy = 0f; p.boost = 0f; p.aiT = 0f }
        bx = 600f; by = 320f; bvx = 0f; bvy = 0f; owner = null; lastK = null; kLock = 0f
    }

    // ---------- lógica ----------
    fun go(p: P, tx: Float, ty: Float) {
        val a = tx - p.x; val b = ty - p.y; val l = hypot(a, b)
        if (l < 8f) { mvx = 0f; mvy = 0f } else { val k = min(1f, l / 50f) / l; mvx = a * k; mvy = b * k }
    }
    fun kick(p: P, dx: Float, dy: Float, sp: Float) {
        val l = max(0.001f, hypot(dx, dy))
        bvx = dx / l * sp; bvy = dy / l * sp; owner = null; lastK = p; kLock = 0.3f
        bx = p.x + dx / l * 38f; by = p.y + dy / l * 38f
    }
    fun goal(team: Int) {
        score[team]++; state = 2; celebT = 3.4f; scTeam = team
        val k = lastK
        scorer = if (k != null && k.team == team && !k.gk) k else ps.first { it.team == team && !it.gk }
        owner = null; bvx = 0f; bvy = 0f
        bx = if (team == 0) W + 25f else -25f; by = 320f
    }
    fun doShoot(c: P) {
        if (owner === c) {
            if (W - c.x < 420f && c.fx > -0.3f) kick(c, W - c.x, 320f + c.fy * 95f - c.y, 880f)
            else kick(c, c.fx, c.fy, 760f)
        } else c.boost = 0.3f
    }
    fun doPass(c: P) {
        if (owner !== c) return
        var best: P? = null; var bs = -9f
        for (m in ps) if (m.team == 0 && !m.gk && m !== c) {
            val dx = m.x - c.x; val dy = m.y - c.y; val l = max(1f, hypot(dx, dy))
            val s = (dx * c.fx + dy * c.fy) / l - l / 1500f
            if (s > bs) { bs = s; best = m }
        }
        val m = best ?: return
        val dx = m.x + m.vx * 0.3f - c.x; val dy = m.y + m.vy * 0.3f - c.y
        kick(c, dx, dy, min(800f, max(450f, hypot(dx, dy) * 2.3f)))
    }

    fun update(dt: Float) {
        t += dt
        if (tapped) {
            tapped = false
            if (state == 0) { setup(); state = 1 } else if (state == 3 && t - endT > 1.2f) state = 0
        }
        if (state == 0 || state == 3) return
        if (state == 1) { clock -= dt; if (clock <= 0f) { clock = 0f; state = 3; endT = t; return } }
        else { celebT -= dt; if (celebT <= 0f) { kickoff(); state = 1 } }
        refx += (min(1100f, max(100f, bx)) - refx) * dt * 0.8f

        val o0 = owner
        val c = if (o0 != null && o0.team == 0 && !o0.gk) o0
        else ps.filter { it.team == 0 && !it.gk }.minByOrNull { (it.x - bx) * (it.x - bx) + (it.y - by) * (it.y - by) }!!
        ctl = c
        val chaser1 = ps.filter { it.team == 1 && !it.gk }.minByOrNull { (it.x - bx) * (it.x - bx) + (it.y - by) * (it.y - by) }

        for (p in ps) {
            var sp = 210f * CH[p.ch].spd
            mvx = 0f; mvy = 0f
            val att = if (p.team == 0) 1f else -1f
            val ow = owner
            if (state == 2) {
                val s = scorer!!
                if (p === s) go(p, 600f, 560f)
                else if (p.team == scTeam && !p.gk) go(p, s.x - 60f + (ps.indexOf(p) % 4) * 30f, s.y - 30f)
            } else if (p === c) {
                if (hypot(jx, jy) > 0.15f) { mvx = jx; mvy = jy }
                if (p.boost > 0f) sp = 480f
            } else if (p.gk) {
                val gx = if (p.team == 0) 55f else W - 55f
                if (ow === p) {
                    p.aiT += dt
                    if (p.aiT > 0.8f) { p.aiT = 0f; kick(p, att, Random.nextFloat() - 0.5f, 640f) }
                } else { go(p, gx, min(GY2 - 10f, max(GY1 + 10f, by))); sp = 185f }
            } else {
                p.aiT -= dt
                val mine = ow != null && ow.team == p.team
                if (ow === p) {
                    go(p, if (p.team == 0) W else 0f, 320f + sin(t + p.ch) * 140f)
                    if (abs((if (p.team == 0) W else 0f) - p.x) < 330f && p.aiT <= 0f) {
                        p.aiT = 1.2f
                        kick(p, att, ((if (Random.nextBoolean()) GY1 + 15f else GY2 - 15f) + Random.nextFloat() * 40f - 20f - p.y) / abs((if (p.team == 0) W else 0f) - p.x), 800f)
                    }
                } else if (p.team == 1 && p === chaser1 && !mine) { go(p, bx, by); sp *= 0.93f }
                else if (mine) go(p, p.hx + att * 200f, p.hy)
                else go(p, p.hx + (bx - 600f) * 0.4f, p.hy + (by - 320f) * 0.3f)
                if (p.team == 1) sp *= 0.93f
            }
            val m = hypot(mvx, mvy)
            if (m > 0.2f) { p.fx += (mvx / m - p.fx) * min(1f, dt * 14f); p.fy += (mvy / m - p.fy) * min(1f, dt * 14f) }
            p.vx += (mvx * sp - p.vx) * min(1f, dt * 10f); p.vy += (mvy * sp - p.vy) * min(1f, dt * 10f)
            p.x = min(W - 20f, max(20f, p.x + p.vx * dt)); p.y = min(H - 5f, max(60f, p.y + p.vy * dt))
            if (p.boost > 0f) p.boost -= dt
        }
        if (state != 1) return

        if (shootP) { shootP = false; doShoot(c) }
        if (passP) { passP = false; doPass(c) }

        // bola
        kLock -= dt
        val ow = owner
        if (ow != null) {
            ownT += dt
            val ax = ow.x + ow.fx * 32f; val ay = ow.y + ow.fy * 20f
            val k = min(1f, dt * 18f)
            bx += (ax - bx) * k; by += (ay - by) * k
            bvx = ow.vx; bvy = ow.vy
        } else {
            bx += bvx * dt; by += bvy * dt
            val f = exp(-1.4f * dt); bvx *= f; bvy *= f
            if (by < 8f) { by = 8f; bvy = -bvy * 0.7f }
            if (by > H - 8f) { by = H - 8f; bvy = -bvy * 0.7f }
            if (bx < 0f || bx > W) {
                if (by in GY1..GY2) { goal(if (bx < 0f) 1 else 0); return }
                bx = min(W, max(0f, bx)); bvx = -bvx * 0.7f
            }
        }
        for (p in ps) {
            if (p === owner) continue
            if (p === lastK && kLock > 0f) continue
            val r = if (p.gk) 46f else 32f
            if (hypot(p.x - bx, p.y - by) < r) {
                val oo = owner
                if (oo == null || (oo.team != p.team && ownT > 0.35f && (p.boost > 0f || Random.nextFloat() < 0.04f))) {
                    owner = p; ownT = 0f; p.aiT = 0f
                }
            }
        }
    }

    // ---------- toque ----------
    fun inB(x: Float, y: Float, cx: Float, cy: Float, r: Float) = hypot(x - cx, y - cy) < r * 1.2f
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex; val x = e.getX(i); val y = e.getY(i)
                if (state != 1) { tapped = true; return true }
                if (inB(x, y, sw - 90f * dn, sh - 100f * dn, 48f * dn)) shootP = true
                else if (inB(x, y, sw - 200f * dn, sh - 60f * dn, 38f * dn)) passP = true
                else if (x < sw * 0.55f && joyId == -1) { joyId = e.getPointerId(i); jx0 = x; jy0 = y; jx = 0f; jy = 0f }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) if (e.getPointerId(i) == joyId) {
                val R = 60f * dn
                var dx = e.getX(i) - jx0; var dy = e.getY(i) - jy0
                val l = hypot(dx, dy); if (l > R) { dx = dx / l * R; dy = dy / l * R }
                jx = dx / R; jy = dy / R
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (e.getPointerId(e.actionIndex) == joyId || e.actionMasked == MotionEvent.ACTION_CANCEL) { joyId = -1; jx = 0f; jy = 0f }
            }
        }
        return true
    }

    // ---------- desenho ----------
    fun otext(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int) {
        pt.textAlign = Paint.Align.CENTER; pt.typeface = Typeface.DEFAULT_BOLD; pt.textSize = size
        pt.style = Paint.Style.STROKE; pt.strokeWidth = size / 7f; pt.color = 0xFF111111.toInt(); c.drawText(s, x, y, pt)
        pt.style = Paint.Style.FILL; pt.color = col; c.drawText(s, x, y, pt)
    }

    fun drawField(c: Canvas) {
        pt.style = Paint.Style.FILL
        for (i in 0 until 12) { pt.color = if (i % 2 == 0) 0xFF3A9A45.toInt() else 0xFF348C3E.toInt(); c.drawRect(i * 100f, 0f, (i + 1) * 100f, H, pt) }
        pt.style = Paint.Style.FILL; pt.color = 0x55000000
        c.drawRect(-55f, GY1, 0f, GY2, pt); c.drawRect(W, GY1, W + 55f, GY2, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 4f; pt.color = 0xCCFFFFFF.toInt()
        c.drawRect(2f, 2f, W - 2f, H - 2f, pt); c.drawLine(W / 2, 0f, W / 2, H, pt)
        c.drawCircle(W / 2, H / 2, 90f, pt)
        c.drawRect(0f, 150f, 170f, 490f, pt); c.drawRect(W - 170f, 150f, W, 490f, pt)
        pt.strokeWidth = 2f; pt.color = 0x88FFFFFF.toInt()
        var y = GY1; while (y <= GY2) { c.drawLine(-55f, y, 0f, y, pt); c.drawLine(W, y, W + 55f, y, pt); y += 20f }
        var x = 0f; while (x <= 55f) { c.drawLine(-x, GY1, -x, GY2, pt); c.drawLine(W + x, GY1, W + x, GY2, pt); x += 20f }
        pt.strokeWidth = 8f; pt.color = Color.WHITE
        c.drawLine(-55f, GY1, 0f, GY1, pt); c.drawLine(-55f, GY2, 0f, GY2, pt); c.drawLine(-55f, GY1, -55f, GY2, pt)
        c.drawLine(W + 55f, GY1, W, GY1, pt); c.drawLine(W + 55f, GY2, W, GY2, pt); c.drawLine(W + 55f, GY1, W + 55f, GY2, pt)
        pt.style = Paint.Style.FILL
    }

    fun drawBall(c: Canvas) {
        pt.style = Paint.Style.FILL
        pt.color = 0x55000000; c.drawOval(bx - 12f, by + 5f, bx + 12f, by + 13f, pt)
        pt.color = Color.WHITE; c.drawCircle(bx, by - 4f, 13f, pt)
        pt.color = 0xFF222222.toInt(); val a = (bx + by) * 0.06f
        c.drawCircle(bx + cos(a) * 6f, by - 4f + sin(a) * 6f, 4.5f, pt); c.drawCircle(bx - cos(a) * 6f, by - 4f - sin(a) * 6f, 3.5f, pt)
    }

    fun drawChar(c: Canvas, x0: Float, y0: Float, ch: Int, kit: Int, sho: Int, fx: Float, run: Float, cel: Int, sad: Boolean, isCtl: Boolean) {
        val C = CH[ch]; val w = C.wide
        var x = x0; var y = y0
        pt.style = Paint.Style.FILL
        pt.color = 0x55000000; c.drawOval(x - 26 * w, y - 7, x + 26 * w, y + 7, pt)
        if (cel == 3) x += sin(t * 6) * 10
        if (cel in 1..3) y -= abs(sin(t * 9)) * 28
        val ph = sin(t * 16) * run * 9
        pt.color = C.skin
        c.drawRect(x - 13 * w, y - 30, x - 4 * w, y - 6 + ph, pt); c.drawRect(x + 4 * w, y - 30, x + 13 * w, y - 6 - ph, pt)
        pt.color = 0xFF222222.toInt()
        c.drawOval(x - 17 * w, y - 10 + ph, x - 2 * w, y + 2 + ph, pt); c.drawOval(x + 2 * w, y - 10 - ph, x + 17 * w, y + 2 - ph, pt)
        pt.color = sho; c.drawRoundRect(x - 19 * w, y - 42, x + 19 * w, y - 24, 6f, 6f, pt)
        pt.color = kit; c.drawRoundRect(x - 21 * w, y - 74, x + 21 * w, y - 34, 12f, 12f, pt)
        pt.color = Color.WHITE; c.drawRect(x - 7, y - 74, x + 7, y - 70, pt)
        // braços
        val sy = y - 66; val s2 = 22 * w
        var lx = x - s2 - 6; var ly = y - 42 - ph / 2; var rx = x + s2 + 6; var ry = y - 42 + ph / 2
        when {
            cel == 1 || cel == 2 -> { lx = x - 36; ly = y - 100 + sin(t * 12) * 8; rx = x + 36; ry = y - 100 - sin(t * 12) * 8 }
            cel == 3 -> { lx = x - 34 * w; ly = y - 96 + sin(t * 10) * 12; rx = x + 34 * w; ry = y - 96 - sin(t * 10) * 12 }
            cel == 4 -> { lx = x - 56 * w; ly = y - 76; rx = x + 56 * w; ry = y - 76 }
            sad -> { ly = y - 36; ry = y - 36 }
        }
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 9f; pt.strokeCap = Paint.Cap.ROUND; pt.color = kit
        c.drawLine(x - s2, sy, lx, ly, pt); c.drawLine(x + s2, sy, rx, ry, pt)
        pt.style = Paint.Style.FILL; pt.color = C.skin; c.drawCircle(lx, ly, 6.5f, pt); c.drawCircle(rx, ry, 6.5f, pt)
        // cabeça
        val hy = y - 94; val hw = 23f * min(w, 1.25f); val hh = if (C.hat == 3) 29f else 23f
        pt.color = C.skin; c.drawOval(x - hw, hy - hh, x + hw, hy + hh, pt)
        pt.color = C.hair
        when (C.hat) {
            0 -> { rf.set(x - hw - 2, hy - 38, x + hw + 2, hy + 2); c.drawArc(rf, 180f, 180f, true, pt)
                c.drawRoundRect(x - hw - 5, hy - 16, x - hw + 3, hy + 8, 3f, 3f, pt); c.drawRoundRect(x + hw - 3, hy - 16, x + hw + 5, hy + 8, 3f, 3f, pt) }
            1 -> { rf.set(x - hw - 3, hy - 32, x + hw + 3, hy + 8); c.drawArc(rf, 180f, 180f, true, pt)
                pt.style = Paint.Style.STROKE; pt.strokeWidth = 9f
                c.drawLine(x - hw, hy, x - hw - 6, hy + 34, pt); c.drawLine(x + hw, hy, x + hw + 6, hy + 34, pt)
                pt.style = Paint.Style.FILL; pt.color = 0xFFFF69B4.toInt(); c.drawCircle(x - hw - 3, hy + 4, 7f, pt); c.drawCircle(x + hw + 3, hy + 4, 7f, pt) }
            2 -> { rf.set(x - hw - 2, hy - 32, x + hw + 2, hy + 6); c.drawArc(rf, 180f, 180f, true, pt)
                c.drawCircle(x - hw - 6, hy - 6, 11f, pt); c.drawCircle(x + hw + 6, hy - 6, 11f, pt)
                c.drawCircle(x - hw - 8, hy + 14, 8f, pt); c.drawCircle(x + hw + 8, hy + 14, 8f, pt)
                pt.color = 0xFFFF69B4.toInt(); c.drawCircle(x, hy - 30, 7f, pt) }
            3 -> { for (k in -2..2) c.drawCircle(x + k * 11, hy - 26 + abs(k) * 5, 12f, pt)
                c.drawRect(x - hw - 2, hy - 18, x - hw + 4, hy + 6, pt); c.drawRect(x + hw - 4, hy - 18, x + hw + 2, hy + 6, pt) }
            4 -> { pt.color = Color.WHITE; rf.set(x - hw - 2, hy - 38, x + hw + 2, hy + 2); c.drawArc(rf, 180f, 180f, true, pt)
                pt.color = 0xFF2B59C3.toInt(); c.drawRect(x - hw - 2, hy - 20, x + hw + 2, hy - 13, pt)
                pt.color = 0xFFE53935.toInt(); c.drawCircle(x, hy - 40, 6f, pt) }
            5 -> { rf.set(x - hw - 2, hy - 30, x + hw + 2, hy + 6); c.drawArc(rf, 180f, 180f, true, pt); c.drawCircle(x, hy - 28, 6f, pt) }
            6 -> { rf.set(x - hw - 3, hy - 32, x + hw + 3, hy + 6); c.drawArc(rf, 180f, 180f, true, pt)
                c.drawRect(x - hw - 4, hy - 8, x - hw + 4, hy + 24, pt); c.drawRect(x + hw - 4, hy - 8, x + hw + 4, hy + 24, pt)
                pt.color = 0xFFE53935.toInt(); c.drawOval(x - 20, hy - 42, x - 2, hy - 26, pt); c.drawOval(x + 2, hy - 42, x + 20, hy - 26, pt); c.drawCircle(x, hy - 34, 5f, pt) }
            7 -> { rf.set(x - hw - 2, hy - 38, x + hw + 2, hy + 2); c.drawArc(rf, 180f, 180f, true, pt)
                pt.color = 0xFF111111.toInt(); c.drawRoundRect(x + fx * 6 - 18, hy - 22, x + fx * 6 + 18 + fx * 10, hy - 16, 3f, 3f, pt) }
            8 -> { rf.set(x - 14, hy - 28, x + 14, hy - 12); c.drawArc(rf, 180f, 180f, true, pt) }
        }
        val ex = fx * 5
        if (C.hat == 4) { pt.color = 0x88FF6B6B.toInt(); c.drawCircle(x + ex - 14, hy + 8, 6f, pt); c.drawCircle(x + ex + 14, hy + 8, 6f, pt) }
        pt.color = Color.WHITE; c.drawCircle(x + ex - 8, hy - 3, 6.5f, pt); c.drawCircle(x + ex + 8, hy - 3, 6.5f, pt)
        pt.color = Color.BLACK; c.drawCircle(x + ex - 8 + fx * 2.5f, hy - 3, 3f, pt); c.drawCircle(x + ex + 8 + fx * 2.5f, hy - 3, 3f, pt)
        if (C.hat == 3) { pt.color = 0xFFD9534F.toInt(); c.drawCircle(x + ex + fx * 4, hy + 5, 6f, pt) }
        if (C.hat == 7 || C.hat == 8) { pt.color = 0xFF111111.toInt(); c.drawRoundRect(x + ex - 11, hy + 7, x + ex + 11, hy + 12, 3f, 3f, pt) }
        if (cel > 0) { pt.color = 0xFF7B1020.toInt(); rf.set(x + ex - 8, hy + 8, x + ex + 8, hy + 21); c.drawOval(rf, pt) }
        else {
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 3f; pt.color = Color.BLACK
            if (sad) { rf.set(x + ex - 8, hy + 13, x + ex + 8, hy + 23); c.drawArc(rf, 200f, 140f, false, pt) }
            else { rf.set(x + ex - 8, hy + 4, x + ex + 8, hy + 16); c.drawArc(rf, 20f, 140f, false, pt) }
            pt.style = Paint.Style.FILL
        }
        if (sad) { pt.color = 0xFF4FC3F7.toInt(); c.drawCircle(x + ex - 8, hy + 6 + (t * 40) % 22, 3.5f, pt); c.drawCircle(x + ex + 8, hy + 6 + (t * 40 + 9) % 22, 3.5f, pt) }
        if (isCtl) {
            pt.color = 0xFFFFEB3B.toInt(); path.reset(); path.moveTo(x, hy - 56); path.lineTo(x - 11, hy - 76); path.lineTo(x + 11, hy - 76); path.close(); c.drawPath(path, pt)
        }
    }

    fun kitOf(p: P) = if (p.gk) (if (p.team == 0) 0xFFFF7A1A.toInt() else 0xFF8E44AD.toInt()) else if (p.team == 0) 0xFFFFD21F.toInt() else 0xFF2D7FF9.toInt()
    fun shortsOf(p: P) = if (p.team == 0) 0xFF1B8A3C.toInt() else Color.WHITE

    fun render(c: Canvas) {
        c.drawColor(0xFF2E7D32.toInt())
        c.save(); c.translate(ox, oy); c.scale(sc, sc)
        drawField(c)
        if (state == 0) {
            for (i in 0..8) drawChar(c, 90f + i * 127f, 540f, i, if (i < 4) 0xFFFFD21F.toInt() else if (i < 8) 0xFF2D7FF9.toInt() else 0xFF111111.toInt(),
                if (i < 4) 0xFF1B8A3C.toInt() else Color.WHITE, 1f, 0f, 1, false, false)
        } else {
            drawChar(c, refx, 630f, 8, 0xFF111111.toInt(), 0xFF111111.toInt(), 1f, 0.5f, if (state == 2) 4 else 0, false, false)
            val sorted = ps.sortedBy { it.y }
            var ballDone = false
            for (p in sorted) {
                if (!ballDone && p.y >= by) { drawBall(c); ballDone = true }
                var cel = 0
                if (state == 2 && p.team == scTeam && !p.gk) cel = if (p === scorer) CH[p.ch].type + 1 else 1
                val f = if (cel == 2 && p === scorer) cos(t * 10) else p.fx
                drawChar(c, p.x, p.y, p.ch, kitOf(p), shortsOf(p), f, min(1f, hypot(p.vx, p.vy) / 150f), cel, state == 2 && p.team != scTeam, p === ctl && state == 1)
            }
            if (!ballDone) drawBall(c)
            if (state == 2) {
                val s = scorer!!
                val txt = CH[s.ch].say
                pt.textSize = 26f; pt.typeface = Typeface.DEFAULT_BOLD; pt.textAlign = Paint.Align.CENTER
                val bw = pt.measureText(txt) + 34f; val by2 = max(70f, s.y - 190f); val bxx = min(W - bw / 2, max(bw / 2, s.x))
                pt.style = Paint.Style.FILL; pt.color = Color.WHITE; c.drawRoundRect(bxx - bw / 2, by2 - 34, bxx + bw / 2, by2 + 14, 18f, 18f, pt)
                pt.color = Color.BLACK; c.drawText(txt, bxx, by2, pt)
            }
        }
        c.restore()
        hud(c)
    }

    fun hud(c: Canvas) {
        if (state == 0) {
            pt.style = Paint.Style.FILL; pt.color = 0x66000000; c.drawRect(0f, 0f, sw, sh * 0.5f, pt)
            otext(c, "FUTEBOLISMO 27", sw / 2, sh * 0.27f, 62f * dn, 0xFFFFD21F.toInt())
            otext(c, "Vila x Turma  •  Toque para jogar", sw / 2, sh * 0.42f, 20f * dn, Color.WHITE)
            return
        }
        // placar
        val bw = 150f * dn
        pt.style = Paint.Style.FILL; pt.color = 0xCC111111.toInt(); c.drawRoundRect(sw / 2 - bw, 6f * dn, sw / 2 + bw, 46f * dn, 14f * dn, 14f * dn, pt)
        pt.textSize = 18f * dn; pt.textAlign = Paint.Align.CENTER; pt.typeface = Typeface.DEFAULT_BOLD
        pt.color = 0xFFFFD21F.toInt(); c.drawText("VILA ${score[0]}", sw / 2 - bw * 0.6f, 34f * dn, pt)
        pt.color = 0xFF6FA8FF.toInt(); c.drawText("${score[1]} TURMA", sw / 2 + bw * 0.6f, 34f * dn, pt)
        pt.color = Color.WHITE; c.drawText(String.format("%d:%02d", clock.toInt() / 60, clock.toInt() % 60), sw / 2, 34f * dn, pt)
        if (state == 1) {
            val jcx = if (joyId != -1) jx0 else 110f * dn; val jcy = if (joyId != -1) jy0 else sh - 100f * dn
            pt.color = 0x33FFFFFF; c.drawCircle(jcx, jcy, 60f * dn, pt)
            pt.color = 0x88FFFFFF.toInt(); c.drawCircle(jcx + jx * 60f * dn, jcy + jy * 60f * dn, 26f * dn, pt)
            pt.color = 0xAAE53935.toInt(); c.drawCircle(sw - 90f * dn, sh - 100f * dn, 48f * dn, pt)
            pt.color = 0xAA2D7FF9.toInt(); c.drawCircle(sw - 200f * dn, sh - 60f * dn, 38f * dn, pt)
            pt.color = Color.WHITE; pt.textSize = 14f * dn
            c.drawText("CHUTE", sw - 90f * dn, sh - 95f * dn, pt); c.drawText("PASSE", sw - 200f * dn, sh - 55f * dn, pt)
        }
        if (state == 2) otext(c, "GOOOOL!", sw / 2, sh * 0.38f, 64f * dn * (1f + 0.08f * sin(t * 10)), 0xFFFFD21F.toInt())
        if (state == 3) {
            pt.style = Paint.Style.FILL; pt.color = 0xAA000000.toInt(); c.drawRect(0f, 0f, sw, sh, pt)
            otext(c, "FIM DE JOGO", sw / 2, sh * 0.3f, 48f * dn, Color.WHITE)
            otext(c, "${score[0]} x ${score[1]}", sw / 2, sh * 0.52f, 60f * dn, 0xFFFFD21F.toInt())
            otext(c, if (score[0] > score[1]) "A Vila venceu!" else if (score[0] < score[1]) "A Turma venceu!" else "Empate!", sw / 2, sh * 0.68f, 26f * dn, Color.WHITE)
            otext(c, "Toque para voltar", sw / 2, sh * 0.85f, 18f * dn, Color.WHITE)
        }
    }
}
