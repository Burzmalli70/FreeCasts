package dev.josephwilliams.freecasts.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedUrlsTest {

    @Test
    fun `upgrades http feed urls to https`() {
        assertEquals(
            "https://feeds.feedburner.com/some-podcast",
            "http://feeds.feedburner.com/some-podcast".normalizeFeedUrl()
        )
    }

    @Test
    fun `leaves https feed urls unchanged`() {
        val url = "https://x5g7iw.podcaster.de/diefilmjunkies.rss"
        assertEquals(url, url.normalizeFeedUrl())
    }

    @Test
    fun `trims whitespace`() {
        assertEquals(
            "https://feeds.feedburner.com/podcast",
            "  http://feeds.feedburner.com/podcast  ".normalizeFeedUrl()
        )
    }
}
