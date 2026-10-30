package com.bobby.vaudoushorter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.util.Size
import androidx.test.platform.app.InstrumentationRegistry
import com.bobby.vaudoushorter.data.PhotoLoader
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoLoaderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun originalIsDecodedAtScreenResolutionAndPreservesFineDetails() {
        val file = File(context.cacheDir, "photo-resolution.png")
        val original = Bitmap.createBitmap(2400, 3600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(original)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        for (x in 0 until 2400 step 16) canvas.drawRect(x.toFloat(), 0f, x + 8f, 3600f, paint)
        file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        original.recycle()
        try {
            val decoded = PhotoLoader.decode(context.contentResolver, Uri.fromFile(file), Size(1200, 1800))
            try {
                assertEquals(1200, decoded.width)
                assertEquals(1800, decoded.height)
                assertTrue(Color.red(decoded.getPixel(1, 400)) < 30)
                assertTrue(Color.red(decoded.getPixel(6, 400)) > 225)
            } finally { decoded.recycle() }
        } finally { file.delete() }
    }

    @Test fun smallOriginalIsNeverArtificiallyUpscaled() {
        val file = File(context.cacheDir, "photo-small.png")
        val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val decoded = PhotoLoader.decode(context.contentResolver, Uri.fromFile(file), Size(1080, 1920))
            assertEquals(80, decoded.width)
            assertEquals(120, decoded.height)
            decoded.recycle()
        } finally { file.delete() }
    }

    @Test fun originalExifRotationIsRespected() {
        val file = File(context.cacheDir, "photo-rotation.jpg")
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        try {
            val decoded = PhotoLoader.decode(context.contentResolver, Uri.fromFile(file), Size(300, 300))
            assertEquals(80, decoded.width)
            assertEquals(120, decoded.height)
            decoded.recycle()
        } finally { file.delete() }
    }
}
