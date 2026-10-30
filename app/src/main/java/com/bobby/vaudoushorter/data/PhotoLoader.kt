package com.bobby.vaudoushorter.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import kotlin.math.ceil
import kotlin.math.min

/** Decode the original file, not MediaStore's potentially 512px thumbnail. */
object PhotoLoader {
    fun decode(resolver: ContentResolver, uri: Uri, viewport: Size): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val target = fittedSize(info.size, viewport)
            decoder.setTargetSize(target.width, target.height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    internal fun fittedSize(original: Size, viewport: Size): Size {
        val scale = min(1.0, min(viewport.width.coerceAtLeast(1).toDouble() / original.width,
            viewport.height.coerceAtLeast(1).toDouble() / original.height))
        return Size(ceil(original.width * scale).toInt().coerceAtLeast(1),
            ceil(original.height * scale).toInt().coerceAtLeast(1))
    }
}
