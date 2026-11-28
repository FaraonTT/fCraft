package com.fcraft.core

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

const val OP = -16777216 // 0xFF000000

fun rgb(c: Int): Int = OP or (c and 0xFFFFFF)

fun addC(c: Int, d: Int): Int {
    val a = c ushr 24
    val r = (((c shr 16) and 255) + d).coerceIn(0, 255)
    val g = (((c shr 8) and 255) + d).coerceIn(0, 255)
    val b = ((c and 255) + d).coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun mulC(c: Int, f: Float): Int {
    val a = c ushr 24
    val r = (((c shr 16) and 255) * f).toInt().coerceIn(0, 255)
    val g = (((c shr 8) and 255) * f).toInt().coerceIn(0, 255)
    val b = ((c and 255) * f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun mixC(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int): Int = ((((a shr s) and 255) * (1 - t)) + (((b shr s) and 255) * t)).toInt().coerceIn(0, 255)
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Холст 16x16 для рисования одной текстуры. */
class P(val px: IntArray, seed: Int) {
    val r = java.util.Random(seed.toLong() * 7919L + 13L)
    fun ri(n: Int): Int = r.nextInt(n)
    fun set(x: Int, y: Int, c: Int) {
        if (x in 0..15 && y in 0..15) px[y * 16 + x] = c
    }

    fun get(x: Int, y: Int): Int = px[y * 16 + x]
    fun fill(c: Int) = px.fill(c)
    fun jit(c: Int, amt: Int): Int = addC(c, r.nextInt(amt * 2 + 1) - amt)
    fun noise(base: Int, amt: Int) {
        for (i in 0 until 256) px[i] = jit(rgb(base), amt)
    }

    fun rect(x0: Int, y0: Int, w: Int, h: Int, c: Int) {
        for (y in y0 until y0 + h) for (x in x0 until x0 + w) set(x, y, c)
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var e = dx + dy
        while (true) {
            set(x, y, c)
            if (x == x1 && y == y1) break
            val e2 = 2 * e
            if (e2 >= dy) {
                e += dy
                x += sx
            }
            if (e2 <= dx) {
                e += dx
                y += sy
            }
        }
    }

    fun disc(cx: Float, cy: Float, rad: Float, c: Int) {
        for (y in 0 until 16) for (x in 0 until 16) {
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            if (sqrt(dx * dx + dy * dy) <= rad) set(x, y, c)
        }
    }

    /** Бесшовное поле плавного шума из cells x cells узлов. */
    fun field(cells: Int): FloatArray {
        val lat = FloatArray(cells * cells) { r.nextFloat() }
        val out = FloatArray(256)
        for (y in 0 until 16) for (x in 0 until 16) {
            val gx = x * cells / 16f
            val gy = y * cells / 16f
            val x0 = gx.toInt()
            val y0 = gy.toInt()
            val fx = gx - x0
            val fy = gy - y0
            val sx = fx * fx * (3 - 2 * fx)
            val sy = fy * fy * (3 - 2 * fy)
            val x1 = (x0 + 1) % cells
            val y1 = (y0 + 1) % cells
            val a = lat[y0 * cells + x0]
            val b = lat[y0 * cells + x1]
            val c = lat[y1 * cells + x0]
            val d = lat[y1 * cells + x1]
            val top = a + (b - a) * sx
            val bot = c + (d - c) * sx
            out[y * 16 + x] = top + (bot - top) * sy
        }
        return out
    }

    /** Пятнистая заливка палитрой — основа «майнкрафтовского» вида. */
    fun palette(cols: IntArray, cells: Int = 4, fine: Float = 0.3f) {
        val f1 = field(cells)
        val f2 = field(cells * 2)
        for (i in 0 until 256) {
            var v = f1[i] * 0.5f + f2[i] * 0.3f + r.nextFloat() * fine * 0.5f
            v = ((v - 0.46f) * 2.4f + 0.5f).coerceIn(0f, 0.999f)
            px[i] = cols[(v * cols.size).toInt()]
        }
    }
}

/** Генераторы текстур. Всё рисуется в коде — внешних картинок нет. */
object G {
    fun shades(c: Int, s: Float): IntArray {
        val b = rgb(c)
        return intArrayOf(mulC(b, 1f - 2.2f * s), mulC(b, 1f - 0.8f * s), mulC(b, 1f + 0.5f * s), mulC(b, 1f + 1.4f * s))
    }

    fun noisy(c: Int, a: Int = 10): (P) -> Unit = { p ->
        p.palette(shades(c, (a / 60f).coerceIn(0.05f, 0.2f)), 4, 0.3f)
    }

    fun grassTop(): (P) -> Unit = { p -> p.palette(shades(0xC8C8C8, 0.1f), 3, 0.4f) }

    fun cobble(base: Int, cells: Int = 10, edge: Float = 0.62f, spread: Float = 0.16f): (P) -> Unit = { p ->
        val sx = FloatArray(cells) { p.ri(160) / 10f }
        val sy = FloatArray(cells) { p.ri(160) / 10f }
        val tone = FloatArray(cells) { 1f - spread + p.r.nextFloat() * spread * 2f }
        val b = rgb(base)
        for (y in 0 until 16) for (x in 0 until 16) {
            var d1 = 1e9f
            var d2 = 1e9f
            var id = 0
            for (i in 0 until cells) {
                var dx = abs(x + 0.5f - sx[i])
                var dy = abs(y + 0.5f - sy[i])
                if (dx > 8f) dx = 16f - dx
                if (dy > 8f) dy = 16f - dy
                val d = sqrt(dx * dx + dy * dy)
                if (d < d1) {
                    d2 = d1; d1 = d; id = i
                } else if (d < d2) d2 = d
            }
            var col = mulC(b, tone[id])
            if (d2 - d1 < 1.1f) col = mulC(col, edge)
            else if (p.ri(100) < 12) col = mulC(col, 0.93f)
            p.set(x, y, col)
        }
    }

    fun ore(stone: Int, spot: Int): (P) -> Unit = { p ->
        p.palette(shades(stone, 0.12f), 4, 0.3f)
        val col = rgb(spot)
        val shape = arrayOf(0 to 0, 1 to 0, 0 to 1, 1 to 1, -1 to 0, 0 to -1)
        repeat(6) {
            val cx = 2 + p.ri(11)
            val cy = 2 + p.ri(11)
            for ((dx, dy) in shape) {
                if ((dx == 0 && dy == 0) || p.ri(100) < 70) {
                    val c = if (p.ri(4) == 0) mulC(col, 1.3f) else if (p.ri(4) == 0) mulC(col, 0.78f) else col
                    p.set(cx + dx, cy + dy, c)
                }
            }
        }
    }

    fun planks(c: Int): (P) -> Unit = { p ->
        val base = rgb(c)
        for (row in 0 until 4) {
            val tone = 0.9f + p.r.nextFloat() * 0.2f
            val y0 = row * 4
            for (y in y0 until y0 + 4) for (x in 0 until 16) {
                var col = mulC(base, tone)
                if (y == y0 + 3) col = mulC(col, 0.7f)
                else if (y == y0) col = mulC(col, 1.08f)
                else if (p.ri(100) < 22) col = mulC(col, 0.9f)
                p.set(x, y, col)
            }
            val jx = (row * 7 + 3) % 16
            for (y in y0 until y0 + 3) p.set(jx, y, mulC(base, 0.74f))
            val sx = p.ri(10)
            val sy = y0 + 1 + p.ri(2)
            for (i in 0..(3 + p.ri(4))) p.set((sx + i) % 16, sy, mulC(base, 0.86f))
        }
    }

    fun logSide(c: Int): (P) -> Unit = { p ->
        val pal = shades(c, 0.16f)
        var idx = p.ri(4)
        for (x in 0 until 16) {
            idx = (idx + p.ri(3) - 1).coerceIn(0, 3)
            for (y in 0 until 16) {
                val k = if (p.ri(100) < 14) (idx + 1).coerceAtMost(3) else if (p.ri(100) < 10) (idx - 1).coerceAtLeast(0) else idx
                p.set(x, y, pal[k])
            }
        }
        val kx = 2 + p.ri(12)
        val ky = 2 + p.ri(12)
        p.set(kx, ky, mulC(rgb(c), 0.6f)); p.set(kx, ky + 1, mulC(rgb(c), 0.7f))
    }

    fun logTop(ring: Int, bark: Int): (P) -> Unit = { p ->
        val barkP = shades(bark, 0.14f)
        val ringP = shades(ring, 0.1f)
        for (y in 0 until 16) for (x in 0 until 16) {
            val d = maxOf(abs(x - 7.5f), abs(y - 7.5f))
            val c = when {
                d > 6.5f -> barkP[p.ri(3)]
                d > 5.5f -> mulC(rgb(ring), 0.78f)
                d.toInt() % 2 == 0 -> ringP[2 + p.ri(2)]
                else -> ringP[p.ri(2)]
            }
            p.set(x, y, c)
        }
        p.rect(7, 7, 2, 2, mulC(rgb(ring), 0.65f))
    }

    fun leaves(c: Int): (P) -> Unit = { p ->
        p.palette(shades(c, 0.2f), 3, 0.5f)
        for (i in 0 until 256) if (p.ri(100) < 9) p.px[i] = 0
    }

    fun bricks(brick: Int, mortar: Int): (P) -> Unit = { p ->
        val bc = rgb(brick)
        val mc = rgb(mortar)
        for (i in 0 until 256) p.px[i] = p.jit(mc, 5)
        for (row in 0 until 4) {
            val off = if (row % 2 == 0) 0 else 4
            for (bx in -1..1) {
                val x0 = bx * 8 + off
                val tone = 0.86f + p.r.nextFloat() * 0.26f
                for (y in row * 4 until row * 4 + 3) for (x in x0 until x0 + 7) {
                    if (x in 0..15) p.set(x, y, mulC(p.jit(bc, 4), if (y == row * 4) tone * 1.1f else tone))
                }
            }
        }
    }

    fun stoneBricks(c: Int): (P) -> Unit = { p ->
        val bc = rgb(c)
        for (i in 0 until 256) p.px[i] = mulC(p.jit(bc, 3), 0.62f)
        for (row in 0 until 2) {
            val off = if (row == 0) 0 else 4
            for (bx in -1..1) {
                val x0 = bx * 8 + off
                val tone = 0.92f + p.r.nextFloat() * 0.16f
                for (y in row * 8 until row * 8 + 7) for (x in x0 until x0 + 7) {
                    if (x in 0..15) {
                        var col = mulC(p.jit(bc, 5), tone)
                        if (y == row * 8) col = mulC(col, 1.12f)
                        p.set(x, y, col)
                    }
                }
            }
        }
    }

    fun dirt(): (P) -> Unit = { p ->
        p.palette(shades(0x866043, 0.13f), 4, 0.35f)
        repeat(6) { p.set(p.ri(16), p.ri(16), rgb(0x5A3E28)) }
        repeat(3) { p.set(p.ri(16), p.ri(16), rgb(0x9A7552)) }
    }

    fun grassSide(dirtC: Int = 0x866043, grass: Int = 0x6A9244): (P) -> Unit = { p ->
        p.palette(shades(dirtC, 0.13f), 4, 0.35f)
        val g = shades(grass, 0.1f)
        for (x in 0 until 16) {
            val d = 3 + p.ri(2)
            for (y in 0 until d) p.set(x, y, g[1 + p.ri(3)])
            if (p.ri(100) < 55) p.set(x, d, g[p.ri(3)])
            if (p.ri(100) < 20) p.set(x, d + 1, g[p.ri(2)])
        }
    }

    fun glass(c: Int, a: Int): (P) -> Unit = { p ->
        for (y in 0 until 16) for (x in 0 until 16) {
            val edge = x == 0 || y == 0 || x == 15 || y == 15
            p.set(x, y, ((if (edge) 0xE0 else a) shl 24) or (c and 0xFFFFFF))
        }
        for (i in 3..5) p.set(i, 3, (0xCC shl 24) or 0xFFFFFF)
        p.set(3, 4, (0xCC shl 24) or 0xFFFFFF)
        p.set(11, 12, (0xAA shl 24) or 0xFFFFFF); p.set(12, 11, (0xAA shl 24) or 0xFFFFFF)
    }

    fun wool(c: Int): (P) -> Unit = { p -> p.palette(shades(c, 0.07f), 8, 0.6f) }

    fun sandstone(c: Int, kind: Int): (P) -> Unit = { p ->
        p.palette(shades(c, 0.06f), 4, 0.3f)
        if (kind == 1) for (y in 0 until 16) for (x in 0 until 16) {
            if (y in 1..2 || y in 13..14) p.set(x, y, mulC(p.get(x, y), 0.9f))
            if (y == 7 || y == 8) p.set(x, y, mulC(p.get(x, y), 0.94f))
        }
    }

    fun metal(c: Int): (P) -> Unit = { p ->
        p.palette(shades(c, 0.05f), 4, 0.2f)
        for (i in 0 until 16) {
            for (e in listOf(0, 15)) {
                p.set(i, e, mulC(p.get(i, e), 1.14f))
                p.set(e, i, mulC(p.get(e, i), 0.86f))
            }
        }
    }

    fun liquid(c: Int, a: Int): (P) -> Unit = { p ->
        p.palette(shades(c, 0.1f), 4, 0.2f)
        for (i in 0 until 256) p.px[i] = (a shl 24) or (p.px[i] and 0xFFFFFF)
    }

    fun lava(): (P) -> Unit = { p ->
        p.palette(intArrayOf(rgb(0xB83A08), rgb(0xE5600F), rgb(0xF58A1F), rgb(0xFFC93C)), 4, 0.5f)
    }

    fun cactusSide(): (P) -> Unit = { p ->
        p.palette(shades(0x2F7B2D, 0.1f), 4, 0.3f)
        for (y in 0 until 16) {
            p.set(2, y, rgb(0x1E5A1E)); p.set(7, y, rgb(0x1E5A1E)); p.set(12, y, rgb(0x1E5A1E))
        }
        repeat(10) { p.set(p.ri(16), p.ri(16), rgb(0xD8E8A0)) }
    }

    fun cactusTop(): (P) -> Unit = { p ->
        p.palette(shades(0x3E8E3A, 0.08f), 3, 0.3f)
        p.rect(0, 0, 16, 1, rgb(0x1E5A1E)); p.rect(0, 15, 16, 1, rgb(0x1E5A1E))
        p.rect(0, 0, 1, 16, rgb(0x1E5A1E)); p.rect(15, 0, 1, 16, rgb(0x1E5A1E))
    }

    fun stripes(c: Int, dark: Int, period: Int): (P) -> Unit = { p ->
        for (y in 0 until 16) for (x in 0 until 16) {
            val col = if (x % period == 0) rgb(dark) else rgb(c)
            p.set(x, y, p.jit(col, 7))
        }
    }

    fun pumpkinTop(): (P) -> Unit = { p ->
        p.palette(shades(0xC77A18, 0.08f), 4, 0.3f)
        p.rect(6, 6, 4, 4, rgb(0x5B7A24))
    }

    fun melonTop(): (P) -> Unit = { p ->
        for (y in 0 until 16) for (x in 0 until 16) {
            val d = maxOf(abs(x - 7.5f), abs(y - 7.5f)).toInt()
            p.set(x, y, p.jit(if (d % 3 == 0) rgb(0x2F6B1B) else rgb(0x7FB02F), 6))
        }
    }

    fun hayTop(): (P) -> Unit = { p ->
        for (y in 0 until 16) for (x in 0 until 16) {
            val d = maxOf(abs(x - 7.5f), abs(y - 7.5f)).toInt()
            p.set(x, y, p.jit(if (d % 2 == 0) rgb(0xC9A22A) else rgb(0xA98318), 7))
        }
    }

    fun craftTop(): (P) -> Unit = { p ->
        planks(0xA77B47)(p)
        for (i in 2..13) {
            p.set(i, 2, rgb(0x5A3D1C)); p.set(i, 13, rgb(0x5A3D1C))
            p.set(2, i, rgb(0x5A3D1C)); p.set(13, i, rgb(0x5A3D1C))
        }
        for (i in 3..12) {
            p.set(i, 5, rgb(0x6E4B24)); p.set(i, 10, rgb(0x6E4B24))
            p.set(5, i, rgb(0x6E4B24)); p.set(10, i, rgb(0x6E4B24))
        }
    }

    fun craftSide(): (P) -> Unit = { p ->
        planks(0x9A6F3D)(p)
        p.rect(2, 3, 5, 2, rgb(0x888888)); p.rect(3, 5, 1, 5, rgb(0x5A3D1C))
        p.rect(9, 3, 5, 5, rgb(0x6E4B24)); p.rect(10, 4, 3, 3, rgb(0xB8945F))
        p.rect(2, 11, 12, 2, rgb(0x777777))
    }

    fun furnaceFront(lit: Boolean): (P) -> Unit = { p ->
        cobble(0x808080, 9, 0.6f, 0.1f)(p)
        p.rect(3, 3, 10, 3, rgb(0x3A3A3A))
        p.rect(3, 7, 10, 7, rgb(0x2A2A2A))
        if (lit) p.rect(5, 9, 6, 4, rgb(0xF0801A))
        p.rect(4, 14, 8, 1, rgb(0x555555))
    }

    fun chestSide(front: Boolean): (P) -> Unit = { p ->
        planks(0x9C6A2C)(p)
        for (i in 0 until 16) {
            p.set(i, 0, rgb(0x5A3A14)); p.set(i, 15, rgb(0x5A3A14)); p.set(i, 5, rgb(0x6B4519))
            p.set(0, i, rgb(0x5A3A14)); p.set(15, i, rgb(0x5A3A14))
        }
        if (front) p.rect(7, 4, 2, 4, rgb(0xD0D0D0))
    }

    fun bookshelf(): (P) -> Unit = { p ->
        planks(0xB8945F)(p)
        val cols = intArrayOf(0x8A2B2B, 0x2B5C8A, 0x2B8A4A, 0x8A6B2B, 0x5B2B8A)
        for (row in 0..1) {
            val y0 = 3 + row * 6
            for (x in 1 until 15 step 2) {
                val c = cols[(x + row * 3) % cols.size]
                p.rect(x, y0, 2, 5, rgb(c))
                p.set(x, y0, mulC(rgb(c), 1.2f))
            }
        }
    }

    fun sponge(): (P) -> Unit = { p ->
        p.palette(shades(0xCFCF4D, 0.08f), 4, 0.4f)
        repeat(18) { p.set(p.ri(16), p.ri(16), rgb(0x8C8C2A)) }
    }

    fun flower(head: Int, stem: Int = 0x3F8F2F, big: Boolean = false): (P) -> Unit = { p ->
        p.line(7, 15, 7, 8, rgb(stem))
        p.set(6, 12, rgb(stem)); p.set(8, 11, rgb(stem))
        if (big) p.rect(5, 4, 5, 4, rgb(head)) else p.rect(6, 5, 3, 3, rgb(head))
        p.set(7, 6, mulC(rgb(head), 0.7f))
    }

    fun tallGrass(c: Int, ferny: Boolean = false): (P) -> Unit = { p ->
        for (i in listOf(2, 5, 8, 11, 13)) {
            val top = 3 + p.ri(6)
            p.line(i, 15, i + p.ri(3) - 1, top, p.jit(rgb(c), 14))
            if (ferny) {
                p.set(i - 1, top + 2, rgb(c)); p.set(i + 1, top + 3, rgb(c))
            }
        }
    }

    fun deadBush(): (P) -> Unit = { p ->
        val c = rgb(0x6F4F25)
        p.line(7, 15, 7, 8, c); p.line(7, 11, 3, 6, c); p.line(7, 10, 11, 5, c); p.line(7, 13, 11, 10, c)
    }

    fun mushroom(cap: Int, stem: Int): (P) -> Unit = { p ->
        p.rect(7, 9, 2, 6, rgb(stem))
        p.rect(4, 5, 8, 4, rgb(cap))
        p.rect(5, 4, 6, 1, rgb(cap))
    }

    fun torch(): (P) -> Unit = { p ->
        p.rect(7, 6, 2, 10, rgb(0x6B4A22))
        p.rect(7, 3, 2, 3, rgb(0xFFD23C))
        p.set(7, 3, rgb(0xFF8A1E))
        p.set(8, 6, rgb(0x8A6232)); p.set(8, 8, rgb(0x8A6232)); p.set(8, 10, rgb(0x8A6232))
    }

    fun cane(): (P) -> Unit = { p ->
        for (x in listOf(3, 8, 12)) {
            p.line(x, 15, x, 1 + p.ri(3), rgb(0x8DCB5C))
            for (y in listOf(4, 9, 14)) p.set(x, y, rgb(0x5C9A33))
        }
    }

    // ---------- иконки предметов ----------
    private fun handle(p: P) {
        p.line(3, 13, 10, 6, rgb(0x6B4A22))
        p.line(4, 13, 11, 6, rgb(0x8A6232))
    }

    fun tool(kind: String, mat: Int): (P) -> Unit = { p ->
        val m = rgb(mat)
        val hi = mulC(m, 1.25f)
        val lo = mulC(m, 0.7f)
        when (kind) {
            "sword" -> {
                p.line(3, 13, 5, 11, rgb(0x6B4A22))
                p.line(4, 9, 8, 13, rgb(0x5A5A5A))
                p.line(6, 10, 13, 3, m); p.line(7, 10, 13, 4, lo); p.line(6, 9, 12, 3, hi)
            }
            "pickaxe" -> {
                handle(p)
                p.line(4, 4, 8, 2, m); p.line(8, 2, 12, 4, m); p.line(12, 4, 13, 8, m)
                p.line(4, 5, 8, 3, lo); p.line(8, 3, 11, 5, lo); p.line(11, 5, 12, 8, lo)
                p.set(8, 2, hi)
            }
            "axe" -> {
                handle(p)
                p.rect(7, 2, 5, 5, m)
                p.rect(7, 2, 5, 1, hi)
                p.rect(11, 3, 1, 4, lo)
                p.rect(6, 4, 1, 3, lo)
            }
            "shovel" -> {
                handle(p)
                p.disc(11.5f, 4.5f, 3f, m)
                p.disc(11f, 4f, 1.5f, hi)
            }
            else -> {
                handle(p)
                p.line(7, 3, 12, 3, m); p.line(7, 3, 7, 6, m); p.line(8, 4, 12, 4, lo)
            }
        }
    }

    fun armor(slot: Int, mat: Int): (P) -> Unit = { p ->
        val m = rgb(mat)
        val lo = mulC(m, 0.72f)
        val hi = mulC(m, 1.2f)
        when (slot) {
            0 -> {
                p.rect(3, 4, 10, 4, m); p.rect(3, 8, 3, 4, m); p.rect(10, 8, 3, 4, m)
                p.rect(3, 4, 10, 1, hi); p.rect(6, 8, 4, 1, lo)
            }
            1 -> {
                p.rect(2, 3, 12, 11, m); p.rect(0, 3, 3, 6, m); p.rect(13, 3, 3, 6, m)
                p.rect(6, 3, 4, 2, lo); p.rect(2, 3, 12, 1, hi); p.rect(7, 6, 2, 8, lo)
            }
            2 -> {
                p.rect(3, 2, 10, 4, m); p.rect(3, 6, 4, 8, m); p.rect(9, 6, 4, 8, m)
                p.rect(3, 2, 10, 1, hi); p.rect(7, 6, 2, 3, lo)
            }
            else -> {
                p.rect(3, 8, 4, 5, m); p.rect(9, 8, 4, 5, m)
                p.rect(2, 11, 5, 3, m); p.rect(9, 11, 5, 3, m)
                p.rect(3, 8, 4, 1, hi); p.rect(9, 8, 4, 1, hi)
            }
        }
    }

    fun gem(c: Int): (P) -> Unit = { p ->
        val rows = listOf(
            3 to (6..9), 4 to (5..10), 5 to (4..11), 6 to (3..12), 7 to (3..12),
            8 to (4..11), 9 to (5..10), 10 to (6..9), 11 to (7..8)
        )
        for ((y, rng) in rows) for (x in rng) {
            val base = rgb(c)
            p.set(
                x, y,
                if ((x + y) % 3 == 0) mulC(base, 1.18f)
                else if (x == rng.first || x == rng.last) mulC(base, 0.7f)
                else base
            )
        }
    }

    fun ingot(c: Int): (P) -> Unit = { p ->
        val m = rgb(c)
        p.rect(3, 8, 10, 4, mulC(m, 0.85f))
        p.rect(5, 6, 9, 2, mulC(m, 1.15f))
        p.rect(3, 8, 10, 1, m)
        p.rect(12, 8, 1, 4, mulC(m, 0.65f))
        p.rect(13, 6, 1, 2, mulC(m, 0.7f))
    }

    fun nugget(c: Int): (P) -> Unit = { p ->
        p.rect(6, 7, 4, 3, rgb(c)); p.rect(7, 6, 2, 1, mulC(rgb(c), 1.2f)); p.rect(7, 10, 3, 1, mulC(rgb(c), 0.7f))
    }

    fun dust(c: Int): (P) -> Unit = { p ->
        repeat(46) {
            val x = 8 + (p.ri(9) - 4)
            val y = 9 + (p.ri(7) - 3)
            p.set(x, y, p.jit(rgb(c), 18))
        }
    }

    fun lump(c: Int): (P) -> Unit = { p ->
        p.disc(8f, 8.5f, 5f, p.jit(rgb(c), 4))
        repeat(14) { p.set(5 + p.ri(7), 5 + p.ri(7), p.jit(mulC(rgb(c), 0.8f), 10)) }
        p.disc(6.5f, 6.5f, 1.5f, mulC(rgb(c), 1.2f))
    }

    fun ball(c: Int): (P) -> Unit = { p ->
        p.disc(8f, 8f, 5f, rgb(c)); p.disc(6.5f, 6.5f, 1.6f, mulC(rgb(c), 1.3f)); p.disc(9.5f, 10f, 2f, mulC(rgb(c), 0.8f))
    }

    fun stick(): (P) -> Unit = { p ->
        p.line(4, 13, 11, 6, rgb(0x6B4A22)); p.line(5, 13, 12, 6, rgb(0x8A6232))
    }

    fun feather(): (P) -> Unit = { p ->
        p.line(4, 12, 12, 4, rgb(0xEDEDED))
        for (i in 0..5) {
            p.set(5 + i, 11 - i + 1, rgb(0xCFCFCF)); p.set(6 + i, 11 - i, rgb(0xF8F8F8))
        }
    }

    fun bone(): (P) -> Unit = { p ->
        p.line(4, 12, 11, 5, rgb(0xE8E4D4)); p.line(5, 12, 12, 5, rgb(0xCFCBB8))
        p.disc(4f, 12f, 2f, rgb(0xE8E4D4)); p.disc(12f, 4f, 2f, rgb(0xE8E4D4))
    }

    fun string(): (P) -> Unit = { p ->
        p.line(3, 12, 6, 9, rgb(0xEEEEEE)); p.line(6, 9, 9, 11, rgb(0xDDDDDD)); p.line(9, 11, 13, 5, rgb(0xEEEEEE))
    }

    fun paper(): (P) -> Unit = { p ->
        p.rect(4, 3, 8, 10, rgb(0xEFEFE6))
        for (y in 5..11 step 2) p.rect(5, y, 6, 1, rgb(0xB5B5A8))
    }

    fun book(): (P) -> Unit = { p ->
        p.rect(3, 3, 10, 11, rgb(0x7A3B2B)); p.rect(4, 4, 8, 9, rgb(0xE9DFC3)); p.rect(3, 3, 2, 11, rgb(0x5B2B1F))
        p.rect(6, 6, 5, 1, rgb(0x8A7A5A)); p.rect(6, 8, 5, 1, rgb(0x8A7A5A))
    }

    fun bowl(): (P) -> Unit = { p ->
        p.rect(3, 7, 10, 1, rgb(0x9A6F3D)); p.rect(4, 8, 8, 3, rgb(0x7A5528)); p.rect(5, 11, 6, 1, rgb(0x6B4A22))
    }

    fun coal(c: Int): (P) -> Unit = { p ->
        p.disc(8f, 8f, 5f, rgb(c)); p.disc(6.5f, 6.5f, 1.8f, mulC(rgb(c), 2.4f)); p.set(10, 9, mulC(rgb(c), 1.8f))
    }

    fun food(c: Int, c2: Int, leaf: Boolean = false): (P) -> Unit = { p ->
        p.disc(8f, 9f, 5f, rgb(c)); p.disc(6.5f, 7.5f, 1.4f, mulC(rgb(c), 1.35f)); p.disc(10f, 11f, 2f, rgb(c2))
        if (leaf) {
            p.line(8, 4, 8, 2, rgb(0x6B4A22)); p.set(9, 2, rgb(0x4CAF3A))
        }
    }

    fun meat(c: Int, bone: Int): (P) -> Unit = { p ->
        p.disc(9f, 7.5f, 4.5f, rgb(c)); p.disc(7.5f, 6f, 1.5f, mulC(rgb(c), 1.3f))
        p.line(5, 11, 3, 13, rgb(bone)); p.disc(3f, 13f, 1.2f, rgb(bone))
    }

    fun bread(): (P) -> Unit = { p ->
        p.rect(3, 6, 10, 5, rgb(0xC98A3C)); p.rect(4, 5, 8, 1, rgb(0xC98A3C)); p.rect(4, 6, 8, 1, rgb(0xDDA55A))
        p.rect(3, 10, 10, 1, rgb(0x9A6528))
    }

    fun sugarLike(c: Int): (P) -> Unit = dust(c)
}

object Tex {
    const val TS = 16
    const val COLS = 32
    private val names = ArrayList<String>()
    private val gens = ArrayList<(P) -> Unit>()
    private val map = HashMap<String, Int>()

    /** Индексы изометрических иконок блоков: id предмета -> тайл. */
    val iso = HashMap<Int, Int>()
    private var rowsTotal = 0

    // Эти имена перекрывают генераторы из реестра более красивыми версиями
    private val overrides = HashMap<String, (P) -> Unit>()

    init {
        overrides["grass_top"] = G.grassTop()
        overrides["cobblestone"] = G.cobble(0x7E7E7E)
        overrides["mossy_cobblestone"] = G.cobble(0x667F62)
        overrides["cobbled_deepslate"] = G.cobble(0x4D4D52, 9)
        overrides["gravel"] = G.cobble(0x8C8683, 20, 0.72f, 0.2f)
        reg("white") { it.fill(rgb(0xFFFFFF)) }
    }

    fun reg(name: String, g: (P) -> Unit): Int {
        map[name]?.let { return it }
        val i = names.size
        names.add(name); gens.add(overrides[name] ?: g); map[name] = i
        return i
    }

    val count: Int get() = names.size
    val rows: Int get() = if (rowsTotal > 0) rowsTotal else max(1, (names.size + COLS - 1) / COLS)

    private fun outline(t: IntArray) {
        val src = t.copyOf()
        for (y in 0 until 16) for (x in 0 until 16) {
            if ((src[y * 16 + x] ushr 24) > 30) continue
            var near = false
            if (x > 0 && (src[y * 16 + x - 1] ushr 24) > 200) near = true
            if (x < 15 && (src[y * 16 + x + 1] ushr 24) > 200) near = true
            if (y > 0 && (src[(y - 1) * 16 + x] ushr 24) > 200) near = true
            if (y < 15 && (src[(y + 1) * 16 + x] ushr 24) > 200) near = true
            if (near) t[y * 16 + x] = (0xDD shl 24) or 0x1A1A24
        }
    }

    private fun tintPx(c: Int, t: Int): Int {
        if (t == 0xFFFFFF) return c
        val a = c ushr 24
        val r = (((c shr 16) and 255) * ((t shr 16) and 255)) / 255
        val g = (((c shr 8) and 255) * ((t shr 8) and 255)) / 255
        val b = ((c and 255) * (t and 255)) / 255
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun isoCube(top: IntArray, side: IntArray, tintTop: Int, tintSide: Int): IntArray {
        val out = IntArray(256)
        for (py in 0 until 16) for (pxx in 0 until 16) {
            val x = pxx + 0.5f
            val y = py + 0.5f
            val s = ((x - 8f) / 7f + (y - 1f) / 4f) / 2f
            val t = ((y - 1f) / 4f - (x - 8f) / 7f) / 2f
            if (s in 0f..1f && t in 0f..1f) {
                val c = top[(t * 16).toInt().coerceIn(0, 15) * 16 + (s * 16).toInt().coerceIn(0, 15)]
                out[py * 16 + pxx] = tintPx(c, tintTop)
                continue
            }
            if (x < 8f) {
                val ls = (x - 1f) / 7f
                val lt = (y - 5f - 4f * ls) / 6f
                if (ls in 0f..1f && lt in 0f..1f) {
                    val c = side[(lt * 16).toInt().coerceIn(0, 15) * 16 + (ls * 16).toInt().coerceIn(0, 15)]
                    out[py * 16 + pxx] = mulC(tintPx(c, tintSide), 0.8f)
                }
            } else {
                val rs = (x - 8f) / 7f
                val rt = (y - 9f + 4f * rs) / 6f
                if (rs in 0f..1f && rt in 0f..1f) {
                    val c = side[(rt * 16).toInt().coerceIn(0, 15) * 16 + (rs * 16).toInt().coerceIn(0, 15)]
                    out[py * 16 + pxx] = mulC(tintPx(c, tintSide), 0.62f)
                }
            }
        }
        return out
    }

    fun buildAtlas(): Bitmap {
        val tiles = ArrayList<IntArray>()
        for (i in 0 until names.size) {
            val t = IntArray(256)
            gens[i](P(t, names[i].hashCode()))
            if (names[i].startsWith("item_")) outline(t)
            tiles.add(t)
        }
        iso.clear()
        for (d in Reg.items) {
            val b = d.block ?: continue
            if (d.hidden || b.render == R_CROSS || b.render == R_LIQUID) continue
            val tintTop = when (b.tint) {
                1, 2 -> 0x8DC25A
                3 -> b.tintColor
                else -> 0xFFFFFF
            }
            val tintSide = when (b.tint) {
                2 -> 0x8DC25A
                3 -> b.tintColor
                else -> 0xFFFFFF
            }
            iso[d.id] = tiles.size
            tiles.add(isoCube(tiles[b.top], tiles[b.side], tintTop, tintSide))
        }
        val rowsN = max(1, (tiles.size + COLS - 1) / COLS)
        rowsTotal = rowsN
        val w = COLS * TS
        val h = rowsN * TS
        val px = IntArray(w * h)
        for (i in 0 until tiles.size) {
            val ox = (i % COLS) * TS
            val oy = (i / COLS) * TS
            val t = tiles[i]
            for (y in 0 until TS) for (x in 0 until TS) px[(oy + y) * w + ox + x] = t[y * TS + x]
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}

object Atlas {
    lateinit var bitmap: Bitmap
    lateinit var image: ImageBitmap

    @Volatile
    var ready = false

    fun tileOf(d: ItemDef): Int = Tex.iso[d.id] ?: d.icon

    @Synchronized
    fun build() {
        if (ready) return
        Reg.touch()
        bitmap = Tex.buildAtlas()
        image = bitmap.asImageBitmap()
        ready = true
    }
}
