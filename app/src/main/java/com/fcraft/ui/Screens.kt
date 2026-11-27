@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fcraft.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fcraft.game.Settings
import com.fcraft.game.WorldMeta
import com.fcraft.game.Worlds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainMenu(onPlay: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val inf = rememberInfiniteTransition(label = "logo")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart), label = "r")
    val rot2 by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(45000, easing = LinearEasing), RepeatMode.Restart), label = "r2")
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cs.background, cs.primaryContainer.copy(alpha = 0.55f))))
            .safeDrawingPadding()
    ) {
        Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(190.dp).graphicsLayer { rotationZ = rot2 }.clip(MaterialShapes.Cookie12Sided.toShape())
                        .background(cs.tertiaryContainer)
                )
                Box(
                    Modifier.size(150.dp).graphicsLayer { rotationZ = rot }.clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(cs.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("f", fontSize = 84.sp, fontWeight = FontWeight.Black, color = cs.onPrimary, modifier = Modifier.graphicsLayer { rotationZ = -rot })
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.Start) {
                Text("fCraft", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = cs.primary)
                Text("Офлайн-песочница из кубиков", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onPlay, Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp)) {
                    Text("Играть", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(onClick = onSettings, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
                    Text("Настройки", fontSize = 17.sp)
                }
                TextButton(onClick = onAbout, Modifier.fillMaxWidth()) { Text("О игре") }
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        title = { Text("fCraft 0.1") },
        text = {
            Text(
                "Полностью офлайн. Весь мир, блоки, мобы и текстуры создаются кодом на устройстве.\n\n" +
                    "Управление: левый джойстик — ходьба, правая часть экрана — камера, «Бить» — удержание для добычи, " +
                    "«Исп.» — поставить блок / открыть верстак, печь, сундук / съесть еду. " +
                    "В творческом режиме двойной прыжок включает полёт."
            )
        }
    )
}

private fun fmt(t: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(t))

@Composable
fun WorldsScreen(onBack: () -> Unit, onPlay: (WorldMeta) -> Unit) {
    val ctx: Context = LocalContext.current
    val list = remember { mutableStateListOf<WorldMeta>().also { it.addAll(Worlds.list(ctx)) } }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WorldMeta?>(null) }
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Назад") }
                Text("Миры", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Миров пока нет — создайте первый!", color = cs.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                    items(list, key = { it.id }) { w ->
                        Card(
                            onClick = { onPlay(w) },
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(52.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(cs.primary),
                                    contentAlignment = Alignment.Center
                                ) { Text(if (w.creative) "✦" else "⛏", fontSize = 24.sp, color = cs.onPrimary) }
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                    Text(w.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        (if (w.creative) "Творческий" else "Выживание") + " • seed ${w.seed} • ${fmt(w.last)}",
                                        style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer
                                    )
                                }
                                TextButton(onClick = { deleting = w }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) { Text("＋ Создать мир", fontWeight = FontWeight.Bold) }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var seed by remember { mutableStateOf("") }
        var creative by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Новый мир") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(value = seed, onValueChange = { seed = it }, label = { Text("Seed (необязательно)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleButton(checked = !creative, onCheckedChange = { creative = false }) { Text("Выживание") }
                        ToggleButton(checked = creative, onCheckedChange = { creative = true }) { Text("Творческий") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sd = seed.trim().let { s -> s.toLongOrNull() ?: if (s.isEmpty()) System.nanoTime() else s.hashCode().toLong() }
                    val m = Worlds.create(ctx, name.trim(), sd, creative)
                    list.add(0, m)
                    creating = false
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Отмена") } }
        )
    }
    deleting?.let { w ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${w.name}»?") },
            text = { Text("Мир будет удалён безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    Worlds.delete(ctx, w); list.remove(w); deleting = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}

@Composable
fun SettingsPanel() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Дальность прорисовки: ${Settings.renderDist} чанков", style = MaterialTheme.typography.titleMedium)
        Slider(value = Settings.renderDist.toFloat(), onValueChange = { Settings.renderDist = it.toInt().coerceIn(3, 10) }, valueRange = 3f..10f, steps = 6)
        Text("Поле зрения: ${Settings.fov.toInt()}°", style = MaterialTheme.typography.titleMedium)
        Slider(value = Settings.fov, onValueChange = { Settings.fov = it }, valueRange = 55f..100f)
        Text("Чувствительность камеры: ${"%.1f".format(Settings.sens)}", style = MaterialTheme.typography.titleMedium)
        Slider(value = Settings.sens, onValueChange = { Settings.sens = it }, valueRange = 0.3f..2.5f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Показывать FPS и координаты", Modifier.weight(1f))
            Switch(checked = Settings.showFps, onCheckedChange = { Settings.showFps = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Динамические цвета (Android 12+)", Modifier.weight(1f))
            Switch(checked = Settings.dynamicColor, onCheckedChange = { Settings.dynamicColor = it })
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { Settings.save(); onBack() }) { Text("← Назад") }
                Text("Настройки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(520.dp)) { SettingsPanel() }
        }
    }
}
