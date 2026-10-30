package com.bobby.vaudoushorter.data

import com.bobby.vaudoushorter.R
import com.bobby.vaudoushorter.i18n.*

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GalleryAccess(val photos: Boolean, val videos: Boolean, val limited: Boolean = false, val canDelete: Boolean = true) {
    val available get() = photos || videos || limited

    companion object {
        fun read(context: Context): GalleryAccess {
            fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
            return if (Build.VERSION.SDK_INT >= 33) GalleryAccess(
                photos = granted(Manifest.permission.READ_MEDIA_IMAGES),
                videos = granted(Manifest.permission.READ_MEDIA_VIDEO),
                limited = Build.VERSION.SDK_INT >= 34 &&
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
                    !(granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO))
            ) else granted(Manifest.permission.READ_EXTERNAL_STORAGE).let {
                GalleryAccess(it, it, canDelete = Build.VERSION.SDK_INT > 29 || granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }

        fun permissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            Build.VERSION.SDK_INT == 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

class GalleryRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val store = ReviewStore(context)

    suspend fun loadMedia(access: GalleryAccess): List<GalleryMedia> = withContext(Dispatchers.IO) {
        buildList {
            if (access.photos || access.limited) addAll(query(false))
            if (access.videos || access.limited) addAll(query(true))
        }
    }

    private fun query(video: Boolean): List<GalleryMedia> {
        val text = UiStrings(context)
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val columns = mutableListOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.VOLUME_NAME
        ).apply { if (video) add(MediaStore.Video.VideoColumns.DURATION) }
        val selection = "${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.SIZE} > 0" +
            if (Build.VERSION.SDK_INT >= 30) " AND ${MediaStore.MediaColumns.IS_TRASHED} = 0" else ""
        return buildList {
            resolver.query(collection, columns.toTypedArray(), selection, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val volume = cursor.getString(7) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                    val base = if (video) MediaStore.Video.Media.getContentUri(volume)
                        else MediaStore.Images.Media.getContentUri(volume)
                    val path = cursor.getString(4).orEmpty().trimEnd('/')
                    add(GalleryMedia(
                        uri = ContentUris.withAppendedId(base, cursor.getLong(0)).toString(),
                        name = cursor.getString(1) ?: text(R.string.untitled),
                        folderKey = "$volume:${cursor.getString(2)}:$path",
                        folderName = cursor.getString(3) ?: path.substringAfterLast('/').ifEmpty { text(R.string.no_folder) },
                        folderPath = "${if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) text(R.string.phone) else text(R.string.sd_card)} · $path",
                        isVideo = video, size = cursor.getLong(5), dateAdded = cursor.getLong(6),
                        duration = if (video) cursor.getLong(8) else 0
                    ))
                }
            } ?: error(text(R.string.gallery_unavailable))
        }
    }

    suspend fun loadReview(): StoredReview = withContext(Dispatchers.IO) { store.read() }
    fun saveChoices(save: ReviewSave) { store.save(save) }
}
