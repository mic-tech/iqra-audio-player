package com.maryumcenter.iqraaudioplayer

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val hasRoot: Boolean = false,
    val rootLabel: String? = null,
    val tracks: List<Track> = emptyList(),
    val scanning: Boolean = false,
    val connected: Boolean = false,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
) {
    val currentTrack: Track?
        get() = tracks.getOrNull(currentIndex)
}

/**
 * Kept apart from [PlayerUiState] on purpose: this ticks several times a second,
 * and folding it into the main state would re-run the whole track list with it.
 */
data class Progress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Bridges the Compose UI to the [PlayerService]'s MediaSession and keeps the
 * queue in step with whatever the [Library] currently holds.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val library = Library.get(application)
    private val status = MutableStateFlow(PlayerStatus())

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var controller: MediaController? = null
    private val controllerReady = CompletableDeferred<MediaController>()

    /** Identifies the track list currently loaded into the player. */
    private var appliedSignature: Int? = null
    private var lastSavedAt = 0L

    val ui: StateFlow<PlayerUiState> = combine(
        library.rootUri,
        library.rootLabel,
        library.tracks,
        library.scanning,
        status,
    ) { root, label, tracks, scanning, state ->
        PlayerUiState(
            hasRoot = root != null,
            rootLabel = label,
            tracks = tracks,
            scanning = scanning,
            connected = state.connected,
            currentIndex = state.currentIndex,
            isPlaying = state.isPlaying,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, snapshot())

    private fun snapshot() = PlayerUiState(
        hasRoot = library.rootUri.value != null,
        rootLabel = library.rootLabel.value,
        tracks = library.tracks.value,
        scanning = library.scanning.value,
    )

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            readPlayerState()
        }
    }

    init {
        connectToService()

        viewModelScope.launch { library.load() }

        viewModelScope.launch {
            val active = controllerReady.await()
            library.tracks.collect { tracks -> syncQueue(active, tracks) }
        }

        viewModelScope.launch {
            controllerReady.await()
            while (true) {
                delay(POSITION_POLL_MS)
                if (controller?.isPlaying != true) continue
                readPlayerState()
                rememberProgress()
            }
        }
    }

    // --- user actions ------------------------------------------------------

    fun setRoot(treeUri: Uri) {
        viewModelScope.launch { library.setRoot(treeUri) }
    }

    fun rescan() {
        viewModelScope.launch { library.rescan() }
    }

    fun playTrack(index: Int) {
        val active = controller ?: return
        if (index !in 0 until active.mediaItemCount) return
        active.seekTo(index, 0L)
        active.prepare()
        active.play()
    }

    fun togglePlayPause() {
        val active = controller ?: return
        if (active.isPlaying) {
            active.pause()
            rememberProgress(force = true)
        } else {
            if (active.playbackState == Player.STATE_IDLE) active.prepare()
            active.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        // Player.seekToPrevious restarts the current track unless we are near its
        // beginning, which is what listeners expect from a "previous" button.
        controller?.seekToPrevious()
    }

    fun seekBack() {
        controller?.seekBack()
    }

    fun seekForward() {
        controller?.seekForward()
    }

    fun seekTo(positionMs: Long) {
        val active = controller ?: return
        active.seekTo(positionMs.coerceAtLeast(0L))
        rememberProgress(force = true)
    }

    // --- service plumbing --------------------------------------------------

    private fun connectToService() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val active = try {
                    future.get()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not connect to playback service", e)
                    return@addListener
                }
                controller = active
                active.addListener(playerListener)
                readPlayerState()
                controllerReady.complete(active)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * Pushes [tracks] into the player. Called on first load and after a rescan;
     * if the file that is currently playing still exists it keeps playing from
     * the same spot at its new position in the list.
     */
    private fun syncQueue(active: MediaController, tracks: List<Track>) {
        if (tracks.isEmpty()) {
            if (appliedSignature != null) {
                active.clearMediaItems()
                appliedSignature = null
            }
            return
        }

        val signature = tracks.map { it.uri }.hashCode()
        if (signature == appliedSignature) return

        // Reconnecting to a still-running service: the queue is already correct,
        // so adopt it instead of reloading and stuttering the current track.
        if (active.mediaItemCount == tracks.size &&
            tracks.indices.all { active.getMediaItemAt(it).mediaId == tracks[it].uri }
        ) {
            appliedSignature = signature
            readPlayerState()
            return
        }

        val playingUri = active.currentMediaItem?.mediaId
        val wasPlaying = active.playWhenReady

        var startIndex = 0
        var startPosition = 0L
        if (playingUri != null) {
            val moved = tracks.indexOfFirst { it.uri == playingUri }
            if (moved >= 0) {
                startIndex = moved
                startPosition = active.currentPosition.coerceAtLeast(0L)
            }
        } else {
            val bookmarked = library.savedTrackIndex
            if (bookmarked in tracks.indices) {
                startIndex = bookmarked
                startPosition = library.savedPositionMs.coerceAtLeast(0L)
            }
        }

        active.setMediaItems(tracks.map(::toMediaItem), startIndex, startPosition)
        active.prepare()
        if (wasPlaying) active.play()
        appliedSignature = signature
        readPlayerState()
    }

    private fun toMediaItem(track: Track): MediaItem {
        val subtitle = track.folder.ifEmpty { library.rootLabel.value ?: "" }
        return MediaItem.Builder()
            .setUri(track.uri.toUri())
            .setMediaId(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(subtitle)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }

    private fun readPlayerState() {
        val active = controller ?: return
        val duration = active.duration
        // Both are StateFlows, so an unchanged value is not re-emitted; only the
        // progress flow actually churns while a track plays.
        status.value = PlayerStatus(
            connected = true,
            isPlaying = active.isPlaying,
            currentIndex = if (active.mediaItemCount > 0) active.currentMediaItemIndex else -1,
        )
        _progress.value = Progress(
            positionMs = active.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration == C.TIME_UNSET) 0L else duration,
        )
    }

    /** Bookmarks the playback position so the next launch resumes where we left off. */
    private fun rememberProgress(force: Boolean = false) {
        val active = controller ?: return
        if (active.mediaItemCount == 0) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSavedAt < SAVE_INTERVAL_MS) return
        lastSavedAt = now
        library.saveProgress(active.currentMediaItemIndex, active.currentPosition)
    }

    override fun onCleared() {
        rememberProgress(force = true)
        controller?.let {
            it.removeListener(playerListener)
            it.release()
        }
        controller = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val POSITION_POLL_MS = 400L
        private const val SAVE_INTERVAL_MS = 3_000L
    }
}

private data class PlayerStatus(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentIndex: Int = -1,
)
