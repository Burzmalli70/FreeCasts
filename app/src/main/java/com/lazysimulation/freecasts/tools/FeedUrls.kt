package com.lazysimulation.freecasts.tools

/**
 * Normalize podcast feed URLs for network requests.
 * Android blocks cleartext HTTP by default; most feed hosts (including FeedBurner) also serve HTTPS.
 */
fun String.normalizeFeedUrl(): String {
    val trimmed = trim()
    return if (trimmed.startsWith("http://", ignoreCase = true)) {
        "https://${trimmed.substring(7)}"
    } else {
        trimmed
    }
}
