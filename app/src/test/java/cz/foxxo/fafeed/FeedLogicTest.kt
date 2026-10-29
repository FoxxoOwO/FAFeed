package cz.foxxo.fafeed

import cz.foxxo.fafeed.data.FeedItem
import cz.foxxo.fafeed.data.FeedMerge
import cz.foxxo.fafeed.data.FeedPlanner
import cz.foxxo.fafeed.data.FeedStore
import cz.foxxo.fafeed.data.SyncMode
import cz.foxxo.fafeed.data.UserScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedLogicTest {

    private fun item(id: Long, author: String = "someone", rating: String = "general") = FeedItem(
        id = id,
        title = "t$id",
        author = author,
        authorDisplay = author,
        rating = rating,
        type = "image",
        thumbUrl = "https://t.furaffinity.net/$id@600-1.jpg",
        width = 200f,
        height = 200f,
    )

    @Test
    fun `merge dedupes, sorts newest first and caps the list`() {
        val existing = listOf(item(10), item(9), item(8))
        val incoming = listOf(item(11), item(10), item(7))

        val merged = FeedMerge.merge(existing, incoming, limit = 4)

        assertEquals(listOf(11L, 10L, 9L, 8L), merged.map { it.id })
    }

    @Test
    fun `first scan seeds only the newest items, later scans take everything above the cursor`() {
        val gallery = listOf(item(5), item(7), item(6), item(1))

        val seeded = FeedMerge.takeNew(gallery, previous = null, seedPerUser = 3)
        assertEquals(listOf(7L, 6L, 5L), seeded.map { it.id })

        val incremental = FeedMerge.takeNew(gallery, previous = UserScan(lastId = 5, scannedAt = 0), seedPerUser = 3)
        assertEquals(listOf(7L, 6L), incremental.map { it.id })

        val nothingNew = FeedMerge.takeNew(gallery, previous = UserScan(lastId = 7, scannedAt = 0), seedPerUser = 3)
        assertTrue(nothingNew.isEmpty())
    }

    @Test
    fun `full sync walks the whole watchlist stalest first`() {
        val users = listOf("a", "b", "c", "d")
        val scans = mapOf(
            "a" to UserScan(1, 300),
            "b" to UserScan(1, 100),
            "c" to UserScan(1, 200),
        )

        val selection = FeedPlanner.select(users, scans, SyncMode.FULL, startIndex = 0, quickWindow = 2)

        assertEquals(listOf("d", "b", "c", "a"), selection)
    }

    @Test
    fun `quick sync takes a rotating window and wraps around`() {
        val users = (1..10).map { "u$it" }

        val first = FeedPlanner.select(users, emptyMap(), SyncMode.QUICK, startIndex = 0, quickWindow = 4)
        assertEquals(listOf("u1", "u2", "u3", "u4"), first)

        val wrapped = FeedPlanner.select(users, emptyMap(), SyncMode.QUICK, startIndex = 8, quickWindow = 4)
        assertEquals(listOf("u9", "u10", "u1", "u2"), wrapped)

        assertEquals(10, FeedPlanner.select(users, emptyMap(), SyncMode.QUICK, startIndex = 0, quickWindow = 99).size)
    }

    @Test
    fun `stored items survive a json round trip`() {
        val items = listOf(item(42, "feve", "adult"), item(41, "marplecat"))

        val restored = FeedStore.parseItems(FeedStore.itemsToJson(items))

        assertEquals(items, restored)
        assertTrue(restored.first().isNsfw)
        assertFalse(restored.last().isNsfw)
    }

    @Test
    fun `broken cache json degrades to an empty feed`() {
        assertTrue(FeedStore.parseItems("not json").isEmpty())
        assertTrue(FeedStore.parseItems("").isEmpty())
    }
}