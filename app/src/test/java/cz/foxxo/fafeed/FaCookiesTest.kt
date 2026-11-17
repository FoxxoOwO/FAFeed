package cz.foxxo.fafeed

import cz.foxxo.fafeed.data.FaCookies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaCookiesTest {

    @Test
    fun mergeCombinesBothHosts() {
        val www = "b=www-session; a=www-user"
        val login = "csrftoken=abc; sessionid=login-session"

        val merged = FaCookies.merge(listOf(www, login))

        assertTrue(merged.contains("b=www-session"))
        assertTrue(merged.contains("a=www-user"))
        assertTrue(merged.contains("csrftoken=abc"))
        assertTrue(merged.contains("sessionid=login-session"))
    }

    @Test
    fun mergePrefersFirstHostOnNameClash() {
        val merged = FaCookies.merge(
            listOf("sessionid=www-value; b=www-session", "sessionid=login-value"),
        )

        assertEquals("sessionid=www-value; b=www-session", merged)
    }

    @Test
    fun mergeSkipsMissingHostsAndBlankParts() {
        assertEquals("b=1", FaCookies.merge(listOf(null, "", "  ", "b=1")))
        assertEquals("", FaCookies.merge(listOf(null, "")))
    }

    @Test
    fun mergeKeepsValuesContainingEquals() {
        val merged = FaCookies.merge(listOf("token=a=b=c"))
        assertEquals("token=a=b=c", merged)
    }

    @Test
    fun hasSessionRejectsCloudflareOnlyJar() {
        val jar = FaCookies.merge(
            listOf(
                "cf_clearance=xyz; __cf_bm=abc; _cfuvid=uv; csrftoken=tok; cf_chl_2=1; fa-cache-clear=1",
            ),
        )
        assertFalse(FaCookies.hasSession(jar))
        assertFalse(FaCookies.hasSession(""))
    }

    @Test
    fun hasSessionAcceptsLegacyAndSsoCookies() {
        assertTrue(FaCookies.hasSession("b=session"))
        assertTrue(FaCookies.hasSession("cf_clearance=xyz; a=user; b=session"))
        assertTrue(FaCookies.hasSession("sessionid=abc123"))
    }
}
