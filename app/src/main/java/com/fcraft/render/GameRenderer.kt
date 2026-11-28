package com.fcraft.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.fcraft.core.Atlas
import com.fcraft.core.R_CROSS
import com.fcraft.core.R_LIQUID
import com.fcraft.core.R_SLAB
import com.fcraft.core.Reg
import com.fcraft.core.Tex
import com.fcraft.game.GameSession
import com.fcraft.game.Mob
import com.fcraft.game.Overlay
import com.fcraft.game.Settings
import com.fcraft.world.FB
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class ChunkGL {
    var vo = 0
    var no = 0
    var vt = 0
    var nt = 0
}

class GameRenderer(private val s: GameSession) : GLSurfaceView.Renderer {
    private val VS = """#version 300 es
        uniform mat4 uMVP;
        uniform vec3 uCam;
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec2 aUV;
        layout(location=2) in vec4 aCol;
        layout(location=3) in vec2 aLight;
        out vec2 vUV; out vec4 vCol; out vec2 vLight; out float vDist; out vec3 vPos;
        void main() {
            gl_Position = uMVP * vec4(aPos, 1.0);
            vUV = aUV; vCol = aCol; vLight = aLight; vPos = aPos;
            vDist = length(aPos - uCam);
        }""".trimIndent()

    private val FS = """#version 300 es
        precision highp float;
        uniform sampler2D uTex;
        uniform float uSky;
        uniform float uMin;
        uniform vec3 uFog;
        uniform float uFogS;
        uniform float uFogE;
        uniform float uFogEnabled;
        uniform float uShadersEnabled;
        uniform vec4 uHeld;
        in vec2 vUV; in vec4 vCol; in vec2 vLight; in float vDist; in vec3 vPos;
        out vec4 o;
        void main() {
            vec4 t = texture(uTex, vUV);
            float a = t.a * vCol.a;
            if (a < 0.1) discard;
            vec3 warm = vec3(1.0, 0.84, 0.56);
            vec3 L = vec3(max(vLight.x * uSky, uMin));
            L = max(L, warm * vLight.y);
            if (uHeld.w > 0.0) {
                float hd = clamp(1.0 - length(vPos - uHeld.xyz) / uHeld.w, 0.0, 1.0);
                L = max(L, warm * hd * 0.95);
            }
            vec3 c = t.rgb * vCol.rgb * L;
            float f = clamp((vDist - uFogS) / (uFogE - uFogS), 0.0, 1.0);
            o = vec4(mix(c, uFog, f), a);
        }""".trimIndent()

    private var prog = 0
    private var uMVP = 0
    private var uCam = 0
    private var uSky = 0
    private var uMin = 0
    private var uFog = 0
    private var uFogS = 0
    private var uFogE = 0
    private var uFogEnabled = 0
    private var uShadersEnabled = 0
    private var uHeld = 0
    private var dynBuf = 0
    private var dynFb: FloatBuffer = ByteBuffer.allocateDirect(4 * 4096).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val gl = HashMap<Long, ChunkGL>()
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val planes = Array(6) { FloatArray(4) }
    private var aspect = 1.7f
    private var last = System.nanoTime()
    private var frames = 0
    private var fpsT = 0f
    private var frameNo = 0
    private var bobT = 0f
    private val cols = Tex.COLS.toFloat()

    override fun onSurfaceCreated(g: GL10?, c: EGLConfig?) {
        prog = link(VS, FS)
        uMVP = GLES30.glGetUniformLocation(prog, "uMVP")
        uCam = GLES30.glGetUniformLocation(prog, "uCam")
        uSky = GLES30.glGetUniformLocation(prog, "uSky")
        uMin = GLES30.glGetUniformLocation(prog, "uMin")
        uFog = GLES30.glGetUniformLocation(prog, "uFog")
        uFogS = GLES30.glGetUniformLocation(prog, "uFogS")
        uFogE = GLES30.glGetUniformLocation(prog, "uFogE")
        uFogEnabled = GLES30.glGetUniformLocation(prog, "uFogEnabled")
        uShadersEnabled = GLES30.glGetUniformLocation(prog, "uShadersEnabled")
        uHeld = GLES30.glGetUniformLocation(prog, "uHeld")
        val ids = IntArray(2)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, Atlas.bitmap, 0)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glGenBuffers(1, ids, 1)
        dynBuf = ids[1]
        gl.clear()
        for (ch in s.world.chunks.values) ch.meshed = 0
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(g: GL10?, w: Int, h: Int) {
        GLES30.glViewport(0, 0, w, h)
        aspect = w.toFloat() / max(1, h)
    }

    private fun link(vs: String, fs: String): Int {
        fun sh(type: Int, src: String): Int {
            val id = GLES30.glCreateShader(type)
            GLES30.glShaderSource(id, src)
            GLES30.glCompileShader(id)
            return id
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, sh(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, sh(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        return p
    }

    private fun upload(buf: Int, data: FloatArray, n: Int) {
        val fb = ByteBuffer.allocateDirect(max(4, n * 4)).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data, 0, n)
        fb.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, n * 4, fb, GLES30.GL_STATIC_DRAW)
    }

    private fun bindAttribs() {
        val st = 44
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, st, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, st, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, st, 20)
        GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribPointer(3, 2, GLES30.GL_FLOAT, false, st, 36)
    }

    private fun drawBuf(buf: Int, n: Int, mode: Int = GLES30.GL_TRIANGLES) {
        if (n == 0) return
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf)
        bindAttribs()
        GLES30.glDrawArrays(mode, 0, n / 11)
    }

    private fun drawDyn(fb: FB, mode: Int = GLES30.GL_TRIANGLES) {
        if (fb.n == 0) return
        if (dynFb.capacity() < fb.n) dynFb = ByteBuffer.allocateDirect(fb.n * 4 * 2).order(ByteOrder.nativeOrder()).asFloatBuffer()
        dynFb.clear()
        dynFb.put(fb.a, 0, fb.n)
        dynFb.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynBuf)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fb.n * 4, dynFb, GLES30.GL_STREAM_DRAW)
        bindAttribs()
        GLES30.glDrawArrays(mode, 0, fb.n / 11)
    }

    private fun setFog(c: FloatArray, start: Float, end: Float) {
        GLES30.glUniform3f(uFog, c[0], c[1], c[2])
        GLES30.glUniform1f(uFogS, start)
        GLES30.glUniform1f(uFogE, end)
    }

    private fun extractFrustum() {
        for (i in 0 until 6) {
            val a = i / 2
            val sgn = if (i % 2 == 0) 1f else -1f
            for (k in 0 until 4) {
                val r3 = vp[k * 4 + 3]
                val rk = vp[k * 4 + a]
                planes[i][k] = r3 + sgn * rk
            }
        }
    }

    private fun visible(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): Boolean {
        for (p in planes) {
            val px = if (p[0] >= 0) x1 else x0
            val py = if (p[1] >= 0) y1 else y0
            val pz = if (p[2] >= 0) z1 else z0
            if (p[0] * px + p[1] * py + p[2] * pz + p[3] < 0) return false
        }
        return true
    }

    private fun quad(fb: FB, a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, r: Float, g: Float, bl: Float, al: Float) {
        val u = 0.5f / cols
        val v = 0.5f / Tex.rows
        for (p in arrayOf(a, b, c, a, c, d)) fb.v(p[0], p[1], p[2], u, v, r, g, bl, al, 1f, 1f)
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun addMob(fb: FB, m: Mob) {
        val cy = cos(m.yaw)
        val sy = sin(m.yaw)
        val shades = floatArrayOf(0.8f, 0.8f, 1f, 0.5f, 0.65f, 0.65f)
        val faces = arrayOf(
            intArrayOf(1, 5, 7, 3), intArrayOf(0, 4, 6, 2), intArrayOf(2, 3, 7, 6),
            intArrayOf(0, 1, 5, 4), intArrayOf(4, 5, 7, 6), intArrayOf(0, 1, 3, 2)
        )
        val u = 0.5f / cols
        val vv = 0.5f / Tex.rows
        for (p in m.type.parts) {
            val ang = if (p.swing != 0f && m.moving) sin(m.walkT) * p.swing else 0f
            val ca = cos(ang)
            val sa = sin(ang)
            val corners = Array(8) { FloatArray(3) }
            for (i in 0 until 8) {
                val ix = i and 1
                val iy = (i shr 1) and 1
                val iz = (i shr 2) and 1
                val lx = (ix - 0.5f) * p.w
                var ly = (iy - 0.5f) * p.h
                var lz = (iz - 0.5f) * p.d
                if (p.swing != 0f) {
                    val qy = ly - p.h / 2f
                    val ry = qy * ca - lz * sa
                    val rz = qy * sa + lz * ca
                    ly = ry + p.h / 2f
                    lz = rz
                }
                val px = p.x + lx
                val py = p.y + ly
                val pz = p.z + lz
                corners[i][0] = m.x + px * cy + pz * sy
                corners[i][1] = m.y + py
                corners[i][2] = m.z - px * sy + pz * cy
            }
            var r = ((p.color shr 16) and 255) / 255f
            var g = ((p.color shr 8) and 255) / 255f
            var b = (p.color and 255) / 255f
            if (m.hurt > 0f) {
                r = 1f; g *= 0.4f; b *= 0.4f
            }
            if (m.fuse > 0f && (m.fuse * 8f).toInt() % 2 == 0) {
                r = 1f; g = 1f; b = 1f
            }
            for (f in 0 until 6) {
                val q = faces[f]
                val sh = shades[f]
                for (k in intArrayOf(0, 1, 2, 0, 2, 3)) {
                    val c = corners[q[k]]
                    fb.v(c[0], c[1], c[2], u, vv, r * sh, g * sh, b * sh, 1f, 1f, 0.6f)
                }
            }
        }
    }

    // ------------------------------------------------------------ рука
    private fun tileUv(t: Int): FloatArray {
        val tx = t % Tex.COLS
        val ty = t / Tex.COLS
        val e = 0.003f
        val rows = Tex.rows.toFloat()
        return floatArrayOf((tx + e) / cols, (tx + 1 - e) / cols, (ty + e) / rows, (ty + 1 - e) / rows)
    }

    private fun box(
        fb: FB, cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float,
        top: Int, side: Int, bottom: Int, r: Float, g: Float, b: Float,
        tr: Float = 1f, tg: Float = 1f, tb: Float = 1f
    ) {
        val faces = arrayOf(
            intArrayOf(1, 5, 7, 3), intArrayOf(0, 4, 6, 2), intArrayOf(2, 3, 7, 6),
            intArrayOf(0, 1, 5, 4), intArrayOf(4, 5, 7, 6), intArrayOf(0, 1, 3, 2)
        )
        val shades = floatArrayOf(0.8f, 0.8f, 1f, 0.55f, 0.7f, 0.7f)
        val px = FloatArray(8)
        val py = FloatArray(8)
        val pz = FloatArray(8)
        for (i in 0 until 8) {
            px[i] = cx + ((i and 1) - 0.5f) * sx
            py[i] = cy + (((i shr 1) and 1) - 0.5f) * sy
            pz[i] = cz + (((i shr 2) and 1) - 0.5f) * sz
        }
        for (f in 0 until 6) {
            val uv = tileUv(
                when (f) {
                    2 -> top
                    3 -> bottom
                    else -> side
                }
            )
            val q = faces[f]
            val sh = shades[f]
            var cr = r
            var cg = g
            var cb = b
            if (f == 2) {
                cr *= tr; cg *= tg; cb *= tb
            }
            val us = floatArrayOf(uv[0], uv[1], uv[1], uv[0])
            val vs = floatArrayOf(uv[3], uv[3], uv[2], uv[2])
            for (k in intArrayOf(0, 1, 2, 0, 2, 3)) {
                fb.v(px[q[k]], py[q[k]], pz[q[k]], us[k], vs[k], cr * sh, cg * sh, cb * sh, 1f, 1f, 0f)
            }
        }
    }

    private fun sprite(fb: FB, cx: Float, cy: Float, cz: Float, size: Float, tile: Int, r: Float, g: Float, b: Float) {
        val uv = tileUv(tile)
        val h = size / 2f
        val xs = floatArrayOf(-h, h, h, -h)
        val ys = floatArrayOf(-h, -h, h, h)
        val us = floatArrayOf(uv[0], uv[1], uv[1], uv[0])
        val vs = floatArrayOf(uv[3], uv[3], uv[2], uv[2])
        for (layer in 0..1) {
            val z = cz + layer * 0.012f
            val sh = if (layer == 0) 1f else 0.7f
            for (k in intArrayOf(0, 1, 2, 0, 2, 3)) {
                fb.v(cx + xs[k], cy + ys[k], z, us[k], vs[k], r * sh, g * sh, b * sh, 1f, 1f, 0f)
            }
        }
    }

    private fun drawHand(skyLevel: Float, dt: Float) {
        val p = s.player
        val sp = hypot(p.vx, p.vz)
        if (p.onGround && sp > 0.5f) bobT += dt * sp * 1.6f
        val held = s.inv.slots[s.hudSel]
        val swingA = max(if (s.swingPhase > 0f) abs(sin(s.swingPhase)) else 0f, sin(s.useSwing * 3.1415927f))

        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUniform3f(uCam, 0f, 0f, 0f)
        GLES30.glUniform1f(uSky, max(skyLevel, 0.6f))
        GLES30.glUniform4f(uHeld, 0f, 0f, 0f, 0f)
        setFog(floatArrayOf(0f, 0f, 0f), 1e6f, 2e6f)

        val m = FloatArray(16)
        val m2 = FloatArray(16)
        val mvp = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(
            m, 0,
            0.36f + sin(bobT) * 0.012f,
            -0.33f - abs(cos(bobT)) * 0.014f - swingA * 0.09f,
            -0.62f - swingA * 0.14f
        )
        Matrix.rotateM(m, 0, -swingA * 48f, 1f, 0f, 0f)
        Matrix.rotateM(m, 0, -swingA * 14f, 0f, 1f, 0f)

        // рука
        val fa = FB(1024)
        box(fa, 0.1f, -0.12f, 0.2f, 0.12f, 0.12f, 0.36f, 0, 0, 0, 0.78f, 0.55f, 0.42f)
        box(fa, 0.1f, -0.12f, 0.36f, 0.14f, 0.14f, 0.2f, 0, 0, 0, 0.23f, 0.36f, 0.63f)
        Matrix.multiplyMM(mvp, 0, proj, 0, m, 0)
        GLES30.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        drawDyn(fa)

        // предмет
        if (held != null) {
            val d = held.def
            val b = d.block
            val fi = FB(1024)
            System.arraycopy(m, 0, m2, 0, 16)
            Matrix.translateM(m2, 0, 0.0f, 0.06f, -0.02f)
            if (b != null && b.render != R_CROSS && b.render != R_LIQUID) {
                Matrix.rotateM(m2, 0, 35f, 0f, 1f, 0f)
                Matrix.rotateM(m2, 0, -10f, 1f, 0f, 0f)
                var r = 1f
                var g = 1f
                var bl = 1f
                var tr = 1f
                var tg = 1f
                var tb = 1f
                when (b.tint) {
                    1 -> {
                        tr = 0.55f; tg = 0.76f; tb = 0.36f
                    }
                    2 -> {
                        r = 0.55f; g = 0.76f; bl = 0.36f
                    }
                    3 -> {
                        r = ((b.tintColor shr 16) and 255) / 255f
                        g = ((b.tintColor shr 8) and 255) / 255f
                        bl = (b.tintColor and 255) / 255f
                    }
                }
                val hgt = if (b.render == R_SLAB) 0.14f else 0.28f
                box(fi, 0f, if (b.render == R_SLAB) -0.07f else 0f, 0f, 0.28f, hgt, 0.28f, b.top, b.side, b.bottom, r, g, bl, tr, tg, tb)
            } else {
                Matrix.rotateM(m2, 0, -30f, 0f, 1f, 0f)
                Matrix.rotateM(m2, 0, 8f, 1f, 0f, 0f)
                val tile = if (b != null) b.side else d.icon
                var r = 1f
                var g = 1f
                var bl = 1f
                if (b != null && b.tint == 2) {
                    r = 0.55f; g = 0.76f; bl = 0.36f
                }
                sprite(fi, 0f, 0.05f, 0f, 0.42f, tile, r, g, bl)
            }
            Matrix.multiplyMM(mvp, 0, proj, 0, m2, 0)
            GLES30.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            drawDyn(fi)
        }
    }

    override fun onDrawFrame(g: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
        last = now
        frames++
        fpsT += dt
        if (fpsT >= 1f) {
            s.fps = frames; frames = 0; fpsT = 0f
        }
        s.tick(dt)

        // загрузка мешей
        var n = 0
        while (n < 4) {
            val m = s.meshOut.poll() ?: break
            n++
            if (s.world.chunkAt(m.cx, m.cz) == null) continue
            val c = gl.getOrPut(s.world.key(m.cx, m.cz)) {
                val ids = IntArray(2)
                GLES30.glGenBuffers(2, ids, 0)
                ChunkGL().also { it.vo = ids[0]; it.vt = ids[1] }
            }
            upload(c.vo, m.data.o, m.data.on); c.no = m.data.on
            upload(c.vt, m.data.t, m.data.tn); c.nt = m.data.tn
        }
        frameNo++
        if (frameNo % 60 == 0) {
            val it = gl.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                val cx = (e.key shr 32).toInt()
                val cz = e.key.toInt()
                if (s.world.chunkAt(cx, cz) == null) {
                    GLES30.glDeleteBuffers(2, intArrayOf(e.value.vo, e.value.vt), 0)
                    it.remove()
                }
            }
        }
        val p = s.player
        val pcx = floor(p.x / 16f).toInt()
        val pcz = floor(p.z / 16f).toInt()
        if (!s.loaded) {
            var cnt = 0
            for (dx in -1..1) for (dz in -1..1) if (gl.containsKey(s.world.key(pcx + dx, pcz + dz))) cnt++
            if (cnt >= 9) s.loaded = true
        }

        // небо
        val sunH = s.sunHeight()
        val b = smoothstep(-0.12f, 0.25f, sunH)
        val skyLevel = 0.14f + 0.86f * b
        val dusk = max(0f, 1f - abs(sunH) / 0.25f) * 0.45f
        val fog = floatArrayOf(
            (0.02f + (0.50f - 0.02f) * b) * (1 - dusk) + 1.0f * dusk * 0.8f,
            (0.03f + (0.70f - 0.03f) * b) * (1 - dusk) + 0.5f * dusk * 0.8f,
            (0.08f + (1.00f - 0.08f) * b) * (1 - dusk) + 0.3f * dusk * 0.8f
        )
        val eyeX = p.x
        val eyeY = p.y + 1.62f
        val eyeZ = p.z
        val eyeId = s.world.blockAt(floor(eyeX).toInt(), floor(eyeY).toInt(), floor(eyeZ).toInt())
        val underwater = Reg.blocks[eyeId]?.liquid == true
        val rd = Settings.renderDist
        var fogS = rd * 16f * 0.55f
        var fogE = rd * 16f - 4f
        val clear = fog.copyOf()
        if (underwater) {
            clear[0] = 0.08f * (0.3f + 0.7f * b); clear[1] = 0.2f * (0.3f + 0.7f * b); clear[2] = 0.5f * (0.3f + 0.7f * b)
            fogS = 1f; fogE = 18f
        }
        GLES30.glClearColor(clear[0], clear[1], clear[2], 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (!s.loaded) return

        val fx = -sin(p.yaw) * cos(p.pitch)
        val fy = sin(p.pitch)
        val fz = -cos(p.yaw) * cos(p.pitch)
        val far = max(300f, (rd + 2) * 16f * 1.6f)
        Matrix.perspectiveM(proj, 0, Settings.fov, aspect, 0.05f, far)
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, eyeX + fx, eyeY + fy, eyeZ + fz, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        extractFrustum()

        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(uMVP, 1, false, vp, 0)
        GLES30.glUniform3f(uCam, eyeX, eyeY, eyeZ)
        GLES30.glUniform1f(uSky, skyLevel)
        GLES30.glUniform1f(uMin, 0.07f)
        GLES30.glUniform1f(uFogEnabled, if (Settings.fog) 1f else 0f)
        GLES30.glUniform1f(uShadersEnabled, if (Settings.shaders) 1f else 0f)
        GLES30.glUniform4f(uHeld, 0f, 0f, 0f, 0f)

        // солнце и луна
        if (!underwater) {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            setFog(clear, 1e6f, 2e6f)
            GLES30.glUniform1f(uSky, 1f)
            val th = s.dayTime / com.fcraft.game.DAY * 6.2831855f
            val fbS = FB(1024)
            for (body in 0..1) {
                val sg = if (body == 0) 1f else -1f
                val dx = cos(th) * sg
                val dy = sin(th) * sg
                val cx = eyeX + dx * 120f
                val cy = eyeY + dy * 120f
                val cz = eyeZ
                val sz = if (body == 0) 12f else 8f
                val ux = -sin(th) * sg
                val uy = cos(th) * sg
                val a = floatArrayOf(cx - ux * sz, cy - uy * sz, cz - sz)
                val bb = floatArrayOf(cx - ux * sz, cy - uy * sz, cz + sz)
                val c = floatArrayOf(cx + ux * sz, cy + uy * sz, cz + sz)
                val d = floatArrayOf(cx + ux * sz, cy + uy * sz, cz - sz)
                if (body == 0) quad(fbS, a, bb, c, d, 1f, 0.95f, 0.6f, 1f) else quad(fbS, a, bb, c, d, 0.85f, 0.9f, 1f, 1f)
            }
            drawDyn(fbS)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glUniform1f(uSky, skyLevel)
        }
        setFog(clear, fogS, fogE)

        // свет от предмета в руке (факел, светокамень и т.п.)
        val hs = s.inv.slots[s.hudSel]
        val hl = hs?.def?.block?.light ?: 0
        if (hl > 0) GLES30.glUniform4f(uHeld, eyeX, eyeY - 0.3f, eyeZ, hl * 0.85f)

        // непрозрачные чанки
        val vis = ArrayList<Pair<Float, ChunkGL>>()
        for ((k, c) in gl) {
            val cx = (k shr 32).toInt()
            val cz = k.toInt()
            if (!visible(cx * 16f, 0f, cz * 16f, cx * 16f + 16f, 96f, cz * 16f + 16f)) continue
            drawBuf(c.vo, c.no)
            if (c.nt > 0) {
                val dx = cx * 16f + 8f - eyeX
                val dz = cz * 16f + 8f - eyeZ
                vis.add((dx * dx + dz * dz) to c)
            }
        }

        // мобы
        val fbM = FB(1 shl 14)
        for (m in s.mobs) {
            val dx = m.x - eyeX
            val dz = m.z - eyeZ
            if (dx * dx + dz * dz > 48f * 48f) continue
            addMob(fbM, m)
        }
        drawDyn(fbM)

        // рамка выделения
        val t = s.target
        if (t != null && s.overlay == Overlay.NONE) {
            val e = 0.002f
            val x0 = t.x - e
            val y0 = t.y - e
            val z0 = t.z - e
            val x1 = t.x + 1 + e
            val y1 = t.y + 1 + e
            val z1 = t.z + 1 + e
            val fl = FB(1024)
            val u = 0.5f / cols
            val v = 0.5f / Tex.rows
            fun ln(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
                fl.v(ax, ay, az, u, v, 0f, 0f, 0f, 1f, 1f, 1f)
                fl.v(bx, by, bz, u, v, 0f, 0f, 0f, 1f, 1f, 1f)
            }
            ln(x0, y0, z0, x1, y0, z0); ln(x0, y1, z0, x1, y1, z0); ln(x0, y0, z1, x1, y0, z1); ln(x0, y1, z1, x1, y1, z1)
            ln(x0, y0, z0, x0, y1, z0); ln(x1, y0, z0, x1, y1, z0); ln(x0, y0, z1, x0, y1, z1); ln(x1, y0, z1, x1, y1, z1)
            ln(x0, y0, z0, x0, y0, z1); ln(x1, y0, z0, x1, y0, z1); ln(x0, y1, z0, x0, y1, z1); ln(x1, y1, z0, x1, y1, z1)
            GLES30.glLineWidth(3f)
            drawDyn(fl, GLES30.GL_LINES)
        }

        // прозрачные слои
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)
        vis.sortByDescending { it.first }
        for ((_, c) in vis) drawBuf(c.vt, c.nt)
        if (t != null && s.breakProg > 0f) {
            val e = 0.003f
            val x0 = t.x - e
            val y0 = t.y - e
            val z0 = t.z - e
            val x1 = t.x + 1 + e
            val y1 = t.y + 1 + e
            val z1 = t.z + 1 + e
            val al = 0.08f + s.breakProg.coerceIn(0f, 1f) * 0.6f
            val fc = FB(512)
            quad(fc, floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x0, y1, z0), 0f, 0f, 0f, al)
            quad(fc, floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1), 0f, 0f, 0f, al)
            quad(fc, floatArrayOf(x0, y0, z0), floatArrayOf(x0, y0, z1), floatArrayOf(x0, y1, z1), floatArrayOf(x0, y1, z0), 0f, 0f, 0f, al)
            quad(fc, floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x1, y1, z0), 0f, 0f, 0f, al)
            quad(fc, floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1), floatArrayOf(x0, y0, z1), 0f, 0f, 0f, al)
            quad(fc, floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1), 0f, 0f, 0f, al)
            drawDyn(fc)
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        // рука поверх всего
        if (s.overlay == Overlay.NONE) drawHand(skyLevel, dt)
    }
}
