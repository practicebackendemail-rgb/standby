package com.dockstandby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.service.dreams.DreamService
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dockstandby.ui.setup.SetupScreen
import com.dockstandby.ui.setup.Onboarding
import com.dockstandby.data.Prefs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dockstandby.ui.standby.StandByRoot
import com.dockstandby.ui.theme.CssText

/** Setup / settings screen (launcher entry); follows the phone's rotation. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoLaunchService.sync(this)
        setContent {
            CssText {
                // First launch shows the walkthrough; afterwards (or after Skip/Start) the setup screen.
                var onboarded by remember { mutableStateOf(Prefs.get(this).settings.value.onboarded) }
                if (onboarded) SetupScreen() else Onboarding { onboarded = true }
            }
        }
    }
}

/** Full-screen StandBy, opened from setup ("Preview StandBy"). */
class StandByActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // Demo mode is for screenshots in debug builds only.
        val demo = BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)
        val page = intent.getIntExtra("page", 0)
        val theme = intent.getStringExtra("theme")
        val time = intent.getStringExtra("time")
        setContent { CssText { StandByRoot(demo, page, theme, time) } }
        // Opened by plugging in: close again when the charger is removed, like iOS StandBy.
        if (intent.getBooleanExtra(EXTRA_AUTO, false)) {
            ContextCompat.registerReceiver(
                this, unplugged, IntentFilter(Intent.ACTION_POWER_DISCONNECTED), ContextCompat.RECEIVER_EXPORTED,
            )
            listening = true
        }
    }

    private var listening = false
    private val unplugged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onDestroy() {
        if (listening) unregisterReceiver(unplugged)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTO = "auto"
    }
}

/**
 * Screen saver ("Daydream"). Android starts it automatically while docked or charging when
 * StandBy is chosen in Settings › Display › Screen saver — this is how auto-open works.
 */
class StandByDreamService : DreamService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        val view = ComposeView(this).apply { setContent { CssText { StandByRoot() } } }
        window.decorView.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeViewModelStoreOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        super.onDestroy()
    }
}
