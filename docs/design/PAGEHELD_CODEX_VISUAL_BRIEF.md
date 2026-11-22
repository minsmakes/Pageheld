# Pageheld — Codex Visual-Implementation Brief

## Task

You are implementing **Pageheld**, a native **Kotlin + Jetpack Compose** Android app. The two attached local images are the approved visual references:

- `docs/design/references/pageheld-ui-overview.png` — five-phone showcase with screen layouts, components, paper styles, templates and personalization.
- `docs/design/references/notebook-paper-reference.png` — the approved **bright, ivory, subtly ruled paper look**.

**IMPORTANT: Open, view, and inspect BOTH images before changing code.** Do not just infer the design from their filenames. If your tools cannot view images, report that limitation and use a visual-capable route before making strong pixel-fidelity claims.

Your primary task is to recreate the **app screens within the five phone mockups** as a working native Android app, as faithfully as practical. The surrounding decorative banner, brand showcase typography and three feature panels are *presentation materials*, not a literal app screen. Their content describes the editor's customization controls.

Treat the illustration as a high-fidelity product art direction. Match the *layout, spacing, hierarchy, palette, typographic character, component placement, icons, tabs, paper ruling, card radii and density* as closely as native rendering and varied Android devices allow. The generated image can contain minor text-artifacts; use the text and flows below as the functional source of truth. Do not embed the mockup screenshots as UI, put fake controls on top of an image, or claim pixel-perfect fidelity without side-by-side inspection.

## FIRST — Repository safety and baseline

1. Confirm the working directory is the user's already-open `Pageheld` Git repository; inspect files and Git status. Do NOT touch CyWell repositories.
2. Preserve all existing and uncommitted work; avoid destructive Git operations.
3. Follow existing `AGENTS.md`/Gradle instructions if available. Use existing project if present; if empty, initialize a clean Android Studio Kotlin/Compose app.
4. Use a feature branch such as `feat/notebook-ui` where appropriate; do not push or merge without user approval.
5. Present a short plan, then actually implement the project in testable increments.

## Pixel-character design system

- Brand wordmark: lower-case **pageheld.** in elegant editorial/serif type, greenish dark charcoal.
- Page base: bright warm ivory, near `#FDFBF6` or slightly warmer `#FBF7EF`.
- Text: near `#303A34`; muted secondary: `#77776F`.
- Accents: muted sage `#627B69` / deep sage `#526B56`.
- Rules: very fine light-gray/beige lines, approximate `#D3D1CB`, low emphasis.
- Layout: premium editorial minimalism; paper-inspired, no cartoon leather, no spiral decorations, no gradients that overwhelm readability.
- Screen margins: approximately 16–20dp; cards moderately rounded 12–16dp with very soft borders/shadows; consistent vertical rhythm; 48dp accessible tap targets.
- Display subtle horizontal notebook ruling as a **programmatically drawn Compose background** (not an image stretched to each screen). Keep text readable.
- Journal editor: margin/ruling alignment must be correct as text size changes. Use the approved paper image as a *reference*, not as a static writing surface.
- Prefer an accessible native font pair approximating the screenshot: elegant serif for titles; clear sans-serif for controls; optional real licensed handwriting font only for editor where readability permits. Never include unlicensed fonts.
- Bottom bar: five clear minimalist outlined icons, compact labels, muted sage tint on selected tab, Android gesture inset respected.
- Status bar/keyboard/system insets must remain functional. App should be responsive on compact Android phones and adapt reasonably to tablets.

## Exact showcase screen breakdown

### 1. Today screen (phone 1)
- Top brand wordmark `pageheld.` with restrained menu/bookmark/notification affordances.
- Date, warm greeting, and an elegant italic editorial heading: **Your day, your story.**
- A primary `Write today's page` card with simple pen icon and short secondary description.
- `Mood check-in` card showing accessible emotion choices (not just emoji without labels).
- `On This Day` card with a photo thumbnail and past-memory date; show only actual qualifying past entries, otherwise a graceful empty state.
- `Recent Entries` list cards; show actual local entries, not fictional entries in production.
- Gentle green circular `+` create action if it can coexist safely with navigation/insets.
- Bottom navigation showing Today selected.

### 2. Journal screen (phone 2)
- Header: `Journal`, search, calendar action.
- Compact filter chips: `All`, `Diary`, `Memory`, `Gratitude` (expand via filtering, not empty decoration).
- Entries grouped by month; rows with media thumbnails, title, date, two-line preview, contextual actions.
- Add-entry floating action; selected Journal tab.
- Smooth scrolling, search over local text/title, empty states and sensible date grouping.

### 3. Editor screen (phone 3 — MOST IMPORTANT)
- Writing page fills the main content area, warm ivory paper with precise horizontal ruling and a subtle vertical margin line as shown in the showcase, while the user's prior clean paper reference shows the baseline paper texture.
- Small date/time header, editable title (e.g. italic serif/handwriting character), clear body text naturally aligning to ruling.
- Optional mood chip, optional manually chosen location chip.
- Inline media thumbnails and `+` attachment tile.
- A compact bottom editor toolbar: text/paper controls, images, video, place, emotion, chapter/template.
- Real multi-paragraph Compose editor, native IME, cursor/selection, responsive text scaling, scrolling, autosave, and Room persistence.
- Do not fake a handwriting input or make a painted text screenshot. Use reliable native input.
- Keep controls usable when keyboard is visible.

### 4. Memories screen (phone 4)
- Header `Memories` with search and filter.
- Real interactive MapLibre map if available, with legally usable tile source and attribution; no background tracking.
- User **manually** selects/pins a place. Explicitly NO automatic stay detection, passive GPS tracking, background location or location-history permissions.
- Pin markers may feature entry image thumbnails. Tapping pin opens a compact anchored memory preview panel, then its saved entries and photos/videos.
- Multiple entries per place; no location needed for ordinary diary pages.
- Offline/no-network state must still allow browsing saved memories by list/timeline. Do not claim truly offline tiles unless actually implemented.
- Bottom navigation showing Memories selected.

### 5. Chapters screen (phone 5)
- Header `Chapters`, top-right `+`.
- Vertical list of elegant paper/book cover cards with illustration/color area on left, chapter title and real entry count on right, chevron affordance.
- Examples in design: university years, summer adventures, a new beginning, favorite places; do not seed these as user data without explicit onboarding choice.
- Create, edit, view, reorder, and associate entries with chapters; delete chapter without deleting its entries.
- Bottom navigation showing Chapters selected.

### 6. You screen (not depicted as phone)
- Extend EXACT same paper design system.
- Provide personal preferences, customizable paper appearance, journal typography and margins, self-care, privacy explanation, local export/import and about.
- Never introduce a visually unrelated settings dashboard.

## Customization shown beneath the five phones

### Paper styles
Render a real chooser with previews matching image visual language:
- Ruled (default), Blank, Grid, Dot Grid, Letter, Scrapbook.
- Persist preference; each entry can override.
- Changing styles does NOT change or delete entry content.

### Entry templates
- Free Writing, Daily Diary, Memory, Gratitude, Letter, Reflection, Workout Reflection.
- Separate a template's content/fields from the paper's visual style.

### Personalization
- Warm neutral and sage paper swatches, font choice, line spacing, margins.
- Avoid creating inaccessible text or broken baseline alignment as users customize.

## Privacy and real data

- Local-first: Room, DataStore, app-private media files. No mandatory account or server-side journal data.
- No analytics SDK or advertising SDK by default.
- Use Android Photo Picker, explicitly import selected media to private storage. Preserve originals outside app.
- Journal entry CRUD, real storage across process restarts, entries optional mood/place, chapters relationship and timestamps.
- Export/import must include portable photo/video files when implemented. Do not ship unsafe or misleading 'backup complete' buttons. Use Storage Access Framework, and secure portable encryption for protected backups; flag not-yet-built export features honestly.
- No fabricated production sample data and no dead-end buttons. Show graceful empty states or mark features as not yet delivered, not as completed.

## Build & verify in stages

A. Inspect repo and implement an executable native Compose shell, design tokens, notebook Canvas, and five-tab nav.
B. Implement Room journal CRUD and **excellent editor** with properly aligned ruling, drafts/autosave, paper selection and real persistence. Prioritize this before maps, elaborate chapter art or time capsules.
C. Add picker/media, manual map, Chapters, Today memories and self-care, keeping every stage buildable.
D. Add complete and secure data export/import, Time Capsules and On This Day if practical; if not, document honest status and next steps.

After every stage run available Gradle build, lint, unit tests, and on-device/emulator checks where available. Do not claim a build succeeded if you did not run it.

### Screenshot comparison / acceptance

- When possible, run the app in an emulator with phone size and density approximating the reference mockups.
- Capture actual screenshots of Today, Journal, New Page/Editor, Memories, Chapters.
- Compare against `docs/design/references/pageheld-ui-overview.png` for structure and palette, **not merely that the same labels exist**.
- Fix major differences in spacing, font hierarchy, notebook background contrast, cards, selected nav state, controls, and writing alignment before calling the visuals complete.
- Verify both empty and populated data states; test with temporary fixtures only in test/debug builds.
- Test typing multiple paragraphs, changing paper style, relaunching, editing, and re-opening attachments; no data loss.
- Respect accessibility and screen-size differences over impossible fixed pixel dimensions.
- Update README and `docs/IMPLEMENTATION_STATUS.md` with true implementation state, tested build commands and outstanding gaps.

## Start

1. Open both design reference images now, describe the main visible design characteristics back briefly.
2. Inspect the current Pageheld repo and Git status.
3. Implement the working, visually faithful foundation and journal first.
4. Build, test, and compare actual screenshots to the reference.
5. Report exact files changed and what really works.
