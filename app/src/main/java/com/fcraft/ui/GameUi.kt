
package com.fcraft.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fcraft.core.ItemStack
import com.fcraft.core.Recipes
import com.fcraft.core.Reg
import com.fcraft.core.ToolType
import com.fcraft.game.GameSession
import com.fcraft.game.Overlay
import com.fcraft.game.Settings
import com.fcraft.game.SlotRef
import com.fcraft.render.GameRenderer
import kotlin.math.roundToInt

object GameHolder {
    var session: GameSession? = null
    var gl: GLSurfaceView? = null
}

@Composable
fun GameScreen(s: GameSession, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    setRenderer(GameRenderer(s))
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    GameHolder.gl = this
                }
            },
            onRelease = { if (GameHolder.gl === it) GameHolder.gl = null },
            modifier = Modifier.fillMaxSize()
        )
        if (!s.loaded) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Генерация мира…", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            if (s.overlay == Overlay.NONE) Controls(s)
            Hud(s)
            when (s.overlay) {
                Overlay.PAUSE -> PauseMenu(s, onExit)
                Overlay.COMMAND -> CommandDialog(s)
                Overlay.DEAD -> DeadScreen(s)
                Overlay.INVENTORY -> if (s.creative) CreativeScreen(s) else SurvivalInventory(s)
                Overlay.CRAFTING -> CraftingTable(s)
                Overlay.FURNACE -> FurnaceScreen(s)
                Overlay.CHEST -> ChestScreen(s)
                else -> {}
            }
        }
    }
}

@Composable
private fun CommandDialog(s: GameSession) {
    var command by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { s.overlay = Overlay.NONE },
        title = { Text("Команды") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("/help — список команд")
                OutlinedTextField(value = command, onValueChange = { command = it }, singleLine = true, label = { Text("Команда") }, placeholder = { Text("/give stone 64") })
            }
        },
        confirmButton = { TextButton(onClick = { s.executeCommand(command) }) { Text("Выполнить") } },
        dismissButton = { TextButton(onClick = { s.overlay = Overlay.NONE }) { Text("Отмена") } }
    )
}

// ---------------------------------------------------------------- управление

@Composable
private fun RoundBtn(label: String, size: Dp, modifier: Modifier, big: Boolean = false, content: Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)).then(content),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = if (big) 22.sp else 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Controls(s: GameSession) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures { change, drag ->
                change.consume()
                s.input.addLook(drag.x, drag.y)
            }
        }
    )
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        // джойстик
        Box(
            Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp).size(150.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val r = size.width / 2f
                        val center = Offset(r, size.height / 2f)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val d = ch.position - center
                            val len = d.getDistance()
                            val v = if (len > r) d * (r / len) else d
                            knob = v
                            s.input.mx = v.x / r
                            s.input.mz = -v.y / r
                            ch.consume()
                        }
                        knob = Offset.Zero
                        s.input.mx = 0f; s.input.mz = 0f
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }.size(62.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
            )
        }

        // кнопки действий
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (s.flyHud) RoundBtn("▼", 56.dp, Modifier, content = Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(); s.input.down = true
                        waitForUpOrCancellation(); s.input.down = false
                    }
                })
                RoundBtn("Исп.", 66.dp, Modifier, content = Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = { s.input.use = true })
                })
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RoundBtn("⤒", 66.dp, Modifier, true, Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(); s.input.jump = true
                        waitForUpOrCancellation(); s.input.jump = false
                    }
                })
                RoundBtn("Бить", 84.dp, Modifier, false, Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(); s.input.breaking = true
                        waitForUpOrCancellation(); s.input.breaking = false
                    }
                })
            }
        }
    }
}

// ---------------------------------------------------------------- HUD

@Composable
private fun Hud(s: GameSession) {
    @Suppress("UNUSED_VARIABLE") val tick = s.invTick
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp)) {
        // прицел
        Canvas(Modifier.align(Alignment.Center).size(24.dp)) {
            val c = center
            drawLine(Color.White, Offset(c.x - 9f, c.y), Offset(c.x + 9f, c.y), 3f)
            drawLine(Color.White, Offset(c.x, c.y - 9f), Offset(c.x, c.y + 9f), 3f)
        }
        if (s.overlay == Overlay.NONE) {
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { s.openInventory() }) { Text("🎒") }
                FilledTonalButton(onClick = { s.openCommands() }) { Text("⌘") }
                FilledTonalButton(onClick = { s.pause() }) { Text("⏸") }
            }
        }
        if (Settings.showFps) {
            Surface(
                Modifier.align(Alignment.TopStart), shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.4f)
            ) { Text(s.hudText, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, color = Color.White) }
        }
        if (s.message.isNotEmpty()) {
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 6.dp), shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) { Text(s.message, Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
        }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            val held = s.inv.slots[s.hudSel]
            if (held != null && s.overlay == Overlay.NONE) {
                Text(held.def.name, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(bottom = 3.dp))
            }
            if (!s.creative && s.overlay == Overlay.NONE) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Row {
                        for (i in 0 until 10) {
                            val v = s.hudHealth - i * 2
                            Text("♥", fontSize = 15.sp, color = if (v >= 2) Color(0xFFE53935) else if (v == 1) Color(0xFFFF8A65) else Color(0xFF444444))
                        }
                    }
                    Row {
                        for (i in 0 until 10) {
                            val v = s.hudHunger - i * 2
                            Text("●", fontSize = 13.sp, color = if (v >= 2) Color(0xFFD9893A) else if (v == 1) Color(0xFF9A6A30) else Color(0xFF444444))
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = 0.35f)) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 0 until 9) SlotView(s.inv.slots[i], 40.dp, selected = i == s.hudSel, onClick = { s.select(i) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- оверлеи

@Composable
private fun Scrim(onClose: (() -> Unit)?, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { } }.safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 6.dp) {
            Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
                content()
                if (onClose != null) {
                    TextButton(onClick = onClose, Modifier.align(Alignment.End)) { Text("Закрыть") }
                }
            }
        }
    }
}

@Composable
private fun PauseMenu(s: GameSession, onExit: () -> Unit) {
    Scrim(null) {
        Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Пауза", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { s.overlay = Overlay.NONE }, Modifier.fillMaxSize().height(56.dp), shape = RoundedCornerShape(28.dp)) { Text("Продолжить") }
            SettingsPanel()
            FilledTonalButton(onClick = { Settings.save(); onExit() }, Modifier.fillMaxSize().height(52.dp), shape = RoundedCornerShape(26.dp)) {
                Text("Сохранить и выйти")
            }
        }
    }
}

@Composable
private fun DeadScreen(s: GameSession) {
    Box(Modifier.fillMaxSize().background(Color(0x99700000)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Вы погибли", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White)
            Button(onClick = { s.respawn() }) { Text("Возродиться") }
        }
    }
}

private val SS = 38.dp

@Composable
private fun Cursor(s: GameSession) {
    val c = s.cursor
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
        if (c != null) {
            ItemIcon(c.id, 32.dp)
            Text(" ${c.def.name} ×${c.count}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        } else {
            Text("Нажмите на ячейку — взять, ещё раз — положить. Долгое нажатие — половина / один.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Slot(s: GameSession, ref: SlotRef, size: Dp = SS) {
    SlotView(ref.arr[ref.i], size, onClick = { s.click(ref, false) }, onLong = { s.click(ref, true) })
}

@Composable
private fun InvGrid(s: GameSession) {
    @Suppress("UNUSED_VARIABLE") val tick = s.invTick
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (c in 0 until 9) Slot(s, SlotRef(s.inv.slots, 9 + r * 9 + c))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (c in 0 until 9) Slot(s, SlotRef(s.inv.slots, c))
        }
    }
}

@Composable
private fun CraftGrid(s: GameSession, w: Int) {
    @Suppress("UNUSED_VARIABLE") val tick = s.invTick
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0 until w) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until w) Slot(s, SlotRef(s.craftGrid, r * w + c))
            }
        }
        Text("➜", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
        Slot(s, SlotRef(arrayOf(s.craftOut), 0, 1), 46.dp)
    }
}

@Composable
private fun SurvivalInventory(s: GameSession) {
    @Suppress("UNUSED_VARIABLE") val tick = s.invTick
    Scrim({ s.closeUi() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Инвентарь", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Cursor(s)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until 4) Slot(s, SlotRef(s.inv.armor, i, 0) { it.def.armorSlot == i })
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Крафт 2×2", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CraftGrid(s, 2)
                Text("Броня: ${s.inv.armorPoints()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InvGrid(s)
        }
    }
}

@Composable
private fun CraftingTable(s: GameSession) {
    @Suppress("UNUSED_VARIABLE") val tick = s.invTick
    Scrim({ s.closeUi() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Верстак", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Cursor(s)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            CraftGrid(s, 3)
            InvGrid(s)
        }
    }
}

@Composable
private fun FurnaceScreen(s: GameSession) {
    @Suppress("UNUSED_VARIABLE") val tick = s.invTick
    val f = s.openFurnace ?: return
    Scrim({ s.closeUi() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Печь", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Cursor(s)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Slot(s, SlotRef(f.slots, 0, 0) { Recipes.smelt.containsKey(it.def.key) })
                LinearProgressIndicator(progress = { (f.burn / f.burnTotal).coerceIn(0f, 1f) }, modifier = Modifier.width(SS), color = Color(0xFFFF8A30))
                Slot(s, SlotRef(f.slots, 1, 0) { it.def.fuel > 0f })
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("➜", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(progress = { (f.progress / 10f).coerceIn(0f, 1f) }, modifier = Modifier.width(50.dp))
            }
            Slot(s, SlotRef(f.slots, 2, 2), 46.dp)
            InvGrid(s)
        }
    }
}

@Composable
private fun ChestScreen(s: GameSession) {
    @Suppress("UNUSED_VARIABLE") val tick = s.invTick
    val chest = s.openChest ?: return
    Scrim({ s.closeUi() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Сундук", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Cursor(s)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until 9) Slot(s, SlotRef(chest, r * 9 + c))
            }
        }
        Spacer(Modifier.height(10.dp))
        InvGrid(s)
    }
}

@Composable
private fun CreativeScreen(s: GameSession) {
    @Suppress("UNUSED_VARIABLE") val tick = s.invTick
    var cat by remember { mutableStateOf(0) }
    val cats = listOf("Блоки", "Инструменты", "Еда", "Материалы")
    val list = remember(cat) {
        Reg.items.filter { d ->
            if (d.id == 0 || d.hidden) false else when (cat) {
                0 -> d.block != null
                1 -> d.tool != ToolType.NONE || d.armorSlot >= 0
                2 -> d.food > 0
                else -> d.block == null && d.tool == ToolType.NONE && d.armorSlot < 0 && d.food == 0
            }
        }
    }
    Scrim({ s.closeUi() }) {
        Column(Modifier.width(640.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((i, n) in cats.withIndex()) FilterChip(selected = cat == i, onClick = { cat = i }, label = { Text(n) })
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(SS + 4.dp),
                modifier = Modifier.height(190.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(list, key = { it.id }) { d ->
                    SlotView(ItemStack(d.id, 1), SS, onClick = { s.giveCreative(d.id) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Хотбар (нажмите, чтобы очистить ячейку)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until 9) SlotView(s.inv.slots[i], SS, selected = i == s.hudSel, onClick = {
                    synchronized(s.lock) { s.inv.slots[i] = null; s.refreshHud() }
                }, onLong = { s.select(i) })
            }
        }
    }
}
