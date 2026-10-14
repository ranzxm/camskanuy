# PRD — Camskanuy

## 1. Overview

**Camskanuy** is the working name of a native Android document scanner that turns document photos into clean images and multi-page PDFs. The application prioritizes short workflows, privacy, offline operation, and minimalist design.

CamScanner is a reference for feature categories, not a target for copying its identity or achieving full parity. Scan results are not guaranteed to match CamScanner.

Status: **Initial MVP implemented; build, lint, unit tests, and Android 35 emulator smoke tests passed for `dc378d1`. Physical-device acceptance remains pending.** This document defines the target behavior, not a claim that every acceptance criterion has passed.

## 2. Agreed decisions

| Area | Decision |
| --- | --- |
| Platform | Native Android, Kotlin |
| UI | XML Views + Material Components, not Compose |
| Connectivity | All core features work offline from first use |
| Storage | On-device storage, without accounts or synchronization |
| Design | Modern minimalist design, light/dark mode |
| OCR | Outside the MVP |
| Build | GitHub Actions; no Android tools installed on the user's computer |
| Initial distribution | Debug APK from Actions artifacts, tested on physical phones |
| Repository | https://github.com/ranzxm/camskanuy.git |

Initial technical baseline: Android 8.0 / API 26 and above. Application ID: `com.ranzxm.camskanuy`. The working name and icon identity can be finalized before public distribution.

## 3. Target users and primary needs

- Users who want to scan assignments, receipts, forms, or work documents without an internet connection.
- Users who want to combine multiple pages into a PDF without registration.
- Users of resource-constrained devices who need responsive previews and simple controls.

Primary flow: **Documents → Camera/Gallery → Edit scan → Arrange pages → Save → Details → Export/Share**.

## 4. MVP scope

### 4.1 Camera and import

- Capture photos using the rear camera through CameraX.
- Provide flash controls appropriate to the camera's capabilities, with clear state feedback.
- Request camera permission when the user opens the camera feature, not on first app launch.
- Import one or multiple images through the system picker; do not request broad storage access.
- Correct input orientation using image metadata.
- Auto-shutter, batch capture without review, and PDF import are outside the MVP.

### 4.2 Scan editing

- Detect the document's four corners locally after capture/import.
- Always allow manual adjustment of crop corners.
- If detection fails, initialize the crop to the image boundaries and show a short message rather than blocking the workflow.
- Apply perspective correction using the four selected corners.
- Support rotation in 90-degree increments.
- Filters: **Original**, **Color**, **Grayscale**, and **Black and white**. Color applies mild readability enhancement.
- Use downsampled previews and higher-resolution images for exports.
- Preserve source images and edit parameters so cropping/filtering can be adjusted without accumulating compression degradation.

### 4.3 Multi-page documents

- A document contains at least one page.
- Add pages from the camera or gallery.
- Re-edit, rotate, delete, and reorder pages.
- Provide accessible alternatives to drag-based reordering, such as Move earlier/later.
- If all pages are deleted, show an empty draft state; disable document saving until a page is added.
- Persist drafts after page confirmation. Offer Resume/Discard when reopening an unfinished draft.

### 4.4 Local document management

- List documents with a first-page thumbnail, name, last-updated date, and page count.
- Default sorting: most recently updated first.
- Default names use local date and time, for example `Scan 2026-10-14 14.30`.
- Names must not be empty after trimming. Keep display names separate from internal IDs/paths; input characters must not enable path traversal.
- Support renaming and deletion with confirmation. Deletion removes the associated metadata and app-private files.
- Search by name, case-insensitively; do not search image contents.
- Single-level folders: create, rename, delete, and move documents. Deleting a folder moves its documents to Unfiled instead of deleting them.
- Nested folders and a trash/recycle bin are outside the MVP.

### 4.5 PDF, JPG, and sharing

- PDFs preserve document page order and have no application watermark.
- MVP PDF pages follow the cropped image's aspect ratio; paper-size presets can be added later.
- JPG export applies to the selected page; bulk JPG/ZIP export is outside the MVP.
- Users select an export destination through the system save dialog.
- Local documents remain available if export is canceled or fails.
- Share PDFs or JPGs through the Android Sharesheet using content URIs and temporary read grants.
- The application does not upload files. Recipient applications or destination providers chosen by the user may use the internet outside Camskanuy's control.

## 5. Outside the MVP

- OCR and document-content search.
- Accounts, cloud sync, collaboration, analytics, and advertising.
- Signatures, custom watermarks, PDF passwords/encryption, and PDF text editing.
- PDF compression with user-selectable quality/size settings.
- Specialized ID card scanning, book scanning, QR/barcodes, object removal, and generative AI features.
- iOS, web, subscriptions, and Play Store release.

Efficient image encoding is still required in the MVP; it is distinct from a dedicated PDF compression feature.

## 6. Data and privacy

- Minimum document metadata: ID, name, creation/update timestamps, optional folder, and draft/saved status.
- Minimum page metadata: ID, document ID, position, source/output references, crop, rotation, and filter.
- Source files, processed pages, and thumbnails reside in app-private storage. File references must not directly use user-entered names.
- Clean up cached sharing exports safely when no longer needed; do not delete them before recipient applications have a chance to read them.
- The MVP has no data transmission, telemetry, or automatic update checks.
- Configure cloud-backup exclusions for document data. Do not claim dedicated application-level encryption for local data.
- Uninstalling or clearing app data removes private documents. The application does not delete files already exported to user-selected locations.
- Briefly explain these storage policies in the application's help/information UI.

## 7. Quality and nonfunctional constraints

- First use in airplane mode must support capture, crop, filters, saving, and local export without additional downloads.
- Heavy operations must not block the main thread. Show meaningful progress; use an indeterminate indicator when actual progress is unavailable.
- Load thumbnails incrementally rather than loading all full-resolution page bitmaps at once.
- Initial target scenario: one 20-page document on a phone with 3 GB RAM without memory-related crashes. This is a test target, not a claim of current capability.
- Measure APK size from a release build or a representative optimized build. The initial 10–30 MB native estimate without OCR is neither a guarantee nor a debug APK acceptance limit.
- Support light/dark mode, font scaling up to 200%, system safe areas, and TalkBack for primary actions.
- The initial application UI is in Indonesian; use string resources for all text to support localization. English documentation does not change the application's UI language.
- Handle denied permissions, unavailable cameras, corrupt input, full storage, and cancellation without losing saved documents.

## 8. Acceptance criteria

| ID | Scenario | Expected result |
| --- | --- | --- |
| AC-01 | Install and launch for the first time in airplane mode | No login/download; basic scanning is usable |
| AC-02 | Deny camera permission | Explanation and import option are available; no crash |
| AC-03 | Capture a tilted document | Automatic/manual cropping and perspective correction produce the selected page |
| AC-04 | Edge detection fails | Manual cropping remains available |
| AC-05 | Apply rotation/filters | Preview and saved output match the edit parameters |
| AC-06 | Arrange a three-page document | Additions, deletions, and reordering are reflected in the PDF |
| AC-07 | Close and reopen the app after confirming a page | The draft can be resumed; saved documents remain available |
| AC-08 | Rename, search, and move a document into a folder | The list and search reflect the changes |
| AC-09 | Delete a folder containing documents | Documents move to Unfiled and are not deleted |
| AC-10 | Export PDF/JPG to a local destination | Files open in external applications; PDF has no watermark |
| AC-11 | Share a document | The recipient receives a readable URI, not a private filesystem path |
| AC-12 | Cancel export or encounter full storage | Appropriate feedback is shown; source documents remain intact |
| AC-13 | Test light/dark mode, TalkBack, and large fonts | Core actions remain readable and usable |
| AC-14 | Push code to GitHub | Actions runs lint, unit tests, and debug assembly; APK is available on success |
| AC-15 | Review the manifest and dependencies | No internet permission or runtime download requirement for core features |

## 9. Implementation phases

1. **Foundation:** project bootstrap, XML theme, navigation, local storage, and CI producing an initial APK.
2. **Scanning:** camera, import, edge detection, cropping/perspective correction, rotation, and local filters.
3. **Documents:** drafts, page arrangement, saving, listing, search, and folders.
4. **Output:** document details, PDF/JPG export, sharing, and failure handling.
5. **Validation:** CI results, physical-device testing, offline/privacy checks, accessibility, memory, and build size.

Each phase must keep the build valid. Temporary/mock behavior must not be treated as a completed feature.

## 10. Definition of done

- MVP scope and acceptance criteria are verified, with CI results and device testing reported separately.
- No interactive placeholders imply that unavailable features already work.
- The repository contains source code, reproducible build configuration, workflows, and accurate documentation.
- The debug APK produced by Actions can be downloaded and installed on target devices.
- Remaining limitations are explicitly documented. Without physical-phone testing, the application is not validated end to end.
