package cz.foxxo.fafeed

import cz.foxxo.fafeed.data.Backup
import cz.foxxo.fafeed.data.BackupData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    @Test
    fun roundTripKeepsSettingsAndLists() {
        val original = BackupData(
            exportedAt = 1234L,
            safeOnly = true,
            blurNsfw = false,
            lang = "cs",
            theme = "dark",
            dynamicColor = true,
            watchUsers = listOf("alice", "bob"),
            mutedUsers = setOf("spam", "ads"),
            accountNames = listOf("foxxo"),
            downloadTreeUri = "content://tree",
            downloadTreeName = "FAFeed",
        )

        val parsed = Backup.parse(Backup.serialize(original))

        assertEquals(original, parsed)
    }

    @Test
    fun invalidJsonOrVersionIsRejected() {
        assertNull(Backup.parse("not json"))
        assertNull(Backup.parse("""{"version":99}"""))
        assertTrue(Backup.parse("""{"version":1}""")!!.watchUsers.isEmpty())
    }
}
