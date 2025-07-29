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
        val inputFeed = InstrumentationRegistry.getInstrumentation().context.assets.open("cog-dis-rss.txt")
        val result = RssParser.parsePodcastFeed(inputFeed)
        assertNotNull(result)
        assertNotNull(result?.podcast)
        assertTrue(result?.episodes?.isNotEmpty() == true)
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