package com.bobby.vaudoushorter

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log

/** Manual QA helper, packaged ONLY in the test APK, running under a different UID.
 * Creates an actual foreign-owned MediaStore image to exercise Android 10 consent.
 * Unlike adb push, MediaStore creates this fixture with normal gallery file ownership.
 */
class ForeignMediaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Vaudou-consent-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VaudouConsent/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        // The test APK normally obtains Kotlin's runtime from the instrumented app.
        // Keep this standalone helper on Android/Java APIs so it can run under its own UID.
        val collection: Uri? = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        if (collection == null) { finish(); return }
        val uri: Uri? = contentResolver.insert(collection, values)
        if (uri == null) { finish(); return }
        val bitmap: Bitmap? = Bitmap.createBitmap(2400, 3300, Bitmap.Config.ARGB_8888)
        if (bitmap == null) { finish(); return }
        val canvas = Canvas(bitmap)
        canvas.scale(3f, 3f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 800f, 1100f, Color.rgb(88, 100, 145), Color.rgb(168, 231, 189), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 800f, 1100f, paint)
        paint.shader = null
        paint.color = Color.rgb(255, 225, 170)
        canvas.drawCircle(540f, 350f, 100f, paint)
        paint.color = Color.rgb(55, 83, 105)
        canvas.drawOval(-150f, 650f, 950f, 1400f, paint)
        val stream = contentResolver.openOutputStream(uri)
        if (stream != null) {
            try { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream) }
            finally { stream.close() }
        }
        bitmap.recycle()
        val published = ContentValues()
        published.put(MediaStore.Images.Media.IS_PENDING, 0)
        contentResolver.update(uri, published, null, null)
        Log.i("VaudouFixture", "Foreign test image: $uri")
        finish()
    }
}
