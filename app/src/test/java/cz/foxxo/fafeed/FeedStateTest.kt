package cz.foxxo.fafeed

import cz.foxxo.fafeed.data.FeedItem
import cz.foxxo.fafeed.ui.FeedState
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedStateTest {

    private fun item(
        id: Long,
        title: String = "Post $id",
        author: String = "artist$id",
        display: String = author,
        rating: String = "general",
    ) = FeedItem(
        id = id,
        title = title,
        author = author,
        authorDisplay = display,
        rating = rating,
        type = "image",
        thumbUrl = "https://t.furaffinity.net/$id@600-1.jpg",
        width = 200f,
        height = 200f,
    )

    @Test
    fun `search matches title author and display name regardless of case`() {
        val items = listOf(
            item(1, title = "Sunset Fox"),
            item(2, author = "foxxo", display = "Foxxo OwO"),
            item(3, title = "Unrelated", author = "someone"),
        )

        assertEquals(listOf(1L, 2L), FeedState(items = items, query = "fox").visibleItems.map { it.id })
        assertEquals(listOf(2L), FeedState(items = items, query = "owo").visibleItems.map { it.id })
    }

    @Test
    fun `rating filter combines with safe mode and search`() {
        val items = listOf(
            item(1, rating = "general"),
            item(2, title = "Fox mature", rating = "mature"),
            item(3, title = "Fox adult", rating = "adult"),
        )

        assertEquals(
            listOf(2L),
            FeedState(items = items, query = "fox", rating = "MATURE").visibleItems.map { it.id },
        )
        assertEquals(emptyList<Long>(), FeedState(items = items, rating = "mature", safeOnly = true).visibleItems.map { it.id })
        assertEquals(listOf(1L), FeedState(items = items, safeOnly = true).visibleItems.map { it.id })
        assertEquals(emptyList<Long>(), FeedState(items = items, query = "nope").visibleItems.map { it.id })
    }
}
