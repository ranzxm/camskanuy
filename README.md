# Camskanuy

A native, offline Android document scanner built with **Kotlin, XML Views, and Material Components**. No accounts, ads, analytics, or runtime model downloads.

## Product scope

- Capture documents with CameraX or import images through the system picker.
- Detect document edges locally, adjust crop corners, correct perspective, and rotate pages.
- Apply original, color, grayscale, or black-and-white rendering.
- Resume drafts and arrange multi-page documents.
- Organize local documents with names, search, and single-level folders.
- Export a multi-page PDF or a selected page as JPG; share through Android's system chooser.
- Light, dark, and system appearance; Indonesian application UI.

OCR, cloud sync, PDF text editing, and release distribution are outside this MVP. Implementation and physical-device verification are separate: a green CI build is not a guarantee of camera quality or full accessibility. See [the test checklist](docs/TESTING.md).

### Initial limitations

- Edge detection uses a small local contrast/component algorithm, not an AI model. Difficult backgrounds, shadows, or curved pages may require manual corner adjustment.
- Page reordering uses accessible Move earlier/later actions; drag-and-drop is not implemented. Rotation is available inside the page editor.
- Processing is capped at a 2400-pixel long edge to bound memory usage; this is not lossless archival scanning.
- Zoom, large-font layouts, TalkBack, camera capture, and the 20-page/3 GB RAM target still require physical-device verification.
- An interrupted external-provider export may leave a partial destination file; the app-private source document remains intact.

## Build and download — GitHub Actions only

**Do not install Android Studio, an Android SDK/NDK, an emulator, or a JDK/Gradle toolchain on the user's computer.** The tracked Gradle Wrapper is project source for CI; do not execute it locally in this environment.

1. Push changes to [ranzxm/camskanuy](https://github.com/ranzxm/camskanuy).
2. Open **Actions → Android CI**. Pushes and pull requests trigger validation; **Run workflow** also works.
3. Wait for `validate-and-build` to complete successfully.
4. Download the `camskanuy-debug-<short-sha>` artifact from the run page. GitHub sign-in may be required to download artifacts.
5. Extract `app-debug.apk`, transfer it to a physical phone running Android 8.0 or newer, and install it. Enable installation from the chosen source if Android asks.

Artifacts expire after 14 days. Rerun the workflow to produce a new one. Debug APKs are for testing, not production distribution. GitHub runner debug signing keys are not guaranteed to persist between runs: an update may require uninstalling an earlier debug build, which deletes its private documents. Export important documents before uninstalling. A stable release-signing setup must use GitHub Secrets and is deliberately not included yet.

### CI toolchain

| Component | Pinned version |
| --- | --- |
| Android minimum / compile / target SDK | 26 / 35 / 35 |
| Android Gradle Plugin | 8.9.2 |
| Gradle Wrapper | 8.11.1, SHA-256 validated |
| Kotlin | 2.1.20 |
| JDK | Temurin 17, provisioned by CI |

CI runs `lintDebug`, `testDebugUnitTest`, and `assembleDebug`, checks the merged manifest for the absence of `INTERNET`, and uploads the APK, test/lint reports, and generated Room schema. Android dependencies are downloaded during the CI build, not during use of the application.

The optional **Android device smoke test** workflow runs manually on a GitHub-hosted Android emulator with Wi-Fi/mobile data disabled. It checks launch, activity recreation, local document operations, and PDF/JPG generation, then captures the Documents screen. It does not replace camera testing on a physical phone, and is not considered passed until a successful run is recorded.

## Privacy and storage

- Only the camera permission is requested directly by the application; gallery import remains available if it is denied.
- Documents, metadata, and source images live in app-private storage. Cloud backup is excluded, and no application-level encryption is claimed.
- Export uses Android's system save dialog. Sharing exposes only temporary exported files through a narrowly scoped FileProvider with temporary read access.
- Other apps or storage providers selected by the user may upload exported/shared files. Camskanuy itself does not upload them.
- Clearing app data or uninstalling deletes private documents. User-exported files remain at their selected destinations.

## Development references

- [AGENTS.md](AGENTS.md): development rules and repository workflow.
- [PRD.md](PRD.md): scope, acceptance criteria, and definition of done.
- [DESIGN.md](DESIGN.md): visual tokens, screens, and accessibility requirements.
- [docs/TESTING.md](docs/TESTING.md): physical-device and CI validation checklist.

Application ID: `com.ranzxm.camskanuy`. The app uses original branding and does not include CamScanner code or assets.
