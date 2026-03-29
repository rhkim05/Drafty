# TODO — Library Screen

Linked from [plan.md](plan.md).

---

## Phase 1: Foundation (Theme + Persistence)

- [ ] Implement `DraftyTheme` with light color scheme and `FolderColor.toComposeColor()` extension
- [ ] Add `createDriver()` to `DatabaseDriverFactory` expect declaration
- [ ] Implement `DatabaseDriverFactoryAndroid` actual (AndroidSqliteDriver)
- [ ] Add `FolderRepository` interface body
- [ ] Add `CanvasRepository` interface body
- [ ] Implement `SqlDelightFolderRepository` (all 6 queries)
- [ ] Implement `SqlDelightCanvasRepository` (all 8 queries)
- [ ] Wire DI in `DraftyApplication` (database + repositories)
- [ ] Build and verify code generation (SQLDelight generates correctly)
- [ ] Typecheck: `./gradlew :shared:compileDebugKotlinAndroid`

## Phase 2: ViewModel + State

- [ ] Create `LibraryItem` sealed interface (FolderItem, CanvasItem)
- [ ] Create `LibraryState` and `BreadcrumbEntry` data classes
- [ ] Implement `LibraryViewModel` with folder/canvas observation via `combine()`
- [ ] Implement navigation: `navigateToFolder`, `navigateUp`, `navigateToBreadcrumb`
- [ ] Implement CRUD: `createFolder`, `createCanvas`, `renameItem`, `deleteItem`
- [ ] Cancel previous observation job on navigation
- [ ] Typecheck: `./gradlew :shared:compileDebugKotlinAndroid`

## Phase 3: Library Screen UI

- [ ] Implement `LibraryScreen` scaffold (TopAppBar, FAB, content area)
- [ ] Implement `LibraryItemCard` (folder icon + canvas preview + title + date)
- [ ] Implement `FolderIcon` composable (colored rounded rectangle)
- [ ] Implement `CanvasPreview` composable (template text indicator)
- [ ] Implement `BreadcrumbBar` (horizontally scrollable breadcrumb row)
- [ ] Implement `EmptyState` (centered message)
- [ ] Implement `formatRelativeDate` utility
- [ ] Implement `LazyVerticalGrid` with `GridCells.Adaptive(160.dp)`
- [ ] Typecheck: `./gradlew :shared:compileDebugKotlinAndroid`

## Phase 4: Dialogs

- [ ] Implement `CreateDialog` with Folder/Canvas tabs
- [ ] Implement `FolderColorPicker` (8 colored circles)
- [ ] Implement `TemplatePicker` (4 preview cards)
- [ ] Implement `DeleteConfirmationDialog`
- [ ] Implement `RenameDialog`
- [ ] Typecheck: `./gradlew :shared:compileDebugKotlinAndroid`

## Phase 5: Navigation Shell

- [ ] Define `Screen` sealed interface in `DraftyApp.kt`
- [ ] Implement `DraftyApp` with `DraftyTheme` wrapping and screen routing
- [ ] Create `LibraryViewModel` with `rememberCoroutineScope` in `DraftyApp`
- [ ] Add canvas screen placeholder (back button returns to library)
- [ ] Typecheck: `./gradlew :androidApp:compileDebugKotlin`

## Phase 6: Integration & Polish

- [ ] Full build: `./gradlew build`
- [ ] Fix any SQLDelight type mapping issues (`has_thumbnail` Boolean vs Long)
- [ ] Verify reactive updates (create folder → grid updates, delete → grid updates)
- [ ] Verify folder navigation (tap folder → shows contents, back → returns)
- [ ] Verify breadcrumb navigation (tap ancestor → jumps correctly)
- [ ] Verify empty state shows for root and empty folders

---

## Manual Testing Checklist (Emulator)

- [ ] App launches and shows Library Screen with empty state
- [ ] FAB opens create dialog
- [ ] Create a folder — appears in grid with correct color
- [ ] Create a canvas — appears in grid with template indicator
- [ ] Tap folder — navigates into it, shows back arrow and breadcrumb
- [ ] Tap back arrow — returns to parent
- [ ] Tap breadcrumb — jumps to that ancestor
- [ ] Create items inside a folder — they appear correctly
- [ ] Long-press item — rename dialog appears
- [ ] Rename an item — title updates in grid
- [ ] Delete a canvas — removed from grid
- [ ] Delete a folder — folder removed, canvases inside move to root
- [ ] Rotate device — app doesn't crash (state reloads from DB)
- [ ] Grid adapts to screen width (2-3 columns on tablet)
