package cz.foxxo.fafeed

import cz.foxxo.fafeed.data.UpdateChecker
import cz.foxxo.fafeed.data.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun versionParsesTagAndPadsMissingParts() {
        assertEquals(Version(listOf(1, 1, 2)), Version.parse("v1.1.2"))
        assertEquals(Version(listOf(2, 0)), Version.parse("V2.0-rc1"))
        assertNull(Version.parse("not-a-version"))
    }

    @Test
    fun versionComparisonHandlesDifferentLengths() {
        assertTrue(Version.parse("v1.2")!! > Version.parse("1.1.9")!!)
        assertEquals(0, Version.parse("v1.1")!!.compareTo(Version.parse("v1.1.0")!!))
        assertFalse(Version.parse("v1.1.1")!! > Version.parse("v1.1.1")!!)
    }

    @Test
    fun parseReleaseFindsApkAsset() {
        val json = """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://github.com/FoxxoOwO/FAFeed/releases/tag/v1.2.0",
              "body": "Fix stuff",
              "assets": [
                {"name": "source.tar.gz", "browser_download_url": "https://example.com/src", "size": 12},
                {"name": "app-debug.apk", "browser_download_url": "https://example.com/app.apk", "size": 9876543}
              ]
            }
        """.trimIndent()
        val release = UpdateChecker.parseRelease(json)
        assertNotNull(release)
        assertEquals("v1.2.0", release!!.tag)
        assertEquals("1.2.0", release.version.toString())
        assertEquals("https://example.com/app.apk", release.apkUrl)
        assertEquals(9_876_543L, release.sizeBytes)
        assertEquals("Fix stuff", release.notes)
    }

    @Test
    fun parseReleaseRejectsReleaseWithoutApk() {
        val json = """{"tag_name":"v9.9.9","assets":[{"name":"notes.txt","browser_download_url":"https://x"}]}"""
        assertNull(UpdateChecker.parseRelease(json))
        assertNull(UpdateChecker.parseRelease("{ broken json"))
    }

    @Test
    fun isNewerUsesCurrentVersionName() {
        val release = UpdateChecker.parseRelease(
            """{"tag_name":"v1.1.3","assets":[{"name":"app-debug.apk","browser_download_url":"https://x"}]}""",
        )!!
        assertTrue(UpdateChecker.isNewer(release, "1.1.2"))
        assertFalse(UpdateChecker.isNewer(release, "1.1.3"))
        assertFalse(UpdateChecker.isNewer(release, "1.2.0"))
    }
}
