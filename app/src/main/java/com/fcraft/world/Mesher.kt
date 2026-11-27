package com.fcraft.world

import com.fcraft.core.*

class MeshData(val o: FloatArray, val on: Int, val t: FloatArray, val tn: Int)

class FB(cap: Int = 1 shl 15) {
    var a = FloatArray(cap)
    var n = 0
    fun v(
        x: Float, y: Float, z: Float, u: Float, v: Float,
        r: Float, g: Float, b: Float, al: Float, sky: Float, blk: Float
    ) {
        if (n + 11 > a.size) a = a.copyOf(a.size * 2)
        a[n++] = x; a[n++] = y; a[n++] = z; a[n++] = u; a[n++] = v
        a[n++] = r; a[n++] = g; a[n++] = b; a[n++] = al; a[n++] = sky; a[n++] = blk
    }
}

object Mesher {
    private val NX = intArrayOf(1, -1, 0, 0, 0, 0)
    private val NY = intArrayOf(0, 0, 1, -1, 0, 0)
    private val NZ = intArrayOf(0, 0, 0, 0, 1, -1)
    private val UX = intArrayOf(0, 0, 1, 1, 1, -1)
    private val UZ = intArrayOf(-1, 1, 0, 0, 0, 0)
    private val VY = intArrayOf(1, 1, 0, 0, 1, 1)
    private val VZ = intArrayOf(0, 0, 1, -1, 0, 0)
    private val SHADE = floatArrayOf(0.8f, 0.8f, 1f, 0.5f, 0.65f, 0.65f)
    private val AO = floatArrayOf(1f, 0.8f, 0.62f, 0.45f)
    private val SU = intArrayOf(-1, 1, 1, -1)
    private val SV = intArrayOf(-1, -1, 1, 1)
    private const val PW = CS + 2

    fun build(world: World, ch: Chunk): MeshData {
        val ox = ch.cx * CS
        val oz = ch.cz * CS
        val ph = WH + 2
        val pad = ShortArray(PW * ph * PW)
        val hm = IntArray(PW * PW)
        val bedrock = Reg.id("bedrock").toShort()
        fun pi(x: Int, y: Int, z: Int) = ((y + 1) * PW + (z + 1)) * PW + (x + 1)
        for (lz in -1..CS) for (lx in -1..CS) {
            val inside = lx in 0 until CS && lz in 0 until CS
            val c = if (inside) ch else world.chunkAt((ox + lx) shr 4, (oz + lz) shr 4)
            val cxl = (ox + lx) and 15
            val czl = (oz + lz) and 15
            pad[pi(lx, -1, lz)] = bedrock
            if (c != null) {
                for (y in 0 until WH) pad[pi(lx, y, lz)] = c.blocks[(y * CS + czl) * CS + cxl]
                hm[(lz + 1) * PW + lx + 1] = c.height[czl * CS + cxl]
            }
        }
        fun gb(x: Int, y: Int, z: Int): Int = if (y >= WH) 0 else pad[pi(x, y, z)].toInt()
        fun opq(x: Int, y: Int, z: Int): Boolean {
            val id = gb(x, y, z)
            if (id == 0) return false
            return Reg.blocks[id]?.render == R_CUBE
        }

        // источники света рядом
        val lights = ArrayList<IntArray>()
        for (dz in -1..1) for (dx in -1..1) {
            val c = world.chunkAt(ch.cx + dx, ch.cz + dz) ?: continue
            if (!c.hasLight) continue
            for (i in c.blocks.indices) {
                val id = c.blocks[i].toInt()
                if (id == 0) continue
                val lv = Reg.blocks[id]?.light ?: 0
                if (lv > 0 && lights.size < 300) {
                    val x = i % CS
                    val z = (i / CS) % CS
                    val y = i / (CS * CS)
                    lights.add(intArrayOf(c.cx * CS + x, y, c.cz * CS + z, lv))
                }
            }
        }
        fun blockLight(wx: Float, wy: Float, wz: Float): Float {
            var best = 0f
            for (l in lights) {
                val dx = wx - (l[0] + 0.5f)
                val dy = wy - (l[1] + 0.5f)
                val dz = wz - (l[2] + 0.5f)
                val d = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                val v = (l[3] - d) / 15f
                if (v > best) best = v
            }
            return best.coerceIn(0f, 1f)
        }
        fun skyLight(x: Int, y: Int, z: Int): Float {
            val hh = hm[(z + 1) * PW + x + 1]
            return if (y > hh) 1f else maxOf(0.0f, 1f - (hh - y + 1) * 0.12f)
        }

        val rows = Tex.rows.toFloat()
        val cols = Tex.COLS.toFloat()
        val o = FB()
        val t = FB()

        fun grassTint(c: Int, comp: Int) = ((c shr comp) and 255) / 255f

        for (y in 0 until WH) for (z in 0 until CS) for (x in 0 until CS) {
            val id = ch.blocks[(y * CS + z) * CS + x].toInt()
            if (id == 0) continue
            val b = Reg.blocks[id] ?: continue
            val wx = ox + x
            val wz = oz + z
            // цвет-множитель
            var tr = 1f
            var tg = 1f
            var tb = 1f
            if (b.tint == 2 || b.tint == 3) {
                val c = if (b.tint == 2) ch.grass else b.tintColor
                tr = grassTint(c, 16); tg = grassTint(c, 8); tb = grassTint(c, 0)
            }
            val selfBlk = if (b.light > 0) b.light / 15f else 0f

            fun uvs(tile: Int): FloatArray {
                val tx = tile % Tex.COLS
                val ty = tile / Tex.COLS
                val e = 0.003f
                return floatArrayOf(
                    (tx + e) / cols, (tx + 1 - e) / cols, (ty + e) / rows, (ty + 1 - e) / rows
                )
            }

            if (b.render == R_CROSS) {
                val uv = uvs(b.side)
                val sky = skyLight(x, y, z)
                val blk = maxOf(selfBlk, blockLight(wx + 0.5f, y + 0.5f, wz + 0.5f))
                val fx = wx.toFloat()
                val fz = wz.toFloat()
                val fy = y.toFloat()
                val buf = o
                val s = 0.9f
                // две диагональные плоскости
                val q = arrayOf(
                    floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f),
                    floatArrayOf(0.1f, 0.9f, 0.9f, 0.1f)
                )
                for (pl in q) {
                    buf.v(fx + pl[0], fy, fz + pl[1], uv[0], uv[3], tr * s, tg * s, tb * s, 1f, sky, blk)
                    buf.v(fx + pl[2], fy, fz + pl[3], uv[1], uv[3], tr * s, tg * s, tb * s, 1f, sky, blk)
                    buf.v(fx + pl[2], fy + 1, fz + pl[3], uv[1], uv[2], tr * s, tg * s, tb * s, 1f, sky, blk)
                    buf.v(fx + pl[0], fy, fz + pl[1], uv[0], uv[3], tr * s, tg * s, tb * s, 1f, sky, blk)
                    buf.v(fx + pl[2], fy + 1, fz + pl[3], uv[1], uv[2], tr * s, tg * s, tb * s, 1f, sky, blk)
                    buf.v(fx + pl[0], fy + 1, fz + pl[1], uv[0], uv[2], tr * s, tg * s, tb * s, 1f, sky, blk)
                }
                continue
            }

            val liquid = b.render == R_LIQUID
            val isLava = liquid && b.light > 0
            val slab = b.render == R_SLAB
            for (f in 0 until 6) {
                val nx = x + NX[f]
                val ny = y + NY[f]
                val nz = z + NZ[f]
                val nid = gb(nx, ny, nz)
                val nb = if (nid == 0) null else Reg.blocks[nid]
                val draw: Boolean = when (b.render) {
                    R_CUBE -> nb == null || nb.render != R_CUBE
                    R_CUTOUT -> nb == null || (nb.render != R_CUBE && nid != id)
                    R_TRANS -> nid != id && (nb == null || nb.render != R_CUBE)
                    R_LIQUID -> nid != id && (nb == null || (nb.render != R_CUBE && !nb.liquid))
                    R_SLAB -> f == 2 || nb == null || nb.render != R_CUBE
                    else -> false
                }
                if (!draw) continue
                val tile = when (f) {
                    2 -> b.top; 3 -> b.bottom; else -> b.side
                }
                var r = tr
                var g = tg
                var bl = tb
                if (b.tint == 1 && f == 2) {
                    r = grassTint(ch.grass, 16); g = grassTint(ch.grass, 8); bl = grassTint(ch.grass, 0)
                }
                val uv = uvs(tile)
                val shade = if (isLava) 1f else SHADE[f]
                val sky = skyLight(nx, ny, nz)
                val blk = maxOf(selfBlk, blockLight(wx + 0.5f + NX[f] * 0.6f, y + 0.5f + NY[f] * 0.6f, wz + 0.5f + NZ[f] * 0.6f))
                val buf = if (b.render == R_TRANS || (liquid && !isLava)) t else o
                val alpha = if (liquid && !isLava) 0.63f else 1f
                var h = 1f
                if (slab) h = 0.5f
                var topLow = 0f
                if (liquid && !isLava && gb(x, y + 1, z) != id) topLow = 0.12f
                if (liquid && isLava && gb(x, y + 1, z) != id) topLow = 0.12f
                val px = FloatArray(4)
                val py = FloatArray(4)
                val pz = FloatArray(4)
                val pu = FloatArray(4)
                val pv = FloatArray(4)
                val ao = FloatArray(4)
                for (k in 0 until 4) {
                    val su = SU[k]
                    val sv = SV[k]
                    // базис грани
                    val ux = UX[f]
                    val uz = UZ[f]
                    val vy = VY[f]
                    val vz = VZ[f]
                    var vx = 0.5f + 0.5f * NX[f] + 0.5f * su * ux
                    var vyy = 0.5f + 0.5f * NY[f] + 0.5f * sv * vy
                    var vzz = 0.5f + 0.5f * NZ[f] + 0.5f * su * uz + 0.5f * sv * vz
                    if (slab || topLow > 0f) {
                        vyy = when (f) {
                            2 -> h - topLow
                            3 -> 0f
                            else -> if (sv > 0) h - topLow else 0f
                        }
                    }
                    px[k] = wx + vx; py[k] = y + vyy; pz[k] = wz + vzz
                    pu[k] = if (su > 0) uv[1] else uv[0]
                    pv[k] = if (f == 2 || f == 3) (if (sv > 0) uv[3] else uv[2]) else (1f - vyy).let { yy -> uv[2] + (uv[3] - uv[2]) * yy.coerceIn(0f, 1f) }
                    // AO только для непрозрачных кубов
                    if (b.render == R_CUBE) {
                        val s1x = nx + su * ux
                        val s1z = nz + su * uz
                        val s1y = ny + sv * vy * 0
                        // боковые соседи по двум касательным осям
                        val a1 = opq(nx + su * ux, ny, nz + su * uz)
                        val a2 = opq(nx, ny + sv * vy, nz + sv * vz)
                        val a3 = opq(nx + su * ux, ny + sv * vy, nz + su * uz + sv * vz)
                        val cnt = if (a1 && a2) 3 else (if (a1) 1 else 0) + (if (a2) 1 else 0) + (if (a3) 1 else 0)
                        ao[k] = AO[cnt]
                        if (s1x == Int.MIN_VALUE || s1z == Int.MIN_VALUE || s1y == Int.MIN_VALUE) ao[k] = 1f
                    } else ao[k] = 1f
                }
                fun emit(k: Int) {
                    val m = shade * ao[k]
                    buf.v(px[k], py[k], pz[k], pu[k], pv[k], r * m, g * m, bl * m, alpha, sky, blk)
                }
                if (ao[0] + ao[2] > ao[1] + ao[3]) {
                    emit(0); emit(1); emit(3); emit(1); emit(2); emit(3)
                } else {
                    emit(0); emit(1); emit(2); emit(0); emit(2); emit(3)
                }
            }
        }
        return MeshData(o.a, o.n, t.a, t.n)
    }
}
