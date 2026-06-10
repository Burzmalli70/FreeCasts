package dev.josephwilliams.freecasts.ui.components

internal fun formatPlaybackTime(currentMs: Long, totalMs: Long): String {
    val currentFormatted = formatSeconds((currentMs / 1000).toInt())
    val totalFormatted = formatSeconds((totalMs / 1000).toInt())
    return "$currentFormatted / $totalFormatted"
}

internal fun formatSeconds(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

fun formatListDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes} min"
    }
}

const val MIN_PLAYBACK_POSITION_FOR_REMAINING_MS = 60_000L

fun hasPartialPlayback(playbackPositionMs: Long, isPlayed: Boolean): Boolean {
    return !isPlayed && playbackPositionMs >= MIN_PLAYBACK_POSITION_FOR_REMAINING_MS
}

fun formatRemainingTime(
    playbackPositionMs: Long,
    durationSeconds: Int?,
    isPlayed: Boolean = false
): String? {
    if (!hasPartialPlayback(playbackPositionMs, isPlayed)) return null
    val totalSeconds = durationSeconds ?: return null
    if (totalSeconds <= 0) return null

    val remainingSeconds = (totalSeconds - (playbackPositionMs / 1000).toInt()).coerceAtLeast(0)
    if (remainingSeconds <= 0) return null

    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m left"
        minutes > 0 -> "$minutes min left"
        else -> "< 1 min left"
    }
}

fun formatEpisodeListDuration(
    playbackPositionMs: Long,
    durationSeconds: Int?,
    isPlayed: Boolean
): String? {
    formatRemainingTime(playbackPositionMs, durationSeconds, isPlayed)?.let { return it }
    return durationSeconds?.let { formatListDuration(it) }
}

internal fun stripHtml(text: String): String {
    return text.replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .trim()
}
