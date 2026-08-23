package com.lazysimulation.freecasts.data.export

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Handles reading and writing FreeCasts backup data to podcasts.json.
 */
class PodcastSubscriptionsFileManager(
    private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun needsLegacyStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    }

    fun hasLegacyStoragePermission(): Boolean {
        if (!needsLegacyStoragePermission()) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun exportBackup(backup: FreeCastsBackup): Result<Unit> {
        val jsonContent = json.encodeToString(backup)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeToDocumentsViaMediaStore(jsonContent)
            } else {
                writeToLegacyDocuments(jsonContent)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importBackup(): Result<FreeCastsBackup> {
        return try {
            val jsonContent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                readFromDocumentsViaMediaStore()
            } else {
                readFromLegacyDocuments()
            }
            Result.success(decodeBackup(jsonContent))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportToUri(uri: Uri, backup: FreeCastsBackup): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.encodeToString(backup).toByteArray())
            } ?: return Result.failure(IOException("Could not open output stream"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importFromUri(uri: Uri): Result<FreeCastsBackup> {
        return try {
            val jsonContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return Result.failure(IOException("Could not open input stream"))

            Result.success(decodeBackup(jsonContent))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeBackup(jsonContent: String): FreeCastsBackup {
        return try {
            json.decodeFromString<FreeCastsBackup>(jsonContent)
        } catch (_: Exception) {
            val legacy = json.decodeFromString<PodcastSubscriptionsExport>(jsonContent)
            FreeCastsBackup(
                version = legacy.version,
                podcasts = legacy.podcasts
            )
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDocumentsViaMediaStore(jsonContent: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val existingUri = findDocumentsFileUri()
        if (existingUri != null) {
            resolver.openOutputStream(existingUri, "wt")?.use { outputStream ->
                outputStream.write(jsonContent.toByteArray())
            } ?: throw IOException("Could not update existing podcasts.json")
            return
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, PODCASTS_EXPORT_FILENAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, contentValues)
            ?: throw IOException("Could not create podcasts.json in Documents")

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonContent.toByteArray())
            } ?: throw IOException("Could not write to podcasts.json")

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun readFromDocumentsViaMediaStore(): String {
        val uri = findDocumentsFileUri()
            ?: throw IOException("podcasts.json not found in Documents")

        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        } ?: throw IOException("Could not read podcasts.json")
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun findDocumentsFileUri(): Uri? {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(
            PODCASTS_EXPORT_FILENAME,
            "${Environment.DIRECTORY_DOCUMENTS}/"
        )

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }

    private fun writeToLegacyDocuments(jsonContent: String) {
        if (!hasLegacyStoragePermission()) {
            throw SecurityException("Storage permission not granted")
        }

        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!documentsDir.exists() && !documentsDir.mkdirs()) {
            throw IOException("Could not access Documents folder")
        }

        val file = File(documentsDir, PODCASTS_EXPORT_FILENAME)
        file.writeText(jsonContent)
    }

    private fun readFromLegacyDocuments(): String {
        if (!hasLegacyStoragePermission()) {
            throw SecurityException("Storage permission not granted")
        }

        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            PODCASTS_EXPORT_FILENAME
        )
        if (!file.exists()) {
            throw IOException("podcasts.json not found in Documents")
        }
        return file.readText()
    }
}
