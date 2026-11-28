# MEMOSPINE. 2.0 — PRODUCTION MASTER PROMPT

Repository: https://github.com/minsmakes/Pageheld
Branch: main
Current version: 1.9.2
Target version: 2.0

Previous name: Pageheld.
New name: Memospine.
Developer: Min's Makes

Act as a senior Android engineer, software architect,
UI/UX designer, product designer, and QA engineer.

Your objective is to deliver a complete, stable,
beautiful, maintainable, production-ready
Memospine 2.0 release.

This is NOT a prototype, experimental redesign,
visual demonstration, or temporary implementation.

Develop the actual production application while
preserving existing functionality, user data,
installation compatibility, and visual identity.


==================================================
1. PROJECT RESOURCES
==================================================

Inspect the existing repository before implementing.

Read the previous production specification:

docs/design/v2.0/MASTER_PROMPT.md

Read the accompanying instructions:

docs/design/v2.0/START_HERE.md

Inspect the approved visual references:

docs/design/v2.0/references/library.png
docs/design/v2.0/references/book_studio.png
docs/design/v2.0/references/open_book.png

Inspect the supplied artwork directory:

docs/design/v2.0/source_assets/Pageheld_Asset_Pack_Vol01/

The artwork archive has ALREADY been extracted.

The existing directory contains:

Pageheld_Asset_Pack_Vol01/
    frames/
    materials/
    stickers/
    tapes/
    catalog.json
    README.md

Read catalog.json and README.md.

Inspect the actual individual artwork files.

Also review the asset preview, if present:

docs/design/v2.0/source_assets/asset_preview.jpg

IMPORTANT:

- Do not attempt to extract the ZIP again.
- Do not require the original ZIP archive.
- Use the already extracted assets.
- Preserve the original source artwork.
- Create optimized runtime copies where necessary.

This prompt takes precedence over all conflicting
instructions in previous specifications.

In particular, this prompt overrides previous
feature-branch instructions and ZIP extraction
requirements.

If optional reference files are unavailable,
report their absence and continue with the
implementation that can safely proceed.


==================================================
2. GIT WORKFLOW — MAIN BRANCH ONLY
==================================================

All development must take place directly on main.

Do not create:

- Feature branches
- Development branches
- Release development branches
- Temporary implementation branches

Before implementation:

- Inspect git status.
- Inspect existing local modifications.
- Inspect current remote configuration.
- Synchronize safely with origin/main.
- Preserve existing uncommitted user work.

During implementation:

- Work directly on main.
- Implement changes incrementally.
- Build and test after each major phase.
- Commit verified milestones.
- Use descriptive commit messages.
- Push verified changes to origin/main.

Never:

- Force-push.
- Destructively reset Git history.
- Discard existing user modifications.
- Commit credentials or signing secrets.

Inspect existing GitHub Actions workflows.

Ensure that ordinary development pushes do not
prematurely publish the official 2.0 release.

Prepare the v2.0 release tag and release artifacts.

Do not push the release tag or publish the official
GitHub release without my final approval.


==================================================
3. PRODUCT VISION
==================================================

Memospine is a private personal library of
the user's stories, experiences, and memories.

It combines:

- Personal journaling
- Memory preservation
- Personal books
- Creative scrapbooking
- Photography
- Handwriting
- Personal customization
- Collecting

The product should be enjoyable even when the
user does not feel like writing a diary entry.

Users should be able to create, decorate,
collect, revisit, and organize their own books.

The emotional inspiration comes from:

- Psychological ownership
- Creative freedom
- Personal identity
- Self-expression
- Aesthetic pleasure
- Collecting meaningful objects
- Personal attachment to digital possessions
- The customization experience of classic
  Korean personal homepages such as Cyworld

Do not reproduce Cyworld itself.

Translate these emotional qualities into
Memospine's private analog notebook experience.

Memospine must not become a social media platform,
virtual room simulator, game, or productivity
dashboard.

Core product experiences:

Record.
Customize.
Collect.
Revisit.


==================================================
4. PRODUCT NAME AND REBRANDING
==================================================

Official product name:

Memospine.

Korean name:

메모스파인

Product description:

Personal journals & memories

Brand meaning:

Memo:
Personal records, thoughts, stories, and memories.

Spine:
The physical spine of a book that holds its
individual pages together.

Keep the branding simple and recognizable.

Do not introduce overly sentimental slogans,
unnecessary marketing language, or childish
branding.

Update user-facing branding in:

- Android launcher label
- Application display name
- Navigation header
- Settings
- About screen
- Default book branding
- Application metadata
- Relevant dialogs
- README
- Installation instructions
- Documentation
- Release notes
- Release artifact names

Use Memospine. as the visual wordmark.
Use Memospine where punctuation is unsuitable.

Preserve:

- Existing four-leaf clover logo
- Existing application icon design identity
- Existing handwritten typography
- Existing theme palettes
- Existing analog notebook aesthetic

Do not alter user-created book titles,
journal content, or personal photographs
as part of the rebranding.

CRITICAL COMPATIBILITY REQUIREMENTS:

Preserve the existing:

applicationId = "app.pageheld"

Preserve the existing Android namespace,
database compatibility, installation identity,
and release signing identity.

Do not create a new Android application simply
because the product name has changed.

Existing Pageheld users must be able to upgrade
to Memospine without losing their personal data.

Keep the existing GitHub repository name:

minsmakes/Pageheld

Do not rename the repository unless explicitly
requested by the user.


==================================================
5. DEVICE SCOPE
==================================================

The new Library and Book Studio experiences
are intended for the TABLET edition.

Preserve the existing smartphone edition:

- Existing navigation
- Existing layouts
- Existing orientation behavior
- Existing reading experience
- Existing writing experience
- Existing settings
- Existing functionality

The smartphone edition receives:

- Memospine branding
- Version 2.0
- Necessary compatible infrastructure changes
- Required database and backup compatibility

Do not introduce the tablet bookshelf layout
into the smartphone interface.

Do not introduce the tablet Book Studio layout
into the smartphone interface.

Do not redesign the smartphone writing toolbar.

Use the existing phone and tablet product flavors.

Inspect how the current product flavors and
device-size checks interact.

Do not identify tablets using landscape
orientation alone.

Both editions must continue to build and run.


==================================================
6. REPOSITORY AND ARCHITECTURE AUDIT
==================================================

Inspect the actual repository structure.

Review relevant existing files, including
where present:

- README.md
- app/build.gradle.kts
- MainActivity.kt
- Design.kt
- Typography.kt
- Notebook.kt
- DiaryHome.kt
- Hardcover.kt
- BookMotion.kt
- BookTurn.kt
- DiaryEditor.kt
- DiaryDocument.kt
- DiaryPens.kt
- WritingSpread.kt
- NotebookSpread.kt
- PageMedia.kt
- InkSurface.kt
- Chapters.kt
- Memories.kt
- Backup.kt

Also inspect:

- Existing Room database entities and DAOs
- Existing database migrations
- Existing DataStore preferences
- Existing media storage architecture
- Existing font resources
- Existing texture resources
- Existing Compose components
- Existing instrumentation tests
- Existing release workflows
- Existing signing configuration
- Existing design documentation

These filenames are guidance.

Use the actual current repository as the
authoritative architectural source.

Identify existing components that can be reused.

Do not unnecessarily rewrite working systems.

Do not introduce duplicate implementations
of existing functionality.


==================================================
7. DESIGN SOURCE OF TRUTH
==================================================

Pageheld 1.9.2 is the authoritative source
for Memospine's established visual identity.

The supplied approved mockups define the
direction of the new tablet experiences.

When a mockup conflicts with the existing
global design system, preserve the existing
design system unless explicitly required
by this specification.

Preserve:

- Existing clover branding
- Existing horizontal navigation
- Rounded navigation selection controls
- Existing typography
- Existing icon family
- Existing paper colors
- Existing desk background resources
- Existing linen rendering
- Existing hardcover appearance
- Existing page-turn animations
- Existing spacing conventions
- Existing reduced-motion preferences

Reuse existing composables where appropriate.

Do not introduce a new global design system.

Avoid:

- Samsung Notes-style document grids
- Generic Material Design dashboards
- Permanent navigation sidebars
- Generic document cards
- Museum or exhibition aesthetics
- Fantasy environments
- Gaming-style layouts
- Glassmorphism
- Excessively strong shadows
- Heavy gradients
- Unnecessary decorative desk objects

The surrounding interface should feel familiar
and restrained.

Individual books should provide the visual
variety and creative personality.


==================================================
8. TYPOGRAPHY
==================================================

Preserve the existing actual bundled font:

몽글동심
Ownglyph Mongmongdays

Existing Android resource:

R.font.ownglyph_mongmongdays

Reuse the existing Typography.kt infrastructure,
including applicable display and notebook
font configuration.

Mongmongdays remains the default font for:

- Navigation
- Library
- Book Studio
- Dialogs
- Settings
- Tool labels
- Default notebook writing

Respect existing user-selected display fonts.

Respect existing user-selected writing fonts.

Allow independent font selection for
individual book cover lettering.

Preserve imported-font functionality.

Do not:

- Replace Mongmongdays with a generic font.
- Embed application labels into artwork.
- Generate static text images for book titles.
- Overwrite existing user font preferences.

Use actual rendered text.

Preserve existing font attribution and licenses.

Support English and Korean through the existing
localization architecture.


==================================================
9. THEME SYSTEM
==================================================

Preserve all four existing themes:

SAGE
Accent: #627547
Cloth: #A4AD80
Background: #F2F3E9
Desk: #DCE1CF

DENIM
Accent: #435F79
Cloth: #93ADBF
Background: #EDF2F6
Desk: #D5E1EC

ROSE
Accent: #884E5C
Cloth: #CBA2A7
Background: #FAF0F0
Desk: #EDD8DC

UMBER
Accent: #795737
Cloth: #C4A88A
Background: #F8EFE3
Desk: #E8D7C2

Use the actual existing definitions in Design.kt
as the authoritative values.

Preserve existing paper, ink, divider,
and supplementary palette definitions.

Sage is the primary visual reference.

IMPORTANT ARCHITECTURAL SEPARATION:

App Theme:
Controls the surrounding application interface.

Book Identity:
Controls the independent appearance of each book.

Changing the global application theme must not
overwrite a user's customized book appearance.

Changing a book's cover color must not alter
the surrounding application's theme.

Book Studio must inherit the active App Theme.


==================================================
10. TABLET NAVIGATION
==================================================

Preserve the existing horizontal navigation header.

Tablet navigation:

Today | Library | Moments

Korean:

오늘 | 서재 | 순간들

Replace the tablet Diaries entrance with Library.

Make Library the default tablet landing screen.

Preserve Today and Moments functionality.

Keep the existing clover logo and wordmark
on the left.

Keep the existing profile access on the right.

Do not introduce a permanent navigation sidebar.

Provide an explicit active-book context when
creating a daily entry.

Every writing action must have a clearly
identified destination book.

Define and document how Today and Moments
operate across multiple books.

Do not introduce ambiguous entry ownership.


==================================================
11. PERSONAL LIBRARY — SMALL BOOKSHELF
==================================================

Implement the approved small bookshelf design.

Use the supplied reference:

docs/design/v2.0/references/library.png

The library is a personal physical-looking
bookshelf, not a generic document grid.

Layout:

- Existing Memospine navigation header
- My Library heading
- New Book action
- Three horizontal physical shelves
- Three book positions per shelf
- Nine book positions per bookshelf page
- Horizontal swipe pagination
- Previous and next navigation controls
- Page indicators

Books must visually rest on the shelves.

Use understated thin wooden shelves with
appropriate texture and subtle natural shadows.

Use the active application theme for the
surrounding background.

Reuse existing desk resources where suitable.

Do not place books inside generic cards.

Allow different books to have independent:

- Cover materials
- Cover colors
- Cover proportions
- Thickness appearance
- Lettering
- Artwork
- Decorative details

Books must remain inside their assigned slots.

Align their bottom edges naturally with
the corresponding shelf.

For version 2.0, exterior proportion differences
may be visual properties only.

Do not unnecessarily change the established
internal writing layout or pagination.

PAGINATION REQUIREMENTS:

Books 1-9:
Bookshelf page 1.

Books 10-18:
Bookshelf page 2.

Books 19-27:
Bookshelf page 3.

Continue dynamically.

Never show more than nine books on one
bookshelf page.

Do not implement infinite vertical scrolling.

Do not use one of the nine positions for
the New Book action.

Handle empty shelves and incomplete rows
gracefully.

Do not populate real user libraries with
demonstration books.

Persist:

- Book ordering
- Current bookshelf page
- Selected book where applicable

Provide smooth horizontal swiping.

Respect reduced-motion preferences.


==================================================
12. BOOK LIFECYCLE
==================================================

Support:

- Create
- Open
- Rename
- Customize
- Duplicate design
- Reorder
- Move between bookshelf pages
- Delete

Provide accessible alternatives to
gesture-only actions.

Require explicit confirmation before deletion.

Explain which content will be deleted.

Do not silently duplicate private journal
content when duplicating a book design.

If content duplication is supported,
make it a separate explicit action.

BOOK CREATION FLOW:

1. Enter the book title.
2. Select a basic material or cover design.
3. Select an initial color.
4. Create the independent book.
5. Open Book Studio for optional customization.

The creation experience must remain short.

Users must be able to create a simple book
without configuring every decorative option.

Offer the original Pageheld-style hardcover
as the default starting design.


==================================================
13. INDEPENDENT BOOK DATA MODEL
==================================================

Each book must have its own persistent identity.

Each book should support:

- Stable unique identifier
- Title
- Creation timestamp
- Modification timestamp
- Library position
- Front cover configuration
- Back cover configuration
- Chapters
- Pages
- Journal entries
- Handwriting
- Media
- Locations
- Bookmarks
- Reading position
- Decorative layers

Users must be able to create independent
books for different personal purposes.

Examples:

- Daily diary
- Travel memories
- Family memories
- Personal thoughts
- Photo journal
- Creative scrapbook
- Personal stories
- Life experiences

Prevent accidental sharing of content
between independent books.

The same date may exist independently
inside different books.


==================================================
14. BOOK OPENING EXPERIENCE
==================================================

When a user selects a book:

- Provide subtle selection feedback.
- Animate the selected book toward the center
  where appropriate.
- Preserve the customized cover.
- Reuse the existing hardcover opening
  animation.
- Enter the existing notebook experience.

The interaction should feel like taking
a physical book from a bookshelf.

Do not replace a customized cover with
a generic cover during opening.

Avoid excessively long animations.

Respect reduced-motion preferences.

When returning to the library, restore
the previous bookshelf position.


==================================================
15. BOOK STUDIO — DEDICATED SCREEN
==================================================

Use the supplied reference:

docs/design/v2.0/references/book_studio.png

Book Studio must be a separate full-screen
tablet workspace.

Never display the Library and Book Studio
side by side.

TOP HEADER:

Left:
Back to Library.

Center:
The current book's actual title.

Right:
Done.

IMPORTANT:

The centered header must show the actual
book title, not the static words Book Studio.

Example:

제주에서의 느린 하루들

The heading must update when the book
title changes.

LEFT WORKSPACE:

- Large interactive hardcover preview
- Front cover selection
- Back cover selection
- Object selection feedback
- Undo and redo

RIGHT WORKSPACE:

- Material selection
- Color selection
- Lettering
- Stickers
- Personal photos
- Borders
- Ribbons
- Decorative layer management

Organize the controls into intuitive tabs.

Suggested tabs:

Cover | Text | Stickers | Photos

Korean:

표지 | 글자 | 스티커 | 사진

The experience should feel like customizing
a real physical notebook.

Preserve existing Memospine typography,
rounded controls, themes, and icon style.

Do not render the reference screenshot
as a static background image.

Implement real interactive Compose components.

Support saving the design.

Support cancellation without destroying
the previously saved design.


==================================================
16. COVER CUSTOMIZATION
==================================================

Implement an extensible material system.

Target materials:

- Linen
- Leather
- Paper
- Suede
- Patterned fabric
- Kraft

The supplied asset directory initially contains
artwork for:

- Linen
- Leather
- Paper

Use the supplied artwork where appropriate.

For additional materials, use visually
appropriate procedural rendering if it
meets production quality.

If additional artwork is required,
provide a precise artwork request.

Do not expose unfinished placeholders
as completed production features.

COLORS:

- Existing Memospine palette colors
- Additional muted colors
- Custom colors where appropriate

BORDERS:

- None
- Classic
- Simple
- Vintage or botanical

RIBBONS:

- None
- Sage
- Cream
- Pink
- Blue
- Additional appropriate colors

LETTERING:

- Editable title
- Optional subtitle
- Independent font
- Independent color
- Text size
- Position
- Alignment
- Rotation

Allow customization of the front and
back covers.

Support book-spine and binding appearance.

Changing the cover material or color must
not remove existing photos, lettering,
stickers, borders, or other decorations.


==================================================
17. EXISTING ARTWORK DIRECTORY
==================================================

Use the already extracted source directory:

docs/design/v2.0/source_assets/Pageheld_Asset_Pack_Vol01/

Do not search for or require:

Pageheld_Asset_Pack_Vol01.zip

The extracted directory is the source artwork.

Read:

catalog.json
README.md

Inspect all existing subdirectories:

materials/
stickers/
frames/
tapes/

The initial collection includes the following
12 selectable assets.

MATERIALS — 3

- Linen
- Leather
- Paper

STICKERS — 6

- Flower
- Clover
- Leaf
- Cat
- Moon
- Star

PHOTO FRAMES — 1

- Polaroid photo frame

MASKING TAPES — 2

- Gingham masking tape
- Floral masking tape

The pack also contains three additional
neutral-color material helper textures.

These helper textures are intended for
dynamic material recoloring.

Verify the actual inventory against catalog.json.

Do not assume filenames, dimensions,
or asset properties without inspecting them.


==================================================
18. ARTWORK INTEGRATION ARCHITECTURE
==================================================

Preserve the original extracted artwork
inside the design documentation directory.

Do not overwrite or destructively modify
the original artwork files.

Create separate optimized runtime assets.

Suggested Android runtime structure:

app/src/main/assets/artwork/
    materials/
    stickers/
    frames/
    tapes/
    catalog.json

Adapt this structure to the actual Android
project architecture when necessary.

Preserve stable asset identifiers.

Do not arbitrarily rename identifiers after
they become part of persistent book designs.

Implement an extensible artwork catalog.

Suggested future categories:

- Quiet Nature
- Paper Memories
- Little Moments
- Night Letters

The built-in artwork catalog should support
adding new collections without requiring
major architectural changes.

Keep artwork available offline.

Do not introduce mandatory external
image-hosting or AI-generation services.

Do not bundle unnecessary duplicate
source artwork inside the APK.

Use appropriate image decoding and caching.

Generate thumbnails when useful.


==================================================
19. ARTWORK QUALITY CONTROL
==================================================

The supplied artwork is prototype-quality
source artwork created for Memospine.

It must be inspected before being accepted
as production artwork.

Verify:

- File integrity
- Image dimensions
- Actual alpha transparency
- Clean sticker edges
- Absence of unwanted background halos
- Texture quality
- Texture repetition seams
- Dynamic color tinting quality
- Visual consistency
- Appropriate sharpness
- Photo frame transparency
- Correct catalog references

Use PNG or suitable lossless WebP
for optimized runtime assets.

Preserve sufficient visual quality
for large tablet displays.

For material rendering:

- Combine neutral textures with the
  user's selected cover color.
- Preserve material detail.
- Avoid destructive recoloring.
- Avoid obvious tiling seams.

For stickers:

- Preserve transparency.
- Preserve original aspect ratio.
- Preserve artwork details.
- Allow independent object transformations.

For photo frames:

- Ensure the photograph is visible through
  the intended transparent opening.
- Render the frame above the photograph.

For masking tapes:

- Preserve the intended decorative texture.
- Support independent positioning and scaling.

For simple borders and ribbons:

- Prefer scalable Compose Canvas or
  vector-based rendering where appropriate.

Do not:

- Replace supplied artwork with emojis.
- Replace materials with flat colors.
- Use generic placeholder icons.
- Use entire mockup screenshots as assets.
- Bake editable text into textures.
- Claim missing artwork is completed.

Correct technical asset defects when possible.

If artistic replacement is required,
report the specific requirement.


==================================================
20. ADDITIONAL ARTWORK REQUESTS
==================================================

If existing artwork is insufficient,
provide an explicit request containing:

- Asset identifier
- Asset display name
- Category
- Purpose
- Required dimensions
- Required file format
- Transparency requirements
- Texture repetition requirements
- Color requirements
- Visual style
- Example placement
- Reason additional artwork is needed

Possible additional assets:

- Suede texture
- Patterned fabric texture
- Kraft paper texture
- Additional botanical stickers
- Additional decorative paper
- Additional photo frames
- Additional masking tapes
- Additional book-cover ornaments

The user can provide additional artwork
created outside the application.

Do not introduce a paid image-generation
dependency into the application.

Do not spend time generating unrelated
artwork or expanding the release scope
without a practical product requirement.


==================================================
21. DECORATIVE LAYER ARCHITECTURE
==================================================

Build the book from independent editable layers.

Supported layer types:

- Text
- Sticker
- Personal photo
- Artwork
- Photo frame
- Masking tape

Each layer should contain:

- Stable unique identifier
- Layer type
- Asset reference
- Normalized X coordinate
- Normalized Y coordinate
- Width
- Height
- Rotation
- Opacity
- Z-order
- Relevant style properties

Support:

- Tap to select
- Drag to reposition
- Resize
- Pinch to scale
- Rotate
- Duplicate
- Delete
- Bring forward
- Send backward
- Undo
- Redo

Allow multiple instances of the same sticker.

Use normalized coordinates rather than
absolute screen coordinates.

The design must remain consistent across
different tablet sizes and rendering scales.

Preserve image aspect ratios unless the
user explicitly changes them.

Clip objects to the appropriate book
cover or page boundary.

Maintain editable layers.

Do not flatten the user's design into
an irreversible static image.


==================================================
22. PERSONAL PHOTO IMPORT
==================================================

Allow users to import their own photographs
and images for book covers and individual pages.

Use Android's system photo picker or
Storage Access Framework where appropriate.

Copy imported files into app-private storage.

Do not rely exclusively on temporary content
URIs for long-term persistence.

Handle:

- Large images
- EXIF orientation
- Unsupported files
- Invalid image files
- Memory-efficient decoding
- Appropriate resizing
- Cropping where applicable
- Asset reference management
- Safe deletion

Imported images must survive restarting
the application.

Include imported images in encrypted backups.

Avoid deleting image files still referenced
by other designs or entries.

Do not upload personal photographs
to external services.


==================================================
23. SHARED BOOK RENDERER
==================================================

Implement shared book rendering logic.

The same rendering architecture must be used for:

- Library thumbnails
- Book Studio preview
- Front covers
- Back covers
- Hardcover opening animations

Render these components independently:

1. Book geometry
2. Base cover color
3. Material texture
4. Book spine
5. Binding details
6. Decorative border
7. Editable lettering
8. Personal photos
9. Stickers
10. Additional decorative layers
11. Ribbon
12. Physical shading and shadows

Reuse and extend the existing hardcover renderer.

A book customized in Book Studio must
have the same appearance in the Library
and when opened.

Do not implement different incompatible
renderers for each screen.

Support appropriate rendering scale,
clipping, and caching.

The library should remain responsive even
when it contains many decorated books.


==================================================
24. OPEN BOOK — EXISTING HARDCOVER EXPERIENCE
==================================================

Use the supplied reference:

docs/design/v2.0/references/open_book.png

Preserve the existing 1.9.2 notebook
reading and writing experience.

Maintain:

- Landscape two-page spreads
- Existing paper styles
- Ivory paper
- Realistic center binding
- Page thickness
- Hardcover geometry
- Existing page-turn gestures
- Existing page-turn animations
- Existing chapters
- Existing bookmarks
- Existing handwriting
- S Pen support
- Pens
- Eraser
- Lasso
- Text editing
- Existing automatic pagination
- Photos
- Videos
- Locations
- Existing save and discard behavior

Do not replace the notebook with a generic
document editor.

Do not redesign working components
unnecessarily.

Preserve existing page identifiers,
document layouts, media placement,
text content, and handwriting.


==================================================
25. TOP WRITING TOOLBAR — MANDATORY
==================================================

Move the primary tablet editing toolbar
to the TOP of the writing screen.

This is an explicit ergonomic requirement.

Reason:

When users write with a hand or S Pen,
their resting palms must not accidentally
activate bottom-mounted controls.

Use the latest approved open-book reference.

Provide top-accessible controls for:

- Undo and redo
- Pen selection
- Writing instruments
- Eraser
- Lasso
- Photos
- Camera
- Video
- Location
- Text
- Stickers
- Page navigation

Keep the toolbar:

- Compact
- Visually consistent
- Accessible
- Outside the writable page area

Preserve as much writable space as possible.

Avoid floating controls that cover
journal content.

Do not place essential editing controls
under the user's resting palm.

Maintain appropriate distinctions between:

- Stylus handwriting
- Finger navigation
- Page turning
- Object selection
- Decoration manipulation

Preserve existing palm-rejection behavior.

Test actual stylus interactions on a
compatible physical device when available.

If physical stylus testing is unavailable,
report that limitation honestly.


==================================================
26. CREATIVE PAGE DECORATION
==================================================

Extend the existing journal editor to allow
artwork and decorations inside books.

Users must be able to:

- Add built-in stickers.
- Add personal photographs.
- Apply photo frames.
- Add masking tape.
- Reposition decorations.
- Resize decorations.
- Rotate decorations.
- Delete decorations.

Preserve existing text and handwriting.

Decorative objects must be associated
with stable logical page identities.

Prevent text reflow from unexpectedly
moving decorations into unrelated entries.

Keep decoration gestures separate
from handwriting gestures.

Existing journal entries must continue
to render correctly without modification.

Reuse the artwork and layer architecture
from Book Studio where appropriate.


==================================================
27. ROOM DATABASE AND MIGRATION
==================================================

Inspect the actual Room database architecture.

The previously reviewed 1.9.2 baseline
used database version 5 with entities
including Page, Chapter, and DiaryLocation.

Verify the current state before implementing.

Introduce a proper Book entity.

Associate relevant existing content
with the appropriate book.

Suggested architecture:

Book
    Chapters
    Pages
    Cover configurations
    Decorative layers
    Reading state

Pages
    Existing writing content
    Existing handwriting
    Media references
    Diary locations
    Page decoration layers

Choose a consistent data representation.

Avoid duplicate sources of truth.

Implement explicit, non-destructive
Room database migrations.

All existing Pageheld user content must
be migrated into one initial legacy book.

Preserve:

- Existing page identifiers
- Existing chapter identifiers
- Existing chapter relationships
- Journal text
- Handwriting
- Drafts
- Photographs
- Videos
- Locations
- Bookmarks
- Timestamps
- Media positions
- Existing preferences

Review existing daily-entry identifiers.

Previously used identifiers such as
daily-$day must not collide across books.

Introduce book-aware identification for
new daily entries while preserving
migrated identifiers.

Scope all relevant queries, writes,
navigation, and deletions to the
appropriate book.

Preserve the smartphone edition's existing
single-notebook experience.

Define an explicit legacy or default-book
context for the smartphone edition.

Ensure that smartphone operations cannot
accidentally overwrite or delete unrelated
tablet books.

Never use destructive database migration
to resolve schema validation errors.

Never clear existing user data as an
upgrade strategy.

Test the migration using representative
1.9.2 database fixtures.


==================================================
28. ENCRYPTED BACKUP AND RESTORE
==================================================

Extend the existing encrypted backup system.

A complete Memospine 2.0 backup must preserve:

- All books
- Book titles
- Book ordering
- Front cover configurations
- Back cover configurations
- Decorative layers
- Imported cover photographs
- Imported page decorations
- Chapters
- Journal entries
- Text
- Handwriting
- Photographs
- Videos
- Locations
- Bookmarks
- Relevant preferences

Use a versioned backup manifest.

Preserve compatibility with supported
older Pageheld backup formats.

An older backup restored into Memospine
must populate the appropriate legacy book.

Validate:

- Manifest versions
- Archive entries
- Asset references
- File names
- Archive size limits
- Invalid or malformed input

Handle restore failures safely.

A failed restoration must not silently
corrupt the existing personal library.

Ensure imported file references are
correctly remapped where necessary.

Test:

- Legacy Pageheld backup restoration
- Memospine multi-book backup restoration
- Book cover restoration
- Imported artwork restoration
- Page decoration restoration
- Corrupted backup rejection

Document compatibility limitations,
including whether previous application
versions can read new backups.


==================================================
29. PRIVACY AND OFFLINE OPERATION
==================================================

Memospine remains a private personal application.

Do not introduce:

- Mandatory user accounts
- Cloud synchronization
- Social networking
- Public profiles
- Advertising
- Subscriptions
- In-app purchases
- External analytics or tracking
- Runtime AI-generation services
- Mandatory image-hosting services

The following must operate offline:

- Library
- Book creation
- Book Studio
- Cover customization
- Built-in artwork
- Personal photo customization
- Journaling
- Reading
- Handwriting

Preserve existing optional location
functionality.

Do not introduce unnecessary network
dependencies or storage permissions.

Keep personal assets in app-private storage.


==================================================
30. PERFORMANCE AND ACCESSIBILITY
==================================================

Optimize for real Android devices.

Ensure:

- Smooth bookshelf pagination
- Smooth book opening
- Responsive handwriting
- Responsive sticker manipulation
- Efficient image rendering
- Stable memory usage
- Correct state restoration
- Appropriate system insets
- Accessible touch targets
- Screen-reader descriptions
- Accessible alternatives to gestures

Avoid:

- Unnecessary recompositions
- Large unbounded image allocations
- Repeated expensive texture decoding
- Blocking file IO on the main thread
- Unnecessary network activity

Test multiple landscape tablet sizes
and display densities.

Respect existing display-size settings.

Respect existing reduced-motion preferences.

Do not communicate important states
using color alone.


==================================================
31. IMPLEMENTATION PHASES
==================================================

PHASE A — AUDIT AND FOUNDATION

- Inspect the repository.
- Inspect the current main branch.
- Preserve existing local modifications.
- Read the production specification.
- Inspect approved visual references.
- Inspect the extracted source artwork.
- Review catalog.json and README.md.
- Review existing architecture.
- Review existing tests.
- Review existing release configuration.
- Establish the migration strategy.
- Establish the implementation plan.

PHASE B — REBRANDING AND MULTI-BOOK FOUNDATION

- Apply Memospine branding.
- Preserve Android installation identity.
- Implement the Book model.
- Implement safe database migration.
- Scope existing content to books.
- Verify legacy content preservation.
- Implement book lifecycle management.

PHASE C — TABLET LIBRARY

- Implement the three-shelf layout.
- Implement nine-book pagination.
- Implement new book creation.
- Implement book management.
- Implement book selection and opening.
- Implement persistent ordering.

PHASE D — BOOK STUDIO AND ARTWORK

- Implement the shared book renderer.
- Integrate the extracted artwork assets.
- Implement material customization.
- Implement independent color customization.
- Implement cover lettering.
- Implement borders and ribbons.
- Implement editable decorative layers.
- Implement personal image import.
- Implement front and back cover customization.
- Verify saved designs.

PHASE E — WRITING EXPERIENCE

- Preserve existing hardcover behavior.
- Move the tablet toolbar to the top.
- Implement page decorations.
- Verify handwriting and touch interactions.
- Verify existing media behavior.

PHASE F — PRODUCTION HARDENING

- Extend encrypted backups.
- Verify legacy migration.
- Verify multi-book backup restoration.
- Test phone and tablet editions.
- Optimize rendering.
- Review accessibility.
- Resolve functional defects.
- Resolve visual defects.

PHASE G — RELEASE CANDIDATE

- Update version information.
- Capture actual application screenshots.
- Run relevant automated tests.
- Build both production editions.
- Verify compatible release signing.
- Verify installation upgrade behavior.
- Update documentation.
- Prepare release notes.
- Prepare release artifacts.
- Prepare the v2.0 release tag.

Build and test after every major phase.

Create incremental verified commits on main.

Push verified implementation changes
to origin/main.

Do not stop after producing a plan.

Proceed with actual implementation.


==================================================
32. VISUAL VERIFICATION
==================================================

The supplied mockups are approved visual
references, not actual running-app screenshots.

They still display Pageheld because they
were created before the rebranding.

Replace the branding with Memospine
in the implemented application.

Use the actual bundled Mongmongdays font.

Capture real screenshots from an emulator
or physical device.

Required screenshot states:

01. Library with nine books
02. Second bookshelf page
03. Empty or partially filled library
04. New Book creation
05. Book Studio material selection
06. Book Studio lettering customization
07. Book Studio sticker selection
08. Book Studio personal photo placement
09. Back cover customization
10. Open book
11. Top writing toolbar
12. Handwriting and page decorations
13. Sage theme
14. Denim theme
15. Rose theme
16. Umber theme
17. Existing smartphone edition

Compare real screenshots against the
approved references.

Review:

- Actual typography
- Theme fidelity
- Shelf alignment
- Book geometry
- Book proportions
- Texture quality
- Sticker appearance
- Cover consistency
- Appropriate spacing
- Toolbar position
- Book Studio header title
- Navigation consistency

Correct visible problems before
declaring production readiness.

Do not present generated mockups
as implementation evidence.


==================================================
33. AUTOMATED TESTING
==================================================

Retain the existing automated tests.

Extend the suite with appropriate coverage.

DATABASE:

- Non-destructive 1.9.2 migration
- Independent book creation
- Cross-book content isolation
- Daily-entry identifier collision prevention
- Safe book deletion
- Existing media preservation

LIBRARY:

- One book
- Nine books
- Ten books
- Eighteen books
- Nineteen books
- Nine-book pagination
- Persistent book ordering
- Book selection
- Navigation restoration
- Empty shelf behavior

BOOK STUDIO:

- Material changes
- Independent color changes
- Lettering customization
- Multiple instances of one sticker
- Personal photo import
- Object movement
- Object scaling
- Object rotation
- Layer ordering
- Undo and redo
- Save and reopen
- Front and back cover persistence
- App theme independence

ARTWORK:

- Valid catalog references
- Correct asset loading
- Transparency
- Missing asset handling
- Persistent asset identifiers

WRITING:

- Existing handwriting
- Existing text editing
- Existing page turning
- Existing photographs
- Existing videos
- Existing chapters
- Existing locations
- Page decoration
- Top toolbar
- Stylus and touch interaction
- Existing save and discard behavior

BACKUP:

- Supported legacy backup restoration
- Multi-book backup export
- Multi-book backup restoration
- Imported photo restoration
- Cover decoration restoration
- Page decoration restoration
- Invalid backup rejection

PHONE REGRESSION:

- Existing navigation
- Existing layouts
- Existing writing behavior
- Existing orientation behavior
- Existing journal accessibility
- No unwanted tablet interface

BUILD:

- Phone debug
- Tablet debug
- Phone release
- Tablet release

Do not disable failing legacy tests merely
to obtain successful build results.

Update tests only when required by
explicitly intended new behavior.

Clearly distinguish automated testing,
emulator testing, and physical-device testing.


==================================================
34. VERSIONING
==================================================

Set the target release version:

versionName = "2.0"

The previously reviewed 1.9.2 baseline
used versionCode 12.

Verify the highest versionCode already
distributed before selecting the new value.

Use a monotonically increasing versionCode.

Use at least 13 if that remains appropriate.

Preserve:

applicationId = "app.pageheld"

Preserve the existing Android namespace.

Preserve compatible release signing.

Do not change the installation identity.


==================================================
35. PRODUCTION RELEASE ARTIFACTS
==================================================

Build both Android editions.

Prepare branded production installers:

Memospine.apk
Memospine-Tablet.apk

Update README download links accordingly.

Preserve previous Pageheld download URLs
where practical through compatible aliases
or appropriate upgrade instructions.

Use the existing compatible release signing
configuration and signing identity.

Verify production signatures using
available Android tooling.

Verify installation upgrade compatibility.

Never commit:

- Keystores
- Signing passwords
- API secrets
- Private credentials

Never generate an unrelated signing key
as a substitute for the existing key.

Do not label debug or unsigned APKs
as production installers.

If compatible signing credentials are
unavailable, report the release blocker.

Prepare:

- Signed phone APK
- Signed tablet APK
- v2.0 release tag
- Updated README
- User-facing release notes
- Actual application screenshots
- Installation instructions
- Upgrade instructions
- Backup and restore guidance
- Artwork attribution
- Font attribution
- Known limitations
- Release verification report

Brand and trademark availability must be
confirmed before official publication.


==================================================
36. RELEASE PUBLICATION APPROVAL
==================================================

All development remains on main.

Commit verified changes.

Push verified development changes
to origin/main.

Prepare the v2.0 release tag.

Do not:

- Push the v2.0 release tag.
- Publish the official GitHub release.
- Trigger an unintended automated release.
- Claim production readiness without evidence.

Official release publication requires
my final approval.


==================================================
37. DEFINITION OF DONE
==================================================

Memospine 2.0 is complete only when:

BRANDING

- Memospine branding is consistently applied.
- Existing clover identity is preserved.
- Actual Mongmongdays typography is used.

LIBRARY

- Multiple independent books are supported.
- Three physical shelves are implemented.
- Each shelf has three book positions.
- Each bookshelf page holds at most nine books.
- Horizontal pagination works.
- Book ordering persists.

BOOK STUDIO

- Book Studio is a dedicated screen.
- The actual book title appears in its header.
- Cover materials genuinely work.
- Cover colors genuinely work.
- Cover lettering is editable.
- Borders and ribbons work.
- Actual supplied artwork is integrated.
- Stickers are independently editable.
- Personal photographs are supported.
- Front and back designs persist.
- Book appearances remain independent
  from the global application theme.

WRITING

- Existing hardcover behavior survives.
- Existing handwriting survives.
- Existing page turning survives.
- The tablet toolbar is positioned at the top.
- Page decorations work.
- Existing media functionality survives.

DATA

- Existing user content survives migration.
- Independent books do not leak content.
- Daily entries do not collide.
- Encrypted backup and restore work.

COMPATIBILITY

- Existing smartphone UI is preserved.
- Both product flavors build successfully.
- Relevant automated tests pass.
- Installation upgrade compatibility is verified.

RELEASE

- Actual application screenshots are reviewed.
- Production APK signing is verified.
- README is updated.
- Release documentation is complete.
- Artwork quality is reviewed.
- Remaining blockers are documented.
- Verified implementation changes are on main.

Do not silently remove mandatory requirements.

Do not declare incomplete functionality
as finished production functionality.


==================================================
38. FINAL DELIVERY REPORT
==================================================

At the end of implementation, provide:

1. Release summary
2. Implemented features
3. Changed files
4. Architecture changes
5. Database migration details
6. Legacy user data preservation evidence
7. Artwork integration details
8. Runtime artwork directory structure
9. Artwork quality verification
10. Additional artwork requirements
11. Actual application screenshots
12. Automated test commands and results
13. Build commands and results
14. Physical-device verification results
15. Phone regression results
16. Production APK locations
17. APK signing verification
18. Upgrade compatibility verification
19. Main branch commit status
20. Origin/main push status
21. Known limitations
22. Remaining blockers
23. Draft GitHub release notes
24. Production readiness assessment

Clearly distinguish:

IMPLEMENTED
VERIFIED
NOT TESTED
BLOCKED
REMAINING WORK

Do not claim successful testing, signing,
installation, migration, or publication
without actual evidence.


==================================================
39. WORKING PRINCIPLES
==================================================

Do not stop after producing a plan.

Implement the actual production application.

Reuse existing working systems.

Avoid unnecessary architecture rewrites.

Do not introduce speculative functionality
unrelated to version 2.0.

Make reasonable engineering decisions
without repeatedly requesting approval.

Request assistance when a genuine blocker
requires user intervention.

Continue safe implementation work whenever
possible.

If artwork is missing, specify exactly
which assets are required.

If physical-device testing is unavailable,
identify what remains unverified.

If release signing is unavailable,
prepare everything else and report
the signing blocker.

The objective is not simply to create
screens that resemble the mockups.

The objective is a beautiful, stable,
reliable, maintainable, production-ready
Memospine 2.0 application.


==================================================
40. START IMPLEMENTATION
==================================================

Begin by:

1. Inspecting the existing repository.
2. Confirming development is on main.
3. Reviewing existing local modifications.
4. Reading the production documentation.
5. Reviewing the three visual references.
6. Reading the extracted artwork catalog.
7. Inspecting the actual artwork files.
8. Reviewing database and backup architecture.
9. Establishing the implementation strategy.
10. Proceeding with actual development.

Remember:

The artwork has already been extracted.

Use:

docs/design/v2.0/source_assets/Pageheld_Asset_Pack_Vol01/

Do not require the ZIP archive.

Work directly on main.

Preserve existing user data.

Preserve the smartphone edition.

Preserve the Memospine visual identity.

Deliver a genuine production release candidate.