package cz.foxxo.fafeed

import cz.foxxo.fafeed.ui.FaDeepLink
import cz.foxxo.fafeed.ui.parseFaDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {
    @Test
    fun viewLinkOpensSubmission() {
        assertEquals(
            FaDeepLink.Submission(123456L),
            parseFaDeepLink("https://www.furaffinity.net/view/123456/"),
        )
        assertEquals(
            FaDeepLink.Submission(42L),
            parseFaDeepLink("https://furaffinity.net/view/42/?foo=bar"),
        )
    }

    @Test
    fun userLinkOpensProfile() {
        assertEquals(
            FaDeepLink.Profile("foxxo"),
            parseFaDeepLink("https://www.furaffinity.net/user/Foxxo/"),
        )
    }

    @Test
    fun unrelatedHostsAreIgnored() {
        assertNull(parseFaDeepLink("https://example.com/view/1/"))
        assertNull(parseFaDeepLink("http://www.furaffinity.net/view/1/"))
        assertNull(parseFaDeepLink(null))
    }

    @Test
    fun missingOrInvalidIdsAreIgnored() {
        assertNull(parseFaDeepLink("https://www.furaffinity.net/view/"))
        assertNull(parseFaDeepLink("https://www.furaffinity.net/view/abc/"))
        assertNull(parseFaDeepLink("https://www.furaffinity.net/gallery/someone/"))
    }
}
