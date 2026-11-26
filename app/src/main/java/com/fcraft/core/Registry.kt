package com.fcraft.core

enum class ToolType { NONE, PICKAXE, AXE, SHOVEL, HOE, SWORD, SHEARS }

const val R_CUBE = 0
const val R_CUTOUT = 1
const val R_TRANS = 2
const val R_LIQUID = 3
const val R_CROSS = 4
const val R_SLAB = 5

class BlockProps {
    var top = 0
    var bottom = 0
    var side = 0
    var hardness = 1f
    var tool = ToolType.NONE
    var needsTool = false
    var tier = 0
    var render = R_CUBE
    var light = 0
    var drop: String? = null
    var dropMin = 1
    var dropMax = 1
    /** 0 нет, 1 биом только сверху, 2 биом весь блок, 3 фиксированный цвет */
    var tint = 0
    var tintColor = 0xFFFFFF
    /** 1 верстак, 2 печь, 3 сундук */
    var interact = 0
    var solid = true
    var liquid = false
}

class ItemDef(val id: Int, val key: String, val name: String, val icon: Int) {
    var maxStack = 64
    var block: BlockProps? = null
    var tool = ToolType.NONE
    var tier = 0
    var speed = 1f
    var attack = 1f
    var durability = 0
    var food = 0
    var fuel = 0f
    var armorSlot = -1
    var armorPts = 0
    var hidden = false
}

object Reg {
    val items = ArrayList<ItemDef>()
    val byKey = HashMap<String, ItemDef>()
    val blocks = arrayOfNulls<BlockProps>(4096)
    val tags = HashMap<String, MutableSet<String>>()

    val COLORS = listOf(
        "white" to 0xF0F0F0, "orange" to 0xF9801D, "magenta" to 0xC74EBD, "light_blue" to 0x3AB3DA,
        "yellow" to 0xFED83D, "lime" to 0x80C71F, "pink" to 0xF38BAA, "gray" to 0x474F52,
        "light_gray" to 0x9D9D97, "cyan" to 0x169C9C, "purple" to 0x8932B8, "blue" to 0x3C44AA,
        "brown" to 0x835432, "green" to 0x5E7C16, "red" to 0xB02E26, "black" to 0x1D1D21
    )

    class W(val k: String, val plank: Int, val bark: Int, val leaf: Int, val nether: Boolean = false)

    val WOODS = listOf(
        W("oak", 0xB8945F, 0x6B5230, 0x59AE30),
        W("spruce", 0x7A5A34, 0x3E2B16, 0x3F6B3F),
        W("birch", 0xD7C98B, 0xD8D6CC, 0x80A755),
        W("jungle", 0xB88764, 0x5A4620, 0x30BB0B),
        W("acacia", 0xBA6337, 0x6D6458, 0x70A63A),
        W("dark_oak", 0x4E3A1E, 0x2C1E0F, 0x4A7A2A),
        W("mangrove", 0x8A3F3B, 0x5A3A2E, 0x8DB127),
        W("cherry", 0xE6B3B6, 0x3A2A2E, 0xF2A9C3),
        W("crimson", 0x6B344B, 0x5A2A3E, 0, true),
        W("warped", 0x2B6B64, 0x2A4F5A, 0, true)
    )

    val TOOL_KINDS = listOf("sword", "pickaxe", "axe", "shovel", "hoe")

    private var inited = false

    fun touch() {}

    private fun titleOf(k: String) = k.split('_').joinToString(" ") { s -> s.replaceFirstChar { c -> c.uppercase() } }
    fun id(k: String): Int = byKey[k]?.id ?: 0
    fun def(k: String): ItemDef? = byKey[k]
    private fun tag(t: String, vararg ks: String) {
        tags.getOrPut(t) { HashSet() }.addAll(ks)
    }

    private fun BlockProps.pick(h: Float, t: Int = 0) {
        hardness = h; tool = ToolType.PICKAXE; needsTool = true; tier = t
    }

    private fun BlockProps.axe(h: Float) {
        hardness = h; tool = ToolType.AXE
    }

    private fun BlockProps.shovel(h: Float) {
        hardness = h; tool = ToolType.SHOVEL
    }

    private fun BlockProps.hand(h: Float) {
        hardness = h
    }

    private fun block(
        key: String, top: Int, side: Int = top, bottom: Int = top,
        cfg: BlockProps.() -> Unit = {}
    ): Int {
        val id = items.size
        val d = ItemDef(id, key, titleOf(key), side)
        val b = BlockProps()
        b.top = top; b.side = side; b.bottom = bottom
        b.cfg()
        d.block = b
        blocks[id] = b
        items.add(d); byKey[key] = d
        return id
    }

    private fun item(key: String, icon: Int, cfg: ItemDef.() -> Unit = {}): Int {
        val id = items.size
        val d = ItemDef(id, key, titleOf(key), icon)
        d.cfg()
        items.add(d); byKey[key] = d
        return id
    }

    private fun n(name: String, c: Int, a: Int = 8) = Tex.reg(name, G.noisy(c, a))

    private fun stoneLike(key: String, c: Int, h: Float = 1.5f, tier: Int = 0, cfg: BlockProps.() -> Unit = {}) {
        block(key, n(key, c)) { pick(h, tier); cfg() }
    }

    init {
        val air = ItemDef(0, "air", "Air", 0)
        air.hidden = true
        items.add(air); byKey["air"] = air

        // ---------------- природные блоки ----------------
        val stoneT = n("stone", 0x7D7D7D, 7)
        block("stone", stoneT) { pick(1.5f); drop = "cobblestone" }
        val dirtT = Tex.reg("dirt", G.dirt())
        block("dirt", dirtT) { shovel(0.5f) }
        block("grass_block", n("grass_top", 0xB0B0B0, 14), Tex.reg("grass_side", G.grassSide()), dirtT) {
            shovel(0.6f); drop = "dirt"; tint = 1
        }
        block("coarse_dirt", n("coarse_dirt", 0x77553B, 14)) { shovel(0.5f) }
        block("podzol", n("podzol_top", 0x5B4220, 10), Tex.reg("podzol_side", G.grassSide(0x866043, 0x5B4220)), dirtT) {
            shovel(0.5f); drop = "dirt"
        }
        block("sand", n("sand", 0xDBD3A0)) { shovel(0.5f) }
        block("red_sand", n("red_sand", 0xBE6621)) { shovel(0.5f) }
        block("gravel", n("gravel", 0x857F7D, 16)) { shovel(0.6f) }
        block("clay", n("clay", 0xA4A8B8, 5)) { shovel(0.6f); drop = "clay_ball"; dropMin = 4; dropMax = 4 }
        block("mud", n("mud", 0x3C3837, 6)) { shovel(0.5f) }
        block("snow_block", n("snow", 0xF5FBFB, 3)) { shovel(0.2f) }
        block("ice", Tex.reg("ice", G.glass(0x9EC8FF, 0xA0))) { hand(0.5f); render = R_TRANS; drop = "" }
        block("packed_ice", n("packed_ice", 0x8DB8F0, 9)) { hand(0.5f); drop = "" }
        block("blue_ice", n("blue_ice", 0x6EA0F2, 9)) { hand(2.8f); drop = "" }
        block("water", Tex.reg("water", G.liquid(0x2E5FE0, 0xA0))) {
            render = R_LIQUID; solid = false; liquid = true; hardness = -1f; drop = ""
        }
        block("lava", Tex.reg("lava", G.lava())) {
            render = R_LIQUID; solid = false; liquid = true; hardness = -1f; drop = ""; light = 15
        }
        block("bedrock", n("bedrock", 0x4A4A4A, 18)) { hardness = -1f; drop = "" }
        items[id("water")].hidden = true
        items[id("lava")].hidden = true

        // ---------------- дерево ----------------
        for (w in WOODS) {
            val pl = Tex.reg("${w.k}_planks", G.planks(w.plank))
            val logK = if (w.nether) "${w.k}_stem" else "${w.k}_log"
            val side = Tex.reg(logK, G.logSide(w.bark))
            val top = Tex.reg("${logK}_top", G.logTop(w.plank, w.bark))
            block("${w.k}_planks", pl) { axe(2f) }
            block(logK, top, side) { axe(2f) }
            val stS = Tex.reg("stripped_$logK", G.logSide(w.plank))
            val stT = Tex.reg("stripped_${logK}_top", G.logTop(w.plank, w.plank))
            block("stripped_$logK", stT, stS) { axe(2f) }
            block("${w.k}_slab", pl) { axe(2f); render = R_SLAB }
            tag("${w.k}_logs", logK, "stripped_$logK")
            tag("logs", logK, "stripped_$logK")
            tag("planks", "${w.k}_planks")
            if (!w.nether) {
                val lt = Tex.reg("${w.k}_leaves", G.leaves(0xB4B4B4))
                block("${w.k}_leaves", lt) {
                    hand(0.2f); render = R_CUTOUT; tint = 3; tintColor = w.leaf
                    drop = "stick"; dropMin = 0; dropMax = 2
                }
            }
        }

        // ---------------- камень и производные ----------------
        block("deepslate", n("deepslate", 0x4F4F55, 6)) { pick(3f); drop = "cobbled_deepslate" }
        val stones = listOf(
            "cobblestone" to 0x6F6F6F, "mossy_cobblestone" to 0x62786A, "andesite" to 0x8A8A8E,
            "diorite" to 0xBDBDBD, "granite" to 0x9A6B58, "polished_andesite" to 0x8E8E92,
            "polished_diorite" to 0xC4C4C6, "polished_granite" to 0xA06E5B, "smooth_stone" to 0x9E9E9E,
            "tuff" to 0x6B6D62, "calcite" to 0xE0E1DC, "dripstone_block" to 0x866B5A,
            "cobbled_deepslate" to 0x4D4D51, "polished_deepslate" to 0x484849,
            "netherrack" to 0x6F3535, "basalt" to 0x4C4C52, "smooth_basalt" to 0x45454A,
            "polished_basalt" to 0x55555C, "blackstone" to 0x2A252D, "polished_blackstone" to 0x32303A,
            "gilded_blackstone" to 0x3A3027, "end_stone" to 0xDBDE9E, "prismarine" to 0x63A094,
            "dark_prismarine" to 0x345C4B, "purpur_block" to 0xA97BA9, "quartz_block" to 0xEAE5DE,
            "smooth_quartz" to 0xEFEAE3, "quartz_pillar" to 0xE6E1D8, "bone_block" to 0xE5E1CF,
            "terracotta" to 0x985E43, "soul_sand" to 0x51402F, "soul_soil" to 0x4B3A2B,
            "packed_mud" to 0x8E6B52, "magma_block" to 0xA8401A
        )
        for ((k, c) in stones) {
            val h = when (k) {
                "cobblestone" -> 2f
                "netherrack" -> 0.4f
                "quartz_block", "smooth_quartz", "quartz_pillar", "bone_block" -> 0.8f
                "end_stone" -> 3f
                "soul_sand", "soul_soil" -> 0.5f
                "magma_block" -> 0.5f
                "tuff" -> 1.5f
                else -> 1.5f
            }
            if (k == "soul_sand" || k == "soul_soil") block(k, n(k, c, 6)) { shovel(h) }
            else block(k, n(k, c, 7)) { pick(h); if (k == "magma_block") light = 3 }
        }
        val brickKinds = listOf(
            "stone_bricks" to 0x7A7A7A, "mossy_stone_bricks" to 0x6C7F6A, "cracked_stone_bricks" to 0x707070,
            "chiseled_stone_bricks" to 0x7E7E7E, "deepslate_bricks" to 0x474749, "deepslate_tiles" to 0x36363A,
            "end_stone_bricks" to 0xDDE0A5, "prismarine_bricks" to 0x63AB9A, "quartz_bricks" to 0xE9E4DB,
            "polished_blackstone_bricks" to 0x2B2630, "mud_bricks" to 0x8B6B50
        )
        for ((k, c) in brickKinds) block(k, Tex.reg(k, G.stoneBricks(c))) { pick(1.5f) }
        block("bricks", Tex.reg("bricks", G.bricks(0x96594B, 0xB0A59A))) { pick(2f) }
        block("nether_bricks", Tex.reg("nether_bricks", G.bricks(0x2E161B, 0x1B0C10))) { pick(2f) }
        block("red_nether_bricks", Tex.reg("red_nether_bricks", G.bricks(0x4A0F12, 0x2B0709))) { pick(2f) }

        for ((k, c) in listOf("sandstone" to 0xDBCFA3, "red_sandstone" to 0xB5601F)) {
            val plain = Tex.reg("${k}_top", G.sandstone(c, 0))
            val side = Tex.reg("${k}_side", G.sandstone(c, 1))
            block(k, plain, side, plain) { pick(0.8f) }
            block("cut_$k", plain, Tex.reg("cut_${k}_side", G.sandstone(c, 1)), plain) { pick(0.8f) }
            block("smooth_$k", plain) { pick(2f) }
            block("chiseled_$k", plain, Tex.reg("chiseled_${k}_side", G.sandstone(c, 1)), plain) { pick(0.8f) }
        }

        block("obsidian", n("obsidian", 0x14101E, 6)) { pick(50f, 3) }
        block("crying_obsidian", n("crying_obsidian", 0x2C0F50, 12)) { pick(50f, 3); light = 10 }
        block("glowstone", n("glowstone", 0xD6A94E, 18)) { hand(0.3f); light = 15; drop = "glowstone_dust"; dropMin = 2; dropMax = 4 }
        block("sea_lantern", n("sea_lantern", 0xAEDBD2, 10)) { hand(0.3f); light = 15; drop = "" }
        block("shroomlight", n("shroomlight", 0xF39A47, 14)) { hand(1f); light = 15 }
        block("sponge", Tex.reg("sponge", G.sponge())) { hand(0.6f) }
        block("hay_block", Tex.reg("hay_top", G.hayTop()), Tex.reg("hay_side", G.stripes(0xC9A22A, 0x8A6A12, 4)), Tex.reg("hay_top", G.hayTop())) { hand(0.5f) }
        block("cactus", Tex.reg("cactus_top", G.cactusTop()), Tex.reg("cactus_side", G.cactusSide())) { hand(0.4f) }
        block("pumpkin", Tex.reg("pumpkin_top", G.pumpkinTop()), Tex.reg("pumpkin_side", G.stripes(0xC77A18, 0x9A5A0E, 4))) { axe(1f) }
        block("melon", Tex.reg("melon_top", G.melonTop()), Tex.reg("melon_side", G.stripes(0x7FB02F, 0x2F6B1B, 3))) { axe(1f); drop = "melon_slice"; dropMin = 3; dropMax = 7 }
        block("bookshelf", Tex.reg("oak_planks", G.planks(0xB8945F)), Tex.reg("bookshelf", G.bookshelf())) {
            axe(1.5f); drop = "book"; dropMin = 3; dropMax = 3
        }
        block("glass", Tex.reg("glass", G.glass(0xDDEEFF, 0x30))) { hand(0.3f); render = R_TRANS; drop = "" }

        // ---------------- цветные семейства ----------------
        for ((cn, cc) in COLORS) {
            block("${cn}_wool", Tex.reg("${cn}_wool", G.wool(cc))) { hand(0.8f) }
            block("${cn}_concrete", n("${cn}_concrete", cc, 3)) { pick(1.8f) }
            block("${cn}_concrete_powder", n("${cn}_concrete_powder", mixC(rgb(cc), rgb(0xCCCCCC), 0.25f), 9)) { shovel(0.5f) }
            block("${cn}_terracotta", n("${cn}_terracotta", mixC(rgb(cc), rgb(0x8F5E45), 0.5f), 5)) { pick(1.25f) }
            block("${cn}_stained_glass", Tex.reg("${cn}_stained_glass", G.glass(cc, 0x70))) { hand(0.3f); render = R_TRANS; drop = "" }
            tag("wool", "${cn}_wool")
        }

        // ---------------- руды ----------------
        val ores = listOf(
            listOf("coal", 0x2B2B2B, 0, "coal", 1, 1),
            listOf("iron", 0xD8AF93, 1, "raw_iron", 1, 1),
            listOf("copper", 0xC0714B, 1, "raw_copper", 2, 5),
            listOf("gold", 0xFCEE4B, 2, "raw_gold", 1, 1),
            listOf("redstone", 0xD62A22, 2, "redstone", 4, 5),
            listOf("lapis", 0x2D4DB3, 1, "lapis_lazuli", 4, 9),
            listOf("diamond", 0x5DECF5, 2, "diamond", 1, 1),
            listOf("emerald", 0x17DD62, 2, "emerald", 1, 1)
        )
        for (o in ores) {
            val k = o[0] as String
            val c = o[1] as Int
            val tier = o[2] as Int
            val dk = o[3] as String
            val mn = o[4] as Int
            val mx = o[5] as Int
            block("${k}_ore", Tex.reg("${k}_ore", G.ore(0x7D7D7D, c))) { pick(3f, tier); drop = dk; dropMin = mn; dropMax = mx }
            block("deepslate_${k}_ore", Tex.reg("deepslate_${k}_ore", G.ore(0x4F4F55, c))) { pick(4.5f, tier); drop = dk; dropMin = mn; dropMax = mx }
        }
        block("nether_gold_ore", Tex.reg("nether_gold_ore", G.ore(0x6F3535, 0xFCEE4B))) { pick(3f); drop = "gold_nugget"; dropMin = 2; dropMax = 6 }
        block("nether_quartz_ore", Tex.reg("nether_quartz_ore", G.ore(0x6F3535, 0xEAE5DE))) { pick(3f); drop = "quartz" }
        block("ancient_debris", n("ancient_debris", 0x5B4036, 14)) { pick(30f, 3) }

        val metals = listOf(
            Triple("coal_block", 0x161616, 0), Triple("iron_block", 0xDCDCDC, 1), Triple("gold_block", 0xF8D93A, 2),
            Triple("copper_block", 0xC06C4B, 1), Triple("diamond_block", 0x62EDE4, 2), Triple("emerald_block", 0x2ECB65, 2),
            Triple("lapis_block", 0x1F44A5, 1), Triple("redstone_block", 0xA81B0E, 2), Triple("netherite_block", 0x45403F, 3),
            Triple("raw_iron_block", 0xA38672, 1), Triple("raw_copper_block", 0x9A5A3E, 1), Triple("raw_gold_block", 0xDDA92E, 2),
            Triple("amethyst_block", 0x8C66C3, 0)
        )
        for ((k, c, t) in metals) block(k, Tex.reg(k, G.metal(c))) { pick(if (k == "netherite_block") 50f else 5f, t) }

        // ---------------- функциональные ----------------
        val craftTop = Tex.reg("crafting_table_top", G.craftTop())
        val craftSide = Tex.reg("crafting_table_side", G.craftSide())
        block("crafting_table", craftTop, craftSide, Tex.reg("oak_planks", G.planks(0xB8945F))) { axe(2.5f); interact = 1 }
        val furnTop = n("furnace_top", 0x8A8A8A, 6)
        block("furnace", furnTop, Tex.reg("furnace_front", G.furnaceFront(false)), furnTop) { pick(3.5f); interact = 2 }
        val chestTop = Tex.reg("chest_top", G.chestSide(false))
        block("chest", chestTop, Tex.reg("chest_front", G.chestSide(true)), chestTop) { axe(2.5f); interact = 3 }
        block("torch", Tex.reg("torch", G.torch())) { render = R_CROSS; solid = false; hardness = 0f; light = 14 }

        // ---------------- растения ----------------
        fun cross(key: String, tex: Int, tintMode: Int = 0, cfg: BlockProps.() -> Unit = {}) {
            block(key, tex) { render = R_CROSS; solid = false; hardness = 0f; tint = tintMode; cfg() }
        }
        cross("short_grass", Tex.reg("short_grass", G.tallGrass(0xB0B0B0)), 2) { drop = "wheat"; dropMin = 0; dropMax = 1 }
        cross("fern", Tex.reg("fern", G.tallGrass(0xA0A0A0, true)), 2)
        cross("dead_bush", Tex.reg("dead_bush", G.deadBush())) { drop = "stick"; dropMin = 0; dropMax = 2 }
        val flowers = listOf(
            "dandelion" to 0xF2E234, "poppy" to 0xD02A2A, "blue_orchid" to 0x2CB8E6, "allium" to 0xB06CD8,
            "azure_bluet" to 0xE8EAF0, "red_tulip" to 0xD8302A, "orange_tulip" to 0xE8841E, "white_tulip" to 0xF0F0F0,
            "pink_tulip" to 0xEF8FB6, "oxeye_daisy" to 0xF4F4EC, "cornflower" to 0x4A6CE8, "lily_of_the_valley" to 0xFAFAFA
        )
        for ((k, c) in flowers) cross(k, Tex.reg(k, G.flower(c)))
        cross("brown_mushroom", Tex.reg("brown_mushroom", G.mushroom(0x8A6A4A, 0xD8CFC0)))
        cross("red_mushroom", Tex.reg("red_mushroom", G.mushroom(0xD02A2A, 0xE8E4D4)))
        cross("sugar_cane", Tex.reg("sugar_cane", G.cane()))

        // ---------------- материалы ----------------
        fun mat(key: String, gen: (P) -> Unit, cfg: ItemDef.() -> Unit = {}) {
            item(key, Tex.reg("item_$key", gen), cfg)
        }
        mat("stick", G.stick()) { fuel = 5f }
        mat("coal", G.coal(0x2B2B2B)) { fuel = 80f }
        mat("charcoal", G.coal(0x3A3225)) { fuel = 80f }
        mat("iron_ingot", G.ingot(0xE0E0E0)); mat("gold_ingot", G.ingot(0xFCE94A)); mat("copper_ingot", G.ingot(0xD07A52))
        mat("netherite_ingot", G.ingot(0x4B4545)); mat("netherite_scrap", G.lump(0x6B4A3E))
        mat("iron_nugget", G.nugget(0xE0E0E0)); mat("gold_nugget", G.nugget(0xFCE94A))
        mat("raw_iron", G.lump(0xC9A58B)); mat("raw_copper", G.lump(0xB5683F)); mat("raw_gold", G.lump(0xE6C23A))
        mat("diamond", G.gem(0x4BE8E0)); mat("emerald", G.gem(0x2ED86A)); mat("quartz", G.gem(0xEDE8DF))
        mat("lapis_lazuli", G.gem(0x2D4DB3)); mat("amethyst_shard", G.gem(0xA77BD8))
        mat("redstone", G.dust(0xD62A22)); mat("glowstone_dust", G.dust(0xF0C860)); mat("gunpowder", G.dust(0x555555))
        mat("sugar", G.dust(0xF4F4F4)); mat("bone_meal", G.dust(0xEEEEDD)); mat("blaze_powder", G.dust(0xF0A020))
        mat("string", G.string()); mat("feather", G.feather()); mat("bone", G.bone())
        mat("leather", G.ball(0x9A5B33)); mat("clay_ball", G.ball(0xA4A8B8)); mat("slime_ball", G.ball(0x6FD35A))
        mat("brick", G.ingot(0x96594B)); mat("nether_brick", G.ingot(0x3A1A20))
        mat("paper", G.paper()); mat("book", G.book()); mat("bowl", G.bowl()) { fuel = 5f }
        mat("ink_sac", G.ball(0x20202A)); mat("spider_eye", G.ball(0x8A2A3A)); mat("ender_pearl", G.ball(0x2A6B5A))
        mat("blaze_rod", G.stick()) { fuel = 120f }
        mat("wheat", G.dust(0xD8B85A)); mat("wheat_seeds", G.dust(0x7FB04A))
        mat("flint", G.lump(0x333333))
        for ((cn, cc) in COLORS) mat("${cn}_dye", G.ball(cc))
        mat("shears", { p ->
            p.line(4, 12, 11, 5, rgb(0xCCCCCC)); p.line(5, 12, 12, 5, rgb(0xAAAAAA)); p.disc(4f, 12f, 1.6f, rgb(0x888888))
            p.disc(12f, 12f, 1.6f, rgb(0x888888)); p.line(12, 12, 5, 5, rgb(0xCCCCCC))
        }) { tool = ToolType.SHEARS; speed = 5f; durability = 238; maxStack = 1 }

        // ---------------- инструменты ----------------
        class Tier(val n: String, val level: Int, val dur: Int, val spd: Float, val bonus: Float, val color: Int)

        val tiers = listOf(
            Tier("wooden", 0, 59, 2f, 0f, 0xA77B47), Tier("stone", 1, 131, 4f, 1f, 0x8A8A8A),
            Tier("iron", 2, 250, 6f, 2f, 0xD8D8D8), Tier("golden", 0, 32, 12f, 0f, 0xFCE94A),
            Tier("diamond", 3, 1561, 8f, 3f, 0x4BE8E0), Tier("netherite", 3, 2031, 9f, 4f, 0x4B4545)
        )
        for (t in tiers) for (kind in TOOL_KINDS) {
            val tt = when (kind) {
                "sword" -> ToolType.SWORD; "pickaxe" -> ToolType.PICKAXE; "axe" -> ToolType.AXE
                "shovel" -> ToolType.SHOVEL; else -> ToolType.HOE
            }
            val atk = when (kind) {
                "sword" -> 4f; "axe" -> 3f; "pickaxe" -> 2f; "shovel" -> 1.5f; else -> 1f
            } + t.bonus
            item("${t.n}_$kind", Tex.reg("item_${t.n}_$kind", G.tool(kind, t.color))) {
                tool = tt; tier = t.level; speed = t.spd; attack = atk; durability = t.dur; maxStack = 1
                if (t.n == "wooden") fuel = 10f
            }
        }

        // ---------------- броня ----------------
        class ArmorSet(val n: String, val color: Int, val pts: IntArray, val dur: Int)

        val sets = listOf(
            ArmorSet("leather", 0x9A5B33, intArrayOf(1, 3, 2, 1), 55), ArmorSet("iron", 0xD8D8D8, intArrayOf(2, 6, 5, 2), 165),
            ArmorSet("golden", 0xFCE94A, intArrayOf(2, 5, 3, 1), 77), ArmorSet("diamond", 0x4BE8E0, intArrayOf(3, 8, 6, 3), 363),
            ArmorSet("netherite", 0x4B4545, intArrayOf(3, 8, 6, 3), 407)
        )
        val pieces = listOf("helmet", "chestplate", "leggings", "boots")
        for (s in sets) for (i in 0 until 4) {
            item("${s.n}_${pieces[i]}", Tex.reg("item_${s.n}_${pieces[i]}", G.armor(i, s.color))) {
                armorSlot = i; armorPts = s.pts[i]; durability = s.dur; maxStack = 1
            }
        }

        // ---------------- еда ----------------
        fun food(key: String, hunger: Int, gen: (P) -> Unit, stack: Int = 64) {
            item(key, Tex.reg("item_$key", gen)) { food = hunger; maxStack = stack }
        }
        food("apple", 4, G.food(0xD02A2A, 0xA01818, true)); food("bread", 5, G.bread())
        food("porkchop", 3, G.meat(0xE8908A, 0xEEEEDD)); food("cooked_porkchop", 8, G.meat(0x9A5A3A, 0xEEEEDD))
        food("beef", 3, G.meat(0xC0392B, 0xEEEEDD)); food("cooked_beef", 8, G.meat(0x6B3A22, 0xEEEEDD))
        food("chicken", 2, G.meat(0xEBC8B0, 0xEEEEDD)); food("cooked_chicken", 6, G.meat(0xC98A4A, 0xEEEEDD))
        food("mutton", 2, G.meat(0xB04A4A, 0xEEEEDD)); food("cooked_mutton", 6, G.meat(0x7A4A30, 0xEEEEDD))
        food("cod", 2, G.meat(0xC9B79A, 0xEEEEDD)); food("cooked_cod", 5, G.meat(0x9A7A50, 0xEEEEDD))
        food("salmon", 2, G.meat(0xE06A5A, 0xEEEEDD)); food("cooked_salmon", 6, G.meat(0xB8643C, 0xEEEEDD))
        food("potato", 1, G.food(0xC9A25A, 0xA8823C)); food("baked_potato", 5, G.food(0xD9B060, 0x8A6A2A))
        food("carrot", 3, G.food(0xE8821E, 0xC86A10)); food("beetroot", 1, G.food(0x8A1A3A, 0x6A0F2A))
        food("melon_slice", 2, G.food(0xE04A4A, 0x7FB02F)); food("sweet_berries", 2, G.food(0xC02A3A, 0x8A1A2A))
        food("cookie", 2, G.food(0xB8864A, 0x4A2A12)); food("golden_apple", 4, G.food(0xFCE94A, 0xD8B820, true))
        food("golden_carrot", 6, G.food(0xF4D03F, 0xD8B820)); food("mushroom_stew", 6, G.food(0xB07A5A, 0x6A4A32), 1)
        food("rotten_flesh", 4, G.meat(0x6B8A4A, 0xAAAAAA))

        // ---------------- теги и топливо ----------------
        tag("stone_crafting", "cobblestone", "cobbled_deepslate", "blackstone")
        tag("coals", "coal", "charcoal")
        tag("sands", "sand", "red_sand")
        for (d in items) {
            val k = d.key
            if (d.block != null && d.fuel == 0f) {
                if (k.endsWith("_planks") || k.endsWith("_log") || k.startsWith("stripped_") && !k.endsWith("_stem") ||
                    k == "crafting_table" || k == "chest" || k == "bookshelf"
                ) d.fuel = 15f
                if (k.endsWith("_slab") && k != "crimson_slab" && k != "warped_slab") d.fuel = 7.5f
                if (k == "coal_block") d.fuel = 800f
            }
            if (d.tool != ToolType.NONE || d.armorSlot >= 0 || k == "shears") d.maxStack = 1
        }
        inited = true
    }
}
