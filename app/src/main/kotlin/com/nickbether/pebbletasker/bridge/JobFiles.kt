package com.nickbether.pebbletasker.bridge

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copies a job result into shared storage so Tasker and other apps can open it. The host's content://
 * URI is readable only by this package and is deleted after an hour, so results are copied at once.
 *
 * Android 10+: MediaStore (Pictures/<folder> or Download/<folder>), no storage permission needed.
 * Android 8-9: this app's external files directory, readable by apps holding storage permission.
 */
object JobFiles {
    data class Saved(val path: String, val uri: String)

    enum class Kind(val prefix: String, val defaultExt: String) { SCREENSHOT("pebble_screenshot", "png"), LOGS("pebble_logs", "txt") }

    /** Folder names only: one or more plain path segments below Pictures/ or Download/. */
    fun sanitizeFolder(folder: String?): String {
        val parts = folder.orEmpty().replace('\\', '/').split('/')
            .map { it.trim().replace(Regex("[^A-Za-z0-9 ._-]"), "_") }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return parts.joinToString("/").ifEmpty { "PebbleTasker" }
    }

    fun extensionFor(mime: String?, kind: Kind): String = when (mime?.lowercase()?.substringBefore(';')?.trim()) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "text/plain" -> "txt"
        "application/zip" -> "zip"
        "application/gzip" -> "gz"
        else -> kind.defaultExt
    }

    fun fileName(kind: Kind, mime: String?, now: Date = Date()): String =
        "${kind.prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)}.${extensionFor(mime, kind)}"

    fun save(context: Context, bytes: ByteArray, kind: Kind, mime: String?, folder: String?): Saved {
        val dir = sanitizeFolder(folder)
        val name = fileName(kind, mime)
        val type = mime?.substringBefore(';')?.trim()?.ifEmpty { null }
            ?: if (kind == Kind.SCREENSHOT) "image/png" else "text/plain"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val base = if (kind == Kind.SCREENSHOT) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
            val collection = if (kind == Kind.SCREENSHOT) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, type)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$base/$dir")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("Could not create $base/$dir/$name")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write $base/$dir/$name")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory().absolutePath
            Saved("$root/$base/$dir/$name", uri.toString())
        } else {
            val base = if (kind == Kind.SCREENSHOT) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
            val target = File(File(context.getExternalFilesDir(base) ?: context.filesDir, dir), name)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            Saved(target.absolutePath, android.net.Uri.fromFile(target).toString())
        }
    }
}
