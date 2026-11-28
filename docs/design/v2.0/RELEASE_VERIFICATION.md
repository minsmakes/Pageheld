# Memospine. 2.0 release candidate

Min's Makes • 28 November 2026 • Android 9 and later • phone and tablet

Both signed release installers use `app.pageheld`, version **2.0 (13)**. Pageheld 1.9.2 used version code 12, the highest found among the archived distributed APKs. Android accepted an in-place upgrade with a real journal entry. The tablet candidate runs on an emulator and has been compared with all three approved references. Visual differences and outstanding device checks keep the candidate below production acceptance.

## Acceptance decision

| Gate | Decision | Evidence and outstanding work |
| --- | --- | --- |
| A · Functional | IN PROGRESS | Real navigation, shelf paging, Studio save/discard, private photo import, pen input and archive restoration exercised. Physical camera, video playback, GPS, S Pen and palm rejection still need a device pass. |
| B · Visual | BLOCKED | The three comparisons below show the corrected build. Thin shelves still lack depth, the Studio lacks several reference materials and ornaments, and the page stack is too flat. See the [artwork requests](ARTWORK_REQUIREMENTS.md). |
| C · Production | IN PROGRESS | Both flavors build and pass their applicable emulator suites. The signed phone and tablet upgrades retained a journal entry. Hardware, accessibility and performance acceptance remain open. |

## Implementation and migration

| Area | Implementation | Source |
| --- | --- | --- |
| Identity and device scope | The launcher and wordmark use Memospine. Both editions retain the clover, namespace, typography preferences and four themes. The tablet flavor uses Library on devices with a smallest width of at least 600 dp. The phone retains the single notebook. | MainActivity.kt, Design.kt, Typography.kt, AndroidManifest.xml, values and values-ko strings, app/build.gradle.kts |
| Library and navigation | Room owns each book's title, order, two cover designs and last-read entry. Today, chapters and Moments resolve through the active book. Daily IDs include the book for new journals. Library offers three shelves, nine slots, paging and management with confirmation for deletion. | LibraryData.kt, TabletLibrary.kt, DiaryHome.kt, Notebook.kt |
| Studio and artwork | One renderer draws shelf, Studio and opening covers. Independent layer IDs retain position, angle, width, opacity, ordering, text and font. Frame placement fits a selected photograph to the aperture and preserves both objects. Front and back are separately editable. | BookSurface.kt, BookStudio.kt, CoverDesign.kt, Artwork.kt, BookMedia.kt |
| Writing | The existing editor, document, ink and page-turn systems remain in use. Tablet controls occupy the top row. Page decorations use logical sheet IDs and a separate editing mode. | DiaryEditor.kt, WritingSpread.kt, DiaryDocument.kt, DiarySheets.kt, PageDecorations.kt, Hardcover.kt |
| Data preservation | Room migration 5 → 6 adds LibraryBook, indexes and a default owner to Page and Chapter. Existing primary keys, entry text, layout, handwriting, media placement and locations remain in place. Version 1 data follows the existing migration chain. Production database callbacks enforce ownership on subsequent writes. | LibraryData.kt, Notebook.kt, LibraryTest.kt, LocationPersistenceTest.kt |
| Backup and restore | The versioned encrypted manifest carries books, covers, entries, chapters, places, settings, font files and media. Restore also reads PAGEHELD1 archives. File references are remapped across covers and sheet decorations. Invalid archives fail before acceptance. | Backup.kt, BookMedia.kt, NotebookTest.kt, LibraryTest.kt |

## Visual verification

The [measurement sheet](VISUAL_SPECIFICATION.md) records reference dimensions, viewport crops and layout ratios. The following comparisons use actual Android screenshots from a 1672 × 941 API 36 emulator at 200 dpi. They align application widths and preserve aspect ratios. The full captures retain Android system bars. The review books and the default notebook created by the phone compatibility run belong to the emulator.

| Screen | Observed correction | Remaining difference | Evidence |
| --- | --- | --- | --- |
| Library | Rebalanced material luminance and weave scale, moved the navigation and shelf bands, increased clearance above covers, and restored round arrows. | The wood face, surrounding shadows and relief remain shallower. Several reference books use missing ornaments. | [Comparison](verification/library/comparison.png) · [Full capture](verification/library/actual.png) · [Review](verification/library/review.md) |
| Book Studio | Enlarged the live board, corrected swatch dimensions and workspace material, and rendered ribbon and border choices. The heading reads the saved book title. | Three supplied materials occupy the six-material reference panel. Botanical corner relief, ambient desk detail and bookmark extension differ. | [Comparison](verification/book_studio/comparison.png) · [Full capture](verification/book_studio/actual.png) · [Review](verification/book_studio/review.md) |
| Open book | Condensed the top toolbar, exposed the hardcover around the pages and added the center ribbon in the book's saved color. | Paper curvature, edge depth and lighting are less pronounced. The demonstration entry text, ink and artwork are user content. | [Comparison](verification/open_book/comparison.png) · [Full capture](verification/open_book/actual.png) · [Review](verification/open_book/review.md) |

[Empty library](verification/library/empty.png) · [Second shelf page](verification/library/second.png) · [New Book](verification/library/new.png) · [Lettering](verification/book_studio/text.png) · [Stickers](verification/book_studio/stickers.png) · [Photo and frame](verification/book_studio/photo.png) · [Back cover](verification/book_studio/back.png) · [Handwriting](verification/open_book/handwriting.png) · [Four theme captures](verification/themes)

## Artwork and offline use

The original pack and catalog are under [source_assets](source_assets/Pageheld_Asset_Pack_Vol01/). The runtime catalog lives in `app/src/main/assets/artwork/vol01/` beside `materials/`, `materials/tint_sources/`, `stickers/`, `frames/` and `tapes/`. The build packages lossless WebP copies of the three materials, six stickers, open Polaroid frame, two tapes and three neutral helpers. Instrumentation decoded each runtime path. The alpha opening and separate photo/frame handles were inspected. The device imported a real photo through the Android picker and displayed it after saving. Library, Studio and writing use local storage. The emulator exercised Library and writing with airplane mode enabled. The optional online basemap retains its existing network dependency.

The requested suede, gingham fabric, kraft, oak, pressed sprig, clasp and lace have delivery specifications linked above. Mongmongdays remains the bundled UI font. Font and artwork attribution and the install, upgrade and backup guidance are in the [draft release notes](RELEASE_NOTES.md) and [README](../../../README.md).

## Builds, signing and upgrade evidence

JDK 21 and the Gradle wrapper completed the debug and release builds for both editions and assembled both instrumentation APKs. The source is compatible with the original signer. No signing material belongs in this repository.

```powershell
.\gradlew.bat --offline :app:assemblePhoneDebug :app:assembleTabletDebug :app:assemblePhoneRelease :app:assembleTabletRelease :app:assemblePhoneDebugAndroidTest :app:assembleTabletDebugAndroidTest
```

The final build completed 249 actionable tasks (42 executed). Neither flavor defines local JVM unit tests. Android instrumentation supplies the test coverage below.

The local signed candidates are in `app/build/outputs/apk/release/v2.0-candidate/`. `apksigner verify --verbose --print-certs` verified APK Signature Scheme v3 for each file. Both certificate SHA-256 fingerprints match the distributed 1.9.2 signer, `e8e19f1817016d8eb81247c36e1e6c6f3414ed907a45cdb3cb3271d0d69b0b33`. `aapt dump badging` confirmed the package and version in each file.

| Installer | SHA-256 of signed APK |
| --- | --- |
| Memospine.apk | `67050c43aae2bad97ce83d5b7d2c457797d12f2e7202729afdd32713428725cb` |
| Memospine-Tablet.apk | `860b1b05942a354199664e89286e9d2889794966c6c4bbfa508b10c25aafe5bd` |

A separate upgrade emulator installed the distributed Pageheld 1.9.2 phone APK and saved the entry `MemospineUpgradeWitness` through the old editor. Installing the first signed 2.0 phone candidate with `adb install -r` preserved the entry and original first-install timestamp. An in-place switch to the signed 2.0 tablet candidate showed the entry in the migrated book. [Phone witness](verification/phone/actual.png) · [Tablet witness](verification/upgrade/tablet-preserved.png)

The final two APKs listed above were then installed in place on that journal. Both editions displayed the original entry and retained the original Android installation timestamp of 03:34:04. A second, fresh tablet emulator independently installed the distributed **1.9.2 tablet** edition. Its old editor saved `TabletUpgradeWitness`. A direct update to the final signed **2.0 tablet** retained the entry and the 09:55:37 installation timestamp. [Before tablet upgrade](verification/upgrade/tablet-v192.png) · [After tablet upgrade](verification/upgrade/tablet-direct.png)

## Automated checks and performance

The final tablet run reported **OK (27 tests)**. The runner includes assumption-gated capture fixtures. The third-shelf test makes 19 books, duplicates the final design, moves the copy to the previous shelf, rebuilds the Library from saved state and confirms deletion of the original. It checks that a private entry never appears in the copy. The backup test removes the imported font before restoration, confirms the restored bytes and cover font ID, and checks the original legacy book creation time, shelf position and last-read ID.

The final portrait phone run at 864 × 1920 and 360 dpi also reported **OK (27 tests)**. Tablet-only Studio and Library cases are assumption-gated on phone. The launcher opened Today and exposed Diaries. The review tablet books stayed out of phone navigation. The phone journal cover, scrolling toolbar, writing, keyboard, handwriting and save flows were exercised. [Phone runner output](verification/phone-tests.txt) · [Phone cover](verification/phone/cover.png)

Two further checks used the tablet's normal controls. Creating a book called IndependentReviewBook with Leather and Rose opened Studio with its title and chosen cover. After Done, it appeared on the second shelf page. In an existing entry, a Star added through the top writing toolbar was still selectable on its original sheet after saving, leaving the book and reopening it. [Created book in Studio](verification/book_studio/created-book.png) · [Page layer controls](verification/open_book/decoration-controls.png) · [Reopened decoration](verification/open_book/reopened-decoration.png)

The full suite also exercised older database migration, entry isolation, wrong-password rejection, the old encrypted format, chapter and place relationships, pen pressure, erasing, lasso, undo/redo, page turns, durable drafts, photo placement and theme persistence. Some intermediate runs failed. Portrait tests initially tapped the cropped edge of toolbar buttons. The harness now activates the named accessible action. A second issue involved sampling a theme before Android displayed it. The test still checks the saved setting and actual screen pixels. A two-library test originally launched a different Room database during its restoration step. The revised test recreates the fixture composition. Production process restoration was exercised separately on the populated emulator.

With the appropriate debug edition and its instrumentation APK installed, run these commands for the selected review device. The opt-in review library seed is separate from the regression suite and only accepts a fresh disposable emulator.

```powershell
adb -s $tablet shell am instrument -w -e captureLibrary true app.pageheld.test/androidx.test.runner.AndroidJUnitRunner
adb -s $phone shell am instrument -w app.pageheld.test/androidx.test.runner.AndroidJUnitRunner
```

Android gfxinfo sampled seven page transitions through the populated review Library on the API 36 emulator. It reported 339 frames, 27 janky frames (7.96%), 18 ms median, 42 ms at the 95th percentile, and 150 ms at the 99th percentile. Process PSS was 126307 KiB. The second shelf page reopened after a force-stop. The shelf layout was also checked at 1920 × 1200 and 240 dpi. These figures cover a debug build on the review host. Tail latency and physical hardware performance remain open for release. [Timing sample](verification/library/performance.txt) · [Alternate viewport](verification/library/alternate-viewport.png) · [Tablet runner output](verification/tablet-tests.txt) · [APK certificate and version output](verification/signatures.txt)

## Physical device and release scope

A connected Galaxy Tab S8 Ultra contained an existing 1.9.2 installation. All candidate installs used disposable emulator data. Physical pen pressure, palm-resting behavior, camera, GPS, real video playback, thermal performance and TalkBack traversal remain **NOT TESTED** on production hardware. The visual, large-library and storage-pressure checks still open in the [implementation tracker](IMPLEMENTATION_STATUS.md) also apply.

**Remaining work** includes Korean accessibility and error copy in the new editing surfaces, end-to-end object rotation and stacking coverage, large or damaged source-photo fixtures, and storage-pressure testing. Some artwork descriptions and error messages in BookStudio.kt and PageDecorations.kt still use English when the system language is Korean.

GitHub hosts the signed tablet candidate as a pre-release. The latest stable download remains Pageheld 1.9.2. The official v2.0 tag remains reserved for a build that passes release acceptance. Brand and trademark clearance remains unconfirmed. [Tablet preview release](https://github.com/minsmakes/Pageheld/releases/tag/v2.0-rc.1)

Implementation milestones 32170f3, 3a76266 and 80503ba were pushed directly to origin/main. The repository had no Actions release workflow on these pushes.
