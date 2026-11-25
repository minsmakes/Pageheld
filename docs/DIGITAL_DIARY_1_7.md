# Pageheld. 1.7 · Digital Diary

November 24, 2026 · Android package `app.pageheld` · version code 8

## Screens and interaction

Today opens on the device’s local day. Its recent list uses saved titles, dates and attachments. Today’s diary opens the shared editor. Diaries contains the sage cloth cover, chapter spread, searchable collection, bookmarks, templates and notes. Custom chapter IDs continue to own their entries. Unfiled entries appear in month groups calculated from the existing creation timestamps.

Moments opens the live MapLibre map with photo pins, a print strip, search, filters and an offline saved-place list. Tapping the profile icon opens the ownership bookplate, paper, typography, media privacy and encrypted backup controls.

The editor keeps Back, the tab menu, title, saving status and Done above a single upper toolbar. The sheets beneath them reuse the original projected paper fold. Short windows show one page. Landscape tablet windows show two. Tapping a page number opens a page chooser. More includes manual page creation, entry title, template, chapter, appearance, bookmarking and deletion.

The pen panel offers four tips, width and ink color. Pen and eraser input stay on the active sheet. Photographs have a Polaroid mount, a selection outline and resize handles. Drag and pinch change a print’s placement. Long-press opens the viewer, including existing video playback and confirmed attachment removal. Mood, weather and recent places have toolbar panels. Detailed location editing retains the existing map, search, GPS, visit details and media links.

## Design comparison

The full-resolution screenshots use the actual Compose UI and isolated review entries. Production ships an empty notebook. The gallery includes the main screens, writing modes, selection panels and keyboard. [Screenshot gallery](design/screenshots/v1.7/overview.jpg)

| Reference detail | Implementation |
| --- | --- |
| Clover lettering and warm paper | Four heart-shaped leaflets, softly shaded paper grain, sage accents and the approved 온글잎 몽몽데이즈몽글동심체. Android supplies missing glyphs. Existing personal font overrides remain available. |
| Physical cover | Generated wooden desktop and edge decorations behind an independently rendered, tappable linen cover with stitching, spine, elastic, owner subtitle and current year. |
| Open notebook | Two facing sheets, curved trim, edge highlights, a shaded binding and native page-fold animation. The index print uses an actual attachment or a clover placeholder. |
| Writing and selection | Editable text, independently rendered strokes, page-local prints, anchored tools and visible folios. The map and photographs reflect real places and media. |

The approved installed font, download and bundled TTF have the same SHA-256 digest, `4f3312fa44ed2569d20781ce25f107a7d392a4d394e5454c7112d297aa1dfd1f`. [Publisher listing](https://www.ownglyph.com/trial/a9e36c1b-9960-47be-864a-06626c9a0716) · [Embedding and attribution](OWNGLYPH-LICENSE.md)

The storyboard is a hand-drawn composite. The implementation uses resizable native screens, system bars, live map attribution and device keyboards. Ink, paper contours, wood grain, photographs and map geography therefore differ from individual painted pixels. The editor adds a compact navigation menu alongside Back so a writer can switch tabs. The photo viewer, detailed place manager and settings retain their existing Android controls.

## Storage and compatibility

Room schema 5 adds two columns to the existing Page table. The 4→5 migration gives existing records an empty layout and completed status. Migrations from schemas 1, 2 and 3 still run in sequence. No table is dropped or re-keyed.

An entry keeps one primary key and one canonical body. Text measurement derives automatic breaks for the current sheet width, typeface and whitespace settings. A form feed records an explicit page break. Version 1 of the layout stores the current sheet, cursor offset, tool, tip, width, color, per-sheet pressure samples and normalized print placement. Changing text size can change which words sit beside a page-anchored drawing. Legacy print placement retains its original fields and is adapted to the new sheet coordinates when opened.

Room writes serialize through the notebook queue. The saving label acknowledges the matching write. Done waits for its final write, then returns to the chapter. A failed write keeps the in-memory edit available for retry. Encrypted archives include draft status and the new layout. On restore, print placements and location links follow renamed attachment files. Existing IDs are retained. User photos are copied into private storage and editing changes their notebook presentation.

## Files

| Area | New or modified files |
| --- | --- |
| Main screens | `MainActivity.kt`, `DiaryHome.kt`, `Journal.kt`, `Chapters.kt`, `Memories.kt`, `BookPages.kt` |
| Editor and persistence | `DiaryEditor.kt`, `WritingSpread.kt`, `DiaryDocument.kt`, `Editor.kt`, `Notebook.kt`, `Backup.kt` |
| Materials and motion | `Hardcover.kt`, `BookTurn.kt`, `Design.kt`, `Typography.kt`, `diary_desk.jpg`, `i_eraser.xml`, `ic_diary_clover.xml`, adaptive launcher icon |
| Build and verification | App Gradle configuration, Android manifest, six Android instrumentation test files, README, implementation history, this report and screenshot gallery |

## Release checks

Both editions assembled in debug and release configurations. Both release APKs were signed locally and verified with Android’s `apksigner`. Their signer matches the existing Pageheld release certificate. `aapt` reports `app.pageheld`, version name 1.7 and version code 8 for both. [GitHub release](https://github.com/minsmakes/Pageheld/releases/tag/v1.7)

| Local installer | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/release/v1.7/Pageheld.apk` | `20F5B9181E2210ECC2F8D2ACE2AC84180F465CA8D22E80A71C4C10A1F62176B5` |
| `app/build/outputs/apk/release/v1.7/Pageheld-Tablet.apk` | `87908935D6E274BEA7B1771B990B3F6670CD2450ADAF5768E79D94D8174D1D42` |

| Environment | Result |
| --- | --- |
| Android 16 tablet-sized emulator, 1920 × 1200 | AndroidJUnitRunner reported `OK (10 tests)`. The portrait-only capture is opt-in. The suite exercised mixed Korean and English text, automatic pagination, returning to edit earlier text, manual page creation, tab switching, completion, pen strokes, erase, undo/redo, mood, weather, photo dragging, two-finger enlargement and reduction, confirmed attachment removal, migration and backup restore. |
| Android 16 phone emulator, 864 × 1920 | The opt-in portrait capture test passed separately. Today, cover and chapter views were captured with normal phone dimensions. |
| Galaxy Tab S8 Ultra, SM-X900, Android 16 | Six storage tests passed on-device. They cover old-schema migration, location ordering, encrypted restore, text boundaries, reopening a ten-sheet mixed-media draft, idempotent completion and an injected SQLite write failure followed by successful retry. |
| Android lint | Both debug editions completed with zero errors and seven warnings each. The reports cover target SDK, backup declarations, launcher assets, application context and a bitmap helper. The JVM test tasks reported no source. |

The November 25 comparison used the supplied editor screenshot and a fresh capture at the same displayed size. The toolbar, page bounds, center fold, folios and photo placement align. The release uses a localized save acknowledgement and slightly stronger paper grain. [Tablet editor capture](design/screenshots/v1.7/typed.png)

With the software keyboard visibly open, the instrumentation check measured the same toolbar bounds and writable-sheet width as before the keyboard opened. The longer writing test pasted mixed Korean and English text, used a page button and a finger swipe to turn between automatically generated spreads, inserted text on an earlier sheet, added a manual page, switched tabs, resumed and completed the same database entry.

The phone and tablet screenshots use the confirmed default font. On a device with an explicitly saved personal font, the profile typography controls retain that choice. The keyboard and page-turn captures document emulator behavior. The date was observed on two successive local dates. An uninterrupted midnight rollover was not separately exercised. Samsung Keyboard composition, S Pen pressure calibration, actual palm contacts and frame pacing during a physical tablet writing session still need hands-on verification. The Galaxy Tab was locked during the device run.

Native text selection applies within the active sheet. Extending one selection by dragging through the gutter into another text field remains a limitation. Layout is measured against the current viewport. Changing paper dimensions or font size can reflow an entry. Very large notebooks remain subject to Android memory, database row size and the existing encrypted archive limits.
