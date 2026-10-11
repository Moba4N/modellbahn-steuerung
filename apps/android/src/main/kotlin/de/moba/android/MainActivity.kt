package de.moba.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell

class MainActivity : ComponentActivity() {
    private val app by lazy { MobaApp(lifecycleScope).also { it.start() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Im Fahrbetrieb bleibt der Bildschirm an.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { MobaShell(app) }
    }
}
