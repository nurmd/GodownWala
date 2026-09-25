package com.example.bhpos

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Lightweight, zero-dependency ContentProvider for sharing update APKs
 * with Android's system PackageInstaller without requiring external libraries.
 */
class AppFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = getFileForUri(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols, 1)
        val row = cursor.newRow()
        for (col in cols) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String {
        val name = uri.lastPathSegment ?: ""
        return if (name.endsWith(".apk", ignoreCase = true)) {
            "application/vnd.android.package-archive"
        } else {
            "application/octet-stream"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = getFileForUri(uri)
        val fileMode = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w" -> ParcelFileDescriptor.MODE_WRITE_ONLY
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, fileMode)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getFileForUri(uri: Uri): File {
        val path = uri.path?.trimStart('/') ?: throw FileNotFoundException("Missing URI path for $uri")
        val ctx = context ?: throw FileNotFoundException("Context unavailable")

        // First check in cacheDir (e.g. cacheDir/updates/GodownWala.apk)
        val cacheFile = File(ctx.cacheDir, path)
        if (cacheFile.exists()) return cacheFile

        // Check in filesDir
        val filesFile = File(ctx.filesDir, path)
        if (filesFile.exists()) return filesFile

        // Check relative to external files dir if available
        val extFiles = ctx.getExternalFilesDir(null)
        if (extFiles != null) {
            val extFile = File(extFiles, path)
            if (extFile.exists()) return extFile
        }

        val directFile = File(path)
        if (directFile.exists()) return directFile

        throw FileNotFoundException("File not found for $uri at path: $path")
    }

    companion object {
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            val cacheDir = context.cacheDir
            val relativePath = if (file.absolutePath.startsWith(cacheDir.absolutePath)) {
                file.absolutePath.removePrefix(cacheDir.absolutePath)
            } else {
                file.name
            }
            val cleanPath = relativePath.trimStart('/')
            return Uri.parse("content://$authority/$cleanPath")
        }
    }
}
