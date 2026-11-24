# Pageheld implementation status

The current checkout implements [Quiet Hardcover](QUIET_HARDCOVER.md). Its report covers the new navigation, rendering, verification, and device limits. The release notes below describe earlier builds.


## Delivered

The app opens to Today with a five-tab navigation bar. Its separate full-screen editor uses real Compose text fields, the Android keyboard, Room autosave, undo and redo for text edits, and a persistent private copy of each selected photo or video. Closing a new page without editing leaves the journal empty.

| Area | Implemented behavior |
| --- | --- |
| Today | Time-dependent greeting, date, new page, daily mood, bookmarks, anniversary lookup across prior years, recent entries |
| Journal | Monthly grouping, title/body/place search, day picker, diary/memory/gratitude filters, bookmarks, open and delete |
| Editor | Editable title and multi-paragraph body, measured text baselines, scrolling, native selection, six paper styles, seven templates, three fonts, five colors, spacing and margin choices |
| Memories | Interactive MapLibre map, ordered per-page places with manual pins or one-time foreground GPS, photo markers, place preview, grouped saved entries, search, attachment filter and local list |
| Chapters | Four original programmatically drawn cover designs, create, rename, change cover, move up/down, associate pages, real counts and delete while retaining pages |
| You | Writing defaults, recent daily check-ins, privacy information and portable encrypted export/import through the Android document picker |

The editor stores a paper-style override per page. Color, font, spacing and margin are notebook preferences. Applying a template to an already-written page preserves the existing prose. Android can supply the system visual picker or its document-picker fallback.

## Storage and backup

Room owns the journal database. DataStore owns preferences. Imported media lives in the application's private files directory. The app requests Internet access for the map and requests foreground location access only for a user-initiated fix. It does not request broad media-library access or background location.

Backups include pages, chapters, media, appearance and mood history. A fresh random salt and PBKDF2-HMAC-SHA256 derive key material from the passphrase. Tink streaming AES-GCM-HKDF protects the archive. Restore authenticates the archive before extracting or updating Room, validates archive member names, and inserts journal records in a transaction. Existing IDs are retained on merge. Archive content is limited to 256 MiB with a 32 MiB manifest limit.

## Visual verification

The two design references were inspected before implementation. The five-phone overview guided the layouts inside the phones. The separate paper image guided the warm neutral palette and fine rules. All backgrounds, controls, cover illustrations and notebook rules are rendered at runtime.

A dedicated API 36 emulator (`PageheldReview`, 864 × 1920, 360 dpi) was used for phone-size inspection. Both empty and populated screens were captured. The software-rendered emulator disconnected twice during capture. The final pass used host GPU rendering and completed on the same dedicated AVD. Comparison led to larger media thumbnails and more breathing room between journal rows, a larger title-to-body interval, measured baseline spacing, chip icons, a botanical title detail, book-cover ribbons and a lower map preview. The map uses real cartography, so its streets, labels and colors differ from the illustrated reference. The photo markers represent actual attachments. Its initial camera defaults to Berlin when no saved pin is available.

The screenshots show temporary journal and chapter fixtures on the review emulator. Production has no seeded entries, chapters, locations or photos. Photography used for these review fixtures came from Unsplash image IDs `photo-1441974231531-c6227db76b6e`, `photo-1470770841072-f978cf4d019e`, `photo-1490730141103-6cac27aaab94`, and `photo-1455390582262-044cdead277a`.

[Five-screen emulator overview](design/screenshots/overview.png) · [Individual captures](design/screenshots/) · [Approved overview](design/references/pageheld-ui-overview.png) · [Paper reference](design/references/notebook-paper-reference.png)

## Build and tests

The build used Gradle 9.6.0, JDK 21 and Android SDK Platform 37. These tasks passed:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest
```

Lint reported 0 errors and 9 warnings. The JVM unit-test task reported `NO-SOURCE`. The Android instrumentation suite was installed and executed on the dedicated emulator:

```text
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w app.pageheld.test/androidx.test.runner.AndroidJUnitRunner
OK (3 tests)
```

The suite covers zero, one and multiple ordered locations, edit and removal, database reopen, version-1 migration, linked backup media, Room chapter deletion retaining pages, encrypted backup and restore with attachment bytes, matching-ID import behavior, incorrect passphrases and authenticated corruption detection. Interactive verification covered multiline entry, switching paper style, force-stop/relaunch, restoring the saved body and paper, Android photo import and full-size viewing, chapter creation, video picker import and playback, applying the Gratitude template, manual place selection, and listing the saved page under its place. With Wi-Fi and mobile data disabled, the local place list opened a saved entry and displayed its private photo. Connectivity was restored afterwards.

## Location update verification

Room version 2 migrates existing places in place. On the populated emulator the five original page rows and four chapter rows retained their pre-upgrade fingerprint. The three original coordinate pairs appeared as map pins. A temporary entry kept its text and two reordered locations across a cold restart. One location came from a map tap and one from a simulated GPS fix with one-time permission. Tapping its marker opened the matching place and entry. The temporary entry was removed. The Samsung SM-S911B received the debug update in place and launched successfully.

[Journal](design/screenshots/journal-refined-review.png) · [Editor](design/screenshots/editor-refined-review.png) · [Paper styles](design/screenshots/appearance-refined-review.png) · [Memory map](design/screenshots/memories-refined-review.png)

## Search, typography and notebook motion

The picker searches the device geocoder on submission and suggests previously saved coordinates locally while typing. It sends the entered query only. Selecting a result recenters the map. The user may move the pin before confirming. There is no billing configuration or API key. Availability and result quality depend on the device geocoder.

The five bottom tabs handle navigation. Mongmongdays regular is the default writing face, with Kalam, serif and sans alternatives. Libre Caslon Text regular and italic preserve the editorial headings on devices that substitute their own system serif. The opening cover supports horizontal dragging or tapping. The editor header arrows turn pages within a chapter or journal result set. You provides separate opening-cover and reduced-motion switches. Android’s system animation setting is respected.

Five instrumentation tests passed on the emulator, including bilingual editor rendering and geocoder result mapping. A separate capture test ran on the Samsung SM-S911B. The phone was updated in place. During manual verification, submitted searches for Berlin and Brandenburg Gate returned named results with addresses. An adjusted pin and a second saved location survived a cold restart. Offline saved-place suggestions, tap and swipe opening, background return, disabled cover, reduced motion, chapter page turns and paper selection were exercised.

[Phone cover](design/screenshots/phone-notebook-cover.png) · [Phone header](design/screenshots/phone-today-header.png) · [Bilingual editor](design/screenshots/bilingual-notebook.png) · [Location search](design/screenshots/place-search-notebook.png)

Files for this increment include `PlaceSearch.kt`, `Locations.kt`, `Memories.kt`, `BookMotion.kt`, `MainActivity.kt`, `Editor.kt`, `Journal.kt`, `Design.kt`, `Chapters.kt`, `Backup.kt`, the manifest, three font resources and license notices, `NotebookExperienceTest.kt`, `CoverCaptureTest.kt`, the README and these verification captures. The Room schema is unchanged by this increment.

## Remaining scope

Time Capsules are deferred. The Today bell opens the daily check-in summary. Scheduled notifications are outside this build. Map browsing needs network tiles or an existing renderer cache. The saved-place list works from the local database. Verification focused on the API 36 phone emulator. API 28, large-screen devices, TalkBack and maximum-size backup stress tests remain unverified.

## File inventory

The root Gradle scripts and wrapper initialize the Android project. Kotlin implementation files are under `app/src/main/java/app/pageheld/`.

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Activity, theme, navigation and editor routing |
| `Notebook.kt` | Room schema, DAO, ordered persistence, DataStore and date helpers |
| `Design.kt` | Paper rendering, design tokens, covers, icons and shared controls |
| `Editor.kt` | Native writing surface, toolbar, customization, media picker and viewers |
| `Journal.kt` | Today, Journal and shared entry rows |
| `Memories.kt` | Map lifecycle, pins, place chooser, previews and local place list |
| `Chapters.kt` | Chapter management and You screen |
| `Backup.kt` | Portable backup format, storage picker and import/export UI |

Additional files include `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/extraction.xml`, individual Lucide icon vectors in `app/src/main/res/drawable/`, `app/src/main/res/font/kalam.ttf`, and `app/src/androidTest/java/app/pageheld/NotebookTest.kt`. Documentation comprises the README, this status file, screenshots, and the Kalam and Lucide license files. The supplied design kit was preserved.

