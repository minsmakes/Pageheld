# Pageheld

Pageheld is a native, local-first Android notebook built with Kotlin, Jetpack Compose and Material 3. Write on ruled ivory paper, attach a photo or video, and organize pages by chapter or manually chosen place.

![Five Pageheld screens captured from the Android emulator](docs/design/screenshots/overview.png)

## Run

Open the repository root in Android Studio. Use JDK 21, install Android SDK Platform 37, sync Gradle, and run the `app` configuration on Android 9 (API 28) or later. On Windows with the SDK and JDK configured, build the debug APK with:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. A fresh installation opens an empty journal. The pages, places and chapters in the screenshots are review fixtures from a dedicated emulator.

## Using the notebook

- Open **Write today’s page** or **+**. Edits save automatically. Use **Aa** for paper and writing preferences and the notebook icon for templates and chapters.
- Choose a photo or video in the Android picker. Pageheld copies the selected file into its private storage. Tap its thumbnail to reopen it.
- Search or filter in **Journal**. The calendar selects a date. Page options include bookmarking and deletion.
- Create a chapter with **+** in **Chapters**. Open a chapter for rename, reorder and delete actions. Its pages stay in Journal when the chapter is removed.
- Add a place in the editor by name, optionally tapping a point on the map. **Memories** groups saved entries by place. Its filters include a saved-place list that works without a map connection.
- In **You**, choose defaults or export an encrypted notebook with a passphrase. Import adds missing pages and chapters, restores appearance and check-ins, and preserves existing pages with matching IDs. Keep the passphrase for recovery.

Room stores pages and chapters. DataStore stores preferences and check-ins. MapLibre uses OpenFreeMap over the network for visible map areas. Pageheld requests no device location permission. Export files contain journal text and attachments and support up to 256 MiB of uncompressed content.

See [implementation and verification details](docs/IMPLEMENTATION_STATUS.md), [the approved design brief](docs/design/PAGEHELD_CODEX_VISUAL_BRIEF.md), [Kalam’s license](docs/KALAM-LICENSE.txt), and [the icon license](docs/LUCIDE-LICENSE.txt).
