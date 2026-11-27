package com.fcraft.game

import com.fcraft.world.World
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

open class Body(var x: Float, var y: Float, var z: Float, val hw: Float, val h: Float) {
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var onGround = false
    var hitWall = false

    fun move(w: World, dt: Float) {
        hitWall = false
        axis(w, vx * dt, 0f, 0f)
        axis(w, 0f, 0f, vz * dt)
        onGround = false
        axis(w, 0f, vy * dt, 0f)
    }

    private fun axis(w: World, dx: Float, dy: Float, dz: Float) {
        val d = max(abs(dx), max(abs(dy), abs(dz)))
        if (d == 0f) return
        val n = ceil(d / 0.05f).toInt()
        val sx = dx / n
        val sy = dy / n
        val sz = dz / n
        for (i in 0 until n) {
            val nx = x + sx
            val ny = y + sy
            val nz = z + sz
            if (w.collides(nx - hw, ny, nz - hw, nx + hw, ny + h, nz + hw)) {
                if (dy != 0f) {
                    if (dy < 0) onGround = true
                    vy = 0f
                } else hitWall = true
                if (dx != 0f) vx = 0f
                if (dz != 0f) vz = 0f
                return
            }
            x = nx; y = ny; z = nz
        }
    }
}

class Part(
    val x: Float, val y: Float, val z: Float, val w: Float, val h: Float, val d: Float,
    val color: Int, val swing: Float = 0f
)

class MobType(
    val key: String, val name: String, val hw: Float, val h: Float, val hp: Float, val speed: Float,
    val hostile: Boolean, val dmg: Float, val parts: List<Part>, val drops: List<Triple<String, Int, Int>>
)

object Mobs {
    val types = HashMap<String, MobType>()
    val passive = ArrayList<MobType>()
    val hostile = ArrayList<MobType>()

    private fun leg(x: Float, z: Float, w: Float, h: Float, c: Int, sw: Float) = Part(x, h / 2f, z, w, h, w, c, sw)

    private fun reg(t: MobType) {
        types[t.key] = t
        if (t.hostile) hostile.add(t) else passive.add(t)
    }

    init {
        val pink = 0xF0A8A0
        reg(
            MobType(
                "pig", "Pig", 0.45f, 0.9f, 10f, 1.4f, false, 0f,
                listOf(
                    Part(0f, 0.55f, 0f, 0.7f, 0.5f, 0.95f, pink), Part(0f, 0.7f, 0.6f, 0.5f, 0.45f, 0.45f, 0xF3B1AA),
                    Part(0f, 0.62f, 0.85f, 0.25f, 0.18f, 0.1f, 0xD98880),
                    leg(-0.2f, 0.3f, 0.2f, 0.35f, 0xE8978F, 0.8f), leg(0.2f, 0.3f, 0.2f, 0.35f, 0xE8978F, -0.8f),
                    leg(-0.2f, -0.3f, 0.2f, 0.35f, 0xE8978F, -0.8f), leg(0.2f, -0.3f, 0.2f, 0.35f, 0xE8978F, 0.8f)
                ),
                listOf(Triple("porkchop", 1, 3))
            )
        )
        reg(
            MobType(
                "cow", "Cow", 0.45f, 1.4f, 10f, 1.2f, false, 0f,
                listOf(
                    Part(0f, 0.95f, 0f, 0.8f, 0.7f, 1.2f, 0x4A3728), Part(0.1f, 1.0f, 0.1f, 0.5f, 0.3f, 0.5f, 0xE8E8E8),
                    Part(0f, 1.25f, 0.8f, 0.55f, 0.55f, 0.4f, 0x5A4636), Part(0f, 1.1f, 1.02f, 0.4f, 0.25f, 0.1f, 0xD8C0B0),
                    leg(-0.25f, 0.45f, 0.22f, 0.6f, 0x3A2A1C, 0.7f), leg(0.25f, 0.45f, 0.22f, 0.6f, 0x3A2A1C, -0.7f),
                    leg(-0.25f, -0.45f, 0.22f, 0.6f, 0x3A2A1C, -0.7f), leg(0.25f, -0.45f, 0.22f, 0.6f, 0x3A2A1C, 0.7f)
                ),
                listOf(Triple("beef", 1, 3), Triple("leather", 0, 2))
            )
        )
        reg(
            MobType(
                "sheep", "Sheep", 0.45f, 1.3f, 8f, 1.2f, false, 0f,
                listOf(
                    Part(0f, 0.95f, 0f, 0.85f, 0.75f, 1.1f, 0xEDEDED), Part(0f, 1.15f, 0.7f, 0.45f, 0.45f, 0.4f, 0xC9B8A4),
                    leg(-0.22f, 0.4f, 0.2f, 0.55f, 0xC9B8A4, 0.7f), leg(0.22f, 0.4f, 0.2f, 0.55f, 0xC9B8A4, -0.7f),
                    leg(-0.22f, -0.4f, 0.2f, 0.55f, 0xC9B8A4, -0.7f), leg(0.22f, -0.4f, 0.2f, 0.55f, 0xC9B8A4, 0.7f)
                ),
                listOf(Triple("mutton", 1, 2), Triple("white_wool", 1, 1))
            )
        )
        reg(
            MobType(
                "chicken", "Chicken", 0.2f, 0.7f, 4f, 1.2f, false, 0f,
                listOf(
                    Part(0f, 0.45f, 0f, 0.35f, 0.4f, 0.5f, 0xF2F2F2), Part(0f, 0.75f, 0.28f, 0.22f, 0.25f, 0.2f, 0xF8F8F8),
                    Part(0f, 0.72f, 0.42f, 0.1f, 0.08f, 0.1f, 0xE8A820), Part(0f, 0.68f, 0.35f, 0.06f, 0.1f, 0.06f, 0xD02A2A),
                    leg(-0.08f, 0f, 0.06f, 0.25f, 0xE8A820, 0.8f), leg(0.08f, 0f, 0.06f, 0.25f, 0xE8A820, -0.8f)
                ),
                listOf(Triple("chicken", 1, 1), Triple("feather", 0, 2))
            )
        )
        reg(
            MobType(
                "zombie", "Zombie", 0.3f, 1.95f, 20f, 1.6f, true, 3f,
                listOf(
                    Part(0f, 1.15f, 0f, 0.5f, 0.75f, 0.25f, 0x2C8A8A), Part(0f, 1.7f, 0f, 0.5f, 0.5f, 0.5f, 0x6EA44E),
                    Part(-0.37f, 1.4f, 0.3f, 0.25f, 0.25f, 0.7f, 0x6EA44E), Part(0.37f, 1.4f, 0.3f, 0.25f, 0.25f, 0.7f, 0x6EA44E),
                    leg(-0.125f, 0f, 0.25f, 0.78f, 0x3B3BA8, 0.7f), leg(0.125f, 0f, 0.25f, 0.78f, 0x3B3BA8, -0.7f)
                ),
                listOf(Triple("rotten_flesh", 0, 2), Triple("carrot", 0, 1))
            )
        )
        reg(
            MobType(
                "skeleton", "Skeleton", 0.3f, 1.95f, 20f, 1.6f, true, 2.5f,
                listOf(
                    Part(0f, 1.15f, 0f, 0.45f, 0.75f, 0.2f, 0xCFCFC4), Part(0f, 1.7f, 0f, 0.5f, 0.5f, 0.5f, 0xE0E0D6),
                    Part(-0.33f, 1.4f, 0.25f, 0.12f, 0.12f, 0.7f, 0xCFCFC4), Part(0.33f, 1.4f, 0.25f, 0.12f, 0.12f, 0.7f, 0xCFCFC4),
                    leg(-0.1f, 0f, 0.12f, 0.78f, 0xCFCFC4, 0.7f), leg(0.1f, 0f, 0.12f, 0.78f, 0xCFCFC4, -0.7f)
                ),
                listOf(Triple("bone", 0, 2))
            )
        )
        reg(
            MobType(
                "creeper", "Creeper", 0.3f, 1.7f, 20f, 1.5f, true, 0f,
                listOf(
                    Part(0f, 0.95f, 0f, 0.5f, 0.75f, 0.3f, 0x4FA33A), Part(0f, 1.5f, 0f, 0.5f, 0.5f, 0.5f, 0x5DB847),
                    leg(-0.12f, 0.14f, 0.24f, 0.35f, 0x3E8A2C, 0.6f), leg(0.12f, 0.14f, 0.24f, 0.35f, 0x3E8A2C, -0.6f),
                    leg(-0.12f, -0.14f, 0.24f, 0.35f, 0x3E8A2C, -0.6f), leg(0.12f, -0.14f, 0.24f, 0.35f, 0x3E8A2C, 0.6f)
                ),
                listOf(Triple("gunpowder", 0, 2))
            )
        )
        reg(
            MobType(
                "spider", "Spider", 0.7f, 0.9f, 16f, 2.2f, true, 2f,
                listOf(
                    Part(0f, 0.5f, 0f, 0.7f, 0.5f, 0.9f, 0x3A2F2F), Part(0f, 0.5f, 0.65f, 0.5f, 0.45f, 0.45f, 0x4A3A3A),
                    Part(0.1f, 0.55f, 0.75f, 0.1f, 0.1f, 0.05f, 0xD02A2A),
                    Part(-0.7f, 0.3f, 0.3f, 0.7f, 0.1f, 0.1f, 0x2A2020, 0.5f), Part(0.7f, 0.3f, 0.3f, 0.7f, 0.1f, 0.1f, 0x2A2020, -0.5f),
                    Part(-0.7f, 0.3f, -0.3f, 0.7f, 0.1f, 0.1f, 0x2A2020, -0.5f), Part(0.7f, 0.3f, -0.3f, 0.7f, 0.1f, 0.1f, 0x2A2020, 0.5f)
                ),
                listOf(Triple("string", 0, 2), Triple("spider_eye", 0, 1))
            )
        )
    }
}

class Mob(val type: MobType, x: Float, y: Float, z: Float) : Body(x, y, z, type.hw, type.h) {
    var yaw = 0f
    var walkT = 0f
    var hp = type.hp
    var hurt = 0f
    var aiTimer = 0f
    var wander = false
    var attackCd = 0f
    var fuse = -1f
    var moving = false
    val rnd = java.util.Random()
}
