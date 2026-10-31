# FA Feed

Nativní Android aplikace (Kotlin + Jetpack Compose), která z FurAffinity udělá klasický
sociální feed: posty od sledovaných uživatelů, nejnovější nahoře, s nekonečným
scrollováním.

## Co umí

- **Feed od sledovaných** – nestojí na notifikacích, ale na samotném watchlistu:
  appka si načte seznam sledovaných (`/watchlist/by/{user}/`) a u každého si přečte
  první stránku galerie (`/gallery/{user}/`). Nové příspěvky pozná podle kurzoru
  (nejvyšší známé submission id), takže se nic neduplikuje.
  - Notifikační fronta `/msg/submissions/` se používá jen jako rychlá vložka, když
    v ní něco je – **vyčištění notifikací už feed nevymaže** (ověřeno: 240 z 312
    příspěvků přišlo z galerií, ne z notifikací).
  - Běžná synchronizace projde rotující dávku 60 sledovaných (~20 s), takže se celý
    watchlist protočí během několika obnovení. V Nastavení je i „Synchronizovat celý
    watchlist“ (269 účtů, ~90 s, ověřeno 269/269).
  - Feed se ukládá lokálně (do 800 příspěvků), takže po startu je hned vidět a řadí se
    od nejnovějších bez ohledu na to, co FA zrovna má v notifikacích.
- **Přihlášení** – ve WebView (Cloudflare Turnstile i případné 2FA zvládne prohlížeč).
  Cookies `a` + `b` (a `cf_clearance`) se uloží a posílají se dál jako hlavička `Cookie`.
- **Karta postu** – avatar autora, jméno, rating (GENERAL / MATURE / ADULT), náhled
  v rozlišení 600 px, titulek, odkaz na FA.
- **Detail** – plný obrázek (`data-fullview-src`), autor, datum, popis, statistiky,
  tagy, tlačítko Stáhnout a Otevřít na FA.
- **Zoom** – klepnutím na obrázek v detailu se otevře fullscreen prohlížeč: pinch zoom
  (1×–6×), dvojklik 2.5×, tažení pro posun (posun je omezený, aby obrázek neuplaval),
  klepnutí / X zavře.
- **Složka pro stahování** – v Nastavení jde vybrat libovolnou složku přes systémový
  picker (SAF tree, oprávnění se ukládá). Bez volby se stahuje do `Stahování/FAFeed`
  přes DownloadManager; s vlastní složkou appka stahuje sama (OkHttp + cookies)
  a zapisuje přes SAF.
- **NSFW** – volitelné skrytí (safe režim) a rozmazání náhledů do klepnutí.
- **Čeština + English** – všechny texty jsou v `strings.xml` (`values/` = anglicky, `values-cs/` = česky). Jazyk jde přepnout v Nastavení (Systém / Čeština / English) a funguje i na starších Androidech; na Androidu 13+ navíc appka nabízí jazykovou volbu v systémovém nastavení přes `locales_config.xml`.
- **Offline start** – poslední feed se cachuje do SharedPreferences, takže se appka
- **Cache náhledů** – náhledy jdou na disk (limit 512 MB, `cacheDir/coil3_disk_cache`), takže se při scrollování zpět ani po restartu appky nestahují znovu; pár karet dopředu se přednačítá. Velikost je vidět v Nastavení a jde smazat.
  otevře okamžitě i bez sítě.

## Architektura

```
app/src/main/java/cz/foxxo/fafeed/
├── FaApp.kt              # Application, Coil ImageLoader (Referer + cookies)
├── MainActivity.kt
├── data/
│   ├── FaParser.kt       # jsoup parser FA "beta" layoutu (bez Android závislostí)
│   ├── FaClient.kt       # OkHttp + cookies
│   ├── FeedRepository.kt # synchronizace watchlistu (galerie + kurzory + rotace)
│   ├── FeedStore.kt      # lokální store, JSON serializace, merge/planner helpery
│   ├── Models.kt         # FeedItem, FeedPage, SubmissionDetail
│   └── Prefs.kt          # cookies, uživatel, přepínače, cache
└── ui/
    ├── AppRoot.kt        # login / feed / detail
    ├── LoginScreen.kt    # WebView login + ověření cookies
    ├── FeedScreen.kt     # LazyColumn + PullToRefresh + nekonečný scroll
    ├── SettingsSheet.kt  # NSFW přepínače, složka pro stahování, odhlášení
    ├── DetailScreen.kt
    ├── FeedViewModel.kt
    └── theme/Theme.kt
```

Parsování stojí na marku FA: `figure[id^=sid-]` s `figcaption`, rating v classách
`r-general|r-mature|r-adult`, thumbnail `//t.furaffinity.net/{id}@200-{ts}.jpg`
(přepisuje se na `@600`).

## Build

```bash
./gradlew :app:assembleDebug      # JDK 17+, AGP 9.4.1, Gradle 9.7.1, compileSdk 36
./gradlew :app:testDebugUnitTest  # 15 testů: parser + logika watchlist feedu
```

`local.properties` s `sdk.dir=...` použij lokálně; v CI stačí `ANDROID_HOME`.

## CI

GitHub Actions (`.github/workflows/android.yml`) při každém pushi do `main`, PR a ručním
spuštění spustí unit testy, sestaví debug APK a nahraje ho jako artifact
`FAFeed-debug-apk`. Když pushneš tag `v*`, APK se přiloží i k GitHub Release.

## Omezení

- FurAffinity nemá veřejné API; appka parsuje HTML. Když FA změní layout, upraví se
  `FaParser.kt` a testy to odhalí (fixture `app/src/test/resources/fa_browse_real.html`).
- Účet musí mít v FA nastavení zapnutý nový (beta) motiv – selectors jsou z něj.
- Sledování (watchlist) se v appce needituje, jen se čte feed.
