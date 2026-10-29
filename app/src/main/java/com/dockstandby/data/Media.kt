package com.dockstandby.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext


fun Context.hasNotificationAccess(): Boolean {
    val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
    val me = ComponentName(this, MediaListenerService::class.java)
    return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
}

data class NowPlaying(
    val title: String,
    val artist: String?,
    val art: Bitmap?,
    val appName: String,
    val playing: Boolean,
    val durationMs: Long,
    /** Position at [positionAt] (elapsedRealtime) plus speed, so the UI can extrapolate. */
    val positionMs: Long,
    val positionAt: Long,
    val speed: Float,
    val controller: MediaController?,
) {
    fun positionNow(): Long {
        if (!playing) return positionMs
        val p = positionMs + ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p
    }
}

/** The most relevant active media session, or null if nothing is playing / no access. */
@Composable
fun rememberNowPlaying(): State<NowPlaying?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<NowPlaying?>(null) }
    DisposableEffect(context) {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        var current: MediaController? = null
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { current?.let { state.value = snapshot(context, it) } }
            override fun onPlaybackStateChanged(s: PlaybackState?) { current?.let { state.value = snapshot(context, it) } }
            override fun onSessionDestroyed() { current = null; state.value = null }
        }
        fun pick(list: List<MediaController>?) {
            val best = list.orEmpty().let { l ->
                l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: l.firstOrNull()
            }
            if (best?.sessionToken != current?.sessionToken) {
                current?.unregisterCallback(callback)
                current = best
                best?.registerCallback(callback, handler)
            }
            state.value = best?.let { snapshot(context, it) }
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        val component = ComponentName(context, MediaListenerService::class.java)
        val registered = runCatching {
            msm!!.addOnActiveSessionsChangedListener(listener, component, handler)
            pick(msm.getActiveSessions(component))
        }.isSuccess
        onDispose {
            current?.unregisterCallback(callback)
            if (registered) msm?.removeOnActiveSessionsChangedListener(listener)
        }
    }
    return state
}

private fun snapshot(context: Context, c: MediaController): NowPlaying? {
    val md = c.metadata ?: return null
    val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
    val ps = c.playbackState
    val appName = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
    }.getOrDefault(c.packageName)
    return NowPlaying(
        title = title,
        artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
        art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
        appName = appName,
        playing = ps?.state == PlaybackState.STATE_PLAYING,
        durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
        positionMs = ps?.position ?: 0,
        positionAt = ps?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
        speed = ps?.playbackSpeed ?: 1f,
        controller = c,
    )
}
