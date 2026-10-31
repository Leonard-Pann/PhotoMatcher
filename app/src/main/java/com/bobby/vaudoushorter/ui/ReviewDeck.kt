package com.bobby.vaudoushorter.ui

import com.bobby.vaudoushorter.R
import com.bobby.vaudoushorter.i18n.*

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobby.vaudoushorter.data.*
import com.bobby.vaudoushorter.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SwipeCommand(val id: Int, val uri: String, val decision: Decision)

@Composable
fun ReviewDeck(
    review: ReviewSnapshot,
    enabled: Boolean,
    command: SwipeCommand?,
    onDecision: (String, Decision) -> Unit,
    onBusy: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    playbackActive: Boolean = true,
    onDecisionStarted: (String, Decision) -> Unit = { _, _ -> }
) {
    val current = review.current ?: return
    Box(modifier.clipToBounds()) {
        // Preload only a still image: the next video must never play in the background.
        if (review.next != null) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                MediaPreview(review.next, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .45f)))
            }
        }
        key(current.uri) {
            SwipeCard(current, enabled, command, onDecision, onBusy, playbackActive, onDecisionStarted)
        }
    }
}

@Composable
private fun SwipeCard(
    media: GalleryMedia,
    enabled: Boolean,
    command: SwipeCommand?,
    onDecision: (String, Decision) -> Unit,
    onBusy: (Boolean) -> Unit,
    playbackActive: Boolean,
    onDecisionStarted: (String, Decision) -> Unit
) {
    val text = LocalText.current
    var target by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(1000f) }
    val x by animateFloatAsState(target,
        animationSpec = when { dragging -> snap(); leaving -> tween(230, easing = FastOutLinearInEasing)
            else -> spring(dampingRatio = .7f, stiffness = 450f) }, label = "card position")
    val entrance = remember { Animatable(.98f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = .65f)) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val decisionHandler by rememberUpdatedState(onDecision)
    val startedHandler by rememberUpdatedState(onDecisionStarted)
    val busyHandler by rememberUpdatedState(onBusy)
    val canAct by rememberUpdatedState(enabled)
    fun decide(decision: Decision) {
        if (leaving || !canAct) return
        leaving = true
        dragging = false
        startedHandler(media.uri, decision)
        busyHandler(true)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        target = width * 1.65f * if (decision == Decision.KEEP) 1 else -1
        scope.launch {
            delay(250)
            decisionHandler(media.uri, decision)
            busyHandler(false)
        }
    }
    DisposableEffect(Unit) { onDispose { busyHandler(false) } }
    LaunchedEffect(command?.id) { command?.takeIf { it.uri == media.uri }?.let { decide(it.decision) } }
    Box(Modifier.fillMaxSize()
        .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
        .graphicsLayer {
            translationX = x
            translationY = abs(x) * .045f
            rotationZ = (x / width) * 13f
            scaleX = entrance.value; scaleY = entrance.value
        }
        .pointerInput(media.uri) {
            detectHorizontalDragGestures(
                onDragStart = { if (canAct && !leaving) { dragging = true; busyHandler(true) } },
                onHorizontalDrag = { change, amount ->
                    if (canAct && !leaving) { change.consume(); target += amount }
                },
                onDragCancel = { if (!leaving) { dragging = false; target = 0f; busyHandler(false) } },
                onDragEnd = {
                    if (!leaving) {
                        dragging = false
                        if (abs(target) > width * .23f && canAct) decide(if (target > 0) Decision.KEEP else Decision.TRASH)
                        else { target = 0f; busyHandler(false) }
                    }
                }
            )
        }
        .clipToBounds().background(Color.Black)) {
        if (media.isVideo) InlineVideo(media, playbackActive && !leaving && !dragging, Modifier.fillMaxSize())
        else MediaPreview(media, Modifier.fillMaxSize())
        if (abs(x) > width * .06f) {
            val keep = x > 0
            val color = if (keep) Mint else Coral
            Box(Modifier.fillMaxSize().background(color.copy(alpha = (abs(x) / width * .25f).coerceIn(0f, .18f))))
            Text(if (keep) text(R.string.stamp_keep) else text(R.string.stamp_delete), color = color, fontWeight = FontWeight.Black,
                fontSize = 28.sp, modifier = Modifier.align(if (keep) Alignment.TopStart else Alignment.TopEnd)
                    .padding(top = 78.dp, start = 24.dp, end = 24.dp).graphicsLayer { rotationZ = if (keep) -12f else 12f }
                    .border(3.dp, color, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(12.dp))
        }
    }
}

@Composable
fun DecisionButtons(enabled: Boolean, canUndo: Boolean, compact: Boolean, onUndo: () -> Unit, onDecision: (Decision) -> Unit) {
    val text = LocalText.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        DecisionButton(text(R.string.delete), Glyph.TRASH, Coral, enabled, if (compact) 56 else 72) { onDecision(Decision.TRASH) }
        DecisionButton(text(R.string.undo), Glyph.UNDO, Honey, canUndo, if (compact) 48 else 56, onUndo)
        DecisionButton(text(R.string.keep), Glyph.ARROW, Mint, enabled, if (compact) 56 else 72) { onDecision(Decision.KEEP) }
    }
}

@Composable
private fun DecisionButton(label: String, glyph: Glyph, color: Color, enabled: Boolean, size: Int, action: () -> Unit) {
        FilledIconButton(onClick = action, enabled = enabled,
            modifier = Modifier.size(size.dp).semantics { contentDescription = label },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Ink,
                disabledContainerColor = color.copy(alpha = .25f), disabledContentColor = color.copy(alpha = .4f))) {
            Mark(glyph, if (enabled) Ink else color.copy(alpha = .4f), Modifier.size(if (size <= 56) 24.dp else 28.dp))
        }
}
