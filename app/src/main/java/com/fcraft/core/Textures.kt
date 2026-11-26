package com.fcraft.core

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
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
}

/** Генераторы текстур. Всё рисуется в коде — внешних картинок нет. */
object G {
    fun noisy(c: Int, a: Int = 10): (P) -> Unit = { p -> p.noise(c, a) }

    fun ore(stone: Int, spot: Int): (P) -> Unit = { p ->
        p.noise(stone, 8)
        repeat(7) {
            val cx = p.ri(13) + 1
            val cy = p.ri(13) + 1
            for (dx in 0..1) for (dy in 0..1) p.set(cx + dx, cy + dy, p.jit(rgb(spot), 14))
        }
    }

    fun planks(c: Int): (P) -> Unit = { p ->
        for (y in 0 until 16) for (x in 0 until 16) {
            var col = p.jit(rgb(c), 6)
            if (y % 4 == 3) col = mulC(col, 0.78f)
            val seam = if ((y / 4) % 2 == 0) 5 else 12
            if (x == seam && y % 4 != 3) col = mulC(col, 0.85f)
            p.set(x, y, col)
        }
    }

    fun logSide(c: Int): (P) -> Unit = { p ->
        for (x in 0 until 16) {
            val f = 0.82f + ((x * 37 + 11) % 5) * 0.06f
            for (y in 0 until 16) p.set(x, y, mulC(p.jit(rgb(c), 5), f))
        }
    }

    fun logTop(ring: Int, bark: Int): (P) -> Unit = { p ->
        for (y in 0 until 16) for (x in 0 until 16) {
            val d = maxOf(abs(x - 7.5f), abs(y - 7.5f))
            val c = if (d > 6.5f) rgb(bark) else if (d.toInt() % 2 == 0) rgb(ring) else mulC(rgb(ring), 0.85f)
            p.set(x, y, p.jit(c, 4))
        }
    }

    fun leaves(c: Int): (P) -> Unit = { p ->
        for (i in 0 until 256) p.px[i] = if (p.ri(100) < 14) 0 else p.jit(rgb(c), 22)
    }

    fun bricks(brick: Int, mortar: Int): (P) -> Unit = { p ->
        for (y in 0 until 16) for (x in 0 until 16) {
            val off = if ((y / 4) % 2 == 0) 0 else 4
            val m = y % 4 == 3 || (x + off) % 8 == 7
            p.set(x, y, if (m) p.jit(rgb(mortar), 4) else p.jit(rgb(brick), 8))
        }
    }

    fun stoneBricks(c: Int): (P) -> Unit = { p ->
        for (y in 0 until 16) for (x in 0 until 16) {
            val off = if (y / 8 == 0) 0 else 4
            val m = y % 8 == 7 || (x + off) % 8 == 7
            p.set(x, y, if (m) mulC(p.jit(rgb(c), 4), 0.7f) else p.jit(rgb(c), 6))
        }
    }

    fun dirt(): (P) -> Unit = { p ->
        p.noise(0x866043, 9)
        repeat(14) { p.set(p.ri(16), p.ri(16), p.jit(rgb(0x5C4129), 8)) }
    }

    fun grassSide(dirtC: Int = 0x866043, grass: Int = 0x79B24A): (P) -> Unit = { p ->
        for (x in 0 until 16) {
            val d = 2 + p.ri(3)
            for (y in 0 until 16) p.set(x, y, if (y < d) p.jit(rgb(grass), 10) else p.jit(rgb(dirtC), 9))
        }
    }

    fun glass(c: Int, a: Int): (P) -> Unit = { p ->
        for (y in 0 until 16) for (x in 0 until 16) {
            val edge = x == 0 || y == 0 || x == 15 || y == 15
            p.set(x, y, ((if (edge) 0xE0 else a) shl 24) or (c and 0xFFFFFF))
        }
        for (i in 3..5) p.set(i, 3, (0xCC shl 24) or 0xFFFFFF)
        p.set(3, 4, (0xCC shl 24) or 0xFFFFFF)
    }

    fun wool(c: Int): (P) -> Unit = { p ->
        p.noise(c, 7)
        for (y in 0 until 16) for (x in 0 until 16) if ((x + y) % 2 == 0) p.set(x, y, mulC(p.get(x, y), 0.95f))
    }

    fun sandstone(c: Int, kind: Int): (P) -> Unit = { p ->
        p.noise(c, 6)
        if (kind == 1) for (y in 0 until 16) for (x in 0 until 16) {
            if (y in 1..2 || y in 13..14) p.set(x, y, mulC(p.get(x, y), 0.9f))
            if (y == 7 || y == 8) p.set(x, y, mulC(p.get(x, y), 0.94f))
        }
    }

    fun metal(c: Int): (P) -> Unit = { p ->
        p.noise(c, 5)
        for (i in 0 until 16) {
            for (e in listOf(0, 15)) {
                p.set(i, e, mulC(p.get(i, e), 1.12f))
                p.set(e, i, mulC(p.get(e, i), 0.88f))
            }
        }
    }

    fun liquid(c: Int, a: Int): (P) -> Unit = { p ->
        for (i in 0 until 256) {
            val col = p.jit(rgb(c), 14)
            p.px[i] = (a shl 24) or (col and 0xFFFFFF)
        }
    }

    fun lava(): (P) -> Unit = { p ->
        p.noise(0xE5600F, 28)
        repeat(30) { p.set(p.ri(16), p.ri(16), p.jit(rgb(0xFFC93C), 10)) }
    }

    fun cactusSide(): (P) -> Unit = { p ->
        p.noise(0x2F7B2D, 8)
        for (y in 0 until 16) {
            p.set(2, y, rgb(0x1E5A1E)); p.set(7, y, rgb(0x1E5A1E)); p.set(12, y, rgb(0x1E5A1E))
        }
        repeat(10) { p.set(p.ri(16), p.ri(16), rgb(0xD8E8A0)) }
    }

    fun cactusTop(): (P) -> Unit = { p ->
        p.noise(0x3E8E3A, 6)
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
        p.noise(0xC77A18, 8)
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
        p.noise(0x808080, 8)
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
        p.noise(0xCFCF4D, 10)
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

    init {
        reg("white") { it.fill(rgb(0xFFFFFF)) }
    }

    fun reg(name: String, g: (P) -> Unit): Int {
        map[name]?.let { return it }
        val i = names.size
        names.add(name); gens.add(g); map[name] = i
        return i
    }

    val count: Int get() = names.size
    val rows: Int get() = maxOf(1, (names.size + COLS - 1) / COLS)

    fun buildAtlas(): Bitmap {
        val w = COLS * TS
        val h = rows * TS
        val px = IntArray(w * h)
        val tile = IntArray(256)
        for (i in 0 until names.size) {
            tile.fill(0)
            gens[i](P(tile, names[i].hashCode()))
            val ox = (i % COLS) * TS
            val oy = (i / COLS) * TS
            for (y in 0 until TS) for (x in 0 until TS) px[(oy + y) * w + ox + x] = tile[y * TS + x]
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}

object Atlas {
    lateinit var bitmap: Bitmap
    lateinit var image: ImageBitmap

    @Volatile
    var ready = false

    @Synchronized
    fun build() {
        if (ready) return
        Reg.touch()
        bitmap = Tex.buildAtlas()
        image = bitmap.asImageBitmap()
        ready = true
    }
}
