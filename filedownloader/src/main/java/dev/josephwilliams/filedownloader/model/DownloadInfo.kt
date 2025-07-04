package dev.josephwilliams.filedownloader.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

@Serializable
data class DownloadInfo(
    val id: String,
    val url: String,
    val fileName: String,
    val destination: String,
    var totalBytes: Long,
    var downloadedByteCount: Long = 0,
    @SerialName("state")
    private var _state: String = DownloadState.QUEUED.name,
    @SerialName("created")
    private val _created: Long = Instant.now().toEpochMilli(),
    @SerialName("lastModifier")
    private val _lastModified: Long = Instant.now().toEpochMilli(),
    var error: String? = null,
    val headers: String? = null
) {
    var state: DownloadState
        get() = DownloadState.valueOf(_state)
        set(value) = setField({ _state = value.name })

    val created: Instant
        get() = Instant.ofEpochMilli(_created)

    val lastModified: Instant
        get() = Instant.ofEpochMilli(_lastModified)

    val progress: Float
        get() {
            if (totalBytes < 1f) return 0f

            return downloadedByteCount.toFloat() / totalBytes
        }

    private inline fun setField(modifier: () -> Unit) {
        modifier()
    }

    fun toJson(): String {
        return Json.encodeToString(this)
    }

    fun saveToFile(filePath: String) {
        File(filePath).writeText(this.toJson())
    }

    companion object {
        fun fromJson(json: String): DownloadInfo {
            return Json.decodeFromString(json)
        }

        fun loadFromFile(filePath: String): DownloadInfo {
            val json = File(filePath).readText()
            return fromJson(json)
        }
    }
}

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}
