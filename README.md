# FA Feed

Nativní Android aplikace (Kotlin + Jetpack Compose), která z FurAffinity udělá klasický
sociální feed: posty od sledovaných uživatelů, nejnovější nahoře, s nekonečným
scrollováním.

## Co umí

- **Feed od sledovaných** – čte `https://www.furaffinity.net/msg/submissions/`, tedy
  frontu „New submissions“ = jen posty od lidí, které sleduješ (ověřeno: 100 % autorů
  je ve watchlistu). Frontpage `/` se nepoužívá, protože míchá celý web. Řazeno od
  nejnovějších, při scrollování se dotahují starší stránky přes odkaz „Next N“.
  Pozor: FA maže notifikace starší 6 měsíců, hloubka feedu je tedy daná tímhle oknem.
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
- **Offline start** – poslední feed se cachuje do SharedPreferences, takže se appka
  otevře okamžitě i bez sítě.

## Architektura

```
app/src/main/java/cz/foxxo/fafeed/
├── FaApp.kt              # Application, Coil ImageLoader (Referer + cookies)
├── MainActivity.kt
├── data/
│   ├── FaParser.kt       # jsoup parser FA "beta" layoutu (bez Android závislostí)
│   ├── FaClient.kt       # OkHttp + cookies
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
./gradlew :app:testDebugUnitTest  # 7 testů parseru (vč. reálné FA stránky)
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
