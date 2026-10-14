# DESIGN — Camskanuy

## 1. Visual direction

**Minimalist, calm, clear, and document-focused.** XML Views is an implementation technology, not a reason to use outdated Android visuals. Use Material Components with a Views-based Material 3 theme, without Compose.

- Keep documents as the visual focus, rather than decorative cards or oversized illustrations.
- Use a single blue accent and a consistent typographic hierarchy.
- Prioritize whitespace, alignment, and contrast over excessive shadows or gradients.
- Avoid statistics dashboards, promotional banners, unnecessary bottom navigation, and duplicate actions.
- Do not imitate CamScanner's logo, assets, or distinctive visual arrangements.

## 2. Design tokens

Store all values in resources/themes; use semantic attributes in layouts rather than hardcoded per-component colors.

### Initial colors

| Semantic token | Light | Dark | Usage |
| --- | --- | --- | --- |
| primary | `#2563EB` | `#AFC6FF` | Primary actions and active selections |
| onPrimary | `#FFFFFF` | `#102B62` | Text/icons on primary |
| primaryContainer | `#DBEAFE` | `#1E3A8A` | Tonal actions and FAB background |
| onPrimaryContainer | `#1E3A8A` | `#DBEAFE` | Text/icons on tonal actions |
| background | `#F8FAFC` | `#0F141C` | Screen background |
| surface | `#FFFFFF` | `#18212D` | Toolbars, sheets, containers |
| surfaceVariant | `#EEF2F6` | `#243041` | Secondary areas and placeholders |
| onSurface | `#172033` | `#EDF2FA` | Primary text |
| onSurfaceVariant | `#526174` | `#B8C4D4` | Metadata and secondary text |
| outline | `#CBD5E1` | `#46566C` | Decorative separators |
| error | `#B3261E` | `#FFB4AB` | Errors and destructive actions |

- Do not use the outline token as the sole boundary indicator for essential controls. Use a color that meets non-text contrast requirements when a boundary is necessary to identify a control.
- Do not distinguish states by color alone; add an icon or label.
- Contrast targets: at least 4.5:1 for normal text and 3:1 for large text/relevant control indicators. Verify actual color pairs, including disabled/selected states.
- Follow the system theme by default. Light/Dark/System options can appear in an Appearance sheet opened from the Documents overflow menu.
- Defer dynamic color to keep the initial palette consistent.

### Typography

Use the system sans-serif font (Roboto on devices that provide it), without downloadable fonts.

| Role | Base size | Weight |
| --- | --- | --- |
| Screen title | 24sp | Medium / 500 |
| Sheet/section title | 20sp | Medium / 500 |
| Document name/primary body | 16sp | Regular or Medium |
| Secondary body | 14sp | Regular / 400 |
| Metadata | 12sp | Regular / 400 |
| Button label | 14sp | Medium / 500 |

- Use `sp`, string resources, and layouts that accommodate text growth.
- Long names may use ellipsis in lists; provide the full name in details/rename views.
- Avoid all-caps styling for every button and fixed heights that clip enlarged text.

### Dimensions and spacing

- Spacing scale: **4, 8, 12, 16, 24, 32 dp**.
- Phone screen horizontal margins: 20dp; gaps between related elements: 8–12dp.
- Corner radii: controls/cards 12dp, sheet top corners 24dp, thumbnails 8dp.
- Minimum touch target: 48 × 48dp; visual icons are typically 24dp.
- Primary buttons: min-height 52dp, not a fixed height.
- Keep elevation low; use separators or surface differences when clearer.
- Apply WindowInsets for status/navigation bars, display cutouts, and the keyboard. Do not apply system padding twice.

## 3. Components and Views implementation

- `MaterialToolbar`: title, back navigation, and contextual overflow.
- `RecyclerView`: document lists, pages, and thumbnails; do not place the entire list inside a ScrollView.
- `ExtendedFloatingActionButton`: Scan action on the Documents screen.
- `MaterialButton`: primary and secondary actions.
- `TextInputLayout` + `TextInputEditText`: renaming, folders, and other inputs with clear errors.
- `BottomSheetDialogFragment`: page sources, export options, folder filters, and appearance settings.
- `MaterialAlertDialogBuilder`: confirmation for deletion or discarding changes.
- Use ViewBinding for view access and ConstraintLayout where appropriate; avoid unnecessary nesting.
- Bundle vector icons with a consistent outline style. Icon-only controls require accessibility labels; decorative thumbnails should not be announced repeatedly.
- A custom crop overlay is allowed for corner manipulation, with accessible alternative controls.

## 4. Screen map and interactions

UI labels and sample copy below are written in English for documentation. Localize them into Indonesian for the initial application UI, as specified in `PRD.md`.

```text
Documents
  ├─ Scan → Camera → Edit scan → Arrange pages
  ├─ Import images → Edit scan → Arrange pages
  ├─ Folders / Search
  └─ Document details → Edit pages / Export / Share

Arrange pages
  ├─ Add → Camera or Gallery → Edit scan
  ├─ Edit page → Edit scan
  └─ Save → Document details
```

Multiple imported images enter an editor review queue; process them one at a time to control memory usage. Show the current position, such as `Page 2 of 5`.

### 4.1 Documents

- Toolbar: **Documents**, search action, and overflow for Appearance/App information.
- Compact folder filter below the toolbar; default to **All documents**. The folder sheet includes Unfiled and folder management.
- Vertical list with thumbnail, name, last-updated date, page count, and per-document overflow.
- Tapping an item opens details. Overflow actions: Rename, Move, Share, Delete.
- Place the **Scan** extended FAB at the bottom right without obscuring the last list item.
- Offer a labeled import button in the empty state and through the camera sheet/actions; do not gate import behind camera permission.
- Empty state: **No documents yet** — “Scan or import an image to create your first document.”
- Empty search: **No documents found**, with an option to clear the search.
- Display an active draft as a **Resume scan** row, separate from saved documents.

### 4.2 Camera

- Make the preview dominant, with dark surrounding controls for readability over the image.
- Top area: back and accessibly labeled flash controls.
- Bottom area: Gallery, a large centered shutter, and access to draft pages when available.
- Short instruction: “Place the document inside the frame.”
- The guide frame does not imply live detection. The MVP detects edges after capture.
- Open the editor after capture; the user selects Use page before it is added to the draft.
- Permission denied: explain why camera access is needed; offer Gallery and Retry/Open settings according to the permission state.

### 4.3 Edit scan

- Toolbar: back, **Edit scan** title, and page context when needed.
- Large preview with a crop overlay and four easy-to-touch corner handles.
- Select **Crop** and **Filter** tool modes through labeled controls; provide rotation as an icon + text action.
- Filter options use small previews and labels: Original, Color, Grayscale, Black and white. Mark the active option with an outline/checkmark, not color alone.
- Bottom primary action: **Use page** for new pages, **Save changes** when re-editing.
- When crop detection fails: “Adjust the document corners manually.”
- Provide a crop reset action. For accessibility, allow selecting and moving corners with directional controls rather than drag gestures alone.
- Navigating back with unapplied changes offers Keep editing/Discard changes.

### 4.4 Arrange pages

- Toolbar: **Arrange pages** and page count.
- Numbered thumbnails with an obvious sequence. Use a list on narrow screens/with large fonts; use a grid only when space permits.
- Tapping a thumbnail opens the editor. Per-page menu: Edit, Rotate, Move earlier/later, Delete.
- A drag handle may support reordering; dragging must not be the only method.
- Secondary **Add page** button opens Camera/Gallery choices.
- Primary **Save document** button opens a name input populated with the default name.
- Empty draft: prompt to add a page, with saving disabled.
- Back offers **Save draft and exit** or **Discard draft**; never discard pages silently.

### 4.5 Document details

- Toolbar: document name and overflow for Rename/Move/Delete.
- Page preview with a `1 / 3` indicator and thumbnail navigation when needed.
- Zoom and page navigation must not compete for gestures; provide previous/next page buttons as alternatives.
- **Edit pages** opens page arrangement to modify the document.
- Bottom actions: **Export** as primary, **Share** as secondary.
- Export/share sheet: **PDF — All pages** or **JPG — Current page**.
- Do not display OCR, password, or cloud options as nonfunctional buttons in the MVP.

## 5. States, feedback, and errors

| State | Behavior |
| --- | --- |
| Loading the list | Lightweight indicator; do not show an empty state before the query completes |
| Processing an image | Keep the preview visible when available; show “Processing page…” and prevent duplicate submission |
| Creating a PDF | Show “Creating PDF…” and page progress when available |
| Save/export succeeded | Brief snackbar; avoid a blocking success dialog |
| Export canceled | Return to details without an error message |
| Corrupt image | Explain that the file cannot be opened; allow another selection and retain valid pages |
| Storage full | Persistent message with Retry after the user frees space |
| Delete document | Dialog names the document and explains that deletion cannot be undone |
| Delete folder | Dialog explains that documents will move to Unfiled |

- Logging operational errors is not sufficient; provide user-facing feedback.
- Do not show fabricated percentages or animations that make failed operations appear successful.
- Explain the status of operations that cannot yet be safely canceled; do not provide a nonfunctional cancel button.

## 6. Accessibility and adaptation

- All core workflows must be usable with TalkBack, including manual cropping and reordering.
- Shutter label: “Take photo”; the flash icon announces its state, such as “Flash off”.
- Return focus to the triggering control when a dialog closes. Announce important errors to accessibility services.
- At 200% font scaling, buttons/toolbars must not clip labels; move secondary actions into overflow when necessary.
- Do not lock orientation solely to avoid layout issues. Handle rotation and camera/editor state restoration.
- On wide screens, constrain form width and use available space for previews instead of stretching every control.
- Use short animations, approximately 150–250 ms, respecting system animation settings. Avoid looping decorative animation.

## 7. Design review checklist

- [ ] All screens follow consistent spacing, colors, typography, and corner radii.
- [ ] Each step has only one visually dominant primary action.
- [ ] Light/dark mode, edge-to-edge layout, keyboard behavior, and rotation are checked.
- [ ] Large fonts and TalkBack do not block core workflows.
- [ ] Empty, loading, success, permission-denied, and error states are provided.
- [ ] The UI does not imply that out-of-scope features are already available.
- [ ] No cloud/login/advertising, CamScanner assets, or online font/icon dependencies are present.
- [ ] Screenshots and device-test results are distinguished from mockups/specifications.

This document is an initial specification, not evidence of an implemented UI. Design changes affecting workflows or scope must be synchronized with `PRD.md`.
