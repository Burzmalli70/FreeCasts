package dev.josephwilliams.freecasts.tools

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.InputStream

class RssParserTests {

    @Test
    fun `test parsePodcastFeed`() {
        TEST_FILES.forEach { file ->
            val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream(file)

            assert(inputStream != null) { "Resource not found: $file" }

            inputStream?.let { stream ->
                val result = RssParser.parsePodcastFeed(stream)
                assertNotNull("Results from $file were null", result)
                assertNotNull("Podcast from $file was null", result?.podcast)
                assertTrue("No episodes parsed for $file", result?.episodes?.isNotEmpty() == true)
            }
        }
    }

    @Test
    fun `test podcast metadata parsing`() {
        val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream("cog-dis-rss.txt")
        assertNotNull("Resource not found", inputStream)

        val result = RssParser.parsePodcastFeed(inputStream!!)
        assertNotNull(result)

        val podcast = result?.podcast
        assertNotNull(podcast)

        // Verify podcast fields
        assertEquals("Cog Diss Patron Feed", podcast?.title)
        assertEquals("https://www.patreon.com/dissonancepod", podcast?.websiteUrl)
        assertEquals("Cognitive Dissonance Podcast", podcast?.author)
        assertNotNull("Podcast description should not be null", podcast?.description)
        assertNotNull("Podcast artwork should not be null", podcast?.artworkUrl)
        assertTrue("Podcast should have categories", podcast?.categories?.isNotEmpty() == true)
        assertTrue("Episode count should match", podcast?.episodeCount == result?.episodes?.size)
    }

    @Test
    fun `test episode enclosure parsing`() {
        val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream("cog-dis-rss.txt")
        assertNotNull("Resource not found", inputStream)

        val result = RssParser.parsePodcastFeed(inputStream!!)
        assertNotNull(result)
        assertTrue("Should have episodes", result?.episodes?.isNotEmpty() == true)

        val firstEpisode = result?.episodes?.first()
        assertNotNull(firstEpisode)

        // Verify enclosure attributes are parsed correctly
        assertTrue("Audio URL should be set from enclosure", firstEpisode?.audioUrl?.contains(".mp3") == true)
        assertNotNull("File size should be parsed from enclosure", firstEpisode?.fileSizeBytes)
        assertEquals("MIME type should be audio/mpeg", "audio/mpeg", firstEpisode?.mimeType)
    }

    @Test
    fun `test episode guid parsing`() {
        val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream("cog-dis-rss.txt")
        assertNotNull("Resource not found", inputStream)

        val result = RssParser.parsePodcastFeed(inputStream!!)
        assertNotNull(result)

        result?.episodes?.forEach { episode ->
            assertTrue("Episode GUID should not be empty: ${episode.title}", episode.guid.isNotEmpty())
        }
    }

    @Test
    fun `test episode number parsing`() {
        val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream("cog-dis-rss.txt")
        assertNotNull("Resource not found", inputStream)

        val result = RssParser.parsePodcastFeed(inputStream!!)
        assertNotNull(result)

        // First episode should have episode number 856
        val firstEpisode = result?.episodes?.first()
        assertNotNull(firstEpisode)
        assertEquals("Episode number should be 856", 856, firstEpisode?.episodeNumber)
    }

    @Test
    fun `test duration parsing`() {
        val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream("criminal-plus-rss.txt")
        assertNotNull("Resource not found", inputStream)

        val result = RssParser.parsePodcastFeed(inputStream!!)
        assertNotNull(result)

        // First episode "Death in Eden" has duration 2417 seconds
        val firstEpisode = result?.episodes?.first()
        assertNotNull(firstEpisode)
        assertEquals("Duration should be 2417 seconds", 2417, firstEpisode?.durationSeconds)
    }

    @Test
    fun `test CDATA stripping in guid`() {
        val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream("criminal-plus-rss.txt")
        assertNotNull("Resource not found", inputStream)

        val result = RssParser.parsePodcastFeed(inputStream!!)
        assertNotNull(result)

        // Verify CDATA is stripped from GUID
        val firstEpisode = result?.episodes?.first()
        assertNotNull(firstEpisode)
        assertTrue("GUID should not contain CDATA markers", !firstEpisode?.guid?.contains("CDATA")!!)
        assertEquals("GUID should be clean UUID", "21cfbb88-60be-11f0-99a3-f7599c093b67", firstEpisode.guid)
    }

    @Test
    fun `test episode artwork parsing`() {
        val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream("criminal-plus-rss.txt")
        assertNotNull("Resource not found", inputStream)

        val result = RssParser.parsePodcastFeed(inputStream!!)
        assertNotNull(result)

        // Episodes should have artwork from itunes:image href attribute
        result?.episodes?.forEach { episode ->
            assertNotNull("Episode artwork should be parsed: ${episode.title}", episode.artworkUrl)
            assertTrue("Episode artwork should be a URL", episode.artworkUrl?.startsWith("http") == true)
        }
    }

    @Test
    fun `test pub date parsing`() {
        val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream("cog-dis-rss.txt")
        assertNotNull("Resource not found", inputStream)

        val result = RssParser.parsePodcastFeed(inputStream!!)
        assertNotNull(result)

        result?.episodes?.forEach { episode ->
            assertNotNull("Episode should have publish date: ${episode.title}", episode.publishedAt)
            assertTrue("Publish date should be positive", (episode.publishedAt ?: 0) > 0)
        }
    }

    @Test
    fun `test tagTextParser`() {
        TEST_FILES.forEach { file ->
            val inputStream: InputStream? = javaClass.classLoader?.getResourceAsStream(file)

            assert(inputStream != null) { "Resource not found: $file" }

            inputStream?.let { stream ->
                stream.bufferedReader().use { reader ->
                    var line: String? = ""

                    while (reader.readLine().also { line = it?.trim() } != null) {
                        val tag = line?.getTagName() ?: continue
                        val text = RssParser.getTagText(tag, line, reader)
                        assertNotNull(text)
                    }
                }
            }
        }
    }
}

val TEST_FILES = listOf(
    "cog-dis-rss.txt",
    "criminal-plus-rss.txt",
    "wtw-rss.txt"
)