# FA Feed

_English? See [README.md](README.md)._ Seznam změn: [CHANGELOG.md](CHANGELOG.md) (anglicky).

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
- **Čeština + English** – všechny texty jsou v `strings.xml` (`values/` = anglicky,
  `values-cs/` = česky). Jazyk jde přepnout v Nastavení (Systém / Čeština / English)
  a funguje i na starších Androidech; na Androidu 13+ navíc appka nabízí jazykovou volbu
  v systémovém nastavení přes `locales_config.xml`.
- **Offline start** – poslední feed se cachuje do SharedPreferences, takže se appka
  otevře okamžitě i bez sítě.
- **Cache náhledů** – náhledy jdou na disk (limit 512 MB, `cacheDir/coil3_disk_cache`),
  takže se při scrollování zpět ani po restartu appky nestahují znovu; pár karet
  dopředu se přednačítá. Velikost je vidět v Nastavení a jde smazat.
- **Animace** – přechod feed ↔ detail, zmáčknutí karty, plynulé rozplynutí NSFW závoje
  a crossfade podtitulku v top baru.
- **Cache na účet** – každý účet si pamatuje svůj feed; při přepnutí se obnoví a znovu se synchronizuje jen pokud jsou data stará.
- **Záloha** – nastavení, watchlist, skrytí autoři a názvy účtů jde exportovat do JSON a znovu obnovit. Cookies a hesla se neukládají.
- **Více účtů** – několik FA přihlášení je uložených šifrovaně; při přepnutí se cache feedu
  smaže a znovu načte, aby se obsahy účtů nemíchaly. Nový účet přidáš přímo v Nastavení.
- **Volba motivu** – světlý / tmavý / systém a volitelně barvy z tapety (Material You) na
  Androidu 12+. Změna v Nastavení se projeví okamžitě.
- **Deep linky** – odkaz `furaffinity.net/view/...` otevře detail a `/user/...` galerii autora
  přímo v appce (jen HTTPS, intent filter v manifestu).
- **Nové od poslední kontroly** – příspěvky přidané od posledního „označit jako přečtené“
  mají odznak NOVÉ a počet v horní liště; klepnutím na počet se označí vše a skočí se nahoru,
  otevření jednoho příspěvku smaže jen jeho odznak.
- **Obnovení po Cloudflare** – když sync narazí na ověření, otevře se WebView, kde kontrolu
  dokončíš ručně; výsledné cookies a User-Agent se pak znovu použijí. Žádné automatické
  obcházení ochrany.
- **Bezpečná session** – cookies FurAffinity jsou v `EncryptedSharedPreferences`; staré
  plaintext cookies se při prvním čtení přenesou.
- **Skrytí autoři** – seznam v Nastavení skryje příspěvky vybraných účtů.
- **Profily autorů** – klepnutím na autora ve feedu nebo v detailu se otevře celá jeho
  galerie s možností načítat starší příspěvky.
- **Hledání a filtry** – prohledávání načtených příspěvků podle autora nebo názvu a filtr
  podle ratingu (GENERAL / MATURE / ADULT). Filtry jsou lokální a kombinují se safe režimem.
- **Aktualizace** – jednou denně při startu appka zkontroluje nejnovější GitHub release,
  stáhne nový APK a nabídne instalaci. V Nastavení je ruční kontrola a vypínač; „Později“
  přeskočí konkrétní verzi. Více v anglickém [README](README.md#updates).

## Architektura

```
app/src/main/java/cz/foxxo/fafeed/
├── FaApp.kt              # Application, Coil ImageLoader (Referer + cookies, disk cache)
├── MainActivity.kt
├── data/
│   ├── FaParser.kt       # jsoup parser FA "beta" layoutu (bez Android závislostí)
│   ├── FaClient.kt       # OkHttp + cookies
│   ├── UpdateChecker.kt  # GitHub releases API, porovnání verzí, APK asset
│   ├── UpdateRepository.kt # stažení APK do cache s progressem
│   ├── FeedRepository.kt # synchronizace watchlistu (galerie + kurzory + rotace)
│   ├── FeedStore.kt      # lokální store, JSON serializace, merge/planner helpery
│   ├── Models.kt         # FeedItem, FeedPage, SubmissionDetail
│   ├── Prefs.kt          # cookies, uživatel, přepínače, cache, jazyk
│   └── SecureStorage.kt  # šifrované cookies + migrace z plaintextu
└── ui/
    ├── AppRoot.kt        # login / feed / detail / profil + deep linky
    ├── DeepLinks.kt      # parser https://www.furaffinity.net/view|/user
    ├── AppLocale.kt      # přepnutí jazyka v appce
    ├── LoginScreen.kt    # WebView login + ověření cookies
    ├── ChallengeScreen.kt # ruční Cloudflare kontrola + předání cookies
    ├── WebAuth.kt        # sdílený DOM probe pro WebView
    ├── FeedScreen.kt     # LazyColumn + PullToRefresh + nekonečný scroll + přednačítání
    ├── SettingsSheet.kt  # NSFW přepínače, jazyk, sync watchlistu, složka, cache, aktualizace
    ├── UpdateViewModel.kt # stav kontroly/stahování a předání instalatéru
    ├── UpdateDialog.kt   # dialog aktualizace, progress, instalace
    ├── DetailScreen.kt
    ├── ProfileScreen.kt  # galerie autora se stránkováním
    ├── ProfileViewModel.kt
    ├── ZoomableImageDialog.kt
    ├── UiUtils.kt        # ukládání obrázků, otevírání FA v prohlížeči
    ├── FeedViewModel.kt
    └── theme/Theme.kt
```

Parsování stojí na marku FA: `figure[id^=sid-]` s `figcaption`, rating v classách
`r-general|r-mature|r-adult`, thumbnail `//t.furaffinity.net/{id}@200-{ts}.jpg`
(přepisuje se na `@600`).

## Build

```bash
./gradlew :app:assembleDebug      # JDK 17+, AGP 9.5.1, Gradle 9.7.1, compileSdk 36
./gradlew :app:testDebugUnitTest  # 36 unit testů: parser, feed, cookies, aktualizace, deep linky, záloha
./gradlew :app:assembleDebugAndroidTest  # Compose UI testy (ke spuštění potřebují zařízení)
./gradlew :app:assembleRelease            # R8-minifikovaný, podepsaný release klíčem
```

`local.properties` s `sdk.dir=...` použij lokálně; v CI stačí `ANDROID_HOME`. Build type
`release` zapíná R8 (`proguard-rules.pro` potlačuje compile-time anotace Tinku); CI dál
publikuje debug build jako `app-debug.apk`.

## CI

GitHub Actions (`.github/workflows/android.yml`) při každém pushi do `main`, PR a ručním
spuštění spustí unit testy, sestaví debug APK a nahraje ho jako artifact
`FAFeed-debug-apk`. Když pushneš tag `v*`, APK se přiloží i k GitHub Release.

Podpisové údaje se neukládají do repozitáře. Lokálně patří do `local.properties`
(gitignored): `signing.keystore`, `signing.storePassword`, `signing.keyAlias`,
`signing.keyPassword`. CI je čte z GitHub Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` a `KEY_PASSWORD`; bez nich použije záložní `app/debug.keystore`.

Verze do 1.3.0 byly podepsané debug klíči (před 1.2.0 se klíč v CI generoval pokaždý
jiný, od 1.2.0 se používal `app/debug.keystore`). Přechod na release klíč vyžaduje
jednorázovou odinstalaci a ztrátu dat; další aktualizace už půjdou normálně.

## Omezení

- FurAffinity nemá veřejné API; appka parsuje HTML. Když FA změní layout, upraví se
  `FaParser.kt` a testy to odhalí (fixture `app/src/test/resources/fa_browse_real.html`).
- Účet musí mít v FA nastavení zapnutý nový (beta) motiv – selectors jsou z něj.
- Sledování (watchlist) se v appce needituje, jen se čte feed.