# AGENTS.md — Camskanuy Development Guidelines

## Context and sources of truth

- Target repository: https://github.com/ranzxm/camskanuy.git.
- Working application name: **Camskanuy**. Application ID: `com.ranzxm.camskanuy`.
- Product: an offline Android document scanner with a friendly, airy, modern UI.
- `PRD.md` defines product scope, behavior, and acceptance criteria.
- `DESIGN.md` defines visuals, interactions, and accessibility.
- This document defines the workflow for developers and agents.
- If documents conflict, clarify the decision and update the affected documents before implementation. Do not silently change scope.

## Mandatory constraints

1. Use **Kotlin + Jetpack Compose + Material 3**. The user-approved Compose redesign replaces the XML UI foundation; do not use Flutter or React Native.
2. All core features must work offline from the first launch. Do not depend on models, modules, or scanner services that require runtime downloads.
3. The MVP has no accounts, cloud sync, advertising, analytics, or OCR.
4. Do not add the `INTERNET` permission to the MVP; also inspect permissions contributed by dependencies in the merged manifest.
5. Do not install Android Studio, Android SDK/NDK, emulators, or any other Android development toolchain on the user's computer. Do not install a local JDK/Gradle to work around this constraint.
6. Run Android builds and validation in **GitHub Actions**. Do not claim a successful build based on code inspection alone.
7. Create an original identity and design; do not copy CamScanner's name, logo, assets, or proprietary code.

## Before making changes

- Inspect the directory structure, configuration, dependencies, and existing user changes before editing.
- Before these documents were created, the project directory was empty and was not a Git repository. Recheck the actual state during subsequent work.
- Make the smallest necessary changes. Avoid unrelated refactoring, dependencies, and directories that are not yet needed.
- Do not manually edit generated files or include build output in the repository.

## Technical foundation

- UI: Compose Material 3, `activity-compose`, and `navigation-compose`; Kotlin screen composables replace XML layouts, ViewBinding, and Fragment navigation.
- Architecture: single activity, ViewModel, StateFlow, coroutines, and a simple local repository. Collect StateFlows with `collectAsStateWithLifecycle` from `lifecycle-runtime-compose`.
- Interoperability: use `AndroidView` only for CameraX `PreviewView`, the existing crop overlay, and optional existing zoom view. Do not rebuild whole screens with embedded Views.
- Camera: CameraX. A custom camera flow and local processing are required; do not use scanner SDKs that require runtime downloads.
- Document metadata: Room. Image and PDF files: app-private storage.
- Import: Android Photo Picker where available, with a Storage Access Framework fallback.
- Export: Storage Access Framework. Sharing: FileProvider with temporary URI read grants.
- Support baseline: Android 8.0 / API 26 and above. Update the PRD when changing this baseline.
- Select compatible stable versions of Kotlin, AGP, Gradle, CI JDK, compileSdk, and targetSdk during project bootstrap. Pin versions; do not use dynamic versions such as `+`.
- Use Gradle Kotlin DSL and a version catalog for dependencies. Gradle is the Android build tool; Bun is not a replacement.
- If supporting JavaScript/TypeScript work is genuinely required, use Bun, not npm/npx/yarn/pnpm. Do not introduce a JS stack just to build Android.

## Intended project structure

```text
.github/workflows/         # Build and validation pipelines
app/src/main/
  java/<application-package>/
    data/                 # Room, repositories, file access
    scanner/              # Local edge detection and image transforms
    ui/
      CamskanuyRoot.kt     # Compose navigation host
      common/             # Shared Compose theme/components, existing zoom View
      documents/DocumentsScreen.kt
      capture/CaptureScreen.kt
      editor/EditorScreen.kt # Existing CropOverlayView.kt may remain
      pages/PagesScreen.kt
      detail/DetailScreen.kt
  res/                    # Strings, drawables, platform theme/configuration
app/src/test/             # Unit tests
app/src/androidTest/      # Instrumentation tests when needed
gradle/                   # Version catalog and wrapper
```

- This structure is a target, not a claim that the implementation already exists.
- Start with a single `app` module. Add layers/modules only when there is a concrete need.
- Do not introduce generic abstractions, dependency injection frameworks, or empty directories without a reason.

## Implementation rules

- Use Kotlin null safety; avoid `!!`, swallowed exceptions, and global mutable UI state.
- Perform decoding, filtering, transformations, compression, and export off the main thread.
- Use downsampled previews; do not keep full-resolution bitmaps for every page in memory.
- Respect screen lifecycles for Flow collection, camera binding, and work cancellation. Dispose camera/View bindings when their composable leaves composition.
- Keep localized text in string resources; centralize Compose colors, typography, spacing, and shapes in shared theme/tokens. Follow `DESIGN.md`; retain XML only for platform configuration/resources, not screen layouts.
- Validate image inputs, EXIF orientation, dimensions, and corrupt files. Do not assume a URI has a filesystem path.
- Keep metadata and files consistent; handle partial failures, full storage, cancellation, and temporary-file cleanup.
- Image-processing dependencies must work offline, have compatible licenses, and be evaluated for APK size impact. Do not add OpenCV without assessing its necessity and cost.
- Do not log document contents, images, or sensitive paths. Do not commit credentials, keystores, or personal documents to Git.
- Disable cloud backup of document data through appropriate Android configuration. Do not promise to prevent all OEM device transfers without testing.

## GitHub Actions and Git

- Trigger the primary workflow on pushes and pull requests; provide `workflow_dispatch` for manual runs.
- CI runners provision a compatible JDK and Android SDK, then use the Gradle Wrapper tracked in the repository.
- Use the official wrapper with integrity validation; do not create a fake wrapper JAR or download binaries from untrusted sources.
- Run at least `./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug` in CI.
- Upload the debug APK as a clearly named artifact, such as `camskanuy-debug-<short-sha>`, with an initial retention period of 14 days.
- Debug APKs are for testing only. Release signing is separate work and must use GitHub Secrets, not key files in the repository.
- Use minimum workflow permissions, defaulting to `contents: read`. Do not expose secrets to untrusted pull requests.
- The user has specified the target repository and requested a push when implementation is ready. Check remotes, branches, access, and uncommitted work before pushing; do not force-push or delete history.
- Do not request tokens in chat. If authentication is unavailable, ask the user to configure GitHub authentication securely.
- If repository initialization or commits are needed, perform them only as part of authorized Git work, not automatically for every documentation edit.

## Verification and reporting

- After code changes, review imports, resources, navigation, permissions, security, and documentation consistency.
- Use Actions for lint, unit tests, and builds. Add emulator/instrumentation testing in CI when needed; do not run a local emulator.
- Camera behavior, scan quality, low-memory behavior, and usability require testing on a physical phone. A successful build does not prove that all features work.
- Report separately: checks actually executed, CI results, device testing, and anything not yet verified.
- Results for `dc378d1` validate the legacy XML implementation only. Compose CI/emulator results for `be4a2b9` are recorded in `docs/TESTING.md`; physical-device acceptance remains pending.
- For Markdown-only changes, a documentation consistency review is sufficient; do not claim Android tests have run.
- Update the PRD/DESIGN together when behavior or design changes. Record new decisions without silently changing existing agreements.
