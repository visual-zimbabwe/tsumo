package com.tsumo.app

import com.tsumo.app.data.BookItem
import com.tsumo.app.data.HistoryItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun bookItemFormattingMatchesEditorialSpec() {
        val eapiBook = BookItem(
            id = "12345",
            commandId = "",
            hash = "abc123",
            name = "Norwegian Wood",
            authors = listOf("Haruki Murakami"),
            year = "1987",
            extension = "epub",
            size = "1.4 MB",
        )
        assertEquals("12345:abc123", eapiBook.effectiveId)
        assertEquals("Haruki Murakami", eapiBook.authorsDisplay)
        assertEquals("1987", eapiBook.yearDisplay)
        assertEquals("EPUB · 1.4 MB", eapiBook.packageDisplay)

        val zeroYearBook = eapiBook.copy(year = "0", extension = "", size = "")
        assertEquals("—", zeroYearBook.yearDisplay)
        assertEquals("—", zeroYearBook.packageDisplay)
    }

    @Test
    fun historyItemConvertsToBookItem() {
        val hist = HistoryItem(
            id = "OarG6e3pPD",
            commandId = "OarG6e3pPD",
            name = "The Metamorphosis",
            extension = "pdf",
            size = "0.8 MB",
            date = "2026-11-14",
        )
        val book = hist.toBookItem()
        assertEquals("OarG6e3pPD", book.effectiveId)
        assertEquals("PDF · 0.8 MB", book.packageDisplay)
    }
}
