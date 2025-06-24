package dev.josephwilliams.filedownloader.model

import java.util.UUID

data class DownloadRequest(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val destinationPath: String,
    val headers: Map<String, String> = emptyMap()
)
