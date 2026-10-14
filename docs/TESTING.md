# Validation checklist

Record the commit SHA, device model, Android version, RAM, and observed results when testing. An unchecked item is **not verified**, even when the code appears to implement it.

## Current Compose migration — validation pending

No Compose build, lint, unit/instrumentation results, screenshots, or physical-device acceptance are recorded yet. Rerun CI against the migrated commit; do not carry forward legacy XML checkmarks, APK size, or warning counts as Compose results.

## Recorded legacy XML validation — `dc378d1`

Historical evidence only: these results predate the Compose redesign.

- [Android CI](https://github.com/ranzxm/camskanuy/actions/runs/38592047115): successful build, lint, and 8 JVM unit tests; no failures or skipped tests.
- Merged debug manifest check passed: no `android.permission.INTERNET`.
- [Android device smoke test](https://github.com/ranzxm/camskanuy/actions/runs/38592048842): 6 instrumentation tests passed on the GitHub-hosted Android 35 x86_64 Pixel 2 emulator, with Wi-Fi/mobile data disabled.
- Instrumentation coverage: activity launch/recreation, persistence, reorder, PDF/JPG generation, folder deletion, orphan reconciliation, failed replacement safety, EXIF rotation, and all four filters.
- Documents-screen screenshot captured and reviewed; available in the device-smoke artifact.
- Debug APK size: **8,364,580 bytes** (about 8.36 MB decimal). Artifact ZIP size is smaller; neither value predicts installed storage or document usage.
- Lint passed with **103 warnings**, mostly newer dependency-version suggestions; warnings also include visual/resource and target-SDK recommendations. This is not a warning-free build or Play Store readiness claim.
- The downloaded validation artifact did not contain the Room schema directory. Preserve a CI-generated schema before implementing any database migration; do not infer schema coverage from a successful build.
- No local Android build/tool installation. Physical camera quality, full TalkBack/font-scale behavior, Android 26 compatibility, and the 20-page/3 GB RAM target remain unverified.

## Automated CI — current Compose commit

- [ ] Gradle Wrapper validation succeeds.
- [ ] `lintDebug` completes without errors.
- [ ] `testDebugUnitTest` passes; inspect the report for actual test counts.
- [ ] `assembleDebug` produces an installable APK.
- [ ] The merged manifest contains no `android.permission.INTERNET`.
- [ ] Debug APK and validation reports are available as Actions artifacts.
- [ ] Emulator smoke tests pass for the Compose commit; capture/review redesigned screens, navigation, and activity recreation.

The local Windows environment is used only for source editing, static inspection, and Git. Do not run Gradle or install Android tooling there.

## Physical-phone acceptance

### Installation and offline behavior

- [ ] Install on Android 8.0 / API 26 or a representative minimum-version device.
- [ ] Install on a recent Android version, including Android 15's edge-to-edge behavior.
- [ ] Enable airplane mode before first launch. Capture, crop, filter, save, and export locally without downloads.
- [ ] Deny camera permission. Import images without granting it; retry and permanently-denied states remain usable.
- [ ] Verify no account, advertisement, analytics prompt, or cloud dependency appears.

### Scanning and editing

- [ ] Capture a flat page against a contrasting background; verify four-corner detection.
- [ ] Capture tilted, shadowed, low-contrast, and partially obscured documents; verify manual fallback and inspect output quality.
- [ ] Move each crop corner, reset it, and reject invalid/self-crossing crops without crashing.
- [ ] Rotate through all four orientations and apply all four filters. Preview and saved output agree.
- [ ] Import portrait/landscape images, images with EXIF rotation/mirroring, and multiple images.
- [ ] Import a corrupt/unsupported image; keep already-confirmed pages and show a useful error.
- [ ] Re-edit a saved page without progressive compression damage to its source.
- [ ] Rotate the device and background the app during editing; draft and editor state recover appropriately.

### Document lifecycle

- [ ] Confirm a page, close the application, and reopen; resume or discard the draft.
- [ ] Add, delete, and reorder at least three pages; confirm exported PDF order.
- [ ] Delete the final page; saving an empty document is not permitted.
- [ ] Save with the suggested name, rename, and search case-insensitively.
- [ ] Create/rename folders and move documents; deleting a folder moves documents to Unfiled.
- [ ] Cancel deletion, then confirm deletion; only the selected document is removed.
- [ ] Process a 20-page document on a 3 GB RAM device without an out-of-memory crash.

### Export and sharing

- [ ] Export PDF to local storage; open it in an external viewer and inspect every page.
- [ ] Export the current page as JPG; verify orientation, filter, and readability.
- [ ] Cancel the system save dialog; preserve the document and avoid an error message.
- [ ] Simulate insufficient space or an unwritable destination; retain source data and show an error.
- [ ] Share PDF/JPG to another app and verify temporary URI read access works.
- [ ] Check that no watermark is added.
- [ ] Export before uninstalling; verify exported files survive, while private documents are removed.

### Design and accessibility

- [ ] Empty-state hero exposes Scan/Import; compact search/folder chips, thumbnail cards, and Resume/Discard draft actions work.
- [ ] Full-viewport camera keeps dark shutter controls accessible; leaving/reopening the screen releases/rebinds CameraX correctly.
- [ ] Editor keeps a large preview, bottom Crop/Filter tabs, filter strip, and expandable corner fine adjustment usable without drag gestures.
- [ ] Document preview and pinned Export/Share remain clear of system bars, thumbnails, and page navigation.
- [ ] Compose navigation/back behavior and lifecycle-aware StateFlows survive rotation/backgrounding without duplicate work or lost confirmed pages.
- [ ] Light, dark, and system modes are consistent across all screens and dialogs.
- [ ] At 200% font scaling, primary controls and labels remain reachable.
- [ ] TalkBack supports capture, manual crop, filter selection, reorder, save, and export.
- [ ] Controls have at least 48dp touch targets and meaningful labels.
- [ ] Keyboard, navigation bars, display cutouts, and landscape layouts do not cover primary actions.
- [ ] Errors and loading states remain understandable and do not imply success.

## Release follow-up

- Measure an optimized build before making APK size claims; do not compare debug APK size to release estimates.
- Set up a stable signing key using GitHub Secrets before release distribution.
- Preserve the generated Room schema from CI in source control before introducing a database migration.
- Review dependency licenses and third-party notices before public release.
- Record unresolved scan-quality, performance, accessibility, and device-compatibility limitations explicitly.
