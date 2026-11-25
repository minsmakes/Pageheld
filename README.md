<p align="center"><img src="docs/readme/pageheld-icon-v1.7.svg" width="112" height="112" alt="Pageheld clover icon"></p>

<h1 align="center">Pageheld.</h1>

<p align="center">
  <a href="https://github.com/minsmakes/Pageheld/releases/latest/download/Pageheld.apk"><img src="docs/readme/download-android.svg" alt="Download phone edition"></a>
  <a href="https://github.com/minsmakes/Pageheld/releases/latest/download/Pageheld-Tablet.apk"><img src="docs/readme/download-tablet.svg" alt="Download tablet edition"></a>
</p>

<p align="center">Version 1.7 · Android 9 or newer</p>

Pageheld is a personal journal for Android. Write on ruled ivory paper, keep photos beside your words, and revisit pages by chapter or place. Your notebook stays on your device.

## Screenshots

<p align="center"><a href="docs/design/screenshots/v1.7/overview.jpg"><img src="docs/design/screenshots/v1.7/overview.jpg" width="960" alt="Pageheld 1.7 on a landscape tablet, showing Today, the diary cover, chapters, Moments, and the writing tools"></a></p>

[Today](docs/design/screenshots/v1.7/today.png) · [Diary cover](docs/design/screenshots/v1.7/cover.png) · [Chapter index](docs/design/screenshots/v1.7/chapters.png) · [Moments](docs/design/screenshots/v1.7/moments.png) · [Diary editor](docs/design/screenshots/v1.7/typed.png)

## Install on your Android device

1. Choose **Download for phone** (Pageheld.apk) or **Download for tablet** (Pageheld-Tablet.apk). An APK is an Android app installer.
2. Your browser may warn you about downloading an app file. Check that you followed the Pageheld link above before choosing **Download** or **Keep**.
3. Tap the finished download, or find the downloaded APK in **Files → Downloads**. Pageheld comes from GitHub, outside Google Play. If Android asks for permission, tap **Settings**, enable **Allow from this source** for the browser or Files app that opened the download, then go back.
4. Tap **Install**, then **Open**. Keep Google Play Protect enabled and allow it to scan the app if asked. If it flags the app as harmful, stop the installation.
5. After installing, search your phone’s Settings for **Install unknown apps**, select the same browser or Files app, and turn **Allow from this source** off again. The wording varies by phone. [Android installation help](https://support.google.com/android/answer/9457058)

## Write your first page

Today shows the local date and your latest writings. Choose **Today’s diary** to continue the day’s entry. In **Diaries**, open the stitched cover and choose a chapter or **New story**. Unfiled entries appear under their month. Custom chapters keep their original order and membership.

The upper toolbar switches between typing, pen, eraser, photographs, mood, weather and places. Text flows onto additional sheets as you write. Use **More → Add page** for a deliberate break. Tap the title to rename an entry, or choose the **Note** template under **Template & chapter**. Tap a print to select it, drag to move and pinch to resize. Long-press to open the attachment viewer.

A successful **Draft saved** status means the edit reached Room. **Done** completes the entry and returns to its chapter. The navigation menu beside Back can take you to Today or Moments while preserving your draft. Select Diaries to resume it.

## Updates and backups

For a new version, use the same download link and tap **Update**. Android keeps your journal when it installs an update signed with the same key. For a backup, open the **profile icon**, export your encrypted notebook, and keep the file and its passphrase somewhere safe.

If you already use a development build, Android may report a signature conflict. Keep that copy installed while you export your notebook and check that you have the backup file and passphrase. Uninstalling Pageheld erases the journal stored on the device.

## Digital Diary · November 24, 2026

Version 1.7 adds the three-tab interface, clover cloth cover, multi-page editor and photograph map. Tablets request landscape. Phones retain their normal orientation behavior. Personal settings include font import, paper, owner name, reduced motion and encrypted backups.

[Version 1.7 implementation and device verification](docs/DIGITAL_DIARY_1_7.md) · [Version 1.6 Quiet Hardcover history](docs/QUIET_HARDCOVER.md)

## Build from source

Open the repository root in Android Studio. Use JDK 21, install Android SDK Platform 37, sync Gradle, and run the `app` configuration on Android 9 (API 28) or later. On Windows with the SDK and JDK configured, build the debug APK with:

```powershell
.\gradlew.bat :app:assemblePhoneDebug :app:assembleTabletDebug
```

The debug installers are under `app/build/outputs/apk/phone/debug/` and `app/build/outputs/apk/tablet/debug/`. A fresh installation opens an empty journal. The pages, places and chapters in the screenshots are review fixtures from a dedicated emulator.

## Places and photographs

**Moments** opens an interactive map. Choose a photo marker to see linked memories. Search and filter places or switch to the saved list from the map controls. **Place** in the editor offers place search and recent locations. **Manage places** opens the existing map pin, GPS, notes, visit time, ordering and media associations.

Room stores entries, notebook layout, chapters and locations. DataStore stores preferences and check-ins. MapLibre uses OpenFreeMap over the network for visible map areas. Pageheld requests foreground location permission when you choose a one-time fix. Export files contain journal text and attachments and support up to 256 MiB of uncompressed content.

See [implementation and verification details](docs/IMPLEMENTATION_STATUS.md), [the approved design brief](docs/design/PAGEHELD_CODEX_VISUAL_BRIEF.md), [Ownglyph font credits and license](docs/OWNGLYPH-LICENSE.md), [Libre Caslon Text’s license](app/src/main/assets/licenses/LibreCaslonText-OFL.txt), [Kalam’s license](docs/KALAM-LICENSE.txt), and [the icon license](docs/LUCIDE-LICENSE.txt).
