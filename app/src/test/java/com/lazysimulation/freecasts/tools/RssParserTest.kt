package com.lazysimulation.freecasts.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RssParserTest {

    @Test
    fun `parses filmjunk rss feed`() {
        val result = parseResource("filmjunk-rss.txt")

        assertNotNull(result)
        val podcast = result!!.podcast!!
        assertEquals("Die Film Junkies", podcast.title)
        assertEquals("Die Film Junkies", podcast.author)
        assertEquals(
            "https://files.pdcstrcdn.de/s/i/234d4e42-405d-4167-ba31-293a03f5bcdf/FILMJUNKIES_podcast2.jpg",
            podcast.artworkUrl
        )
        assertEquals("TV & Film", podcast.categories)
        assertEquals(19, result.episodes.size)
        assertEquals(19, podcast.episodeCount)

        val firstEpisode = result.episodes.first()
        assertEquals("Vergangenheit und Zukunft - Podcast #19", firstEpisode.title)
        assertEquals(
            "https://x5g7iw.podcaster.de/diefilmjunkies/media/Die_Film_Junkies_Podcast_19.mp3?origin=feed",
            firstEpisode.audioUrl
        )
        assertEquals(19, firstEpisode.episodeNumber)
        assertEquals(3184, firstEpisode.durationSeconds)
        assertTrue(firstEpisode.description.orEmpty().contains("Filme 1999"))
    }

    @Test
    fun `parses supported sample rss feeds`() {
        val feeds = listOf(
            "citneed-rss.txt",
            "criminal-plus-rss.txt",
            "filmjunk-rss.txt",
            "unpretty-rss.txt",
        )

        feeds.forEach { resource ->
            val result = parseResource(resource)
            assertNotNull("Failed to parse $resource", result)
            assertNotNull("Missing podcast in $resource", result!!.podcast)
            assertTrue("No episodes in $resource", result.episodes.isNotEmpty())
        }
    }

    @Test
    fun `uses enclosure url as guid when guid element is missing`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>No Guid Show</title>
                <item>
                  <title>Episode Without Guid</title>
                  <enclosure url="https://cdn.example.com/ep1.mp3" type="audio/mpeg" />
                  <pubDate>Mon, 01 Jan 2024 12:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val result = RssParser.parsePodcastFeed(xml.byteInputStream())

        assertNotNull(result)
        assertEquals(1, result!!.episodes.size)
        assertEquals("https://cdn.example.com/ep1.mp3", result.episodes.first().guid)
    }

    @Test
    fun `assigns distinct fallback guids when guid and enclosure are missing`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Title Only Show</title>
                <item>
                  <title>First</title>
                  <pubDate>Mon, 01 Jan 2024 12:00:00 GMT</pubDate>
                </item>
                <item>
                  <title>Second</title>
                  <pubDate>Tue, 02 Jan 2024 12:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val result = RssParser.parsePodcastFeed(xml.byteInputStream())

        assertNotNull(result)
        assertEquals(2, result!!.episodes.size)
        val guids = result.episodes.map { it.guid }
        assertTrue(guids.all { it.isNotBlank() })
        assertEquals(2, guids.toSet().size)
    }

    private fun parseResource(name: String): RssParser.ParseResult? {
        val inputStream = javaClass.classLoader!!.getResourceAsStream(name)
        checkNotNull(inputStream) { "Missing test resource: $name" }
        return inputStream.use { RssParser.parsePodcastFeed(it) }
    }
}
