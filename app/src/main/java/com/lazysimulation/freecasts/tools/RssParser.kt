package com.lazysimulation.freecasts.tools

import android.util.Xml
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Podcast
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.toInstant
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * RSS Feed Parser using XmlPullParser for robust parsing of podcast feeds.
 * Handles minified XML, various namespace configurations, and edge cases.
 */
object RssParser {
    
    fun parsePodcastFeed(inputStream: InputStream): ParseResult? {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream.sanitizeMalformedXml(), null)
            
            var podcast: Podcast? = null
            val episodes = mutableListOf<Episode>()
            val categories = mutableListOf<String>()
            var insideChannel = false
            var insideItem = false
            var currentEpisode: Episode? = null
            var currentImageUrl: String? = null
            var insideImage = false
            
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when {
                            tagName.equals("channel", ignoreCase = true) -> {
                                insideChannel = true
                            }
                            tagName.equals("item", ignoreCase = true) -> {
                                insideItem = true
                                currentEpisode = Episode()
                            }
                            tagName.equals("image", ignoreCase = true) && insideChannel && !insideItem -> {
                                insideImage = true
                            }
                            insideItem -> {
                                currentEpisode = parseEpisodeTag(parser, tagName, currentEpisode)
                            }
                            insideImage -> {
                                if (tagName.equals("url", ignoreCase = true)) {
                                    currentImageUrl = parser.nextText()
                                }
                            }
                            insideChannel && !insideItem -> {
                                podcast = parseChannelTag(parser, tagName, podcast, categories)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when {
                            tagName.equals("channel", ignoreCase = true) -> {
                                insideChannel = false
                            }
                            tagName.equals("item", ignoreCase = true) -> {
                                currentEpisode?.let { episode ->
                                    val withStableGuid = ensureStableEpisodeGuid(episode)
                                    if (withStableGuid.title.isNotBlank() || withStableGuid.audioUrl.isNotBlank()) {
                                        episodes.add(withStableGuid)
                                    }
                                }
                                currentEpisode = null
                                insideItem = false
                            }
                            tagName.equals("image", ignoreCase = true) -> {
                                if (insideImage && currentImageUrl != null) {
                                    podcast = podcast?.copy(artworkUrl = currentImageUrl) 
                                        ?: Podcast(artworkUrl = currentImageUrl)
                                }
                                insideImage = false
                                currentImageUrl = null
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            
            // Set categories
            if (categories.isNotEmpty()) {
                val categoriesStr = categories.joinToString(", ")
                podcast = podcast?.copy(categories = categoriesStr)
            }
            
            // Set episode count
            if (episodes.isNotEmpty()) {
                podcast = podcast?.copy(episodeCount = episodes.size)
            }
            
            if (podcast != null) ParseResult(podcast, episodes) else null
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun parseChannelTag(
        parser: XmlPullParser, 
        tagName: String, 
        currentPodcast: Podcast?,
        categories: MutableList<String>
    ): Podcast? {
        var podcast = currentPodcast
        
        when {
            tagName.equals("title", ignoreCase = true) -> {
                val title = parser.safeNextText()
                podcast = podcast?.copy(title = title) ?: Podcast(title = title)
            }
            tagName.equals("link", ignoreCase = true) && !tagName.contains(":") -> {
                // Only process plain <link>, not atom:link or other namespaced links
                val link = parser.safeNextText()
                if (link.isNotBlank() && !link.startsWith("http://www.w3.org")) {
                    podcast = podcast?.copy(websiteUrl = link) ?: Podcast(websiteUrl = link)
                }
            }
            tagName.equals("description", ignoreCase = true) -> {
                val description = parser.safeNextText().stripCdata()
                podcast = podcast?.copy(description = description) ?: Podcast(description = description)
            }
            tagName.equals("itunes:image", ignoreCase = true) -> {
                val imageUrl = parser.getAttributeValue(null, "href")
                if (!imageUrl.isNullOrBlank()) {
                    podcast = podcast?.copy(artworkUrl = imageUrl) ?: Podcast(artworkUrl = imageUrl)
                }
            }
            tagName.equals("itunes:author", ignoreCase = true) -> {
                val author = parser.safeNextText()
                podcast = podcast?.copy(author = author) ?: Podcast(author = author)
            }
            tagName.equals("itunes:category", ignoreCase = true) -> {
                val category = parser.getAttributeValue(null, "text")
                if (!category.isNullOrBlank()) {
                    categories.add(category)
                }
            }
//            tagName.equals("language", ignoreCase = true) -> {
//                val language = parser.safeNextText()
//                podcast = podcast?.copy(language = language) ?: Podcast(language = language)
//            }
        }
        
        return podcast
    }
    
    private fun parseEpisodeTag(
        parser: XmlPullParser,
        tagName: String,
        currentEpisode: Episode?
    ): Episode? {
        var episode = currentEpisode
        
        when {
            tagName.equals("title", ignoreCase = true) -> {
                val title = parser.safeNextText()
                episode = episode?.copy(title = title) ?: Episode(title = title)
            }
            tagName.equals("guid", ignoreCase = true) -> {
                val guid = parser.safeNextText().stripCdata()
                episode = episode?.copy(guid = guid) ?: Episode(guid = guid)
            }
            tagName.equals("pubDate", ignoreCase = true) -> {
                val pubDate = parser.safeNextText()
                val timeMillis = pubDate.toTimeMillis()
                episode = episode?.copy(publishedAt = timeMillis) ?: Episode(publishedAt = timeMillis)
            }
            tagName.equals("description", ignoreCase = true) -> {
                val description = parser.safeNextText().stripCdata()
                episode = episode?.copy(description = description) ?: Episode(description = description)
            }
            tagName.equals("itunes:duration", ignoreCase = true) -> {
                val duration = parser.safeNextText()
                val time = duration.parseDuration()
                episode = episode?.copy(durationSeconds = time) ?: Episode(durationSeconds = time)
            }
            tagName.equals("enclosure", ignoreCase = true) -> {
                val audioUrl = parser.getAttributeValue(null, "url") ?: ""
                val fileSize = parser.getAttributeValue(null, "length")?.toLongOrNull()
                val mimeType = parser.getAttributeValue(null, "type")
                episode = episode?.copy(
                    audioUrl = audioUrl,
                    fileSizeBytes = fileSize,
                    mimeType = mimeType
                ) ?: Episode(
                    audioUrl = audioUrl,
                    fileSizeBytes = fileSize,
                    mimeType = mimeType
                )
            }
            tagName.equals("itunes:image", ignoreCase = true) -> {
                val imageUrl = parser.getAttributeValue(null, "href")
                if (!imageUrl.isNullOrBlank()) {
                    episode = episode?.copy(artworkUrl = imageUrl) ?: Episode(artworkUrl = imageUrl)
                }
            }
            tagName.equals("itunes:episode", ignoreCase = true) -> {
                val episodeNum = parser.safeNextText().toIntOrNull()
                episode = episode?.copy(episodeNumber = episodeNum) ?: Episode(episodeNumber = episodeNum)
            }
            tagName.equals("itunes:season", ignoreCase = true) -> {
                val seasonNum = parser.safeNextText().toIntOrNull()
                episode = episode?.copy(seasonNumber = seasonNum) ?: Episode(seasonNumber = seasonNum)
            }
//            tagName.equals("link", ignoreCase = true) -> {
//                val link = parser.safeNextText()
//                if (link.isNotBlank()) {
//                    episode = episode?.copy(websiteUrl = link) ?: Episode(websiteUrl = link)
//                }
//            }
            tagName.equals("content:encoded", ignoreCase = true) -> {
                // Use content:encoded as description if description is empty
                val content = parser.safeNextText().stripCdata()
                if (episode?.description.isNullOrBlank() && content.isNotBlank()) {
                    episode = episode?.copy(description = content) ?: Episode(description = content)
                }
            }
        }
        
        return episode
    }

    /**
     * GUID is unique in the local DB. Missing or blank RSS guids would otherwise collide
     * (often as "") and, under REPLACE conflict handling, delete prior rows and CASCADE
     * playlist memberships. Prefer enclosure URL, then a stable title/pubDate key.
     */
    internal fun ensureStableEpisodeGuid(episode: Episode): Episode {
        if (episode.guid.isNotBlank()) return episode
        val fallback = when {
            episode.audioUrl.isNotBlank() -> episode.audioUrl
            else -> "title:${episode.title}|pub:${episode.publishedAt ?: 0}"
        }
        return episode.copy(guid = fallback)
    }

    data class ParseResult(
        val podcast: Podcast?,
        val episodes: List<Episode>
    )
}

/**
 * Read all text and CDATA content until the matching end tag.
 */
private fun XmlPullParser.readElementText(): String {
    if (isEmptyElementTag) return ""

    val builder = StringBuilder()
    val startDepth = depth
    while (true) {
        when (next()) {
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                text?.let(builder::append)
            }
            XmlPullParser.END_TAG -> {
                if (depth == startDepth) break
            }
            XmlPullParser.END_DOCUMENT -> break
        }
    }
    return builder.toString()
}

/**
 * Safely get next text content, handling edge cases where nextText() might fail.
 */
private fun XmlPullParser.safeNextText(): String {
    return try {
        readElementText()
    } catch (e: Exception) {
        ""
    }
}

private fun InputStream.sanitizeMalformedXml(): InputStream {
    val content = bufferedReader().use { it.readText() }
    return ByteArrayInputStream(content.fixUnescapedAmpersands().toByteArray(Charsets.UTF_8))
}

private val UNESCAPED_AMPERSAND = Regex("""&(?!amp;|lt;|gt;|quot;|apos;|#[0-9]+;|#x[0-9a-fA-F]+;)""")

/**
 * Fix common RSS/XML authoring mistakes without altering CDATA sections.
 */
private fun String.fixUnescapedAmpersands(): String {
    val result = StringBuilder()
    var index = 0
    while (index < length) {
        val cdataStart = indexOf("<![CDATA[", index)
        if (cdataStart == -1) {
            result.append(UNESCAPED_AMPERSAND.replace(substring(index), "&amp;"))
            break
        }

        result.append(UNESCAPED_AMPERSAND.replace(substring(index, cdataStart), "&amp;"))

        val cdataEnd = indexOf("]]>", cdataStart)
        if (cdataEnd == -1) {
            result.append(substring(cdataStart))
            break
        }

        result.append(substring(cdataStart, cdataEnd + 3))
        index = cdataEnd + 3
    }
    return result.toString()
}

fun String.stripCdata(): String {
    var result = this.trim()
    if (result.startsWith("<![CDATA[")) {
        result = result.removePrefix("<![CDATA[")
    }
    if (result.endsWith("]]>")) {
        result = result.removeSuffix("]]>")
    }
    return result.trim()
}

/**
 * Parse duration which can be:
 * - Seconds as integer (e.g., "2417")
 * - HH:MM:SS format (e.g., "01:30:45")
 * - MM:SS format (e.g., "45:30")
 */
fun String.parseDuration(): Int {
    // Try parsing as plain integer (seconds)
    this.toIntOrNull()?.let { return it }
    
    // Try parsing as time format (HH:MM:SS or MM:SS)
    val parts = this.split(":").mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]  // HH:MM:SS
        2 -> parts[0] * 60 + parts[1]  // MM:SS
        else -> 0
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
fun String.toTimeMillis(): Long {
    val trimmed = trim()
    if (trimmed.isEmpty()) return 0

    // First try LocalDateTime format
    try {
        return LocalDateTime.parse(trimmed).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (_: Exception) {
        // Continue to try other formats
    }

    // RSS feeds often use obsolete RFC 822 zone names (EST, PDT, …) that
    // kotlinx-datetime's RFC_1123 parser rejects. Normalize those to offsets.
    val candidates = listOf(trimmed, trimmed.withRfc822ZoneAsOffset()).distinct()

    for (candidate in candidates) {
        for (format in DEFINED_FORMATS) {
            try {
                val components = format.parse(candidate)
                return components.toInstantUsingOffset().toEpochMilliseconds()
            } catch (_: Exception) {
                continue
            }
        }
    }

    return 0
}

/**
 * Maps common RFC 822 timezone abbreviations to numeric offsets.
 * Many podcast feeds (e.g. Film Junk) publish pubDates like
 * "Mon, 24 Aug 2026 12:00:00 EST" which fail kotlinx RFC_1123 parsing.
 */
internal fun String.withRfc822ZoneAsOffset(): String {
    val lastSpace = lastIndexOf(' ')
    if (lastSpace < 0 || lastSpace == length - 1) return this
    val zone = substring(lastSpace + 1)
    val offset = RFC822_ZONE_OFFSETS[zone.uppercase()] ?: return this
    return substring(0, lastSpace + 1) + offset
}

/** Obsolete RFC 822 / RFC 1123 named zones still seen in RSS pubDate values. */
private val RFC822_ZONE_OFFSETS = mapOf(
    "UT" to "+0000",
    "GMT" to "+0000",
    "EST" to "-0500",
    "EDT" to "-0400",
    "CST" to "-0600",
    "CDT" to "-0500",
    "MST" to "-0700",
    "MDT" to "-0600",
    "PST" to "-0800",
    "PDT" to "-0700",
)

val DEFINED_FORMATS = listOf(
    DateTimeComponents.Formats.RFC_1123,
    DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET
)
