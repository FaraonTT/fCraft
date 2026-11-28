package com.fcraft.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fcraft.core.*
import com.fcraft.world.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

enum class Overlay { NONE, PAUSE, INVENTORY, CRAFTING, FURNACE, CHEST, DEAD }

class MeshResult(val cx: Int, val cz: Int, val data: MeshData)

class Hit(val x: Int, val y: Int, val z: Int, val nx: Int, val ny: Int, val nz: Int, val dist: Float)

class Input {
    @Volatile var mx = 0f
    @Volatile var mz = 0f
    @Volatile var jump = false
    @Volatile var down = false
    @Volatile var breaking = false
    @Volatile var use = false
    private var lx = 0f
    private var ly = 0f

    @Synchronized
    fun addLook(dx: Float, dy: Float) {
        lx += dx; ly += dy
    }

    @Synchronized
    fun takeLook(): FloatArray {
        val r = floatArrayOf(lx, ly)
        lx = 0f; ly = 0f
        return r
    }

    fun reset() {
        mx = 0f; mz = 0f; jump = false; down = false; breaking = false; use = false
    }
}

class Player : Body(0f, 80f, 0f, 0.3f, 1.8f) {
    var yaw = 0f
    var pitch = 0f
    var health = 20f
    var hunger = 20f
    var flying = false
    var peakY = 80f
    var air = 10f
}

class SlotRef(
    val arr: Array<ItemStack?>, val i: Int, val kind: Int = 0,
    val accepts: (ItemStack) -> Boolean = { true }
)

const val DAY = 1200f

class GameSession(val ctx: Context, val meta: WorldMeta) {
    val lock = Any()
    private val dir = meta.dir(ctx)
    val world = World(meta.seed, dir)
    val player = Player()
    val inv = Inventory()
    val input = Input()
    val mobs = ArrayList<Mob>()
    val furnaces = HashMap<Long, Furnace>()
    val chests = HashMap<Long, Array<ItemStack?>>()
    val meshOut = ConcurrentLinkedQueue<MeshResult>()
    val creative = meta.creative
    var dayTime = 100f

    // состояние для интерфейса
    var overlay by mutableStateOf(Overlay.NONE)
    var invTick by mutableIntStateOf(0)
    var hudHealth by mutableIntStateOf(20)
    var hudHunger by mutableIntStateOf(20)
    var hudSel by mutableIntStateOf(0)
    var hudText by mutableStateOf("")
    var message by mutableStateOf("")
    var loaded by mutableStateOf(false)
    var flyHud by mutableStateOf(false)
    var fps = 0

    val craftGrid = arrayOfNulls<ItemStack>(9)
    var craftOut: ItemStack? = null
    var gridW = 2
    var cursor: ItemStack? = null
    var openFurnace: Furnace? = null
    var openChest: Array<ItemStack?>? = null

    // состояние цели и руки
    @Volatile var target: Hit? = null
    @Volatile var breakProg = 0f
    @Volatile var swingPhase = 0f
    @Volatile var useSwing = 0f

    private var breakX = 0
    private var breakY = 0
    private var breakZ = 0
    private var creativeCd = 0f
    private var attackCd = 0f
    private var eatCd = 0f
    private var spawnTimer = 3f
    private var hudTimer = 0f
    private var msgTimer = 0f
    private var saveTimer = 0f
    private var unloadTimer = 0f
    private var jumpPrev = false
    private var lastJumpTap = 0L
    private var spawnFixed = false
    private val rnd = java.util.Random()
    private val GRASS = Reg.id("grass_block")
    private var running = false
    private val workers = ArrayList<Thread>()
    private var offsetCache: List<IntArray> = emptyList()
    private var offsetR = -1

    // ---------------- запуск ----------------
    fun start() {
        val sc = world.gen.spawnColumn()
        player.x = sc[0] + 0.5f
        player.z = sc[1] + 0.5f
        player.y = world.gen.height(sc[0], sc[1]) + 2f
        player.peakY = player.y
        load()
        running = true
        for (i in 0 until 2) {
            val t = Thread { workerLoop() }
            t.isDaemon = true
            t.priority = Thread.NORM_PRIORITY - 1
            t.start()
            workers.add(t)
        }
        refreshHud()
    }

    fun stop() {
        running = false
        save()
    }

    private fun offsets(r: Int): List<IntArray> {
        if (r != offsetR) {
            val l = ArrayList<IntArray>()
            for (dx in -r..r) for (dz in -r..r) l.add(intArrayOf(dx, dz, dx * dx + dz * dz))
            l.sortBy { it[2] }
            offsetCache = l
            offsetR = r
        }
        return offsetCache
    }

    private fun workerLoop() {
        while (running) {
            var did = false
            try {
                val pcx = floor(player.x / 16f).toInt()
                val pcz = floor(player.z / 16f).toInt()
                val rd = Settings.renderDist
                for (o in offsets(rd + 1)) {
                    val cx = pcx + o[0]
                    val cz = pcz + o[1]
                    val c = world.chunkAt(cx, cz)
                    if (c == null) {
                        world.getOrGen(cx, cz); did = true; break
                    }
                    val ver = c.version.get()
                    if (c.meshed != ver && o[2] <= rd * rd + rd &&
                        world.chunkAt(cx + 1, cz) != null && world.chunkAt(cx - 1, cz) != null &&
                        world.chunkAt(cx, cz + 1) != null && world.chunkAt(cx, cz - 1) != null &&
                        c.busy.compareAndSet(false, true)
                    ) {
                        try {
                            val md = Mesher.build(world, c)
                            c.meshed = ver
                            meshOut.add(MeshResult(cx, cz, md))
                        } finally {
                            c.busy.set(false)
                        }
                        did = true; break
                    }
                }
            } catch (_: Exception) {
            }
            if (!did) try {
                Thread.sleep(8)
            } catch (_: InterruptedException) {
            }
        }
    }

    // ---------------- небо ----------------
    fun sunHeight(): Float = sin(dayTime / DAY * 6.2831855f)
    fun isNight(): Boolean = sunHeight() < -0.05f

    // ---------------- главный тик ----------------
    fun tick(dt: Float) {
        synchronized(lock) {
            for (f in furnaces.values) f.tick(dt)
            if (overlay == Overlay.FURNACE) {
                hudTimer += dt
                if (hudTimer > 0.2f) {
                    hudTimer = 0f; invTick++
                }
            }
            if (msgTimer > 0f) {
                msgTimer -= dt
                if (msgTimer <= 0f) message = ""
            }
            if (!loaded || overlay != Overlay.NONE) return
            if (!spawnFixed) {
                // если спавн оказался внутри кроны/блока — поднимаем игрока
                var n = 0
                val p = player
                while (n < 60 && world.collides(p.x - 0.3f, p.y, p.z - 0.3f, p.x + 0.3f, p.y + 1.8f, p.z + 0.3f)) {
                    p.y += 1f; n++
                }
                p.peakY = p.y
                spawnFixed = true
            }
            dayTime = (dayTime + dt) % DAY
            useSwing = max(0f, useSwing - dt * 3.5f)
            physics(dt)
            interaction(dt)
            updateMobs(dt)
            survival(dt)
            saveTimer += dt
            if (saveTimer > 60f) {
                saveTimer = 0f; save()
            }
            unloadTimer += dt
            if (unloadTimer > 2f) {
                unloadTimer = 0f; unloadFar()
            }
            hudTimer += dt
            if (hudTimer > 0.2f) {
                hudTimer = 0f
                hudText = "X ${player.x.toInt()}  Y ${player.y.toInt()}  Z ${player.z.toInt()}" +
                    (if (Settings.showFps) "   ${fps} FPS" else "")
            }
        }
    }

    private fun unloadFar() {
        val pcx = floor(player.x / 16f).toInt()
        val pcz = floor(player.z / 16f).toInt()
        val lim = Settings.renderDist + 3
        for (c in world.chunks.values.toList()) {
            if (abs(c.cx - pcx) > lim || abs(c.cz - pcz) > lim) world.unload(c.cx, c.cz)
        }
    }

    private fun toast(s: String) {
        message = s; msgTimer = 2.5f
    }

    // ---------------- физика игрока ----------------
    private fun physics(dt: Float) {
        val p = player
        val lk = input.takeLook()
        p.yaw -= lk[0] * 0.0035f * Settings.sens
        p.pitch = (p.pitch - lk[1] * 0.0035f * Settings.sens).coerceIn(-1.55f, 1.55f)
        val sy = sin(p.yaw)
        val cy = cos(p.yaw)
        var mx = input.mx
        var mz = input.mz
        val len = hypot(mx, mz)
        if (len > 1f) {
            mx /= len; mz /= len
        }
        val feetId = world.blockAt(floor(p.x).toInt(), floor(p.y + 0.3f).toInt(), floor(p.z).toInt())
        val inLiquid = Reg.blocks[feetId]?.liquid == true
        val inLava = inLiquid && (Reg.blocks[feetId]?.light ?: 0) > 0

        // двойное нажатие прыжка — полёт в творческом режиме
        if (input.jump && !jumpPrev && creative) {
            val now = System.currentTimeMillis()
            if (now - lastJumpTap < 300) {
                p.flying = !p.flying; flyHud = p.flying
            }
            lastJumpTap = now
        }
        jumpPrev = input.jump
        if (p.flying && !creative) {
            p.flying = false; flyHud = false
        }

        var speed = if (len > 0.9f && mz > 0.7f) 5.6f else 4.3f
        if (p.flying) speed = 10.5f
        if (inLiquid) speed *= 0.55f
        val tx = (-sy * mz + cy * mx) * speed
        val tz = (-cy * mz - sy * mx) * speed
        val k = min(1f, dt * (if (p.onGround || p.flying || inLiquid) 14f else 4f))
        p.vx += (tx - p.vx) * k
        p.vz += (tz - p.vz) * k

        if (p.flying) {
            val ty = ((if (input.jump) 1f else 0f) - (if (input.down) 1f else 0f)) * 9f
            p.vy += (ty - p.vy) * min(1f, dt * 10f)
        } else if (inLiquid) {
            p.vy -= 8f * dt
            if (input.jump) p.vy += 22f * dt
            p.vy = p.vy.coerceIn(-4f, 3.5f)
        } else {
            p.vy -= 32f * dt
            if (input.jump && p.onGround) p.vy = 9.2f
            if (p.vy < -55f) p.vy = -55f
        }
        p.move(world, dt)

        // автопрыжок
        if (p.hitWall && p.onGround && len > 0.3f && !p.flying) {
            val fx = tx / max(speed, 0.01f)
            val fz = tz / max(speed, 0.01f)
            if (!world.collides(p.x - 0.3f + fx * 0.4f, p.y + 1.0f, p.z - 0.3f + fz * 0.4f, p.x + 0.3f + fx * 0.4f, p.y + 2.8f, p.z + 0.3f + fz * 0.4f)) p.vy = 8.6f
        }

        // падение
        if (p.onGround || inLiquid || p.flying) {
            val d = p.peakY - p.y
            if (d > 3f && !creative && !inLiquid && !p.flying) hurt(d - 3f)
            p.peakY = p.y
        } else if (p.y > p.peakY) p.peakY = p.y

        if (inLava) hurt(4f * dt)
        val headId = world.blockAt(floor(p.x).toInt(), floor(p.y + 1.62f).toInt(), floor(p.z).toInt())
        if (Reg.blocks[headId]?.liquid == true && !creative) {
            p.air -= dt
            if (p.air < 0f) {
                p.air = 0f; hurt(2f * dt)
            }
        } else p.air = min(10f, p.air + dt * 4f)
        if (p.y < -20f) hurt(100f)
    }

    fun hurt(d: Float) {
        if (creative || overlay == Overlay.DEAD) return
        val dd = d * (1f - min(20, inv.armorPoints()) * 0.04f)
        player.health -= dd
        if (player.health <= 0f) {
            player.health = 0f
            overlay = Overlay.DEAD
            input.reset()
        }
        hudHealth = ceil(player.health).toInt()
    }

    fun respawn() {
        synchronized(lock) {
            val sc = world.gen.spawnColumn()
            player.x = sc[0] + 0.5f; player.z = sc[1] + 0.5f
            player.y = world.gen.height(sc[0], sc[1]) + 3f
            player.vx = 0f; player.vy = 0f; player.vz = 0f
            player.peakY = player.y
            player.health = 20f; player.hunger = 20f
            mobs.clear()
            spawnFixed = false
            overlay = Overlay.NONE
            refreshHud()
        }
    }

    private fun survival(dt: Float) {
        if (creative) return
        val p = player
        p.hunger = max(0f, p.hunger - dt * (if (hypot(p.vx, p.vz) > 5f) 0.05f else 0.02f))
        if (p.hunger >= 18f && p.health < 20f) p.health = min(20f, p.health + dt * 0.6f)
        if (p.hunger <= 0f && p.health > 1f) p.health -= dt * 0.25f
        hudHealth = ceil(p.health).toInt()
        hudHunger = ceil(p.hunger).toInt()
    }

    fun refreshHud() {
        hudHealth = ceil(player.health).toInt()
        hudHunger = ceil(player.hunger).toInt()
        hudSel = inv.selected
        invTick++
    }

    // ---------------- лучи ----------------
    private fun raycast(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxD: Float): Hit? {
        var x = floor(ox).toInt()
        var y = floor(oy).toInt()
        var z = floor(oz).toInt()
        val stX = if (dx > 0) 1 else -1
        val stY = if (dy > 0) 1 else -1
        val stZ = if (dz > 0) 1 else -1
        val inf = Float.MAX_VALUE
        val tdX = if (dx != 0f) abs(1f / dx) else inf
        val tdY = if (dy != 0f) abs(1f / dy) else inf
        val tdZ = if (dz != 0f) abs(1f / dz) else inf
        var tmX = if (dx > 0) (x + 1 - ox) / dx else if (dx < 0) (ox - x) / -dx else inf
        var tmY = if (dy > 0) (y + 1 - oy) / dy else if (dy < 0) (oy - y) / -dy else inf
        var tmZ = if (dz > 0) (z + 1 - oz) / dz else if (dz < 0) (oz - z) / -dz else inf
        var t = 0f
        var nx = 0
        var ny = 0
        var nz = 0
        while (t <= maxD) {
            val id = world.blockAt(x, y, z)
            if (id != 0) {
                val b = Reg.blocks[id]
                if (b != null && !b.liquid && (nx != 0 || ny != 0 || nz != 0)) return Hit(x, y, z, nx, ny, nz, t)
            }
            if (tmX < tmY && tmX < tmZ) {
                x += stX; t = tmX; tmX += tdX; nx = -stX; ny = 0; nz = 0
            } else if (tmY < tmZ) {
                y += stY; t = tmY; tmY += tdY; nx = 0; ny = -stY; nz = 0
            } else {
                z += stZ; t = tmZ; tmZ += tdZ; nx = 0; ny = 0; nz = -stZ
            }
        }
        return null
    }

    private fun rayBox(
        ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float,
        mnx: Float, mny: Float, mnz: Float, mxx: Float, mxy: Float, mxz: Float
    ): Float {
        var t0 = 0f
        var t1 = Float.MAX_VALUE
        val o = floatArrayOf(ox, oy, oz)
        val d = floatArrayOf(dx, dy, dz)
        val mn = floatArrayOf(mnx, mny, mnz)
        val mx = floatArrayOf(mxx, mxy, mxz)
        for (i in 0 until 3) {
            if (abs(d[i]) < 1e-6f) {
                if (o[i] < mn[i] || o[i] > mx[i]) return -1f
            } else {
                var a = (mn[i] - o[i]) / d[i]
                var b = (mx[i] - o[i]) / d[i]
                if (a > b) {
                    val tmp = a; a = b; b = tmp
                }
                t0 = max(t0, a); t1 = min(t1, b)
                if (t0 > t1) return -1f
            }
        }
        return t0
    }

    // ---------------- взаимодействие ----------------
    fun breakTime(id: Int): Float {
        val b = Reg.blocks[id] ?: return 0f
        if (b.hardness < 0f) return Float.MAX_VALUE
        if (b.hardness == 0f) return 0.05f
        val held = inv.held()?.def
        var spd = 1f
        var ok = !b.needsTool
        if (held != null) {
            if (b.tool != ToolType.NONE && held.tool == b.tool) {
                spd = held.speed
                if (held.tier >= b.tier) ok = true
            } else if (held.tool == ToolType.SHEARS && (b.render == R_CUTOUT || Reg.items[id].key.endsWith("_wool"))) spd = 5f
        }
        return b.hardness * (if (ok) 1.5f else 5f) / spd
    }

    private fun interaction(dt: Float) {
        val p = player
        val ey = p.y + 1.62f
        val fx = -sin(p.yaw) * cos(p.pitch)
        val fy = sin(p.pitch)
        val fz = -cos(p.yaw) * cos(p.pitch)
        val reach = if (creative) 6f else 4.5f
        var hit = raycast(p.x, ey, p.z, fx, fy, fz, reach)
        var mobHit: Mob? = null
        var best = hit?.dist ?: Float.MAX_VALUE
        for (m in mobs) {
            val t = rayBox(p.x, ey, p.z, fx, fy, fz, m.x - m.hw, m.y, m.z - m.hw, m.x + m.hw, m.y + m.h, m.z + m.hw)
            if (t >= 0f && t < min(best, 3.6f)) {
                best = t; mobHit = m
            }
        }
        if (mobHit != null) hit = null
        target = hit
        attackCd -= dt

        if (input.breaking) {
            if (mobHit != null) {
                swingPhase += dt * 11f
                if (attackCd <= 0f) {
                    hitMob(mobHit); attackCd = 0.45f
                }
                breakProg = 0f
            } else if (hit != null) {
                swingPhase += dt * 11f
                val id = world.blockAt(hit.x, hit.y, hit.z)
                val b = Reg.blocks[id]
                if (b != null) {
                    if (creative) {
                        creativeCd -= dt
                        if (creativeCd <= 0f && b.hardness >= 0f) {
                            breakBlock(hit.x, hit.y, hit.z); creativeCd = 0.25f
                        }
                    } else {
                        if (hit.x == breakX && hit.y == breakY && hit.z == breakZ) {
                            breakProg += dt / breakTime(id)
                        } else {
                            breakX = hit.x; breakY = hit.y; breakZ = hit.z; breakProg = 0f
                        }
                        if (breakProg >= 1f) {
                            breakBlock(hit.x, hit.y, hit.z); breakProg = 0f
                        }
                    }
                }
            } else {
                breakProg = 0f
                swingPhase += dt * 11f
            }
        } else {
            breakProg = 0f
            creativeCd = 0f
            swingPhase = 0f
        }

        eatCd -= dt
        if (input.use) {
            input.use = false
            useAction(hit)
        }
    }

    private fun pk(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

    private fun useAction(hit: Hit?) {
        useSwing = 1f
        val held = inv.held()
        if (hit != null) {
            val id = world.blockAt(hit.x, hit.y, hit.z)
            val b = Reg.blocks[id]
            if (b != null && b.interact != 0) {
                val key = pk(hit.x, hit.y, hit.z)
                when (b.interact) {
                    1 -> {
                        gridW = 3; clearCraft(); overlay = Overlay.CRAFTING
                    }
                    2 -> {
                        openFurnace = furnaces.getOrPut(key) { Furnace() }; overlay = Overlay.FURNACE
                    }
                    else -> {
                        openChest = chests.getOrPut(key) { arrayOfNulls(27) }; overlay = Overlay.CHEST
                    }
                }
                input.reset()
                invTick++
                return
            }
        }
        if (held != null) {
            val d = held.def
            if (d.food > 0) {
                if (eatCd <= 0f && player.hunger < 20f) {
                    player.hunger = min(20f, player.hunger + d.food)
                    if (!creative) inv.consumeHeld()
                    eatCd = 0.8f
                    refreshHud()
                }
                return
            }
            val blk = d.block
            if (blk != null && hit != null) {
                val bx = hit.x + hit.nx
                val by = hit.y + hit.ny
                val bz = hit.z + hit.nz
                if (by !in 1 until WH) return
                val cur = Reg.blocks[world.blockAt(bx, by, bz)]
                val replaceable = world.blockAt(bx, by, bz) == 0 || cur?.liquid == true || cur?.render == R_CROSS
                if (!replaceable) return
                if (blk.render == R_CROSS && Reg.blocks[world.blockAt(bx, by - 1, bz)]?.solid != true) return
                val p = player
                if (blk.solid && p.x - 0.3f < bx + 1 && p.x + 0.3f > bx && p.y < by + 1 && p.y + 1.8f > by &&
                    p.z - 0.3f < bz + 1 && p.z + 0.3f > bz
                ) return
                world.setBlock(bx, by, bz, d.id)
                if (!creative) inv.consumeHeld()
                refreshHud()
            }
        }
    }

    private fun breakBlock(x: Int, y: Int, z: Int) {
        val id = world.blockAt(x, y, z)
        val b = Reg.blocks[id] ?: return
        if (b.hardness < 0f) return
        val held = inv.held()?.def
        val harvest = !b.needsTool || (held != null && held.tool == b.tool && held.tier >= b.tier)
        world.setBlock(x, y, z, 0)
        val key = Reg.items[id].key
        val pos = pk(x, y, z)
        if (b.interact == 3) chests.remove(pos)?.let { arr -> for (s in arr) if (s != null) inv.add(s) }
        if (b.interact == 2) furnaces.remove(pos)?.let { f -> for (s in f.slots) if (s != null) inv.add(s) }
        if (!creative) {
            val shears = held?.tool == ToolType.SHEARS && (b.render == R_CUTOUT || key.endsWith("_wool"))
            if (shears) inv.addKey(key)
            else if (harvest) {
                val dk = b.drop ?: key
                if (dk.isNotEmpty()) inv.addKey(dk, b.dropMin + rnd.nextInt(b.dropMax - b.dropMin + 1))
            }
            if (held != null && held.durability > 0 && b.hardness > 0f) damageHeld()
        }
        // растения над блоком
        var yy = y + 1
        while (yy < WH) {
            val up = world.blockAt(x, yy, z)
            val ub = Reg.blocks[up]
            if (up == 0 || ub == null || ub.render != R_CROSS) break
            world.setBlock(x, yy, z, 0)
            if (!creative) {
                val dk = ub.drop ?: Reg.items[up].key
                if (dk.isNotEmpty()) inv.addKey(dk, ub.dropMin + rnd.nextInt(ub.dropMax - ub.dropMin + 1))
            }
            yy++
        }
        refreshHud()
    }

    private fun damageHeld() {
        val s = inv.held() ?: return
        s.dmg++
        if (s.dmg >= s.def.durability) inv.slots[inv.selected] = null
    }

    private fun hitMob(m: Mob) {
        val held = inv.held()
        val dmg = if (held != null && held.def.attack > 1f) held.def.attack else 1f
        m.hp -= if (creative) 100f else dmg
        m.hurt = 0.4f
        val dx = m.x - player.x
        val dz = m.z - player.z
        val l = max(0.01f, hypot(dx, dz))
        m.vx = dx / l * 6f; m.vz = dz / l * 6f; m.vy = 5f
        if (!creative && held != null && held.def.durability > 0 && held.def.tool != ToolType.NONE) {
            damageHeld(); refreshHud()
        }
    }

    // ---------------- мобы ----------------
    private fun die(m: Mob) {
        if (m.hp <= -50f) return
        for ((k, mn, mx) in m.type.drops) {
            val n = mn + rnd.nextInt(mx - mn + 1)
            if (n > 0) inv.addKey(k, n)
        }
        refreshHud()
    }

    private fun explode(m: Mob) {
        val r = 3
        val cx = floor(m.x).toInt()
        val cy = floor(m.y + 0.5f).toInt()
        val cz = floor(m.z).toInt()
        for (dx in -r..r) for (dy in -r..r) for (dz in -r..r) {
            if (dx * dx + dy * dy + dz * dz > r * r + 1) continue
            val id = world.blockAt(cx + dx, cy + dy, cz + dz)
            val b = Reg.blocks[id] ?: continue
            if (id != 0 && b.hardness in 0f..49f) world.setBlock(cx + dx, cy + dy, cz + dz, 0)
        }
        val d = hypot(player.x - m.x, player.z - m.z)
        if (d < 6f) hurt(14f * (1f - d / 6f))
        m.hp = -100f
    }

    private fun trySpawn(night: Boolean) {
        val hc = mobs.count { it.type.hostile }
        val pc = mobs.size - hc
        val hostile = night && hc < 8 && rnd.nextFloat() < 0.7f
        if (!hostile && pc >= 12) return
        val ang = rnd.nextFloat() * 6.2831855f
        val r = 16f + rnd.nextFloat() * 14f
        val x = floor(player.x + cos(ang) * r).toInt()
        val z = floor(player.z + sin(ang) * r).toInt()
        val sy = world.surfaceY(x, z)
        if (sy <= SEA) return
        if (!hostile && world.blockAt(x, sy, z) != GRASS) return
        val list = if (hostile) Mobs.hostile else Mobs.passive
        val t = list[rnd.nextInt(list.size)]
        val m = Mob(t, x + 0.5f, sy + 1f, z + 0.5f)
        if (world.collides(m.x - t.hw, m.y, m.z - t.hw, m.x + t.hw, m.y + t.h, m.z + t.hw)) return
        m.yaw = rnd.nextFloat() * 6.2831855f
        mobs.add(m)
    }

    private fun updateMobs(dt: Float) {
        val night = isNight()
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = 2f; trySpawn(night)
        }
        val it = mobs.iterator()
        while (it.hasNext()) {
            val m = it.next()
            val dx = player.x - m.x
            val dz = player.z - m.z
            val dist = hypot(dx, dz)
            if (dist > 56f || m.y < -10f) {
                it.remove(); continue
            }
            if (m.hp <= 0f) {
                die(m); it.remove(); continue
            }
            if (m.type.hostile && !night && world.surfaceY(floor(m.x).toInt(), floor(m.z).toInt()) <= m.y &&
                rnd.nextFloat() < dt * 0.15f
            ) {
                it.remove(); continue
            }
            m.hurt = max(0f, m.hurt - dt)
            m.attackCd -= dt
            var tx = 0f
            var tz = 0f
            m.moving = false
            var chase = false
            if (m.type.hostile && dist < 24f && overlay != Overlay.DEAD) {
                chase = true
                m.yaw = atan2(dx, dz)
                if (m.type.key == "creeper" && dist < 3f) {
                    m.fuse = if (m.fuse < 0f) 0f else m.fuse + dt
                    if (m.fuse > 1.5f) {
                        explode(m); continue
                    }
                } else {
                    m.fuse = -1f
                    tx = sin(m.yaw); tz = cos(m.yaw); m.moving = true
                }
            } else {
                m.aiTimer -= dt
                if (m.aiTimer <= 0f) {
                    m.aiTimer = 1f + m.rnd.nextFloat() * 3f
                    m.wander = m.rnd.nextFloat() > 0.45f
                    m.yaw = m.rnd.nextFloat() * 6.2831855f
                }
                if (m.wander) {
                    tx = sin(m.yaw); tz = cos(m.yaw); m.moving = true
                }
            }
            val sp = m.type.speed * (if (chase) 1.35f else 1f) * 2.2f
            val k = min(1f, dt * 6f)
            m.vx += (tx * sp - m.vx) * k
            m.vz += (tz * sp - m.vz) * k
            val inWater = Reg.blocks[world.blockAt(floor(m.x).toInt(), floor(m.y + 0.3f).toInt(), floor(m.z).toInt())]?.liquid == true
            if (inWater) m.vy = min(2f, m.vy + 20f * dt) else m.vy = max(-50f, m.vy - 32f * dt)
            if (m.hitWall && m.onGround && m.moving) m.vy = 8f
            m.move(world, dt)
            if (m.moving) m.walkT += sp * dt * 2.4f
            if (m.type.hostile && m.type.dmg > 0f && m.attackCd <= 0f && dist < 1.1f + m.hw && abs(player.y - m.y) < 1.6f) {
                hurt(m.type.dmg); m.attackCd = 1f
                player.vx += dx / max(0.1f, dist) * 3f; player.vz += dz / max(0.1f, dist) * 3f
            }
        }
    }

    // ---------------- инвентарь и крафт ----------------
    fun select(i: Int) {
        inv.selected = i.coerceIn(0, 8)
        hudSel = inv.selected
    }

    fun updateCraft() {
        val n = gridW * gridW
        craftOut = Recipes.find(craftGrid.toList().subList(0, n), gridW, gridW)
    }

    private fun takeCraft() {
        val n = gridW * gridW
        for (i in 0 until n) {
            val s = craftGrid[i] ?: continue
            s.count--
            if (s.count <= 0) craftGrid[i] = null
        }
        updateCraft()
    }

    fun clearCraft() {
        for (i in craftGrid.indices) craftGrid[i]?.let { inv.add(it) }
        craftGrid.fill(null)
        craftOut = null
    }

    fun click(r: SlotRef, right: Boolean) {
        synchronized(lock) {
            val s = r.arr[r.i]
            val c = cursor
            when (r.kind) {
                1, 2 -> {
                    if (s != null && (c == null || (c.sameKind(s) && c.count + s.count <= c.max))) {
                        if (c == null) cursor = s.copy() else c.count += s.count
                        if (r.kind == 1) takeCraft() else r.arr[r.i] = null
                    }
                }
                else -> {
                    if (!right) {
                        if (c == null) {
                            if (s != null) {
                                cursor = s; r.arr[r.i] = null
                            }
                        } else if (!r.accepts(c)) {
                        } else if (s == null) {
                            r.arr[r.i] = c; cursor = null
                        } else if (s.sameKind(c)) {
                            val m = min(c.count, s.max - s.count)
                            s.count += m; c.count -= m
                            if (c.count == 0) cursor = null
                        } else {
                            r.arr[r.i] = c; cursor = s
                        }
                    } else {
                        if (c == null) {
                            if (s != null) {
                                val h = (s.count + 1) / 2
                                cursor = ItemStack(s.id, h, s.dmg)
                                s.count -= h
                                if (s.count == 0) r.arr[r.i] = null
                            }
                        } else if (r.accepts(c)) {
                            if (s == null) {
                                r.arr[r.i] = ItemStack(c.id, 1, c.dmg)
                                c.count--
                                if (c.count == 0) cursor = null
                            } else if (s.sameKind(c) && s.count < s.max) {
                                s.count++; c.count--
                                if (c.count == 0) cursor = null
                            }
                        }
                    }
                }
            }
            if (r.arr === craftGrid) updateCraft()
            refreshHud()
        }
    }

    fun giveCreative(id: Int) {
        synchronized(lock) {
            val d = Reg.items[id]
            inv.add(ItemStack(id, if (d.maxStack > 1) 64 else 1))
            refreshHud()
        }
    }

    fun updateCursor(st: ItemStack?) {
        synchronized(lock) {
            cursor = st
            invTick++
        }
    }

    fun openInventory() {
        synchronized(lock) {
            gridW = 2; clearCraft(); overlay = Overlay.INVENTORY
            input.reset()
            invTick++
        }
    }

    fun closeUi() {
        synchronized(lock) {
            clearCraft()
            cursor?.let { inv.add(it) }
            cursor = null
            openFurnace = null; openChest = null
            overlay = Overlay.NONE
            refreshHud()
        }
    }

    fun pause() {
        synchronized(lock) {
            if (overlay == Overlay.NONE) {
                overlay = Overlay.PAUSE
                input.reset()
            }
        }
    }

    // ---------------- сохранение ----------------
    private fun ws(o: DataOutputStream, s: ItemStack?) {
        if (s == null) o.writeUTF("") else {
            o.writeUTF(s.def.key); o.writeInt(s.count); o.writeInt(s.dmg)
        }
    }

    private fun readStack(i: DataInputStream): ItemStack? {
        val key = i.readUTF()
        if (key.isEmpty()) return null
        val d = Reg.def(key)
        val c = i.readInt()
        val dm = i.readInt()
        return if (d == null) null else ItemStack(d.id, c, dm)
    }

    fun save() {
        synchronized(lock) {
            try {
                dir.mkdirs()
                DataOutputStream(File(dir, "player.dat").outputStream().buffered()).use { o ->
                    o.writeInt(2)
                    for (v in floatArrayOf(player.x, player.y, player.z, player.yaw, player.pitch, player.health, player.hunger, dayTime)) o.writeFloat(v)
                    for (s in inv.slots) ws(o, s)
                    for (s in inv.armor) ws(o, s)
                    o.writeInt(furnaces.size)
                    for ((k, f) in furnaces) {
                        o.writeLong(k); o.writeFloat(f.burn); o.writeFloat(f.burnTotal); o.writeFloat(f.progress)
                        for (s in f.slots) ws(o, s)
                    }
                    o.writeInt(chests.size)
                    for ((k, a) in chests) {
                        o.writeLong(k)
                        for (s in a) ws(o, s)
                    }
                }
                world.saveAll()
                meta.last = System.currentTimeMillis()
                Worlds.writeMeta(ctx, meta)
            } catch (_: Exception) {
            }
        }
    }

    private fun load() {
        val f = File(dir, "player.dat")
        if (!f.exists()) return
        try {
            DataInputStream(f.inputStream().buffered()).use { i ->
                i.readInt()
                player.x = i.readFloat(); player.y = i.readFloat(); player.z = i.readFloat()
                player.yaw = i.readFloat(); player.pitch = i.readFloat()
                player.health = i.readFloat(); player.hunger = i.readFloat(); dayTime = i.readFloat()
                player.peakY = player.y
                for (k in 0 until 36) inv.slots[k] = readStack(i)
                for (k in 0 until 4) inv.armor[k] = readStack(i)
                repeat(i.readInt()) {
                    val k = i.readLong()
                    val fu = Furnace()
                    fu.burn = i.readFloat(); fu.burnTotal = i.readFloat(); fu.progress = i.readFloat()
                    for (j in 0 until 3) fu.slots[j] = readStack(i)
                    furnaces[k] = fu
                }
                repeat(i.readInt()) {
                    val k = i.readLong()
                    val a = arrayOfNulls<ItemStack>(27)
                    for (j in 0 until 27) a[j] = readStack(i)
                    chests[k] = a
                }
            }
        } catch (_: Exception) {
        }
    }
}
