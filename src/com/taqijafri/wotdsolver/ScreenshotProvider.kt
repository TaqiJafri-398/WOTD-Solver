package com.taqijafri.wotdsolver

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Minimal file provider serving camera captures from the app's private cache.
 * Avoids the androidx FileProvider dependency; only serves files inside
 * cacheDir/screenshots and rejects any path traversal.
 */
class ScreenshotProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun fileFor(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw FileNotFoundException("missing file name")
        require(name.isNotEmpty() && !name.contains("/") && !name.contains("..")) { "bad file name" }
        return File(File(context!!.cacheDir, "screenshots").apply { mkdirs() }, name)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = fileFor(uri)
        if (!f.exists() && !mode.contains("w")) throw FileNotFoundException(uri.toString())
        val m = if (mode.contains("w")) ParcelFileDescriptor.MODE_READ_WRITE
        else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(f, m)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val f = fileFor(uri)
        val cursor = MatrixCursor(arrayOf("_display_name", "_size"))
        cursor.addRow(arrayOf<Any>(f.name, f.length()))
        return cursor
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException()
}
