package com.tsumo.app

import com.tsumo.app.data.BookItem
import com.tsumo.app.data.HistoryItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun bookItemFormattingMatchesEditorialSpec() {
        val book = BookItem(
            id = "2680",
            commandId = "gutenberg:2680",
            source = "gutenberg",
            name = "Meditations",
            authors = listOf("Marcus Aurelius"),
            year = "180",
            extension = "epub",
            size = "0.5 MB",
        )
        assertEquals("gutenberg:2680", book.effectiveId)
        assertEquals("Marcus Aurelius", book.authorsDisplay)
        assertEquals("180", book.yearDisplay)
        assertEquals("EPUB · 0.5 MB", book.packageDisplay)

        val zeroYearBook = book.copy(year = "0", extension = "", size = "")
        assertEquals("—", zeroYearBook.yearDisplay)
        assertEquals("—", zeroYearBook.packageDisplay)
    }

    @Test
    fun historyItemConvertsToBookItem() {
        val hist = HistoryItem(
            id = "gutenberg:2680",
            commandId = "gutenberg:2680",
            source = "gutenberg",
            name = "Meditations",
            extension = "epub",
            size = "0.5 MB",
            date = "2026-11-14",
        )
        val book = hist.toBookItem()
        assertEquals("gutenberg:2680", book.effectiveId)
        assertEquals("gutenberg", book.source)
        assertEquals("EPUB · 0.5 MB", book.packageDisplay)
    }
}
