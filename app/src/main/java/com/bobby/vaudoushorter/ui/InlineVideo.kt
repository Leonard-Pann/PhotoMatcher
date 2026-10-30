package com.bobby.vaudoushorter.ui

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.bobby.vaudoushorter.data.GalleryMedia
import com.bobby.vaudoushorter.ui.theme.Ink

/** TextureView follows the card's transforms on Android 10, including during a swipe. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun InlineVideo(media: GalleryMedia, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(media.uri) { ExoPlayer.Builder(context).build() }
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var pausedByUser by remember(media.uri) { mutableStateOf(false) }
    var rendered by remember(media.uri) { mutableStateOf(false) }
    var failed by remember(media.uri) { mutableStateOf(false) }
    var playing by remember(media.uri) { mutableStateOf(false) }
    var aspect by remember(media.uri) { mutableFloatStateOf(1f) }
    var stoppedForBackground by remember(media.uri) { mutableStateOf(false) }
    var resumePosition by remember(media.uri) { mutableLongStateOf(0L) }

    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            // Pause immediately, even before Compose processes the lifecycle change.
            if (!foreground) player.pause()
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !stoppedForBackground) {
                resumePosition = player.currentPosition
                player.stop() // Close file handles before exit-time deletion frees storage.
                stoppedForBackground = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failed = true }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
        }
        player.addListener(listener)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)
        player.setMediaItem(MediaItem.fromUri(media.uri))
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, active, foreground, pausedByUser, stoppedForBackground) {
        if (active && foreground && stoppedForBackground) {
            player.prepare()
            player.seekTo(resumePosition)
            stoppedForBackground = false
        }
        player.playWhenReady = active && foreground && !pausedByUser
    }
    BoxWithConstraints(modifier.background(Color.Black).semantics {
        contentDescription = "Vidéo : ${media.name}"
        stateDescription = when {
            failed -> "Lecture indisponible"
            playing && rendered -> "Lecture en cours"
            rendered -> "En pause"
            else -> "Chargement"
        }
    }.clickable(enabled = active && !failed, onClickLabel = if (pausedByUser) "Reprendre la vidéo" else "Mettre la vidéo en pause") {
        pausedByUser = !pausedByUser
    }) {
        // Only show the thumbnail until a real video frame has arrived.
        if (!rendered || failed) MediaPreview(media, Modifier.fillMaxSize())
        if (!failed) {
            val fitted = if (maxWidth / maxHeight > aspect)
                Modifier.fillMaxHeight().aspectRatio(aspect, matchHeightConstraintsFirst = true)
            else Modifier.fillMaxWidth().aspectRatio(aspect)
            key(player) {
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = fitted.align(Alignment.Center),
                    factory = { viewContext -> TextureView(viewContext).also {
                        it.isOpaque = false
                        player.setVideoTextureView(it)
                    } },
                    update = { it.keepScreenOn = playing && active && foreground }
                )
            }
        }
        if (failed) Text("Lecture indisponible", color = Color.White,
            modifier = Modifier.align(Alignment.Center).background(Ink.copy(alpha = .85f)).padding(12.dp))
        else if (pausedByUser && active) Mark(Glyph.PLAY, Color.White,
            Modifier.align(Alignment.Center).size(40.dp))
    }
}
