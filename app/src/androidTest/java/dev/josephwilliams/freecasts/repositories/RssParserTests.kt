package dev.josephwilliams.freecasts.repositories

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssParserTests {

    @Test
    fun test_parsePodcastFeed() = runBlocking {
        TEST_FILES.forEach {
            val inputFeed =
                InstrumentationRegistry.getInstrumentation().context.assets.open(it)
            val result = RssParser.parsePodcastFeed(inputFeed)
            assertNotNull("Results from $it were null", result)
            assertNotNull("Podcast from $it was null", result?.podcast)
            assertTrue("No episodes parsed for $it", result?.episodes?.isNotEmpty() == true)
        }
    }

    @Test
    fun test_tagTextParser() = runBlocking {
        InstrumentationRegistry.getInstrumentation().context.assets.open("tag-tests.txt").bufferedReader().use {
            var line: String? = ""

            while(it.readLine().also { line = it?.trim() } != null) {
                val tag = line?.getTagName() ?: continue
                val text = RssParser.getTagText(tag, line, it)
                assertNotNull(text)
            }
        }
    }
}

val TEST_FILES = listOf(
    "cog-dis-rss.txt",
    "criminal-plus-rss.txt",
    "wtw-rss.txt"
)