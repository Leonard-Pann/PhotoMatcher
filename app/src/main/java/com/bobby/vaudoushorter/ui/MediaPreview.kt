package com.bobby.vaudoushorter.ui

import android.graphics.Bitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object Thumbnails {
    val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
    }
}

private data class Thumbnail(val bitmap: Bitmap? = null, val failed: Boolean = false)

@Composable
fun MediaPreview(media: GalleryMedia, modifier: Modifier = Modifier, small: Boolean = false) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val cacheKey = "${media.uri}:${if (small) "thumbnail" else "original"}:${viewport.width}x${viewport.height}"
    val signal = remember(cacheKey) { CancellationSignal() }
    DisposableEffect(signal) { onDispose { signal.cancel() } }
    val thumbnail by produceState(Thumbnail(Thumbnails.cache.get(cacheKey)), cacheKey) {
        value = Thumbnail(Thumbnails.cache.get(cacheKey))
        if (value.bitmap == null && !inspection && viewport.width > 0 && viewport.height > 0) {
            value = try {
                withContext(Dispatchers.IO) {
                    val bitmap = if (small || media.isVideo) context.contentResolver.loadThumbnail(Uri.parse(media.uri),
                        if (small) Size(160, 160) else Size(viewport.width, viewport.height), signal)
                    else PhotoLoader.decode(context.contentResolver, Uri.parse(media.uri), Size(viewport.width, viewport.height))
                    Thumbnails.cache.put(cacheKey, bitmap)
                    Thumbnail(bitmap)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Thumbnail(failed = true) }
        }
    }
    Box(modifier.onSizeChanged { viewport = it }.background(if (small) Panel else Color.Black), contentAlignment = Alignment.Center) {
        val bitmap = thumbnail.bitmap
        when {
            bitmap != null -> Image(bitmap.asImageBitmap(), media.name, Modifier.fillMaxSize(),
                contentScale = if (small) ContentScale.Crop else ContentScale.Fit)
            thumbnail.failed || inspection -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Mark(if (media.isVideo) Glyph.PLAY else Glyph.PHOTO, Lavender, Modifier.size(40.dp))
                if (!small) Text(if (inspection) "Votre média" else "Aperçu indisponible",
                    color = Muted, modifier = Modifier.padding(12.dp))
            }
            else -> CircularProgressIndicator(Modifier.size(if (small) 18.dp else 28.dp), color = Lavender, strokeWidth = 2.dp)
        }
    }
}
