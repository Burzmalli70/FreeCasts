package dev.josephwilliams.freecasts.tools

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