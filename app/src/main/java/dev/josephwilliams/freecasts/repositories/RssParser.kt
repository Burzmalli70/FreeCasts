package dev.josephwilliams.freecasts.repositories

import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Podcast
import java.io.BufferedReader
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.absoluteValue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object RssParser {
    fun parsePodcastFeed(inputStream: InputStream): ParseResult? {
        var podcast: Podcast? = null
        var episodes: MutableList<Episode> = mutableListOf()
        inputStream.bufferedReader().use { stream ->
            var line: String? = ""
            while(stream.readLine().also { line = it?.trim() } != null) {
                val tag = line?.getTagName()
                when(tag) {
                    ShowTag.TITLE.tagName -> {
                        val title = getTagText(tag, line, stream)
                        podcast = podcast?.copy(title = title) ?: Podcast(title = title)
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

    fun parseEpisodeItem(reader: BufferedReader): Episode? {
        var line: String? = ""
        var episode: Episode? = null
        while(reader.readLine().also { line = it?.trim() } != null) {
            val tag = line?.getTagName()
            when (tag) {
                EpisodeTag.TITLE.tagName -> {
                    val title = getTagText(tag, line, reader)
                    episode = episode?.copy(title = title) ?: Episode(title = title)
                }
                EpisodeTag.LINK.tagName -> {
                    val link = getTagText(tag, line, reader)
                    episode = episode?.copy(audioUrl = link) ?: Episode(audioUrl = link)
                }
                EpisodeTag.GUID.tagName -> {
                    val guid = getTagText(tag, line, reader)
                    episode = episode?.copy(podcastId = guid) ?: Episode(podcastId = guid)
                }
                EpisodeTag.PUB_DATE.tagName -> {
                    val pubDate = getTagText(tag, line, reader)
                    episode = episode?.copy(publicationDate = pubDate.toTimeMillis()) ?: Episode(publicationDate = pubDate.toLong())
                }
                EpisodeTag.DESCRIPTION.tagName -> {
                    val description = getTagText(tag, line, reader)
                    episode = episode?.copy(description = description) ?: Episode(description = description)
                }
                else -> if(line?.checkEndTag("item") == true) return episode
            }
        }

        return episode
    }

    fun getTagText(tag: String, start: String, reader: BufferedReader): String {
        if (start.indexOf("<$tag") < 0) return ""
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
        DESCRIPTION("description")
    }

    val showTags = ShowTag.entries.map { it.tagName }
    val episodeTags = EpisodeTag.entries.map { it.tagName }
}

fun String.getTagName(): String? {
    return if (this.firstOrNull() == '<') {
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
    try {
        val ldt = LocalDateTime.parse(this)
        return ldt.atZone(ZoneId.systemDefault()).toInstant()?.toEpochMilli() ?: 0
    } catch (ex: Exception) {
        return 0
    }
}