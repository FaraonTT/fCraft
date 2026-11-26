package com.fcraft.core

object Recipes {
    class R(val w: Int, val h: Int, val ing: List<String?>, val out: String, val n: Int, val shapeless: Boolean)

    val list = ArrayList<R>()
    val smelt = HashMap<String, String>()

    /** Узор: строки-ряды и записи вида "X=ключ" (тег — с префиксом #). */
    private fun shaped(out: String, n: Int, vararg a: String) {
        if (Reg.def(out) == null) return
        val rows = ArrayList<String>()
        val map = HashMap<Char, String>()
        for (s in a) {
            if (s.length > 2 && s[1] == '=') map[s[0]] = s.substring(2) else rows.add(s)
        }
        val w = rows.maxOf { it.length }
        val ing = ArrayList<String?>()
        for (r in rows) for (x in 0 until w) {
            val c = if (x < r.length) r[x] else ' '
            ing.add(if (c == ' ') null else map[c])
        }
        list.add(R(w, rows.size, ing, out, n, false))
    }

    private fun shapeless(out: String, n: Int, vararg ing: String) {
        if (Reg.def(out) == null) return
        list.add(R(ing.size, 1, ing.toList(), out, n, true))
    }

    init {
        // --- дерево ---
        for (w in Reg.WOODS) {
            shapeless("${w.k}_planks", 4, "#${w.k}_logs")
            shaped("${w.k}_slab", 6, "MMM", "M=${w.k}_planks")
        }
        shaped("stick", 4, "P", "P", "P=#planks")
        shaped("crafting_table", 1, "PP", "PP", "P=#planks")
        shaped("chest", 1, "PPP", "P P", "PPP", "P=#planks")
        shaped("furnace", 1, "CCC", "C C", "CCC", "C=#stone_crafting")
        shaped("bowl", 4, "P P", " P ", "P=#planks")
        shaped("torch", 4, "C", "S", "C=#coals", "S=stick")
        shaped("bookshelf", 1, "PPP", "BBB", "PPP", "P=#planks", "B=book")
        shaped("paper", 3, "CCC", "C=sugar_cane")
        shapeless("sugar", 1, "sugar_cane")
        shapeless("book", 1, "paper", "paper", "paper", "leather")
        shaped("white_wool", 1, "SS", "SS", "S=string")
        shaped("shears", 1, " I", "I ", "I=iron_ingot")

        // --- инструменты ---
        val mats = listOf(
            "wooden" to "#planks", "stone" to "#stone_crafting", "iron" to "iron_ingot",
            "golden" to "gold_ingot", "diamond" to "diamond"
        )
        for ((n, m) in mats) {
            shaped("${n}_pickaxe", 1, "MMM", " S ", " S ", "M=$m", "S=stick")
            shaped("${n}_axe", 1, "MM", "MS", " S", "M=$m", "S=stick")
            shaped("${n}_shovel", 1, "M", "S", "S", "M=$m", "S=stick")
            shaped("${n}_hoe", 1, "MM", " S", " S", "M=$m", "S=stick")
            shaped("${n}_sword", 1, "M", "M", "S", "M=$m", "S=stick")
        }
        for (k in Reg.TOOL_KINDS) shapeless("netherite_$k", 1, "diamond_$k", "netherite_ingot")

        // --- броня ---
        val armorMats = listOf("leather" to "leather", "iron" to "iron_ingot", "golden" to "gold_ingot", "diamond" to "diamond")
        for ((n, m) in armorMats) {
            shaped("${n}_helmet", 1, "MMM", "M M", "M=$m")
            shaped("${n}_chestplate", 1, "M M", "MMM", "MMM", "M=$m")
            shaped("${n}_leggings", 1, "MMM", "M M", "M M", "M=$m")
            shaped("${n}_boots", 1, "M M", "M M", "M=$m")
        }
        for (p in listOf("helmet", "chestplate", "leggings", "boots")) shapeless("netherite_$p", 1, "diamond_$p", "netherite_ingot")

        // --- блоки ↔ слитки ---
        val comp = listOf(
            "iron_block" to "iron_ingot", "gold_block" to "gold_ingot", "copper_block" to "copper_ingot",
            "diamond_block" to "diamond", "emerald_block" to "emerald", "lapis_block" to "lapis_lazuli",
            "redstone_block" to "redstone", "coal_block" to "coal", "netherite_block" to "netherite_ingot",
            "raw_iron_block" to "raw_iron", "raw_copper_block" to "raw_copper", "raw_gold_block" to "raw_gold",
            "hay_block" to "wheat", "melon" to "melon_slice", "amethyst_block" to "amethyst_shard"
        )
        for ((b, m) in comp) {
            shaped(b, 1, "MMM", "MMM", "MMM", "M=$m")
            if (b != "melon") shapeless(m, 9, b)
        }
        shapeless("melon_slice", 9, "melon")
        shapeless("iron_nugget", 9, "iron_ingot"); shapeless("gold_nugget", 9, "gold_ingot")
        shaped("iron_ingot", 1, "NNN", "NNN", "NNN", "N=iron_nugget")
        shaped("gold_ingot", 1, "NNN", "NNN", "NNN", "N=gold_nugget")
        shapeless("netherite_ingot", 1, "netherite_scrap", "netherite_scrap", "netherite_scrap", "netherite_scrap", "gold_ingot", "gold_ingot", "gold_ingot", "gold_ingot")

        // --- строительные блоки ---
        shaped("stone_bricks", 4, "SS", "SS", "S=stone")
        shaped("polished_andesite", 4, "SS", "SS", "S=andesite")
        shaped("polished_diorite", 4, "SS", "SS", "S=diorite")
        shaped("polished_granite", 4, "SS", "SS", "S=granite")
        shaped("polished_deepslate", 4, "SS", "SS", "S=cobbled_deepslate")
        shaped("deepslate_bricks", 4, "SS", "SS", "S=polished_deepslate")
        shaped("polished_blackstone", 4, "SS", "SS", "S=blackstone")
        shaped("polished_basalt", 2, "SS", "SS", "S=basalt")
        shaped("end_stone_bricks", 4, "SS", "SS", "S=end_stone")
        shaped("prismarine_bricks", 1, "SS", "SS", "S=prismarine")
        shaped("sandstone", 1, "SS", "SS", "S=sand")
        shaped("red_sandstone", 1, "SS", "SS", "S=red_sand")
        shaped("cut_sandstone", 4, "SS", "SS", "S=sandstone")
        shaped("cut_red_sandstone", 4, "SS", "SS", "S=red_sandstone")
        shaped("bricks", 1, "SS", "SS", "S=brick")
        shaped("nether_bricks", 1, "SS", "SS", "S=nether_brick")
        shaped("quartz_block", 1, "SS", "SS", "S=quartz")
        shaped("quartz_pillar", 2, "Q", "Q", "Q=quartz_block")
        shaped("glowstone", 1, "SS", "SS", "S=glowstone_dust")
        shaped("clay", 1, "SS", "SS", "S=clay_ball")
        shaped("mud_bricks", 4, "SS", "SS", "S=packed_mud")
        shapeless("andesite", 2, "diorite", "cobblestone")
        shapeless("diorite", 2, "cobblestone", "quartz")
        shapeless("granite", 1, "diorite", "quartz")
        shapeless("mossy_cobblestone", 1, "cobblestone", "short_grass")
        shapeless("mossy_stone_bricks", 1, "stone_bricks", "short_grass")
        shaped("chiseled_stone_bricks", 1, "S", "S", "S=stone_bricks")
        shaped("jack_o_lantern_placeholder", 1, "X", "X=none")
        shaped("sea_lantern", 1, "PQP", "QQQ", "PQP", "P=prismarine", "Q=quartz")
        shapeless("packed_mud", 1, "mud", "wheat")
        shaped("snow_block", 1, "SS", "SS", "S=ice")
        shapeless("blue_ice", 1, "packed_ice", "packed_ice")
        shaped("packed_ice", 1, "II", "II", "I=ice")

        // --- еда ---
        shaped("bread", 1, "WWW", "W=wheat")
        shaped("golden_apple", 1, "GGG", "GAG", "GGG", "G=gold_ingot", "A=apple")
        shaped("golden_carrot", 1, "GGG", "GCG", "GGG", "G=gold_nugget", "C=carrot")
        shapeless("mushroom_stew", 1, "brown_mushroom", "red_mushroom", "bowl")
        shaped("cookie", 8, "WDW", "W=wheat", "D=cocoa_placeholder")

        // --- красители ---
        val dyeFrom = listOf(
            "dandelion" to "yellow", "poppy" to "red", "red_tulip" to "red", "orange_tulip" to "orange",
            "white_tulip" to "light_gray", "pink_tulip" to "pink", "azure_bluet" to "light_gray",
            "oxeye_daisy" to "light_gray", "cornflower" to "blue", "lily_of_the_valley" to "white",
            "allium" to "magenta", "blue_orchid" to "light_blue", "bone_meal" to "white",
            "lapis_lazuli" to "blue", "ink_sac" to "black", "beetroot" to "red"
        )
        for ((src, c) in dyeFrom) shapeless("${c}_dye", 1, src)
        shapeless("bone_meal", 3, "bone")
        val mix = listOf(
            Triple("orange", "red", "yellow"), Triple("lime", "green", "white"), Triple("pink", "red", "white"),
            Triple("gray", "black", "white"), Triple("light_gray", "gray", "white"), Triple("light_blue", "blue", "white"),
            Triple("cyan", "blue", "green"), Triple("purple", "blue", "red"), Triple("magenta", "purple", "pink")
        )
        for ((o, a, b) in mix) shapeless("${o}_dye", 2, "${a}_dye", "${b}_dye")
        for ((c, _) in Reg.COLORS) {
            if (c != "white") shapeless("${c}_wool", 1, "white_wool", "${c}_dye")
            shaped("${c}_terracotta", 8, "TTT", "TDT", "TTT", "T=terracotta", "D=${c}_dye")
            shaped("${c}_stained_glass", 8, "GGG", "GDG", "GGG", "G=glass", "D=${c}_dye")
            shapeless("${c}_concrete_powder", 8, "#sands", "#sands", "#sands", "#sands", "gravel", "gravel", "gravel", "gravel", "${c}_dye")
            // Воды в игре нет, поэтому бетон делается прямо из порошка
            shapeless("${c}_concrete", 1, "${c}_concrete_powder", "${c}_concrete_powder")
        }

        // ================= ПЛАВКА =================
        val s = smelt
        s["sand"] = "glass"; s["red_sand"] = "glass"; s["cobblestone"] = "stone"; s["cobbled_deepslate"] = "deepslate"
        s["stone"] = "smooth_stone"; s["sandstone"] = "smooth_sandstone"; s["red_sandstone"] = "smooth_red_sandstone"
        s["quartz_block"] = "smooth_quartz"; s["clay_ball"] = "brick"; s["clay"] = "terracotta"
        s["netherrack"] = "nether_brick"; s["stone_bricks"] = "cracked_stone_bricks"; s["cactus"] = "green_dye"
        s["basalt"] = "smooth_basalt"
        for (w in Reg.WOODS) if (!w.nether) s["${w.k}_log"] = "charcoal"
        s["raw_iron"] = "iron_ingot"; s["raw_gold"] = "gold_ingot"; s["raw_copper"] = "copper_ingot"
        for ((k, out) in listOf(
            "iron" to "iron_ingot", "gold" to "gold_ingot", "copper" to "copper_ingot", "coal" to "coal",
            "redstone" to "redstone", "lapis" to "lapis_lazuli", "diamond" to "diamond", "emerald" to "emerald"
        )) {
            s["${k}_ore"] = out; s["deepslate_${k}_ore"] = out
        }
        s["nether_quartz_ore"] = "quartz"; s["nether_gold_ore"] = "gold_ingot"; s["ancient_debris"] = "netherite_scrap"
        s["porkchop"] = "cooked_porkchop"; s["beef"] = "cooked_beef"; s["chicken"] = "cooked_chicken"
        s["mutton"] = "cooked_mutton"; s["potato"] = "baked_potato"; s["cod"] = "cooked_cod"; s["salmon"] = "cooked_salmon"
    }

    fun ok(want: String?, have: ItemStack?): Boolean {
        if (want == null) return have == null
        if (have == null) return false
        return if (want[0] == '#') Reg.tags[want.substring(1)]?.contains(have.def.key) == true
        else have.def.key == want
    }

    /** Подбирает рецепт для сетки gw x gh. */
    fun find(grid: List<ItemStack?>, gw: Int, gh: Int): ItemStack? {
        var minX = gw
        var maxX = -1
        var minY = gh
        var maxY = -1
        var cnt = 0
        for (y in 0 until gh) for (x in 0 until gw) {
            if (grid[y * gw + x] != null) {
                cnt++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (cnt == 0) return null
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        for (r in list) {
            val ok: Boolean
            if (r.shapeless) {
                if (r.ing.size != cnt) continue
                val used = BooleanArray(gw * gh)
                var all = true
                for (want in r.ing) {
                    var found = false
                    for (i in 0 until gw * gh) {
                        if (!used[i] && grid[i] != null && ok(want, grid[i])) {
                            used[i] = true; found = true; break
                        }
                    }
                    if (!found) {
                        all = false; break
                    }
                }
                ok = all
            } else {
                ok = r.w == bw && r.h == bh && (test(r, grid, gw, minX, minY, false) || test(r, grid, gw, minX, minY, true))
            }
            if (ok) {
                val d = Reg.def(r.out) ?: continue
                return ItemStack(d.id, r.n)
            }
        }
        return null
    }

    private fun test(r: R, grid: List<ItemStack?>, gw: Int, ox: Int, oy: Int, mirror: Boolean): Boolean {
        for (y in 0 until r.h) for (x in 0 until r.w) {
            val rx = if (mirror) r.w - 1 - x else x
            if (!ok(r.ing[y * r.w + rx], grid[(oy + y) * gw + ox + x])) return false
        }
        return true
    }
}
