package com.fcraft

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fcraft.core.Atlas
import com.fcraft.game.GameSession
import com.fcraft.game.Overlay
import com.fcraft.game.Settings
import com.fcraft.game.WorldMeta
import com.fcraft.ui.AboutDialog
import com.fcraft.ui.FCraftTheme
import com.fcraft.ui.GameHolder
import com.fcraft.ui.GameScreen
import com.fcraft.ui.MainMenu
import com.fcraft.ui.SettingsScreen
import com.fcraft.ui.WorldsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Settings.init(this)
        setContent {
            FCraftTheme { App() }
        }
    }

    override fun onResume() {
        super.onResume()
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        GameHolder.gl?.onResume()
    }

    override fun onPause() {
        super.onPause()
        GameHolder.gl?.onPause()
        val s = GameHolder.session
        if (s != null) Thread { s.save() }.start()
        Settings.save()
    }
}

private enum class Screen { MENU, WORLDS, SETTINGS, GAME }

@Composable
private fun App() {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(Atlas.ready) }
    LaunchedEffect(Unit) {
        if (!Atlas.ready) withContext(Dispatchers.Default) { Atlas.build() }
        ready = true
    }
    var screen by remember { mutableStateOf(Screen.MENU) }
    var about by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<GameSession?>(null) }
    val scope = rememberCoroutineScope()

    fun play(m: WorldMeta) {
        scope.launch {
            val s = withContext(Dispatchers.Default) {
                GameSession(ctx.applicationContext, m).also { it.start() }
            }
            GameHolder.session = s
            session = s
            screen = Screen.GAME
        }
    }

    fun exit() {
        val s = session
        screen = Screen.WORLDS
        session = null
        GameHolder.session = null
        if (s != null) Thread { s.stop() }.start()
    }

    if (!ready) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text("Рисуем текстуры…", style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        when (screen) {
            Screen.MENU -> MainMenu(onPlay = { screen = Screen.WORLDS }, onSettings = { screen = Screen.SETTINGS }, onAbout = { about = true })
            Screen.WORLDS -> {
                BackHandler { screen = Screen.MENU }
                WorldsScreen(onBack = { screen = Screen.MENU }, onPlay = { play(it) })
            }
            Screen.SETTINGS -> {
                BackHandler { Settings.save(); screen = Screen.MENU }
                SettingsScreen(onBack = { screen = Screen.MENU })
            }
            Screen.GAME -> {
                val s = session
                if (s != null) {
                    BackHandler {
                        when (s.overlay) {
                            Overlay.NONE -> s.pause()
                            Overlay.PAUSE -> s.overlay = Overlay.NONE
                            Overlay.DEAD -> {}
                            else -> s.closeUi()
                        }
                    }
                    GameScreen(s, onExit = { exit() })
                }
            }
        }
        if (about) AboutDialog { about = false }
    }
}
