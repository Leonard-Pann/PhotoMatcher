package com.bobby.vaudoushorter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobby.vaudoushorter.GalleryUiState
import com.bobby.vaudoushorter.GalleryViewModel
import com.bobby.vaudoushorter.R
import com.bobby.vaudoushorter.data.*
import com.bobby.vaudoushorter.ui.theme.*

@Composable
fun GalleryScreen(state: GalleryUiState, model: GalleryViewModel, requestAccess: () -> Unit, openSettings: () -> Unit,
    onExit: () -> Unit) {
    var foldersOpen by rememberSaveable { mutableStateOf(false) }
    var trashOpen by rememberSaveable { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf<SwipeCommand?>(null) }
    var commandId by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val ready = !state.loading && !state.deleting && !state.exiting && state.error == null
    val manageAccess = {
        if (state.access.limited || !state.access.photos || !state.access.videos) requestAccess() else openSettings()
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { message ->
            snackbar.showSnackbar(message)
            model.clearNotice()
        }
    }
    LaunchedEffect(state.deleting) { if (state.deleting) trashOpen = true }

    GalleryLayout(state, ready && !busy, ready && state.review.canUndo && !busy,
        optionsEnabled = !state.deleting && !state.exiting && !busy,
        optionsOpen = optionsOpen,
        onOptions = { optionsOpen = it },
        onFolders = { foldersOpen = true },
        onTrash = { trashOpen = true },
        onPermissions = manageAccess,
        onRefresh = model::refresh,
        onFilter = model::selectFilter,
        onUndo = { command = null; model.undo() },
        onDecision = { decision ->
            state.review.current?.let { command = SwipeCommand(++commandId, it.uri, decision) }
        }, onExit = onExit, snackbar = snackbar
    ) {
        Stage(state, command, playbackActive = ready && !optionsOpen && !foldersOpen && !trashOpen,
            onDecision = { uri, decision -> command = null; model.choose(uri, decision) },
            onDecisionStarted = model::stageChoice,
            onBusy = { busy = it }, requestAccess = requestAccess, openSettings = openSettings,
            onRefresh = model::refresh, openTrash = { trashOpen = true }, openFolders = { foldersOpen = true })
    }
    if (foldersOpen) FolderSheet(state, model::selectFolder, manageAccess, model::refresh) { foldersOpen = false }
    if (trashOpen) TrashSheet(state, model::restore, model::restoreAll, onExit, requestAccess) { trashOpen = false }
}

/** One layout for portrait and landscape: the media always receives all remaining space. */
@Composable
internal fun GalleryLayout(
    state: GalleryUiState,
    canChoose: Boolean,
    canUndo: Boolean,
    optionsEnabled: Boolean,
    optionsOpen: Boolean,
    onOptions: (Boolean) -> Unit,
    onFolders: () -> Unit,
    onTrash: () -> Unit,
    onPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (MediaFilter) -> Unit,
    onUndo: () -> Unit,
    onDecision: (Decision) -> Unit,
    onExit: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    mediaContent: @Composable () -> Unit
) {
    Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Header(state, optionsEnabled, optionsOpen, onOptions, onFolders, onTrash, onPermissions, onRefresh, onExit)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaFilter.entries.forEach { filter ->
                    FilterChip(selected = state.filter == filter, onClick = { onFilter(filter) }, enabled = canChoose,
                        label = { Text(filter.label, fontSize = 13.sp) }, shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lavender.copy(alpha = .16f),
                            selectedLabelColor = Lavender))
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(Color.Black).testTag("media-stage")) {
                val compact = maxHeight < 360.dp
                mediaContent()
                if (state.access.available && !state.loading && state.error == null && state.review.total > 0) {
                    ReviewCounters(state.review, Modifier.align(Alignment.TopCenter))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f))))
                        .padding(top = 20.dp, bottom = if (compact) 12.dp else 24.dp)) {
                        DecisionButtons(enabled = canChoose && state.review.current != null, canUndo = canUndo,
                            compact = compact, onUndo = onUndo, onDecision = onDecision)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: GalleryUiState, enabled: Boolean, menuOpen: Boolean, onMenu: (Boolean) -> Unit,
    onFolders: () -> Unit, onTrash: () -> Unit, onPermissions: () -> Unit, onRefresh: () -> Unit, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(Lavender), contentAlignment = Alignment.Center) {
            Mark(Glyph.SPARK, Ink, Modifier.size(21.dp))
        }
        Text(stringResource(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.7).sp,
            modifier = Modifier.weight(1f).padding(start = 10.dp))
        Box {
            IconButton(onClick = { onMenu(true) }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Options" }) { Mark(Glyph.MORE, Muted) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                DropdownMenuItem(text = { Text("Choisir un dossier") }, leadingIcon = { Mark(Glyph.FOLDER, Lavender) },
                    onClick = { onMenu(false); onFolders() })
                DropdownMenuItem(text = { Text("À supprimer (${state.review.trash.size})") }, leadingIcon = { Mark(Glyph.TRASH, Coral) },
                    onClick = { onMenu(false); onTrash() })
                DropdownMenuItem(text = { Text("Gérer l’accès aux médias") }, leadingIcon = { Mark(Glyph.SHIELD, Mint) },
                    onClick = { onMenu(false); onPermissions() })
                DropdownMenuItem(text = { Text("Actualiser") }, leadingIcon = { Mark(Glyph.REFRESH, Muted) },
                    onClick = { onMenu(false); onRefresh() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Appliquer et quitter") }, leadingIcon = { Mark(Glyph.ARROW, Mint) },
                    onClick = { onMenu(false); onExit() })
            }
        }
    }
}

@Composable
private fun ReviewCounters(review: ReviewSnapshot, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent)))
        .padding(top = 12.dp, bottom = 24.dp, start = 12.dp, end = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Counter(review.kept, "Gardés", Mint, Modifier.weight(1f))
        Counter(review.discarded, "À supprimer", Coral, Modifier.weight(1f))
        Counter(review.remaining, "Restants", Color.White, Modifier.weight(1f))
    }
}

@Composable
private fun Counter(count: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, lineHeight = 16.sp, color = Color.White.copy(alpha = .8f))
    }
}

@Composable
private fun Stage(state: GalleryUiState, command: SwipeCommand?, playbackActive: Boolean,
    onDecision: (String, Decision) -> Unit, onDecisionStarted: (String, Decision) -> Unit,
    onBusy: (Boolean) -> Unit, requestAccess: () -> Unit,
    openSettings: () -> Unit, onRefresh: () -> Unit, openTrash: () -> Unit, openFolders: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            !state.access.available && !state.loading -> MessageCard(Glyph.SHIELD, "Ouvrez votre galerie",
                "Autorisez l’accès aux photos et vidéos pour commencer.", "Autoriser l’accès", requestAccess,
                "Ouvrir les paramètres", openSettings)
            state.loading -> CircularProgressIndicator(color = Lavender, strokeWidth = 2.dp)
            state.error != null -> MessageCard(Glyph.REFRESH, "Une petite pause", state.error, "Réessayer", onRefresh)
            state.review.current != null -> ReviewDeck(state.review, !state.deleting && !state.exiting, command, onDecision, onBusy,
                playbackActive = playbackActive, modifier = Modifier.fillMaxSize(), onDecisionStarted = onDecisionStarted)
            state.review.total == 0 -> MessageCard(Glyph.PHOTO, "Rien par ici", "Aucun média accessible dans cette sélection.",
                "Choisir un dossier", openFolders, if (state.access.limited) "Modifier l’accès" else "Actualiser",
                if (state.access.limited) requestAccess else onRefresh)
            else -> Box(Modifier.fillMaxSize().padding(top = 68.dp, bottom = 100.dp)) {
                MessageCard(Glyph.SPARK, "Tout est trié !", "Aucun média déjà trié ne vous sera proposé à nouveau.",
                    if (state.review.trash.isNotEmpty()) "Revoir la corbeille" else "Choisir un autre dossier",
                    if (state.review.trash.isNotEmpty()) openTrash else openFolders)
            }
        }
    }
}

@Composable
private fun MessageCard(glyph: Glyph, title: String, message: String, action: String, onAction: () -> Unit,
    secondary: String? = null, onSecondary: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Mark(glyph, Lavender, Modifier.size(32.dp))
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        Text(message, color = Muted, textAlign = TextAlign.Center, fontSize = 13.sp)
        Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp), shape = CircleShape) { Text(action) }
        if (secondary != null) TextButton(onClick = onSecondary) { Text(secondary, color = Lavender) }
    }
}
