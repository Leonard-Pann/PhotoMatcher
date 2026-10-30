package com.bobby.vaudoushorter.ui

import com.bobby.vaudoushorter.R
import com.bobby.vaudoushorter.i18n.*

import android.graphics.Bitmap
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.bobby.vaudoushorter.data.GalleryMedia
import com.bobby.vaudoushorter.data.PhotoLoader
import com.bobby.vaudoushorter.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object Thumbnails {
    val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
    }
}

private data class Thumbnail(val bitmap: Bitmap? = null, val failed: Boolean = false)

/** Called on IO. Decode a bounded preview directly, without relying on the system thumbnail cache. */
private suspend fun loadSmallPreview(context: Context, media: GalleryMedia, size: Size, signal: CancellationSignal): Bitmap {
    val uri = Uri.parse(media.uri)
    return try {
        if (!media.isVideo) PhotoLoader.decode(context.contentResolver, uri, size)
        else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                requireNotNull(retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    size.width, size.height))
            } finally { retriever.release() }
        }
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        signal.throwIfCanceled()
        // MediaStore may support previews for a format the direct decoder cannot read.
        context.contentResolver.loadThumbnail(uri, size, signal)
    }
}

@Composable
fun MediaPreview(media: GalleryMedia, modifier: Modifier = Modifier, small: Boolean = false) {
    val text = LocalText.current
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // List thumbnails have a fixed decode size and do not depend on a layout callback.
    val requestSize = if (small) Size(160, 160)
        else if (viewport.width > 0 && viewport.height > 0) Size(viewport.width, viewport.height) else null
    val cacheKey = "${media.uri}:${if (small) "thumbnail" else "original"}:${requestSize?.width ?: 0}x${requestSize?.height ?: 0}"
    val thumbnail by produceState(Thumbnail(Thumbnails.cache.get(cacheKey)), cacheKey) {
        value = Thumbnail(Thumbnails.cache.get(cacheKey))
        if (value.bitmap == null && !inspection && requestSize != null) {
            // CancellationSignal is single-use and belongs to this producer invocation.
            // Disposal cancels only this load, never a later load of the same URI.
            val signal = CancellationSignal()
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { signal.cancel() }
            }
            try {
                value = withContext(Dispatchers.IO) {
                    val bitmap = if (small) loadSmallPreview(context, media, requestSize, signal)
                    else if (media.isVideo) context.contentResolver.loadThumbnail(Uri.parse(media.uri), requestSize, signal)
                    else PhotoLoader.decode(context.contentResolver, Uri.parse(media.uri), requestSize)
                    Thumbnails.cache.put(cacheKey, bitmap)
                    Thumbnail(bitmap)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                ensureActive() // A disposed request is not a failed image.
                value = Thumbnail(failed = true)
            } finally { cancellation.cancel() }
        }
    }
    Box(modifier.onSizeChanged { viewport = it }.background(if (small) Panel else Color.Black), contentAlignment = Alignment.Center) {
        val bitmap = thumbnail.bitmap
        when {
            bitmap != null -> Image(bitmap.asImageBitmap(), media.name, Modifier.fillMaxSize(),
                contentScale = if (small) ContentScale.Crop else ContentScale.Fit)
            thumbnail.failed || inspection -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Mark(if (media.isVideo) Glyph.PLAY else Glyph.PHOTO, Mint, Modifier.size(40.dp))
                if (!small) Text(if (inspection) text(R.string.preview_media) else text(R.string.preview_unavailable),
                    color = Muted, modifier = Modifier.padding(12.dp))
            }
            else -> CircularProgressIndicator(Modifier.size(if (small) 18.dp else 28.dp), color = Mint, strokeWidth = 2.dp)
        }
    }
}
