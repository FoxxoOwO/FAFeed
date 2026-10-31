# FA Feed

_Česky? Přečti si [README.cs.md](README.cs.md)._

Native Android app (Kotlin + Jetpack Compose) that turns FurAffinity into a classic
social feed: posts from the accounts you follow, newest first, with endless scrolling.

## What it does

- **Feed from the accounts you follow** – it does not rely on notifications but on the
  watchlist itself: the app loads your watch list (`/watchlist/by/{user}/`) and reads the
  first gallery page of every account (`/gallery/{user}/`). New posts are recognised by a
  cursor (the highest known submission id), so nothing is duplicated.
  - The notification queue `/msg/submissions/` is used only as a quick insert when it has
    something in it – **clearing notifications no longer empties the feed** (verified: 240
    of 312 posts came from galleries, not from notifications).
  - A normal sync walks a rotating batch of 60 followed accounts (~20 s), so the whole
    watchlist cycles within a few refreshes. Settings also offers "Sync the whole
    watchlist" (269 accounts, ~90 s, verified 269/269).
  - The feed is stored locally (up to 800 posts), so it is visible immediately after
    launch and sorted newest-first no matter what FA currently has in notifications.
- **Sign-in** – in a WebView (Cloudflare Turnstile and any 2FA are handled by the
  browser). Cookies `a` + `b` (and `cf_clearance`) are stored and sent as the `Cookie`
  header.
- **Post card** – author avatar, name, rating (GENERAL / MATURE / ADULT), 600 px
  thumbnail, title, link to FA.
- **Detail** – full image (`data-fullview-src`), author, date, description, stats, tags,
  Download and Open on FA buttons.
- **Zoom** – tapping the image in the detail opens a fullscreen viewer: pinch zoom
  (1×–6×), double-tap 2.5×, drag to pan (panning is constrained so the image does not
  drift), tap / X to close.
- **Download folder** – any folder can be picked in Settings through the system picker
  (SAF tree, the permission is persisted). Without a pick, downloads go to
  `Downloads/FAFeed` via DownloadManager; with a custom folder the app downloads itself
  (OkHttp + cookies) and writes through SAF.
- **NSFW** – optional hiding (safe mode) and blurring of previews until tapped.
- **Czech + English** – all strings live in `strings.xml` (`values/` = English,
  `values-cs/` = Czech). The language can be switched in Settings
  (System / Čeština / English) and works on older Androids too; on Android 13+ the app
  also appears in the system per-app language settings via `locales_config.xml`.
- **Offline start** – the last feed is cached in SharedPreferences, so the app opens
  instantly even without a network.
- **Thumbnail cache** – thumbnails are cached on disk (512 MB limit,
  `cacheDir/coil3_disk_cache`), so scrolling back or restarting never re-downloads them;
  a few cards ahead are prefetched. The size is shown in Settings and can be cleared.
- **Animations** – feed ↔ detail transitions, card press feedback, a smooth NSFW veil
  fade, and a crossfade in the top bar subtitle.

## Architecture

```
app/src/main/java/cz/foxxo/fafeed/
├── FaApp.kt              # Application, Coil ImageLoader (Referer + cookies, disk cache)
├── MainActivity.kt
├── data/
│   ├── FaParser.kt       # jsoup parser for the FA "beta" layout (no Android deps)
│   ├── FaClient.kt       # OkHttp + cookies
│   ├── FeedRepository.kt # watchlist sync (galleries + cursors + rotation)
│   ├── FeedStore.kt      # local store, JSON serialisation, merge/planner helpers
│   ├── Models.kt         # FeedItem, FeedPage, SubmissionDetail
│   └── Prefs.kt          # cookies, user, switches, cache, language
└── ui/
    ├── AppRoot.kt        # login / feed / detail routing
    ├── AppLocale.kt      # in-app language override
    ├── LoginScreen.kt    # WebView login + cookie verification
    ├── FeedScreen.kt     # LazyColumn + PullToRefresh + endless scroll + prefetch
    ├── SettingsSheet.kt  # NSFW switches, language, watchlist sync, folder, cache
    ├── DetailScreen.kt
    ├── ZoomableImageDialog.kt
    ├── UiUtils.kt        # saving images, opening FA in a browser
    ├── FeedViewModel.kt
    └── theme/Theme.kt
```

Parsing relies on FA's markup: `figure[id^=sid-]` with `figcaption`, the rating in
classes `r-general|r-mature|r-adult`, the thumbnail
`//t.furaffinity.net/{id}@200-{ts}.jpg` (rewritten to `@600`).

## Build

```bash
./gradlew :app:assembleDebug      # JDK 17+, AGP 9.4.1, Gradle 9.7.1, compileSdk 36
./gradlew :app:testDebugUnitTest  # 15 tests: parser + watchlist feed logic
```

Use a local `local.properties` with `sdk.dir=...`; in CI `ANDROID_HOME` is enough.

## CI

GitHub Actions (`.github/workflows/android.yml`) runs on every push to `main`, on pull
requests and on manual dispatch: it runs the unit tests, builds the debug APK and uploads
it as the `FAFeed-debug-apk` artifact. Pushing a `v*` tag also attaches the APK to a
GitHub Release.

Release APKs are debug-signed with the CI keystore, so they cannot upgrade over a debug
APK you built locally – uninstall that one first.

## Limitations

- FurAffinity has no public API; the app parses HTML. If FA changes its layout,
  `FaParser.kt` has to be updated and the tests catch it (fixture
  `app/src/test/resources/fa_browse_real.html`).
- The account must have the new (beta) theme enabled in the FA settings – the selectors
  come from it.
- The watchlist cannot be edited in the app, it is only read to build the feed.