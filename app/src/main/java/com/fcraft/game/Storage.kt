package com.fcraft.game

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

object Settings {
    private var prefs: SharedPreferences? = null
    var renderDist by mutableIntStateOf(6)
    var fov by mutableFloatStateOf(72f)
    var sens by mutableFloatStateOf(1f)
    var showFps by mutableStateOf(true)
    var dynamicColor by mutableStateOf(false)
    var uiScale by mutableFloatStateOf(1f)

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("fcraft", Context.MODE_PRIVATE)
        prefs = p
        renderDist = p.getInt("rd", 6)
        fov = p.getFloat("fov", 72f)
        sens = p.getFloat("sens", 1f)
        showFps = p.getBoolean("fps", true)
        dynamicColor = p.getBoolean("dyn", false)
        uiScale = p.getFloat("ui", 1f)
    }

    fun save() {
        prefs?.edit()
            ?.putInt("rd", renderDist)?.putFloat("fov", fov)?.putFloat("sens", sens)
            ?.putBoolean("fps", showFps)?.putBoolean("dyn", dynamicColor)?.putFloat("ui", uiScale)
            ?.apply()
    }
}

class WorldMeta(val id: String, var name: String, val seed: Long, val creative: Boolean, var last: Long) {
    fun dir(ctx: Context) = File(Worlds.root(ctx), id)
}

object Worlds {
    fun root(ctx: Context): File = File(ctx.filesDir, "worlds").apply { mkdirs() }

    fun list(ctx: Context): List<WorldMeta> {
        val res = ArrayList<WorldMeta>()
        root(ctx).listFiles()?.forEach { d ->
            val f = File(d, "meta.txt")
            if (f.exists()) try {
                val l = f.readLines()
                res.add(WorldMeta(d.name, l[0], l[1].toLong(), l[2] == "1", l[3].toLong()))
            } catch (_: Exception) {
            }
        }
        return res.sortedByDescending { it.last }
    }

    fun writeMeta(ctx: Context, m: WorldMeta) {
        val d = m.dir(ctx)
        d.mkdirs()
        File(d, "meta.txt").writeText("${m.name.replace('\n', ' ')}\n${m.seed}\n${if (m.creative) "1" else "0"}\n${m.last}\n")
    }

    fun create(ctx: Context, name: String, seed: Long, creative: Boolean): WorldMeta {
        val m = WorldMeta("w" + System.currentTimeMillis(), name.ifBlank { "Новый мир" }, seed, creative, System.currentTimeMillis())
        writeMeta(ctx, m)
        return m
    }

    fun delete(ctx: Context, m: WorldMeta) {
        m.dir(ctx).deleteRecursively()
    }
}
