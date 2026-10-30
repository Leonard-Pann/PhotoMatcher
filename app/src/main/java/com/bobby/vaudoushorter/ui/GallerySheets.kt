package com.bobby.vaudoushorter.ui

import com.bobby.vaudoushorter.R
import com.bobby.vaudoushorter.i18n.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobby.vaudoushorter.GalleryUiState
import com.bobby.vaudoushorter.data.*
import com.bobby.vaudoushorter.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSheet(state: GalleryUiState, onSelect: (String?) -> Unit, onPermissions: () -> Unit, onRefresh: () -> Unit, onClose: () -> Unit) {
    val text = LocalText.current
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).stableSheetScroll().testTag("folder-sheet").padding(horizontal = 24.dp)) {
            Text(text(R.string.folders_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text(R.string.folders_description), color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            LazyColumn(Modifier.weight(1f, fill = false).testTag("folder-list")) {
                item {
                    FolderRow(text(R.string.whole_gallery), text(R.string.whole_gallery_description), state.review.folders.sumOf { it.count },
                        state.folder == null) { onSelect(null); onClose() }
                }
                items(state.review.folders, key = { it.key }) { folder ->
                    FolderRow(folder.name, folder.path, folder.count, state.folder == folder.key) { onSelect(folder.key); onClose() }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Muted.copy(alpha = .15f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPermissions) { Text(text(R.string.manage_access)) }
                TextButton(onClick = { onRefresh(); onClose() }) {
                    Mark(Glyph.REFRESH, Mint, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text(text(R.string.refresh))
                }
            }
            Text(text(R.string.privacy),
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        }
    }
}

@Composable
private fun FolderRow(title: String, subtitle: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(if (selected) Mint.copy(alpha = .1f) else Panel)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Mark(Glyph.FOLDER, if (selected) Mint else Muted)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = if (selected) Mint else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(count.toString(), color = Muted, fontSize = 12.sp)
        RadioButton(selected, onClick = null, modifier = Modifier.padding(start = 8.dp).size(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashSheet(state: GalleryUiState, onRestore: (String) -> Unit, onRestoreAll: () -> Unit, onExit: () -> Unit,
    onRequestAccess: () -> Unit, onClose: () -> Unit) {
    val text = LocalText.current
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val busy by rememberUpdatedState(state.deleting || state.exiting)
    val trash = state.review.trash
    val history = state.deletedHistory.asReversed()
    ModalBottomSheet(onDismissRequest = { if (!busy) onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { !busy || it != SheetValue.Hidden }), containerColor = Panel) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).stableSheetScroll().testTag("trash-sheet").padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text(R.string.deletions), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = text(R.string.close_deletions) }) { Mark(Glyph.CLOSE, Muted) }
            }
            TabRow(selectedTabIndex = tab, containerColor = Panel, modifier = Modifier.padding(vertical = 12.dp)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(text(R.string.pending_count, trash.size), fontSize = 12.sp) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(text(R.string.deleted_count, history.size), fontSize = 12.sp) })
            }
            if (tab == 0) {
                Text(text(R.string.pending_description),
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                if (android.os.Build.VERSION.SDK_INT >= 30) Text(text(R.string.confirmation_description),
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                if (trash.isEmpty()) Text(text(R.string.no_pending), color = Mint, modifier = Modifier.padding(vertical = 24.dp))
                else LazyColumn(Modifier.weight(1f, fill = false).testTag("pending-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(trash, key = { it.uri }) { media ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MediaPreview(media, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), small = true)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(media.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text("${if (media.isVideo) text(R.string.video) else text(R.string.photo)} · ${media.folderName}", color = Muted,
                                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onRestore(media.uri) }, enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = text(R.string.restore_media, media.name) }) { Mark(Glyph.UNDO, Mint) }
                        }
                    }
                }
                if (busy) {
                    LinearProgressIndicator(progress = { state.deleteProgress.toFloat() / state.deleteTotal.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp), color = Coral)
                    Text(text(R.string.apply_progress, state.deleteProgress, state.deleteTotal), color = Muted,
                        modifier = Modifier.padding(vertical = 12.dp))
                } else if (trash.isNotEmpty()) {
                    TextButton(onClick = onRestoreAll, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(text(R.string.restore_all)) }
                    Button(onClick = if (state.access.canDelete) onExit else onRequestAccess, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp)) {
                        Text(if (state.access.canDelete) text(R.string.apply_exit) else text(R.string.allow_delete), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(text(R.string.history_description),
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                if (history.isEmpty()) Text(text(R.string.no_history), color = Muted, modifier = Modifier.padding(vertical = 24.dp))
                else LazyColumn(Modifier.weight(1f, fill = false).testTag("history-list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(history, key = { it.media.uri }) { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Mark(if (entry.media.isVideo) Glyph.PLAY else Glyph.PHOTO, Muted, Modifier.size(24.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(entry.media.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text("${if (entry.media.isVideo) text(R.string.video) else text(R.string.photo)} · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, text.locale).format(java.util.Date(entry.deletedAt))}",
                                    color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
