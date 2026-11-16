package com.tsumo.app

import com.tsumo.app.data.Feed
import com.tsumo.app.data.OpenMode
import com.tsumo.app.data.ReadChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun feedDisplayTitlePrefersCustomTitle() {
        val f = Feed(
            id = 1L,
            url = "https://aeon.co/feed.rss",
            title = "Aeon Magazine",
            customTitle = "Aeon",
        )
        assertEquals("Aeon", f.displayTitle)
    }

    @Test
    fun openModeRoundTripsWireValue() {
        assertEquals(OpenMode.CUSTOM_TAB, OpenMode.fromWire("custom_tab"))
        assertEquals(OpenMode.BROWSER, OpenMode.fromWire("browser"))
        assertEquals(OpenMode.READER, OpenMode.fromWire("unknown"))
    }

    @Test
    fun readChannelKeysAreDistinct() {
        val f = Feed(id = 7L, url = "https://example.com/rss", title = "Example")
        assertEquals("all", ReadChannel.All.key)
        assertEquals("tag:History", ReadChannel.Tag("History").key)
        assertEquals("feed:7", ReadChannel.FeedChannel(f).key)
    }
}
