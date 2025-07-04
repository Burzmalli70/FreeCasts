package dev.josephwilliams.filedownloader.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.josephwilliams.filedownloader.model.DownloadState
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val persistenceManager by lazy {
        PersistenceManager()
    }

    private val fileDownloader by lazy {
        FileDownloader(context, persistenceManager)
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return Result.failure()

        val downloadInfo = persistenceManager.getDownloadInfo(downloadId) ?: return Result.failure()

        val headers: Map<String, String> = downloadInfo.headers?.let {
            Json.decodeFromString(it)
        } ?: emptyMap()

        return try {
            fileDownloader.download(downloadInfo, headers)

            val finalState = persistenceManager.getDownloadInfo(downloadId)?.state

            when (finalState) {
                DownloadState.COMPLETED -> Result.success()
                DownloadState.PAUSED -> Result.retry()
                DownloadState.FAILED -> Result.retry()
                else -> Result.failure()
            }
        } catch (e: Exception) {
            persistenceManager.getDownloadInfo(downloadId)?.let {
                persistenceManager.updateDownloadInfo(
                    it.copy(
                        _state = DownloadState.FAILED.name,
                        error = e.message
                    )
                )
            }
            Result.failure()
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"

        fun createWorkRequest(downloadId: String): OneTimeWorkRequest {
            val data = workDataOf(KEY_DOWNLOAD_ID to downloadId)

            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MAX_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }
    }
}