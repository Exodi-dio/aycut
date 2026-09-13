package com.exodi.aycut.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.exodi.aycut.core.media.MediaAsset
import com.exodi.aycut.core.model.MediaId
import java.io.File
import java.util.UUID

/**
 * Imports user-selected media (SAF / ACTION_GET_CONTENT — no storage
 * permissions needed) into app-private storage, reading metadata and
 * producing thumbnail frames. All Android-breaking APIs are confined here.
 */
class AppMediaLibrary(
    private val appContext: Context,
    private val mediaDir: File,
) {

    enum class RetrieverMode {
        FILE, URI,
    }

    @Volatile
    private var mode: RetrieverMode = RetrieverMode.FILE

    fun importFromUri(uri: Uri): MediaAsset {
        // Source filename first (for display + a stable-ish local name).
        val sourceName = queryDisplayName(uri) ?: "import-${System.currentTimeMillis()}"
        val id = MediaId(UUID.randomUUID().toString())
        val file = File(mediaDir, "${id.raw}.${extensionOf(sourceName)}")

        // SAF gives a streaming read; copy the bytes so decode/thumbnail
        // never depends on the picker's URI outliving the session.
        appContext.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "cannot open $uri" }
            file.outputStream().use { output -> input.copyTo(output) }
        }

        val retriever = MediaMetadataRetriever()
        try {
            when (mode) {
                RetrieverMode.FILE -> retriever.setDataSource(file.absolutePath)
                RetrieverMode.URI -> retriever.setDataSource(appContext, uri)
            }
            return MediaAsset(
                id = id,
                displayName = sourceName,
                mimeType = appContext.contentResolver.getType(uri),
                durationMicros = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_DURATION),
                width = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt(),
                height = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt(),
                rotationDegrees = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt(),
            )
        } finally {
            retriever.release()
        }
    }

    /** Frame at [atMicros] (microseconds), closest sync frame, or null. */
    fun thumbnail(asset: MediaAsset, atMicros: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            when (mode) {
                RetrieverMode.FILE -> {
                    val file = File(mediaDir, fileFor(asset))
                    retriever.setDataSource(file.absolutePath)
                }
                RetrieverMode.URI -> retriever.setDataSource(appContext, assetUri(asset))
            }
            retriever.getFrameAtTime(atMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.metadataLong(key: Int): Long =
        extractMetadata(key)?.toLongOrNull() ?: 0L

    private fun queryDisplayName(uri: Uri): String? {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val raw = cursor.getString(idx)
                    if (!raw.isNullOrBlank()) return raw
                }
            }
        }
        return null
    }

    /** Stored file name for an asset (matches [importFromUri] storage). */
    internal fun fileFor(asset: MediaAsset): String =
        "${asset.id.raw}.${extensionOf(asset.displayName)}"

    private fun assetUri(asset: MediaAsset): Uri =
        Uri.fromFile(File(mediaDir, fileFor(asset)))

    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot in 0 until name.length - 1) name.substring(dot + 1).lowercase() else "dat"
    }
}