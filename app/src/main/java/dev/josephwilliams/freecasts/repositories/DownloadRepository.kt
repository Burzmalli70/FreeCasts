package dev.josephwilliams.freecasts.repositories

import dev.joewilliams.downloadmanager.Download
import dev.joewilliams.downloadmanager.DownloadPersistenceRepository
import dev.joewilliams.downloadmanager.DownloadStatus
import dev.josephwilliams.freecasts.model.daos.DownloadDao

class DownloadRepository(
    private val downloadDao: DownloadDao
): DownloadPersistenceRepository() {
    override suspend fun getDownloadsByStatus(status: DownloadStatus): List<Download> {
        return downloadDao.getDownloadsByStatus(status.toEntity()).map { it.toModel() }
    }

    override suspend fun saveDownload(download: Download) {
        if (download.id != 0) {
            downloadDao.update(download.toEntity())
        } else {
            downloadDao.insertDownload(download.toEntity())
        }
    }

    override suspend fun deleteDownload(download: Download) {
        downloadDao.delete(download.toEntity())
    }
}

fun Download.toEntity(): dev.josephwilliams.freecasts.model.entities.Download {
    return dev.josephwilliams.freecasts.model.entities.Download(
        id = id,
        currentBytes = currentBytes,
        totalBytes = totalBytes,
        status = status.toEntity(),
        url = url,
        filePath = filePath,
        fileName = fileName
    )
}

fun dev.josephwilliams.freecasts.model.entities.Download.toModel(): Download {
    return Download(
        id = id,
        currentBytes = currentBytes,
        totalBytes = totalBytes,
        status = status.toModel(),
        url = url,
        filePath = filePath,
        fileName = fileName
    )
}

fun DownloadStatus.toEntity(): dev.josephwilliams.freecasts.model.entities.DownloadStatus {
    return dev.josephwilliams.freecasts.model.entities.DownloadStatus.safeFromString(this.name)
}