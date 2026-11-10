package com.blocknize.app.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface MediaSessionUiState {
    data object AccessRequired : MediaSessionUiState
    data object NoSession : MediaSessionUiState
    data class Track(
        val title: String,
        val artist: String,
        val playerName: String,
        val artwork: Bitmap?,
        val isPlaying: Boolean,
        val canSkipPrevious: Boolean,
        val canSkipNext: Boolean,
        val canTogglePlayback: Boolean
    ) : MediaSessionUiState
}

class MediaSessionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var selectedController: MediaController? = null

    fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val expected = ComponentName(appContext, BlockNizeNotificationListener::class.java)
            .flattenToString()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun observe(): Flow<MediaSessionUiState> = callbackFlow {
        if (!hasNotificationAccess()) {
            selectedController = null
            trySend(MediaSessionUiState.AccessRequired)
            awaitClose { }
            return@callbackFlow
        }

        val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
        var activeControllers = emptyList<MediaController>()

        fun publishState() {
            val candidates = activeControllers.filter { controller ->
                val state = controller.playbackState?.state
                state != null && state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_STOPPED
            }
            val controller = candidates.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: candidates.firstOrNull()

            selectedController = controller
            if (controller == null) {
                trySend(MediaSessionUiState.NoSession)
                return
            }

            val metadata = controller.metadata
            val state = controller.playbackState
            val actions = state?.actions ?: 0L
            val playerName = runCatching {
                appContext.packageManager.getApplicationLabel(
                    appContext.packageManager.getApplicationInfo(controller.packageName, 0)
                ).toString()
            }.getOrDefault(controller.packageName)
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: "Reproducción multimedia"
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
            val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            trySend(MediaSessionUiState.Track(
                title = title,
                artist = artist,
                playerName = playerName,
                artwork = artwork,
                isPlaying = isPlaying,
                canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                canTogglePlayback = actions and (
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
                ) != 0L || state == null
            ))
        }

        fun updateControllers(controllers: List<MediaController>) {
            callbacks.forEach { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
            callbacks.clear()
            activeControllers = controllers
            controllers.forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publishState()
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publishState()
                    override fun onSessionDestroyed() {
                        runCatching { updateControllers(sessionManager.getActiveSessions(listenerComponent)) }
                    }
                }
                callbacks[controller] = callback
                runCatching { controller.registerCallback(callback, mainHandler) }
            }
            publishState()
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateControllers(controllers.orEmpty())
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(listener, listenerComponent, mainHandler)
            updateControllers(sessionManager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            selectedController = null
            trySend(MediaSessionUiState.AccessRequired)
        }

        awaitClose {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(listener) }
            callbacks.forEach { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
            callbacks.clear()
            selectedController = null
        }
    }

    fun skipPrevious() = runCatching { selectedController?.transportControls?.skipToPrevious() }
    fun skipNext() = runCatching { selectedController?.transportControls?.skipToNext() }
    fun togglePlayback(isPlaying: Boolean) = runCatching {
        val controls = selectedController?.transportControls ?: return@runCatching
        if (isPlaying) controls.pause() else controls.play()
    }

    private val listenerComponent
        get() = ComponentName(appContext, BlockNizeNotificationListener::class.java)

}
