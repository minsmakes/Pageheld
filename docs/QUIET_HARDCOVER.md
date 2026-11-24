# Quiet Hardcover

Pageheld opens as a clothbound journal. In landscape, Today pairs the daily reflection with an editable page. The daily page resumes the most recent entry from the current day, or starts a draft. Opening an empty draft creates no database row. Journal’s + action remains available for additional entries on the same day.

## Using the book

- Tap or pull the cover from right to left. Release a short drag to let the cover settle back. The book icon in the running head closes it.
- Drag a finger inward from a paper edge to turn. Reverse direction before releasing to cancel. The arrows beside the folio and the five chapter tabs provide tap alternatives.
- Journal places its searchable, date-filterable index opposite the selected entry. A pencil opens the full editor. Long entries continue vertically on the reading page. The editor retains templates, undo and redo, weather, mood, ordered places, per-entry paper, and media placement.
- Tap the top ribbon for bookmarked entries. The Journal entry menu and editor page options manage bookmarks.
- Memories mounts photographs and films from journal attachments. Tap a print to open the attachment. The facing page lists places. Its filter menu opens the map. Compact windows have Album and Places controls.
- Chapters lists the notebook sections and user-created chapters. You holds a writable ownership bookplate, writing preferences, mood history, and encrypted import and export.

## Rendering and state

The board uses a shared, deterministic woven tile, a recessed joint, foil-colored lettering, and the Pageheld sprout. The closed proportions follow the size of one leaf. During opening, the hinge moves from the edge of the centered closed volume to the center of the spread. The board rotates around that hinge in perspective. Its inside face carries the facing leaf as it comes to rest.

The open book combines a raised back board, layered fore-edges, curved paper trim, a binding shadow, and separate paper and fabric textures. The running heads, folios, ribbon and tabs belong to the book container. The layout responds to window constraints, system insets, keyboard visibility and font scale. Both APK editions use the responsive renderer.

During a turn, Compose records the outgoing and incoming spreads into two graphics layers. The renderer integrates a curved sheet from spine to fore-edge, with different bend angles at the grabbed and opposite corners. It projects mesh vertices in perspective and maps triangles onto the front or reverse face. The stationary halves come from their respective spreads. The shade changes with the local slope. Rendering uses reusable geometry buffers and display lists without per-frame bitmap capture.

The committed location identifies the section, entry, reading or editing mode, chapter, and bookmark filter. The transient turn controller owns one destination until a drag completes or cancels. The stable location and cover state survive activity recreation. Android’s animator setting and the notebook’s Reduce motion preference bypass the animated turn.

Room remains the source of journal records. DataStore holds the optional bookplate name and opening-hint state alongside existing preferences. The database version, migrations, backup format, private attachment storage, application ID, product flavors, and signing configuration carry forward.

## Source changes

All production Kotlin files below are in `app/src/main/java/app/pageheld/`.

| Files | Change |
| --- | --- |
| `Hardcover.kt` (added) | Material and dimension tokens, textile shader, botanical artwork, boards, ribbon, curved trim, and binding shading |
| `BookTurn.kt` (added) | Saveable location, gesture arbitration, transition state, and projected sheet mesh |
| `BookPages.kt` (added) | Reflection, reading continuation, scrapbook, contents, bookplate, and shared journal search |
| `NotebookSpread.kt`, `BookMotion.kt` | Responsive container, physical fittings, reversible cover, folios, and system motion setting |
| `MainActivity.kt` | Navigation and production-data integration |
| `Design.kt`, `Typography.kt` | Paper palette, serif heading hierarchy, font defaults, and writing rules |
| `Editor.kt`, `Journal.kt` | Inline daily editor, compact controls, date lists, and editing or viewing media from a leaf |
| `Memories.kt`, `Chapters.kt` | Place and album navigation, chapter management, ownership, and personal settings |
| `app/src/androidTest/java/app/pageheld/HardcoverNavigationTest.kt` (added) | Cover, reversible drag, navigation, and writing regression test |
| `app/src/androidTest/java/app/pageheld/NotebookExperienceTest.kt` | Media-drag distance adapts to tablet width. The test waits for image decoding and uses accessible actions for animated sheet controls |
| `README.md`, `docs/IMPLEMENTATION_STATUS.md`, this report, and `docs/design/screenshots/hardcover/` | Build notes, verification, and running-app captures |

## Build and install

Use JDK 21, Android SDK Platform 37, and the checked-in Gradle wrapper. In PowerShell at the repository root, use the following commands. The JDK path shown is the one available on the review workstation.

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\corretto-21.0.4"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleTabletDebug
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb devices -l
& $adb -s TABLET_SERIAL install -r .\app\build\outputs\apk\tablet\debug\app-tablet-debug.apk
& $adb -s TABLET_SERIAL shell am start -n app.pageheld/.MainActivity
```

Replace `TABLET_SERIAL` with the tablet’s identifier. The debug APK works for a fresh install or an update with the same signer. The connected Galaxy Tab S8 Ultra has a release-signed installation with a different certificate. To update that installation and retain the journal, use Android Studio’s **Generate Signed App Bundle or APK**, choose **APK**, the original Pageheld signing key, and **tabletRelease**. Install that signed APK with the same `install -r` command. An uninstall erases the app’s private journal.

## Verification

The review used the API 36 PageheldReview emulator. The landscape configuration was 1920 × 1200 at 220 dpi. The compact portrait configuration was 864 × 1920 at 360 dpi. These are captures from the running application with the emulator’s existing review entries.

[Cover](design/screenshots/hardcover/cover.png) · [Partly open cover](design/screenshots/hardcover/opening.png) · [Today](design/screenshots/hardcover/today.png) · [Journal](design/screenshots/hardcover/journal.png) · [Held page turn](design/screenshots/hardcover/turn.png) · [Memories](design/screenshots/hardcover/memories.png) · [Chapters](design/screenshots/hardcover/chapters.png) · [Bookplate](design/screenshots/hardcover/ownership.png) · [Portrait](design/screenshots/hardcover/portrait.png) · [Portrait keyboard](design/screenshots/hardcover/portrait-writing.png)

- Both debug editions assembled. The JVM test tasks reported `NO-SOURCE`. The Android suite exercised Room migrations, database reopen, backup round-trips, attachment movement and removal, location search mapping, and the new touch-navigation and typing regression. The runner reported `OK (7 tests)` in both window configurations. One of those seven is the existing opt-in capture test, which is assumption-skipped by default. The screenshots in this report were taken separately with ADB.
- Before installation and after exercising the new interface, the ordered contents of the five Page rows, four Chapter rows, and three DiaryLocation rows produced identical SHA-256 fingerprints. Opening an empty Today draft added no entry.
- Manual checks included reversing a cover drag, holding a sheet mid-turn, turning forward from “A calm Sunday” to “Study progress” and back, using the ribbon to open the bookmarked index, and showing and dismissing the keyboard, and rotating the compact writing page into a landscape spread. Journal search remained reachable at 130% system font scaling with system animations disabled.
- Tablet debug lint completed with **0 errors and 6 warnings**. Warnings concern target SDK level, backup declarations, launcher resources, the application-context singleton, and a bitmap helper. The redesign preserves the existing manifest and database configuration.
- During eight chapter jumps, Android `dumpsys gfxinfo` recorded 246 frames, 62 janky frames (25.2%), a 32 ms median, and a 113 ms 90th percentile. This was a debug build on a host-GPU emulator. Stable 60 FPS remains an open performance target.

## Remaining work

The mesh approximates a bending sheet. Local shading can reveal facets around a tight fold. Compact mode shows a paper reverse as the sheet leaves the single-page viewport. Long entries use scroll continuation and a single entry folio. Dedicated typeset pagination remains a future enhancement.

The physical tablet was locked and has a different signing certificate. Physical-tablet frame pacing, S Pen input, full TalkBack traversal, and OEM split-screen behavior still need device verification. Profile the signed build with frame timing on the target tablet before tuning mesh density or reducing rendering quality.

Persistent handwriting is a separate milestone. It needs a stroke format, backup and restore coverage, pressure and eraser input, undo and redo, and palm-rejection testing. The current page-turn recognizer accepts finger input and yields the writing surface to its editor and media gestures.
