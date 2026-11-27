@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fcraft.ui

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fcraft.core.Atlas
import com.fcraft.core.ItemStack
import com.fcraft.core.Reg
import com.fcraft.core.Tex
import com.fcraft.game.Settings
import kotlin.math.roundToInt

@Composable
fun FCraftTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (Settings.dynamicColor && Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(ctx) else darkColorScheme(
        primary = Color(0xFF8FD96B), onPrimary = Color(0xFF0B3900),
        primaryContainer = Color(0xFF225A00), onPrimaryContainer = Color(0xFFAAF580),
        secondary = Color(0xFFBCCBAA), onSecondary = Color(0xFF273421),
        secondaryContainer = Color(0xFF3D4B36), onSecondaryContainer = Color(0xFFD8E7C4),
        tertiary = Color(0xFF9FCFD3), onTertiary = Color(0xFF00363B),
        tertiaryContainer = Color(0xFF1E4D53), onTertiaryContainer = Color(0xFFBBEBF0),
        background = Color(0xFF11140E), onBackground = Color(0xFFE2E3D8),
        surface = Color(0xFF11140E), onSurface = Color(0xFFE2E3D8),
        surfaceVariant = Color(0xFF43483E), onSurfaceVariant = Color(0xFFC3C8BA),
        outline = Color(0xFF8D9286)
    )
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography(),
        content = content
    )
}

@Composable
fun ItemIcon(id: Int, sz: Dp, modifier: Modifier = Modifier) {
    val def = Reg.items[id]
    val img = Atlas.image
    Canvas(modifier.size(sz)) {
        val t = def.icon
        val tx = (t % Tex.COLS) * 16
        val ty = (t / Tex.COLS) * 16
        drawImage(
            img,
            srcOffset = IntOffset(tx, ty),
            srcSize = IntSize(16, 16),
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
            filterQuality = FilterQuality.None
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlotView(
    stack: ItemStack?, sz: Dp, selected: Boolean = false,
    onClick: () -> Unit = {}, onLong: () -> Unit = {}
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(sz)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) {
        if (stack != null) {
            ItemIcon(stack.id, sz * 0.72f)
            if (stack.count > 1) {
                Text(
                    "${stack.count}",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
            }
            val dur = stack.def.durability
            if (dur > 0 && stack.dmg > 0) {
                val frac = (1f - stack.dmg.toFloat() / dur).coerceIn(0f, 1f)
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                        .size(width = sz * 0.7f * frac, height = 2.5.dp)
                        .background(if (frac > 0.5f) Color(0xFF7CD957) else if (frac > 0.2f) Color(0xFFE8B030) else Color(0xFFE04A3A))
                )
            }
        }
    }
}
