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

internal fun stripHtml(text: String): String {
    return text.replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .trim()
}
