package cz.foxxo.fafeed

import cz.foxxo.fafeed.data.FaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaParserTest {

    private val feedHtml = """
        <!DOCTYPE html>
        <html><head><title>Fur Affinity</title></head>
        <body>
        <div id="my-username"><img class="loggedin_user_avatar" alt="FoxxoOwO" src="//a.furaffinity.net/foxxoowo.gif"></div>
        <section id="gallery-browse" class="gallery s-200">
          <figure id="sid-66792410" class="r-general t-image u-electricwindglockfist">
            <b><u><a href="/view/66792410/">
              <img class="blocked-content" alt="" src="//t.furaffinity.net/66792410@200-1792768906.jpg"
                   data-width="172.423" data-height="200" loading="lazy" />
            </a></u></b>
            <figcaption>
              <p><a href="/view/66792410/" title="Ursula plush">Ursula plush</a></p>
              <p><i>by</i> <a href="/user/electricwindglockfist/" title="ElectricWindGlockFist">ElectricWindGlockFist</a></p>
            </figcaption>
          </figure>
          <figure id="sid-66792408" class="r-adult t-image u-patrostupid">
            <b><u><a href="/view/66792408/">
              <img src="//t.furaffinity.net/66792408@200-1792768881.jpg" data-width="200" data-height="200" />
            </a></u></b>
            <figcaption>
              <p><a href="/view/66792408/" title="fishing spooky">fishing spooky</a></p>
              <p><i>by</i> <a href="/user/patrostupid/" title="PatroStupid">PatroStupid</a></p>
            </figcaption>
          </figure>
        </section>
        <div class="floatright"><form method="get" action="/">
            <input type="hidden" name="page" value="2">
            <button class="button" type="submit">Next</button>
        </form></div>
        </body></html>
    """.trimIndent()

    private val submissionHtml = """
        <!DOCTYPE html>
        <html><head><title>Ursula plush -- Fur Affinity</title>
        <meta property="og:url" content="https://www.furaffinity.net/view/66792410/">
        </head><body>
        <div id="submission_page" class="page-content-type-image">
          <div class="submission-title"><h2>Ursula plush</h2></div>
          <div class="submission-description-header">
            <span class="popup_date" data-time="1792768906">Oct 23, 2026 09:21 PM</span>
          </div>
          <div class="submission-description-artist">
            <a href="/user/electricwindglockfist/">ElectricWindGlockFist</a>
          </div>
          <img id="submissionImg" src="//d.furaffinity.net/art/x/1792768906.jpg"
               data-preview-src="//t.furaffinity.net/66792410@200-1792768906.jpg"
               data-fullview-src="//d.furaffinity.net/art/x/1792768906.jpg" />
          <div class="submission-page-stats">
            <div><div>1,234</div><div>Views</div></div>
            <div><div>56</div><div>Comments</div></div>
            <div><div>789</div><div>Faves</div></div>
            <div><div>General</div><div>Rating</div></div>
          </div>
          <div class="submission-tags">
            <a data-tag-name="bear" href="/search/@keywords bear">bear</a>
            <a data-tag-name="plush" href="/search/@keywords plush">plush</a>
          </div>
          <div class="submission-description-text">
            First line<br />Second line with &amp; entity<br /><br />After gap
          </div>
          <div id="submission-options">
            <a href="//d.furaffinity.net/download/x/1792768906.jpg">Download</a>
            <a href="/fav/66792410/">+Fav</a>
          </div>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `feed items are parsed newest first`() {
        val page = FaParser.parseFeed(feedHtml, "https://www.furaffinity.net/")

        assertEquals(2, page.items.size)
        assertEquals(66792410L, page.items[0].id)
        assertEquals(66792408L, page.items[1].id)
        assertEquals("Ursula plush", page.items[0].title)
        assertEquals("ElectricWindGlockFist", page.items[0].authorDisplay)
        assertEquals("electricwindglockfist", page.items[0].author)
        assertEquals("general", page.items[0].rating)
        assertEquals("adult", page.items[1].rating)
        assertTrue(page.items[1].isNsfw)
        assertEquals("https://t.furaffinity.net/66792410@600-1792768906.jpg", page.items[0].thumbUrl)
        assertEquals("FoxxoOwO", page.loggedInUser)
        assertEquals(false, page.loginRequired)
    }

    @Test
    fun `next page url is built from the pager form`() {
        val page = FaParser.parseFeed(feedHtml, "https://www.furaffinity.net/")
        assertEquals("https://www.furaffinity.net/?page=2", page.nextUrl)
    }

    @Test
    fun `login wall is detected`() {
        val html = "<html><head><title>Login Required -- Fur Affinity [dot] net</title></head><body></body></html>"
        val page = FaParser.parseFeed(html, "https://www.furaffinity.net/")
        assertTrue(page.loginRequired)
        assertEquals(0, page.items.size)
    }

    @Test
    fun `submission detail is parsed`() {
        val detail = FaParser.parseSubmission(submissionHtml, "https://www.furaffinity.net/view/66792410/")

        assertEquals(66792410L, detail.id)
        assertEquals("Ursula plush", detail.title)
        assertEquals("electricwindglockfist", detail.author)
        assertEquals("ElectricWindGlockFist", detail.authorDisplay)
        assertEquals("https://d.furaffinity.net/art/x/1792768906.jpg", detail.imageUrl)
        assertEquals(1792768906L, detail.dateEpoch)
        assertEquals("1,234", detail.views)
        assertEquals("789", detail.favorites)
        assertEquals("56", detail.comments)
        assertEquals(listOf("bear", "plush"), detail.tags)
        assertEquals("https://d.furaffinity.net/download/x/1792768906.jpg", detail.downloadUrl)
        assertTrue(detail.description.startsWith("First line"))
        assertTrue(detail.description.contains("Second line with & entity"))
        assertTrue(detail.description.contains("\n"))
    }

    @Test
    fun `thumbnail upscale only touches the size marker`() {
        assertEquals(
            "https://t.furaffinity.net/66792410@600-1792768906.jpg",
            FaParser.upscaleThumb("https://t.furaffinity.net/66792410@200-1792768906.jpg"),
        )
        assertEquals(
            "https://a.furaffinity.net/foxxoowo.gif",
            FaParser.upscaleThumb("https://a.furaffinity.net/foxxoowo.gif"),
        )
    }

    @Test
    fun `real fur affinity page parses end to end`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("fa_browse_real.html")
            ?: error("fixture missing")
        val html = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        val page = FaParser.parseFeed(html, "https://www.furaffinity.net/browse/")

        assertTrue("expected a full page of cards, got ${page.items.size}", page.items.size >= 40)
        assertTrue(page.items.all { it.id > 0L })
        assertTrue(page.items.all { it.thumbUrl.startsWith("https://t.furaffinity.net/") })
        assertTrue(page.items.all { it.thumbUrl.contains("@600-") })
        assertTrue(page.items.all { it.author.isNotBlank() && it.title.isNotBlank() })
        assertTrue("newest first", page.items.first().id > page.items.last().id)
        assertNull(page.loggedInUser)
    }

    @Test
    fun `watchlist page yields usernames and the next page`() {
        val html = """
            <html><head><title>Fur Affinity</title></head><body>
            <div class="section-body watch-list">
              <div class="watch-list-items watch-row inline">
                ~<span class="c-usernameBlockSimple"><a href="/user/-Riska-/" target="_blank">-Riska-</a></span>
              </div>
              <div class="watch-list-items watch-row new-section inline">
                ~<span class="c-usernameBlockSimple"><a href="/user/Aimi/" target="_blank">Aimi</a></span>
              </div>
              <div class="watch-list-items watch-row inline">
                ~<span class="c-usernameBlockSimple"><a href="/user/akulaenaya/" target="_blank">Akulaenaya</a></span>
              </div>
            </div>
            <div class="mini-nav"><a href="/watchlist/by/foxxoowo/2/" rel="next">Next</a></div>
            </body></html>
        """.trimIndent()

        val page = FaParser.parseWatchlist(html, "https://www.furaffinity.net/watchlist/by/foxxoowo/")

        assertEquals(listOf("-riska-", "aimi", "akulaenaya"), page.users)
        assertEquals("https://www.furaffinity.net/watchlist/by/foxxoowo/2/", page.nextUrl)
    }

    @Test
    fun `gallery page figures are parsed for the watchlist scan`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("fa_browse_real.html") ?: error("fixture missing")
        val html = stream.use { it.readBytes().toString(Charsets.UTF_8) }

        val items = FaParser.parseFigures(html, "https://www.furaffinity.net/gallery/foo/")

        assertTrue(items.size >= 40)
        assertEquals(items.map { it.id }.sortedDescending(), items.map { it.id })
    }

    @Test
    fun `empty feed reports no next page`() {
        val page = FaParser.parseFeed("<html><body></body></html>", "https://www.furaffinity.net/")
        assertNull(page.nextUrl)
        assertNotNull(page.items)
    }
}
