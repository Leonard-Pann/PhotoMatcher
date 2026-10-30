package com.bobby.vaudoushorter

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bobby.vaudoushorter.data.*
import com.bobby.vaudoushorter.i18n.UiStrings
import com.bobby.vaudoushorter.ui.MediaPreview
import com.bobby.vaudoushorter.ui.Thumbnails
import com.bobby.vaudoushorter.ui.TrashSheet
import com.bobby.vaudoushorter.ui.theme.VaudouShorterTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MediaPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val fixtures = mutableListOf<GalleryMedia>()

    @Before fun createMedia() {
        val photo = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        try {
            insert(false) { output -> photo.compress(Bitmap.CompressFormat.PNG, 100, output) }
        } finally { photo.recycle() }
        val clip = createVideoFixture(context.cacheDir)
        try { insert(true) { output -> clip.inputStream().use { it.copyTo(output) } } }
        finally { clip.delete() }
        Thumbnails.cache.evictAll()
    }

    private fun insert(video: Boolean, write: (java.io.OutputStream) -> Unit) {
        val name = "preview-${System.nanoTime()}.${if (video) "mp4" else "png"}"
        val uri = requireNotNull(resolver.insert(
            if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/PreviewTests" else "Pictures/PreviewTests")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
        fixtures.add(GalleryMedia(uri.toString(), name, "fixture", "PreviewTests", "PreviewTests", video, 1, 1))
        resolver.openOutputStream(uri)!!.use(write)
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    @After fun removeMedia() {
        fixtures.forEach { resolver.delete(Uri.parse(it.uri), null, null) }
        Thumbnails.cache.evictAll()
    }

    private fun waitForImage(media: GalleryMedia) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(media.name).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(media.name).assertIsDisplayed()
    }

    private fun checkReusableRow(video: Boolean) {
        val media = fixtures.first { it.isVideo == video }
        val active = mutableStateOf(true)
        compose.setContent {
            VaudouShorterTheme {
                ReusableContentHost(active.value) {
                    MediaPreview(media, Modifier.size(56.dp), small = true)
                }
            }
        }
        waitForImage(media)
        repeat(3) {
            compose.runOnIdle { active.value = false }
            compose.waitForIdle()
            // Full-size gallery photos can evict tiny previews from the shared bounded cache.
            // Force a real reload of the SAME reusable row rather than just testing a cache hit.
            compose.runOnIdle { Thumbnails.cache.evictAll(); active.value = true }
            waitForImage(media)
        }
    }

    @Test fun photoThumbnailReloadsAfterReusableRowDeactivationAndCacheEviction() = checkReusableRow(false)
    @Test fun videoThumbnailReloadsAfterReusableRowDeactivationAndCacheEviction() = checkReusableRow(true)

    @Test fun originalsProvidePhotoAndVideoPreviewsWhenSystemThumbnailServiceCannotHandleTheUri() {
        // file:// has no thumbnail ContentProvider. The original remains readable by this app.
        val photo = java.io.File(context.cacheDir, "fallback-photo.png")
        val bitmap = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val video = createVideoFixture(context.cacheDir)
        val items = listOf(photo, video).mapIndexed { index, file ->
            GalleryMedia(Uri.fromFile(file).toString(), file.name, "fallback", "Fallback", "Fallback", index == 1, 1, 1)
        }
        try {
            compose.setContent {
                VaudouShorterTheme {
                    Column { items.forEach { MediaPreview(it, Modifier.size(56.dp), small = true) } }
                }
            }
            items.forEach(::waitForImage)
        } finally { photo.delete(); video.delete() }
    }

    @Test fun bothThumbnailsSurviveTabChangesAndReopeningTrash() {
        val open = mutableStateOf(true)
        val text = UiStrings(context)
        val state = GalleryUiState(loading = false, access = GalleryAccess(true, true),
            review = ReviewSnapshot(trash = fixtures.toList()))
        compose.setContent {
            VaudouShorterTheme {
                if (open.value) TrashSheet(state, {}, {}, {}, {}, { open.value = false })
            }
        }
        fixtures.forEach(::waitForImage)
        repeat(3) {
            compose.onNodeWithText(text(R.string.deleted_count, 0)).performClick()
            compose.runOnIdle { Thumbnails.cache.evictAll() }
            compose.onNodeWithText(text(R.string.pending_count, 2)).performClick()
            fixtures.forEach(::waitForImage)
        }
        compose.onNodeWithContentDescription(text(R.string.close_deletions)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { Thumbnails.cache.evictAll(); open.value = true }
        fixtures.forEach(::waitForImage)
    }
}
