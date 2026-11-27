package com.fcraft.world

import com.fcraft.core.*
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.floor

const val CS = 16
const val WH = 96
const val SEA = 30

class Chunk(val cx: Int, val cz: Int) {
    val blocks = ShortArray(CS * CS * WH)
    val height = IntArray(CS * CS)
    val version = AtomicInteger(1)

    @Volatile
    var meshed = 0
    val busy = AtomicBoolean(false)

    @Volatile
    var modified = false

    @Volatile
    var hasLight = false
    var grass = 0x79C05A
    fun get(x: Int, y: Int, z: Int): Int = blocks[(y * CS + z) * CS + x].toInt()
}

class Gen(private val seed: Long) {
    private val nCont = Noise(seed)
    private val nHill = Noise(seed + 1)
    private val nMnt = Noise(seed + 2)
    private val nTemp = Noise(seed + 3)
    private val nHum = Noise(seed + 4)
    private val nC1 = Noise(seed + 5)
    private val nC2 = Noise(seed + 6)
    private val nC3 = Noise(seed + 7)

    private val STONE = Reg.id("stone")
    private val DEEP = Reg.id("deepslate")
    private val DIRT = Reg.id("dirt")
    private val GRASS = Reg.id("grass_block")
    private val SAND = Reg.id("sand")
    private val SANDSTONE = Reg.id("sandstone")
    private val GRAVEL = Reg.id("gravel")
    private val SNOW = Reg.id("snow_block")
    private val WATER = Reg.id("water")
    private val LAVA = Reg.id("lava")
    private val BEDROCK = Reg.id("bedrock")
    private val SHORT = Reg.id("short_grass")
    private val FERN = Reg.id("fern")
    private val DEAD = Reg.id("dead_bush")
    private val CACTUS = Reg.id("cactus")
    private val CANE = Reg.id("sugar_cane")
    private val PUMPKIN = Reg.id("pumpkin")
    private val BMUSH = Reg.id("brown_mushroom")
    private val RMUSH = Reg.id("red_mushroom")
    private val FLOWERS = intArrayOf(
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
        "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley"
    ).map { Reg.id(it) }.toIntArray()
    private val OAK_LOG = Reg.id("oak_log")
    private val OAK_LEAF = Reg.id("oak_leaves")
    private val BIRCH_LOG = Reg.id("birch_log")
    private val BIRCH_LEAF = Reg.id("birch_leaves")
    private val SPRUCE_LOG = Reg.id("spruce_log")
    private val SPRUCE_LEAF = Reg.id("spruce_leaves")
    private val GRANITE = Reg.id("granite")
    private val DIORITE = Reg.id("diorite")
    private val ANDESITE = Reg.id("andesite")
    private val TUFF = Reg.id("tuff")
    private val CLAY = Reg.id("clay")

    private class Ore(val key: String, val count: Int, val ymin: Int, val ymax: Int, val size: Int)

    private val ores = listOf(
        Ore("coal", 20, 5, 70, 9), Ore("iron", 14, 4, 60, 6), Ore("copper", 8, 4, 50, 7),
        Ore("gold", 3, 3, 28, 5), Ore("redstone", 5, 3, 16, 6), Ore("lapis", 2, 3, 30, 5),
        Ore("diamond", 2, 3, 16, 4), Ore("emerald", 2, 20, 70, 1)
    )
    private val oreIds = ores.map { Reg.id("${it.key}_ore") to Reg.id("deepslate_${it.key}_ore") }

    private fun smooth(t: Double): Double {
        val c = t.coerceIn(0.0, 1.0)
        return c * c * (3 - 2 * c)
    }

    fun height(wx: Int, wz: Int): Int {
        val cont = nCont.fbm(wx * 0.0035, wz * 0.0035, 4)
        val hills = nHill.fbm(wx * 0.02, wz * 0.02, 3)
        val m = nMnt.fbm(wx * 0.004 + 50, wz * 0.004 - 30, 3)
        val mm = smooth((m - 0.1) / 0.28)
        val h = SEA + 3 + cont * 34 + hills * 6 + mm * 34
        return h.toInt().coerceIn(6, WH - 16)
    }

    private fun temp(wx: Int, wz: Int) = nTemp.fbm(wx * 0.0025, wz * 0.0025, 3)
    private fun hum(wx: Int, wz: Int) = nHum.fbm(wx * 0.003 + 10, wz * 0.003, 3)

    fun biome(wx: Int, wz: Int, h: Int): Int {
        val t = temp(wx, wz)
        val hu = hum(wx, wz)
        return when {
            h > SEA + 36 -> 4
            t > 0.1 && hu < 0.04 -> 2
            t < -0.14 -> 3
            hu > 0.07 -> 1
            else -> 0
        }
    }

    fun spawnColumn(): IntArray {
        var r = 0
        while (r < 4000) {
            var x = -r
            while (x <= r) {
                for (z in intArrayOf(-r, r)) if (height(x, z) > SEA + 1) return intArrayOf(x, z)
                x += 8
            }
            var z = -r
            while (z <= r) {
                for (xx in intArrayOf(-r, r)) if (height(xx, z) > SEA + 1) return intArrayOf(xx, z)
                z += 8
            }
            r += 8
        }
        return intArrayOf(0, 0)
    }

    fun generate(cx: Int, cz: Int): Chunk {
        val ch = Chunk(cx, cz)
        val ox = cx * CS
        val oz = cz * CS
        val b = ch.blocks
        fun idx(x: Int, y: Int, z: Int) = (y * CS + z) * CS + x

        val centerH = height(ox + 8, oz + 8)
        ch.grass = when (biome(ox + 8, oz + 8, centerH)) {
            2 -> 0xBFB755; 3 -> 0x80B497; 4 -> 0x8AB689; 1 -> 0x6FBF4A; else -> 0x8DC25A
        }

        val heights = IntArray(CS * CS)
        val biomes = IntArray(CS * CS)
        for (z in 0 until CS) for (x in 0 until CS) {
            val wx = ox + x
            val wz = oz + z
            val h = height(wx, wz)
            val bio = biome(wx, wz, h)
            heights[z * CS + x] = h
            biomes[z * CS + x] = bio
            val beach = h <= SEA + 1
            val top = when {
                beach -> if (Hash.f2(wx, wz, seed) < 0.25f && h < SEA) GRAVEL else SAND
                bio == 2 -> SAND
                bio == 3 -> SNOW
                bio == 4 -> if (h > SEA + 46) SNOW else STONE
                else -> GRASS
            }
            val sub = when {
                beach -> SAND
                bio == 2 -> SAND
                bio == 4 -> STONE
                else -> DIRT
            }
            for (y in 0..h) {
                val id = when {
                    y == 0 -> BEDROCK
                    y <= 2 && Hash.f2(wx * 7 + y, wz, seed) < 0.5f -> BEDROCK
                    y == h -> top
                    y >= h - 3 -> sub
                    bio == 2 && y >= h - 7 -> SANDSTONE
                    y < 8 -> DEEP
                    else -> STONE
                }
                b[idx(x, y, z)] = id.toShort()
            }
            // пещеры
            val yMax = h - 4
            for (y in 4..yMax) {
                val fx = wx * 0.04
                val fz = wz * 0.04
                val a = nC1.n3(fx, y * 0.06, fz)
                val c2 = nC2.n3(fx + 100, y * 0.06, fz)
                var carve = a * a + c2 * c2 < 0.011
                if (!carve && y < h - 8) carve = nC3.n3(wx * 0.02, y * 0.03, wz * 0.02) > 0.4
                if (carve) b[idx(x, y, z)] = (if (y < 9) LAVA else 0).toShort()
            }
            for (y in h + 1..SEA) b[idx(x, y, z)] = WATER.toShort()
        }

        // руды и блобы
        val rnd = java.util.Random(seed * 31 + cx * 341873128712L + cz * 132897987541L)
        fun vein(id: Int, deepId: Int, count: Int, ymin: Int, ymax: Int, size: Int, host: Boolean) {
            repeat(count) {
                var x = rnd.nextInt(CS)
                var y = ymin + rnd.nextInt(maxOf(1, ymax - ymin))
                var z = rnd.nextInt(CS)
                repeat(size) {
                    if (x in 0 until CS && z in 0 until CS && y in 1 until WH) {
                        val cur = b[idx(x, y, z)].toInt()
                        if (cur == STONE || cur == DEEP || (host && (cur == DIRT || cur == STONE))) {
                            b[idx(x, y, z)] = (if (cur == DEEP && deepId != 0) deepId else id).toShort()
                        }
                    }
                    x += rnd.nextInt(3) - 1; y += rnd.nextInt(3) - 1; z += rnd.nextInt(3) - 1
                }
            }
        }
        for ((i, o) in ores.withIndex()) {
            val (o1, o2) = oreIds[i]
            vein(o1, o2, o.count, o.ymin, o.ymax, o.size, false)
        }
        vein(GRANITE, 0, 4, 10, 70, 24, false)
        vein(DIORITE, 0, 4, 10, 70, 24, false)
        vein(ANDESITE, 0, 4, 10, 70, 24, false)
        vein(TUFF, 0, 3, 3, 20, 20, false)
        vein(GRAVEL, 0, 3, 10, 60, 18, false)
        vein(DIRT, 0, 4, 20, 70, 18, false)

        // высотная карта (до деревьев нужна по возможности точная)
        fun recomputeHeight() {
            for (z in 0 until CS) for (x in 0 until CS) {
                var y = WH - 1
                while (y > 0) {
                    val id = b[idx(x, y, z)].toInt()
                    val p = Reg.blocks[id]
                    if (id != 0 && p != null && (p.render == R_CUBE || p.render == R_CUTOUT || p.render == R_SLAB)) break
                    y--
                }
                ch.height[z * CS + x] = y
            }
        }

        fun put(wx: Int, y: Int, wz: Int, id: Int, onlyAir: Boolean) {
            val lx = wx - ox
            val lz = wz - oz
            if (lx !in 0 until CS || lz !in 0 until CS || y !in 1 until WH) return
            val cur = b[idx(lx, y, lz)].toInt()
            if (onlyAir && cur != 0) {
                val p = Reg.blocks[cur]
                if (p == null || (p.render != R_CROSS && p.render != R_CUTOUT)) return
            }
            b[idx(lx, y, lz)] = id.toShort()
        }

        // деревья (с запасом по краям, чтобы кроны на стыках чанков совпадали)
        for (dz in -3 until CS + 3) for (dx in -3 until CS + 3) {
            val wx = ox + dx
            val wz = oz + dz
            val hh = Hash.h2(wx, wz, seed + 99)
            val g = (hh ushr 4) and 0xFFFFF
            if (g % 20 != 0) continue
            val q = g / 20
            val h = height(wx, wz)
            if (h <= SEA + 1) continue
            val bio = biome(wx, wz, h)
            val density = when (bio) {
                1 -> 2; 0 -> 20; 3 -> 3; 4 -> if (h < SEA + 40) 4 else 0; else -> 0
            }
            if (density == 0 || q % density != 0) continue
            val kind = when (bio) {
                3, 4 -> 2
                1 -> if (((hh ushr 24) and 3) == 0) 1 else 0
                else -> 0
            }
            val y0 = h + 1
            val th = when (kind) {
                0 -> 4 + (hh ushr 8) % 3
                1 -> 5 + (hh ushr 8) % 3
                else -> 6 + (hh ushr 8) % 3
            }
            val log = when (kind) { 0 -> OAK_LOG; 1 -> BIRCH_LOG; else -> SPRUCE_LOG }
            val leaf = when (kind) { 0 -> OAK_LEAF; 1 -> BIRCH_LEAF; else -> SPRUCE_LEAF }
            if (kind < 2) {
                for (yy in th - 2..th + 1) {
                    val r = if (yy >= th) 1 else 2
                    for (ax in -r..r) for (az in -r..r) {
                        if (Math.abs(ax) == r && Math.abs(az) == r && (r == 2 || yy == th + 1) &&
                            Hash.f2(wx + ax, wz + az + yy, seed) < 0.5f
                        ) continue
                        put(wx + ax, y0 + yy, wz + az, leaf, true)
                    }
                }
            } else {
                for (yy in 2..th + 1) {
                    val r = if (yy == th + 1) 0 else minOf(2, (th + 1 - yy) / 2 + 1)
                    for (ax in -r..r) for (az in -r..r) {
                        if (r == 2 && Math.abs(ax) == 2 && Math.abs(az) == 2) continue
                        put(wx + ax, y0 + yy, wz + az, leaf, true)
                    }
                }
            }
            for (i in 0 until th) put(wx, y0 + i, wz, log, false)
        }

        // растительность
        for (z in 0 until CS) for (x in 0 until CS) {
            val h = heights[z * CS + x]
            val bio = biomes[z * CS + x]
            val wx = ox + x
            val wz = oz + z
            val topId = b[idx(x, h, z)].toInt()
            if (b[idx(x, h + 1, z)].toInt() != 0 || h + 3 >= WH) continue
            val u = Hash.f2(wx, wz, seed + 5)
            val y = h + 1
            when {
                topId == GRASS -> when {
                    u < 0.10 -> b[idx(x, y, z)] = (if (u < 0.01) FERN else SHORT).toShort()
                    u < 0.115 -> b[idx(x, y, z)] = FLOWERS[(Hash.h2(wx, wz, seed) ushr 5) % FLOWERS.size].toShort()
                    bio == 1 && u < 0.12 -> b[idx(x, y, z)] = (if (u < 0.1175) BMUSH else RMUSH).toShort()
                    bio == 0 && u > 0.9995 -> b[idx(x, y, z)] = PUMPKIN.toShort()
                }
                topId == SAND && bio == 2 && h > SEA -> when {
                    u < 0.006 -> {
                        val n = 1 + (Hash.h2(wx, wz, seed) ushr 3) % 3
                        for (i in 0 until n) b[idx(x, y + i, z)] = CACTUS.toShort()
                    }
                    u < 0.02 -> b[idx(x, y, z)] = DEAD.toShort()
                }
                topId == SAND && h == SEA + 1 && u < 0.08 -> {
                    val n = 1 + (Hash.h2(wx, wz, seed) ushr 3) % 3
                    for (i in 0 until n) b[idx(x, y + i, z)] = CANE.toShort()
                }
                topId == SNOW && bio == 3 && u < 0.03 -> b[idx(x, y, z)] = DEAD.toShort()
            }
        }

        recomputeHeight()
        for (i in b.indices) {
            val id = b[i].toInt()
            if (id != 0 && (Reg.blocks[id]?.light ?: 0) > 0) {
                ch.hasLight = true; break
            }
        }
        return ch
    }
}

class World(val seed: Long, private val dir: File?) {
    val gen = Gen(seed)
    val chunks = ConcurrentHashMap<Long, Chunk>()
    private val bedrock = Reg.id("bedrock")
    private val genLock = Any()

    fun key(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
    fun chunkAt(cx: Int, cz: Int): Chunk? = chunks[key(cx, cz)]

    fun getOrGen(cx: Int, cz: Int): Chunk {
        val k = key(cx, cz)
        chunks[k]?.let { return it }
        synchronized(genLock) {
            chunks[k]?.let { return it }
            val c = load(cx, cz) ?: gen.generate(cx, cz)
            chunks[k] = c
            return c
        }
    }

    fun blockAt(x: Int, y: Int, z: Int): Int {
        if (y < 0) return bedrock
        if (y >= WH) return 0
        val c = chunks[key(x shr 4, z shr 4)] ?: return bedrock
        return c.blocks[(y * CS + (z and 15)) * CS + (x and 15)].toInt()
    }

    fun isLoaded(x: Int, z: Int) = chunks.containsKey(key(x shr 4, z shr 4))

    fun surfaceY(x: Int, z: Int): Int {
        val c = chunks[key(x shr 4, z shr 4)] ?: return -1
        return c.height[(z and 15) * CS + (x and 15)]
    }

    private fun bump(cx: Int, cz: Int) {
        chunks[key(cx, cz)]?.version?.incrementAndGet()
    }

    fun setBlock(x: Int, y: Int, z: Int, id: Int) {
        if (y !in 0 until WH) return
        val cx = x shr 4
        val cz = z shr 4
        val c = chunks[key(cx, cz)] ?: return
        val lx = x and 15
        val lz = z and 15
        val old = c.blocks[(y * CS + lz) * CS + lx].toInt()
        c.blocks[(y * CS + lz) * CS + lx] = id.toShort()
        c.modified = true
        val lightChange = (Reg.blocks[id]?.light ?: 0) > 0 || (Reg.blocks[old]?.light ?: 0) > 0
        if (lightChange) c.hasLight = true
        var hy = WH - 1
        while (hy > 0) {
            val bid = c.blocks[(hy * CS + lz) * CS + lx].toInt()
            val p = Reg.blocks[bid]
            if (bid != 0 && p != null && (p.render == R_CUBE || p.render == R_CUTOUT || p.render == R_SLAB)) break
            hy--
        }
        c.height[lz * CS + lx] = hy
        c.version.incrementAndGet()
        if (lightChange) {
            for (dx in -1..1) for (dz in -1..1) if (dx != 0 || dz != 0) bump(cx + dx, cz + dz)
        } else {
            if (lx == 0) bump(cx - 1, cz)
            if (lx == 15) bump(cx + 1, cz)
            if (lz == 0) bump(cx, cz - 1)
            if (lz == 15) bump(cx, cz + 1)
        }
    }

    fun collides(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): Boolean {
        val ix0 = floor(x0).toInt()
        val ix1 = floor(x1 - 0.0001f).toInt()
        val iy0 = floor(y0).toInt()
        val iy1 = floor(y1 - 0.0001f).toInt()
        val iz0 = floor(z0).toInt()
        val iz1 = floor(z1 - 0.0001f).toInt()
        for (y in iy0..iy1) for (z in iz0..iz1) for (x in ix0..ix1) {
            val id = blockAt(x, y, z)
            if (id == 0) continue
            val p = Reg.blocks[id] ?: continue
            if (!p.solid) continue
            val top = if (p.render == R_SLAB) y + 0.5f else y + 1f
            if (y0 < top && y1 > y) return true
        }
        return false
    }

    // ---------- сохранение ----------
    private fun file(cx: Int, cz: Int) = File(dir, "chunks/${cx}_$cz.bin")

    fun saveChunk(c: Chunk) {
        if (dir == null || !c.modified) return
        try {
            val f = file(c.cx, c.cz)
            f.parentFile?.mkdirs()
            val bb = ByteBuffer.allocate(c.blocks.size * 2)
            bb.asShortBuffer().put(c.blocks)
            DeflaterOutputStream(f.outputStream(), Deflater(1)).use { it.write(bb.array()) }
        } catch (_: Exception) {
        }
    }

    fun saveAll() {
        for (c in chunks.values) saveChunk(c)
    }

    private fun load(cx: Int, cz: Int): Chunk? {
        if (dir == null) return null
        val f = file(cx, cz)
        if (!f.exists()) return null
        return try {
            val data = InflaterInputStream(f.inputStream()).use { it.readBytes() }
            val c = Chunk(cx, cz)
            ByteBuffer.wrap(data).asShortBuffer().get(c.blocks)
            c.modified = true
            for (z in 0 until CS) for (x in 0 until CS) {
                var y = WH - 1
                while (y > 0) {
                    val id = c.blocks[(y * CS + z) * CS + x].toInt()
                    val p = Reg.blocks[id]
                    if (id != 0 && p != null && (p.render == R_CUBE || p.render == R_CUTOUT || p.render == R_SLAB)) break
                    y--
                }
                c.height[z * CS + x] = y
            }
            for (i in c.blocks.indices) {
                val id = c.blocks[i].toInt()
                if (id != 0 && (Reg.blocks[id]?.light ?: 0) > 0) {
                    c.hasLight = true; break
                }
            }
            c.grass = gen.let { 0x79C05A }
            c
        } catch (_: Exception) {
            null
        }
    }

    fun unload(cx: Int, cz: Int) {
        val c = chunks.remove(key(cx, cz)) ?: return
        saveChunk(c)
    }
}
