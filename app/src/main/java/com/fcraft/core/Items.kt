package com.fcraft.core

class ItemStack(var id: Int, var count: Int = 1, var dmg: Int = 0) {
    val def: ItemDef get() = Reg.items[id]
    val max: Int get() = Reg.items[id].maxStack
    fun copy() = ItemStack(id, count, dmg)
    fun sameKind(o: ItemStack) = id == o.id && dmg == o.dmg
}

class Inventory {
    val slots = arrayOfNulls<ItemStack>(36)
    val armor = arrayOfNulls<ItemStack>(4)
    var selected = 0

    fun held(): ItemStack? = slots[selected]

    /** Возвращает остаток, который не поместился. */
    fun add(s: ItemStack): Int {
        var left = s.count
        val max = s.max
        if (max > 1) {
            for (i in 0 until 36) {
                val t = slots[i]
                if (t != null && t.id == s.id && t.count < max) {
                    val m = minOf(left, max - t.count)
                    t.count += m
                    left -= m
                    if (left == 0) return 0
                }
            }
        }
        for (i in 0 until 36) {
            if (slots[i] == null) {
                val m = minOf(left, max)
                slots[i] = ItemStack(s.id, m, s.dmg)
                left -= m
                if (left == 0) return 0
            }
        }
        return left
    }

    fun addKey(key: String, n: Int = 1) {
        val d = Reg.def(key) ?: return
        if (n > 0) add(ItemStack(d.id, n))
    }

    fun consumeHeld() {
        val s = slots[selected] ?: return
        s.count--
        if (s.count <= 0) slots[selected] = null
    }

    fun armorPoints(): Int {
        var p = 0
        for (a in armor) if (a != null) p += a.def.armorPts
        return p
    }
}

/** Печь: 0 — вход, 1 — топливо, 2 — результат. */
class Furnace {
    val slots = arrayOfNulls<ItemStack>(3)
    var burn = 0f
    var burnTotal = 1f
    var progress = 0f

    private fun result(): ItemStack? {
        val i = slots[0] ?: return null
        val outKey = Recipes.smelt[i.def.key] ?: return null
        val od = Reg.def(outKey) ?: return null
        val o = slots[2]
        if (o != null && (o.id != od.id || o.count >= o.max)) return null
        return ItemStack(od.id, 1)
    }

    fun tick(dt: Float) {
        val res = result()
        if (burn <= 0f && res != null) {
            val f = slots[1]
            if (f != null && f.def.fuel > 0f) {
                burn = f.def.fuel
                burnTotal = burn
                f.count--
                if (f.count <= 0) slots[1] = null
            }
        }
        if (burn > 0f) {
            burn -= dt
            if (res != null) {
                progress += dt
                if (progress >= 10f) {
                    progress = 0f
                    val o = slots[2]
                    if (o == null) slots[2] = res else o.count++
                    val i = slots[0]!!
                    i.count--
                    if (i.count <= 0) slots[0] = null
                }
            } else progress = 0f
        } else if (progress > 0f) progress = maxOf(0f, progress - dt * 2f)
    }
}
