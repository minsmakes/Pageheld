# Pageheld

<p align="center">
  <img src="docs/readme/pageheld-icon.png" alt="Pageheld app icon" width="112">
</p>

<p align="center">
  <a href="https://github.com/minsmakes/Pageheld/releases/latest/download/Pageheld.apk"><img src="docs/readme/download-android.svg" alt="Download phone edition"></a>
  <a href="https://github.com/minsmakes/Pageheld/releases/latest/download/Pageheld-Tablet.apk"><img src="docs/readme/download-tablet.svg" alt="Download tablet edition"></a>
</p>

<p align="center">Version 1.6 · Android 9 or newer</p>

Pageheld is a personal journal for Android. Write on ruled ivory paper, keep photos beside your words, and revisit pages by chapter or place. Your notebook stays on your device.

## Install on your Android device

1. Choose **Download for phone** (Pageheld.apk) or **Download for tablet** (Pageheld-Tablet.apk). An APK is an Android app installer.
2. Your browser may warn you about downloading an app file. Check that you followed the Pageheld link above before choosing **Download** or **Keep**.
3. Tap the finished download, or find the downloaded APK in **Files → Downloads**. Pageheld comes from GitHub, outside Google Play. If Android asks for permission, tap **Settings**, enable **Allow from this source** for the browser or Files app that opened the download, then go back.
4. Tap **Install**, then **Open**. Keep Google Play Protect enabled and allow it to scan the app if asked. If it flags the app as harmful, stop the installation.
5. After installing, search your phone’s Settings for **Install unknown apps**, select the same browser or Files app, and turn **Allow from this source** off again. The wording varies by phone. [Android installation help](https://support.google.com/android/answer/9457058)

## Write your first page

Open the cloth cover. On a landscape tablet, write directly on the right-hand page of Today. In a narrow window, choose **Write today’s page**. Writing saves automatically. The outer-edge tabs lead to Journal, Memories, Chapters, and You. Use the ribbon at the top to revisit bookmarks.

- Pick a mood in **Today**. It appears on the home screen and is prefilled on new diary pages. Tap the mood on a page to change it for that entry.
- Tap **Weather** at the top of a diary page to record what it was like outside. You can add or change this detail offline.
- Use the image button to attach a photo or video. Press and hold its thumbnail, then drag it to the spot you want on the page. Release to save its position. To delete an attachment, tap the thumbnail, choose **Remove attachment**, and confirm. Your original photo or video stays in your phone’s library.

## Updates and backups

For a new version, use the same download link and tap **Update**. Android keeps your journal when it installs an update signed with the same key. For a backup, open **You**, export your encrypted notebook, and keep the file and its passphrase somewhere safe.

If you already use a development build, Android may report a signature conflict. Keep that copy installed while you export your notebook and check that you have the backup file and passphrase. Uninstalling Pageheld erases the journal stored on the device.

## Quiet Hardcover

Pageheld 1.6 brings the responsive Quiet Hardcover redesign to both editions.

![Today on a landscape tablet-sized emulator](docs/design/screenshots/hardcover/today.png)

Pull an outer page edge to turn, or use the arrows beside the page number. The top-left book icon closes the cover. A wide window opens two facing pages. In portrait or split screen, the book adapts to the available width. **Chapters** opens the contents, and **You** holds the ownership bookplate and notebook settings.

[Rendering, verification, screenshots, and installation](docs/QUIET_HARDCOVER.md)

## Build from source

Open the repository root in Android Studio. Use JDK 21, install Android SDK Platform 37, sync Gradle, and run the `app` configuration on Android 9 (API 28) or later. On Windows with the SDK and JDK configured, build the debug APK with:

```powershell
.\gradlew.bat :app:assemblePhoneDebug :app:assembleTabletDebug
```

The debug installers are under `app/build/outputs/apk/phone/debug/` and `app/build/outputs/apk/tablet/debug/`. A fresh installation opens an empty journal. The pages, places and chapters in the screenshots are review fixtures from a dedicated emulator.

## Using the notebook

- Swipe or tap the cover to open your notebook. **You** contains the opening-cover and reduced-motion switches. Use the editor header arrows to turn between saved pages.
- Write on **Today’s story**, or use **+** in Journal for a separate entry. Edits save automatically. Use **Aa** for paper and writing preferences and the notebook icon for templates and chapters.
- Choose a photo or video in the Android picker. Pageheld copies the selected file into its private storage. Tap its thumbnail to reopen it.
- Search or filter in **Journal**. The calendar selects a date. Page options include bookmarking and deletion.
- Create a chapter with **+** in **Chapters**. Open a chapter for rename, reorder and delete actions. Its pages stay in Journal when the chapter is removed.
- Use the editor’s pin tool to add, edit, reorder or remove places on a page. Give each place a name or coordinates, optionally record a visit time and notes, and link page attachments. Submit a place, address or landmark to the device geocoder. Matching saved names appear locally while typing. Choose a result, adjust its pin and confirm **Add this place**. Tap the map or choose **Use current location** for a one-time fix. **Memories** groups saved entries by place. Its filters include a saved-place list that works without a map connection.
- In **You**, choose defaults or export an encrypted notebook with a passphrase. Import adds missing pages and chapters, restores appearance and check-ins, and preserves existing pages with matching IDs. Keep the passphrase for recovery.

Room stores pages and chapters. DataStore stores preferences and check-ins. MapLibre uses OpenFreeMap over the network for visible map areas. Pageheld requests foreground location permission when you choose a one-time fix. Export files contain journal text and attachments and support up to 256 MiB of uncompressed content.

See [implementation and verification details](docs/IMPLEMENTATION_STATUS.md), [the approved design brief](docs/design/PAGEHELD_CODEX_VISUAL_BRIEF.md), [Ownglyph font credits and license](docs/OWNGLYPH-LICENSE.md), [Libre Caslon Text’s license](app/src/main/assets/licenses/LibreCaslonText-OFL.txt), [Kalam’s license](docs/KALAM-LICENSE.txt), and [the icon license](docs/LUCIDE-LICENSE.txt).
