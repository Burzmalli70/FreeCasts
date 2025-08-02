package dev.josephwilliams.freecasts.repositories

import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Podcast
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.parse
import kotlinx.datetime.toInstant
import java.io.BufferedReader
import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


object RssParser {
    fun parsePodcastFeed(inputStream: InputStream): ParseResult? {
        val backup = inputStream.bufferedReader().readText()
        val prettyXml = prettifyXml(backup.byteInputStream(), 2) ?: backup
        val stream = prettyXml.byteInputStream()
        var podcast: Podcast? = null
        var episodes: MutableList<Episode> = mutableListOf()
        stream.bufferedReader().use { stream ->
            var line: String? = ""
            while(stream.readLine().also { line = it?.trim() } != null) {
                when(val tag = line?.getTagName()) {
                    ShowTag.TITLE.tagName -> {
                        getTagText(tag, line, stream)?.let { title ->
                            podcast = podcast?.copy(title = title) ?: Podcast(title = title)
                        }
                    }
                    ShowTag.LINK.tagName -> {
                        val link = getTagText(tag, line, stream)
                        podcast = podcast?.copy(feedUrl = link) ?: Podcast(feedUrl = link)
                    }
                    ShowTag.DESCRIPTION.tagName -> {
                        val description = getTagText(tag, line, stream)
                        podcast = podcast?.copy(description = description) ?: Podcast(description = description)
                    }
                    ShowTag.IMAGE.tagName -> {
                        val image = getTagText(tag, line, stream)
                        podcast = podcast?.copy(smallImageUrl = image, largeImageUrl = image) ?: Podcast(smallImageUrl = image, largeImageUrl = image)
                    }
                    ShowTag.AUTHOR.tagName -> {
                        val author = getTagText(tag, line, stream)
                        podcast = podcast?.copy(author = author) ?: Podcast(author = author)
                    }
                    ShowTag.ITEM.tagName -> {
                        parseEpisodeItem(stream)?.let { episode ->
                            episodes.add(episode)
                        }
                    }
                    else -> continue
                }
            }
        }

        return if (podcast != null) ParseResult(podcast, episodes) else null
    }

    private fun parseEpisodeItem(reader: BufferedReader): Episode? {
        var line: String? = ""
        var episode: Episode? = null
        while(reader.readLine().also { line = it?.trim() } != null) {
            when (val tag = line?.getTagName()) {
                EpisodeTag.TITLE.tagName -> {
                    val title = getTagText(tag, line, reader)
                    episode = episode?.copy(title = title) ?: Episode(title = title)
                }
                EpisodeTag.LINK.tagName -> {
                    val link = getTagText(tag, line, reader)
                    episode = episode?.copy(audioUrl = link) ?: Episode(audioUrl = link)
                }
                EpisodeTag.PUB_DATE.tagName -> {
                    val pubDate = getTagText(tag, line, reader)
                    episode = episode?.copy(publicationDate = pubDate?.toTimeMillis()) ?: Episode(publicationDate = pubDate?.toTimeMillis())
                }
                EpisodeTag.DESCRIPTION.tagName -> {
                    val description = getTagText(tag, line, reader)
                    episode = episode?.copy(description = description) ?: Episode(description = description)
                }
                EpisodeTag.DURATION.tagName -> {
                    val duration = getTagText(tag, line, reader)
                    val time = duration?.toLongOrNull() ?: 0
                    episode = episode?.copy(duration = time) ?: Episode(duration = time)
                }
                else -> if(line?.checkEndTag("item") == true) return episode
            }
        }

        return episode
    }

    fun getTagText(tag: String, start: String?, reader: BufferedReader): String? {
        start ?: return null
        if (start.indexOf("<$tag") < 0) return null
        val fullTag = start.substring(0, start.indexOf('>'))
        val tagStartLen = fullTag.length
        if (start.indexOf("</$tag>") > 0) return start.substring(tagStartLen + 1, start.indexOf("</$tag>"))
        var runningDesc = start.substring(tagStartLen + 1)
        do {
            val next = reader.readLine().trim()
            runningDesc += "\n$next"
        } while(next.indexOf("</$tag>") < 0)
        runningDesc = runningDesc.substring(0, runningDesc.indexOf("</$tag>"))
        return runningDesc
    }

    data class ParseResult(
        val podcast: Podcast?,
        val episodes: List<Episode>
    )

    fun prettifyXml(input: String, indent: Int): String? {
        try {
            val xmlInput = StreamSource(StringReader(input))
            val stringWriter = StringWriter()
            val xmlOutput = StreamResult(stringWriter)
            val transformerFactory = TransformerFactory.newInstance()
            transformerFactory.setAttribute("indent-number", indent)
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.transform(xmlInput, xmlOutput)
            return xmlOutput.writer.toString()
        } catch (e: java.lang.Exception) {
            return null
        }
    }

    fun prettifyXml(inputStream: InputStream, indent: Int): String? {
        try {
            val xmlInput = StreamSource(inputStream)
            val stringWriter = StringWriter()
            val xmlOutput = StreamResult(stringWriter)
            val transformerFactory = TransformerFactory.newInstance()
//            transformerFactory.setAttribute("indent-number", indent)
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.transform(xmlInput, xmlOutput)
            return xmlOutput.writer.toString()
        } catch (e: java.lang.Exception) {
            return null
        }
    }

    enum class ShowTag(val tagName: String) {
        TITLE("title"),
        LINK("link"),
        DESCRIPTION("description"),
        IMAGE("image"),
        AUTHOR("author"),
        ITEM("item")
    }

    enum class EpisodeTag(val tagName: String) {
        TITLE("title"),
        LINK("link"),
        GUID("guid"),
        PUB_DATE("pubDate"),
        DESCRIPTION("description"),
        DURATION("itunes:duration")
    }

    val showTags = ShowTag.entries.map { it.tagName }
    val episodeTags = EpisodeTag.entries.map { it.tagName }
}

fun String.getTagName(): String? {
    return if (this.firstOrNull() == '<' && this.contains('>')) {
        if (this.indexOf(' ') < 0) return this.substring(1, this.indexOf('>'))
        val firstEnd = minOf(this.indexOf(' '), this.indexOf('>'))
        this.substring(1, firstEnd)
    } else {
        null
    }
}

fun String.checkEndTag(tagName: String): Boolean {
    return this.indexOf("</$tagName>") >= 0
}

@OptIn(ExperimentalTime::class)
fun String.toTimeMillis(): Long {
    return try {
        LocalDateTime.parse(this).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (ex: Exception) {
        for (format in DEFINED_FORMATS) {
            return Instant.parse(this, format).toEpochMilliseconds()
        }
        0
    }
}

val DEFINED_FORMATS = listOf(
    DateTimeComponents.Formats.RFC_1123,
    DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET
)