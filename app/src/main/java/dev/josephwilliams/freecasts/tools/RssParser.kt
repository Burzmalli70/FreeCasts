package dev.josephwilliams.freecasts.tools

import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.toInstant
import java.io.BufferedReader
import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

object RssParser {
    fun parsePodcastFeed(inputStream: InputStream): ParseResult? {
        val backup = inputStream.bufferedReader().readText()
        val prettyXml = prettifyXml(backup.byteInputStream(), 2) ?: backup
        val xmlStream = prettyXml.byteInputStream()
        var podcast: Podcast? = null
        val episodes: MutableList<Episode> = mutableListOf()
        var categories: MutableList<String> = mutableListOf()

        xmlStream.bufferedReader().use { reader ->
            var line: String? = ""
            while (reader.readLine().also { line = it?.trim() } != null) {
                when (val tag = line?.getTagName()) {
                    ShowTag.TITLE.tagName -> {
                        getTagText(tag, line, reader)?.let { title ->
                            podcast = podcast?.copy(title = title) ?: Podcast(title = title)
                        }
                    }
                    ShowTag.LINK.tagName -> {
                        val link = getTagText(tag, line, reader) ?: ""
                        podcast = podcast?.copy(websiteUrl = link) ?: Podcast(websiteUrl = link)
                    }
                    ShowTag.DESCRIPTION.tagName -> {
                        val description = getTagText(tag, line, reader)?.stripCdata()
                        podcast = podcast?.copy(description = description) ?: Podcast(description = description)
                    }
                    ShowTag.IMAGE.tagName -> {
                        // Standard RSS <image> is a container with nested <url>
                        parseImageContainer(reader)?.let { imageUrl ->
                            podcast = podcast?.copy(artworkUrl = imageUrl) ?: Podcast(artworkUrl = imageUrl)
                        }
                    }
                    ShowTag.ITUNES_IMAGE.tagName -> {
                        // iTunes image uses href attribute: <itunes:image href="..."/>
                        line.getTagAttribute("href")?.let { imageUrl ->
                            podcast = podcast?.copy(artworkUrl = imageUrl) ?: Podcast(artworkUrl = imageUrl)
                        }
                    }
                    ShowTag.AUTHOR.tagName -> {
                        val author = getTagText(tag, line, reader)
                        podcast = podcast?.copy(author = author) ?: Podcast(author = author)
                    }
                    ShowTag.ITUNES_CATEGORY.tagName -> {
                        line?.getTagAttribute("text")?.let { category ->
                            categories.add(category)
                        }
                    }
                    ShowTag.ITEM.tagName -> {
                        parseEpisodeItem(reader)?.let { episode ->
                            episodes.add(episode)
                        }
                    }
                    else -> continue
                }
            }
        }

        // Set categories as comma-separated string
        if (categories.isNotEmpty()) {
            val categoriesStr = categories.joinToString(", ")
            podcast = podcast?.copy(categories = categoriesStr) ?: Podcast(categories = categoriesStr)
        }

        // Set episode count
        if (episodes.isNotEmpty()) {
            podcast = podcast?.copy(episodeCount = episodes.size)
        }

        return if (podcast != null) ParseResult(podcast, episodes) else null
    }

    private fun parseImageContainer(reader: BufferedReader): String? {
        var line: String? = ""
        while (reader.readLine().also { line = it?.trim() } != null) {
            val tag = line?.getTagName()
            if (tag == "url") {
                return getTagText(tag, line, reader)
            }
            if (line?.checkEndTag("image") == true) {
                return null
            }
        }
        return null
    }

    private fun parseEpisodeItem(reader: BufferedReader): Episode? {
        var line: String? = ""
        var episode: Episode? = null
        while (reader.readLine().also { line = it?.trim() } != null) {
            when (val tag = line?.getTagName()) {
                EpisodeTag.TITLE.tagName -> {
                    val title = getTagText(tag, line, reader) ?: ""
                    episode = episode?.copy(title = title) ?: Episode(title = title)
                }
                EpisodeTag.GUID.tagName -> {
                    val guid = getTagText(tag, line, reader)?.stripCdata() ?: ""
                    episode = episode?.copy(guid = guid) ?: Episode(guid = guid)
                }
                EpisodeTag.PUB_DATE.tagName -> {
                    val pubDate = getTagText(tag, line, reader)
                    episode = episode?.copy(publishedAt = pubDate?.toTimeMillis()) ?: Episode(publishedAt = pubDate?.toTimeMillis())
                }
                EpisodeTag.DESCRIPTION.tagName -> {
                    val description = getTagText(tag, line, reader)?.stripCdata()
                    episode = episode?.copy(description = description) ?: Episode(description = description)
                }
                EpisodeTag.DURATION.tagName -> {
                    val duration = getTagText(tag, line, reader)
                    val time = duration?.parseDuration() ?: 0
                    episode = episode?.copy(durationSeconds = time) ?: Episode(durationSeconds = time)
                }
                EpisodeTag.ENCLOSURE.tagName -> {
                    val audioUrl = line?.getTagAttribute("url") ?: ""
                    val fileSize = line?.getTagAttribute("length")?.toLongOrNull()
                    val mimeType = line?.getTagAttribute("type")
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
                EpisodeTag.ITUNES_IMAGE.tagName -> {
                    line?.getTagAttribute("href")?.let { imageUrl ->
                        episode = episode?.copy(artworkUrl = imageUrl) ?: Episode(artworkUrl = imageUrl)
                    }
                }
                EpisodeTag.ITUNES_EPISODE.tagName -> {
                    val episodeNum = getTagText(tag, line, reader)?.toIntOrNull()
                    episode = episode?.copy(episodeNumber = episodeNum) ?: Episode(episodeNumber = episodeNum)
                }
                EpisodeTag.ITUNES_SEASON.tagName -> {
                    val seasonNum = getTagText(tag, line, reader)?.toIntOrNull()
                    episode = episode?.copy(seasonNumber = seasonNum) ?: Episode(seasonNumber = seasonNum)
                }
                else -> if (line?.checkEndTag("item") == true) return episode
            }
        }

        return episode
    }

    fun getTagText(tag: String, start: String?, reader: BufferedReader): String? {
        start ?: return null
        val tagStartIndex = start.indexOf("<$tag")
        if (tagStartIndex < 0) return null
        
        val closingBracketIndex = start.indexOf('>', tagStartIndex)
        if (closingBracketIndex < 0) return null
        
        // Check for self-closing tag like <tag attr="value"/>
        if (start.getOrNull(closingBracketIndex - 1) == '/') return null
        
        val tagStartLen = closingBracketIndex + 1
        val endTagIndex = start.indexOf("</$tag>")
        
        if (endTagIndex > 0) {
            return start.substring(tagStartLen, endTagIndex)
        }
        
        var runningDesc = start.substring(tagStartLen)
        var next: String
        do {
            next = reader.readLine()?.trim() ?: break
            runningDesc += "\n$next"
        } while (next.indexOf("</$tag>") < 0)
        
        val finalEndIndex = runningDesc.indexOf("</$tag>")
        return if (finalEndIndex >= 0) {
            runningDesc.substring(0, finalEndIndex)
        } else {
            runningDesc
        }
    }

    data class ParseResult(
        val podcast: Podcast?,
        val episodes: List<Episode>
    )

    fun prettifyXml(input: String, indent: Int): String? {
        return try {
            val xmlInput = StreamSource(StringReader(input))
            val stringWriter = StringWriter()
            val xmlOutput = StreamResult(stringWriter)
            val transformerFactory = TransformerFactory.newInstance()
            transformerFactory.setAttribute("indent-number", indent)
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.transform(xmlInput, xmlOutput)
            xmlOutput.writer.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun prettifyXml(inputStream: InputStream, indent: Int): String? {
        return try {
            val xmlInput = StreamSource(inputStream)
            val stringWriter = StringWriter()
            val xmlOutput = StreamResult(stringWriter)
            val transformerFactory = TransformerFactory.newInstance()
            transformerFactory.setAttribute("indent-number", indent)
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.transform(xmlInput, xmlOutput)
            xmlOutput.writer.toString()
        } catch (e: Exception) {
            null
        }
    }

    enum class ShowTag(val tagName: String) {
        TITLE("title"),
        LINK("link"),
        DESCRIPTION("description"),
        IMAGE("image"),
        ITUNES_IMAGE("itunes:image"),
        AUTHOR("itunes:author"),
        ITUNES_CATEGORY("itunes:category"),
        ITEM("item")
    }

    enum class EpisodeTag(val tagName: String) {
        TITLE("title"),
        GUID("guid"),
        PUB_DATE("pubDate"),
        DESCRIPTION("description"),
        DURATION("itunes:duration"),
        ENCLOSURE("enclosure"),
        ITUNES_IMAGE("itunes:image"),
        ITUNES_EPISODE("itunes:episode"),
        ITUNES_SEASON("itunes:season")
    }
}

fun String.getTagName(): String? {
    if (this.firstOrNull() != '<' || !this.contains('>')) return null
    
    val tagStart = 1
    val spaceIndex = this.indexOf(' ', tagStart)
    val closingIndex = this.indexOf('>', tagStart)
    
    if (closingIndex < 0) return null
    
    val endIndex = when {
        spaceIndex < 0 -> closingIndex
        else -> minOf(spaceIndex, closingIndex)
    }
    
    return this.substring(tagStart, endIndex)
}

fun String.getTagAttribute(attributeName: String): String? {
    // Match attribute="value" or attribute='value'
    val pattern = """$attributeName\s*=\s*["']([^"']+)["']""".toRegex()
    return pattern.find(this)?.groupValues?.getOrNull(1)
}

fun String.checkEndTag(tagName: String): Boolean {
    return this.contains("</$tagName>")
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
    // First try LocalDateTime format
    try {
        return LocalDateTime.parse(this).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (_: Exception) {
        // Continue to try other formats
    }
    
    // Try each defined format using DateTimeComponents
    for (format in DEFINED_FORMATS) {
        try {
            val components = format.parse(this)
            return components.toInstantUsingOffset().toEpochMilliseconds()
        } catch (_: Exception) {
            continue
        }
    }
    
    return 0
}

val DEFINED_FORMATS = listOf(
    DateTimeComponents.Formats.RFC_1123,
    DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET
)
