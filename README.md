# FA Feed

_Česky? Přečti si [README.cs.md](README.cs.md)._ Release notes: [CHANGELOG.md](CHANGELOG.md).

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
- **Cloudflare recovery** – when a sync is blocked, a WebView opens so you can finish the
  security check manually; the resulting cookies and User-Agent are reused by the scraper.
  There is no automated bypass of bot protection.
- **Secure session** – the FurAffinity cookie is stored in `EncryptedSharedPreferences`;
  existing plaintext cookies are migrated on first read.
- **Hidden authors** – a mute list in Settings hides posts from selected accounts.
- **Author profiles** – tap an author in the feed or in a post detail to open their full
  gallery with "Load older submissions" pagination.
- **Search and filters** – search loaded posts by author or title and filter by rating
  (GENERAL / MATURE / ADULT). Filters are local and combine with safe mode.
- **Updates** – once a day on startup the app checks the latest GitHub release, downloads
  the new APK in the background and lets you install it with one tap. A manual check and
  an on/off switch live in Settings; "Later" skips that particular version.

## Architecture

```
app/src/main/java/cz/foxxo/fafeed/
├── FaApp.kt              # Application, Coil ImageLoader (Referer + cookies, disk cache)
├── MainActivity.kt
├── data/
│   ├── FaParser.kt       # jsoup parser for the FA "beta" layout (no Android deps)
│   ├── FaClient.kt       # OkHttp + cookies
│   ├── UpdateChecker.kt  # GitHub releases API, version comparison, APK asset parsing
│   ├── UpdateRepository.kt # APK download into the app cache with progress
│   ├── FeedRepository.kt # watchlist sync (galleries + cursors + rotation)
│   ├── FeedStore.kt      # local store, JSON serialisation, merge/planner helpers
│   ├── Models.kt         # FeedItem, FeedPage, SubmissionDetail
│   ├── Prefs.kt          # cookies, user, switches, cache, language
│   └── SecureStorage.kt  # encrypted cookie store + plaintext migration
└── ui/
    ├── AppRoot.kt        # login / feed / detail routing
    ├── AppLocale.kt      # in-app language override
    ├── LoginScreen.kt    # WebView login + cookie verification
    ├── ChallengeScreen.kt # manual Cloudflare check + cookie hand-back
    ├── WebAuth.kt        # shared WebView DOM probe
    ├── FeedScreen.kt     # LazyColumn + PullToRefresh + endless scroll + prefetch
    ├── SettingsSheet.kt  # NSFW switches, language, watchlist sync, folder, cache, updates
    ├── UpdateViewModel.kt # update check/download state and install hand-off
    ├── UpdateDialog.kt   # update prompt, progress and install confirmation
    ├── DetailScreen.kt
    ├── ProfileScreen.kt  # author gallery with pagination
    ├── ProfileViewModel.kt
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
./gradlew :app:assembleDebug      # JDK 17+, AGP 9.5.1, Gradle 9.7.1, compileSdk 36
./gradlew :app:testDebugUnitTest  # 30 unit tests: parser, feed logic, cookies, updater
./gradlew :app:assembleDebugAndroidTest  # Compose UI smoke tests (needs a device to run)
./gradlew :app:assembleRelease            # R8-minified, signed with the release key
```

Use a local `local.properties` with `sdk.dir=...`; in CI `ANDROID_HOME` is enough.
The `release` build type enables R8 (`proguard-rules.pro` suppresses Tink's compile-time
Error Prone annotations). CI still publishes the debug build type as `app-debug.apk`.

## CI

GitHub Actions (`.github/workflows/android.yml`) runs on every push to `main`, on pull
requests and on manual dispatch: it runs the unit tests, builds the debug APK and uploads
it as the `FAFeed-debug-apk` artifact. Pushing a `v*` tag also attaches the APK to a
GitHub Release.

Signing credentials are never committed. Add these to your local `local.properties`
(gitignored):

```properties
signing.keystore=/path/to/key.jks
signing.storePassword=...
signing.keyAlias=...
signing.keyPassword=...
```

CI reads the same values from GitHub Secrets `KEYSTORE_BASE64` (base64 of the `.jks`
file), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. If the secrets are missing,
the build falls back to `app/debug.keystore`; a release must never be published with
that fallback if earlier release APKs used a different key, because in-app updates
would be rejected as a signature mismatch.

## Updates

The updater talks to `https://api.github.com/repos/FoxxoOwO/FAFeed/releases/latest`
(no FurAffinity cookies are sent there) and looks for an `.apk` asset in the release.
The tag (`v1.2.0`) is compared numerically with the app's `versionName`; if it is newer:

1. On startup, at most once per 24 hours, a dialog offers **Download and install**.
   Settings has a manual **Check for updates** button and an on/off switch for the
   automatic check. **Later** remembers the skipped tag and does not offer it again.
2. The APK is streamed into the app's own cache (`cacheDir/updates`) with a progress
   indicator; no storage permission is required.
3. The file is handed to the system installer via a `FileProvider` URI.

Android does not allow a normal app to install updates silently, so the last step always
shows the system confirmation dialog. On Android 8+ the app also needs "Install unknown
apps" to be allowed for FA Feed; the updater opens that system setting if it is missing.
`REQUEST_INSTALL_PACKAGES` is declared in the manifest.

**Note:** versions up to 1.3.0 were signed with debug keystores (the CI runner generated
a different one before 1.2.0, and `app/debug.keystore` was used from 1.2.0 onwards).
Moving to the release key configured in `local.properties` / GitHub Secrets requires a
one-time uninstall (the app data is lost). All later releases can be updated in place.

## Limitations

- FurAffinity has no public API; the app parses HTML. If FA changes its layout,
  `FaParser.kt` has to be updated and the tests catch it (fixture
  `app/src/test/resources/fa_browse_real.html`).
- The account must have the new (beta) theme enabled in the FA settings – the selectors
  come from it.
- The watchlist cannot be edited in the app, it is only read to build the feed.
- Update checks use the unauthenticated GitHub API (60 requests/hour per IP); automatic
  checks are throttled to once a day, manual checks are up to the user.
- Cloudflare challenges cannot be solved automatically; the app only provides a WebView
  for the user to complete the check, then reuses the resulting cookies.