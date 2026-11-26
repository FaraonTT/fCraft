package com.fcraft.core

import kotlin.math.floor

/** Классический шум Перлина (2D/3D) с детерминированной перестановкой от seed. */
class Noise(seed: Long) {
    private val p = IntArray(512)

    init {
        val perm = (0..255).toMutableList()
        perm.shuffle(java.util.Random(seed))
        for (i in 0 until 512) p[i] = perm[i and 255]
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(t: Double, a: Double, b: Double) = a + t * (b - a)

    private fun g2(h: Int, x: Double, y: Double): Double = when (h and 7) {
        0 -> x + y
        1 -> x - y
        2 -> -x + y
        3 -> -x - y
        4 -> x
        5 -> -x
        6 -> y
        else -> -y
    }

    private fun g3(h: Int, x: Double, y: Double, z: Double): Double {
        val g = h and 15
        val u = if (g < 8) x else y
        val v = if (g < 4) y else if (g == 12 || g == 14) x else z
        return (if ((g and 1) == 0) u else -u) + (if ((g and 2) == 0) v else -v)
    }

    fun n2(x: Double, y: Double): Double {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val xx = xi and 255
        val yy = yi and 255
        val u = fade(xf)
        val v = fade(yf)
        val aa = p[p[xx] + yy]
        val ab = p[p[xx] + yy + 1]
        val ba = p[p[xx + 1] + yy]
        val bb = p[p[xx + 1] + yy + 1]
        return lerp(
            v,
            lerp(u, g2(aa, xf, yf), g2(ba, xf - 1, yf)),
            lerp(u, g2(ab, xf, yf - 1), g2(bb, xf - 1, yf - 1))
        ) * 0.7
    }

    fun n3(x0: Double, y0: Double, z0: Double): Double {
        val xi = floor(x0).toInt()
        val yi = floor(y0).toInt()
        val zi = floor(z0).toInt()
        val xx = xi and 255
        val yy = yi and 255
        val zz = zi and 255
        val x = x0 - xi
        val y = y0 - yi
        val z = z0 - zi
        val u = fade(x)
        val v = fade(y)
        val w = fade(z)
        val a = p[xx] + yy
        val aa = p[a] + zz
        val ab = p[a + 1] + zz
        val b = p[xx + 1] + yy
        val ba = p[b] + zz
        val bb = p[b + 1] + zz
        return lerp(
            w,
            lerp(
                v,
                lerp(u, g3(p[aa], x, y, z), g3(p[ba], x - 1, y, z)),
                lerp(u, g3(p[ab], x, y - 1, z), g3(p[bb], x - 1, y - 1, z))
            ),
            lerp(
                v,
                lerp(u, g3(p[aa + 1], x, y, z - 1), g3(p[ba + 1], x - 1, y, z - 1)),
                lerp(u, g3(p[ab + 1], x, y - 1, z - 1), g3(p[bb + 1], x - 1, y - 1, z - 1))
            )
        ) * 0.6
    }

    fun fbm(x: Double, z: Double, octaves: Int): Double {
        var amp = 1.0
        var f = 1.0
        var s = 0.0
        var t = 0.0
        for (i in 0 until octaves) {
            s += n2(x * f, z * f) * amp
            t += amp
            amp *= 0.5
            f *= 2.0
        }
        return s / t
    }
}

object Hash {
    fun h2(x: Int, z: Int, s: Long): Int {
        var h = x * 374761393 + z * 668265263 + (s xor (s ushr 32)).toInt() * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    fun f2(x: Int, z: Int, s: Long): Float = (h2(x, z, s) and 0xFFFFFF) / 16777216f
}
