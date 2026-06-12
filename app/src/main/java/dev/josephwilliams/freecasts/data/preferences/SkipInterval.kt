package dev.josephwilliams.freecasts.data.preferences

const val DEFAULT_SKIP_INTERVAL_SECONDS = 30

val SKIP_INTERVAL_OPTIONS_SECONDS = listOf(10, 20, 30, 45, 60)

fun normalizeSkipIntervalSeconds(seconds: Int): Int {
    return seconds.takeIf { it in SKIP_INTERVAL_OPTIONS_SECONDS } ?: DEFAULT_SKIP_INTERVAL_SECONDS
}
