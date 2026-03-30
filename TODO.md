# TODO — Basic Library Screen

## Phase 1: Gradle Dependencies

- [ ] Add Koin dependencies to `shared/build.gradle.kts` (`koin-core`, `koin-compose`, `koin-compose-viewmodel` in commonMain; `koin-android` in androidMain)
- [ ] Add Navigation Compose dependency (`navigation-compose:2.8.0-alpha10`) to commonMain
- [ ] Add SQLDelight coroutines extensions (`coroutines-extensions:2.0.2`) to commonMain
- [ ] Remove `com.benasher44:uuid:0.8.4` from commonMain (replaced by `kotlin.uuid.Uuid`)
- [ ] Verify all dependencies resolve with `./gradlew :shared:dependencies`

## Phase 2: DraftyTheme

- [ ] Implement `DraftyColors` data class and `DarkColors` instance with neon/cyberpunk hex values
- [ ] Create `LocalDraftyColors` CompositionLocal and `DraftyTheme` object in `shared/src/commonMain/.../ui/theme/DraftyTheme.kt`
- [ ] Implement `DraftyTheme` composable wrapper (with minimal MaterialTheme for Material3 component compatibility)
- [ ] Create `FolderColorMapping.kt` — `FolderColor.toColor()` extension mapping each enum to neon accent color

## Phase 3: Repository Interfaces (commonMain)

- [ ] Implement `FolderRepository` interface in `shared/src/commonMain/.../persistence/FolderRepository.kt` (getRootFolders, getFoldersByParent, getFolderById, insert, update, delete)
- [ ] Implement `CanvasRepository` interface in `shared/src/commonMain/.../persistence/CanvasRepository.kt` (getRootCanvasSummaries, getCanvasSummariesByFolder, getCanvasById, getThumbnail, insert, updateMetadata, updateThumbnail, delete)

## Phase 4: Android Repository Implementations (androidMain)

- [ ] Verify/fill `DatabaseDriverFactory` expect declaration in commonMain
- [ ] Implement `DatabaseDriverFactory` actual class in androidMain (AndroidSqliteDriver, WAL via API 29+ default)
- [ ] Implement `SqlDelightFolderRepository` in androidMain (using `.asFlow().mapToList()` for reactive queries, mapping SQL rows to `Folder` domain model)
- [ ] Implement `SqlDelightCanvasRepository` in androidMain (same pattern, mapping to `Canvas`/`Canvas.Summary`)
- [ ] Build generated SQLDelight sources and verify mapper syntax works (`./gradlew :shared:generateDebugDraftyDatabaseInterface` or full build)

## Phase 5: LibraryViewModel (commonMain)

- [ ] Define `LibraryState` data class (currentFolderId, currentFolderTitle, folders, canvases, isLoading, navigationStack)
- [ ] Define `FolderBreadcrumb` data class
- [ ] Implement `LibraryViewModel` class with injected `FolderRepository`, `CanvasRepository`, `CoroutineScope`
- [ ] Implement `observeCurrentFolder()` — combines folder + canvas Flows reactively using `collectLatest` + `combine`
- [ ] Implement `navigateToFolder()` — pushes breadcrumb, updates currentFolderId
- [ ] Implement `navigateBack()` — pops breadcrumb stack, returns false at root
- [ ] Implement `createFolder()` — generates UUID, inserts via repository (Flow auto-updates UI)
- [ ] Implement `createCanvas()` — generates UUID, inserts via repository

## Phase 6: LibraryScreen Composable (commonMain)

- [ ] Implement `LibraryScreen` composable — main layout with top bar, grid, FAB, create dialog trigger
- [ ] Implement `LibraryTopBar` — custom Row with back button (when not at root) and title
- [ ] Implement `LibraryGrid` — `LazyVerticalGrid` with adaptive columns (160dp min), folders first then canvases
- [ ] Implement `FolderCard` — dark surface card with neon color strip, folder icon, title
- [ ] Implement `CanvasCard` — dark surface card with purple strip, placeholder icon, title, relative date
- [ ] Implement `CreateDialog` — AlertDialog with folder/canvas toggle (FilterChips), title input, color picker or template picker
- [ ] Implement `FolderColorPicker` — row of colored circles for each `FolderColor` entry
- [ ] Implement `TemplatePicker` — row of FilterChips for each `Template` entry
- [ ] Implement `EmptyLibraryMessage` — centered "No canvases yet" + "Tap + to create one"
- [ ] Create `DateFormat.kt` — `formatDate()` utility (relative time: just now, Xm, Xh, Xd, Xmo ago)

## Phase 7: Koin DI Setup

- [ ] Create `KoinModules.kt` in commonMain with `sharedModule` (empty for now — ViewModel constructed manually)
- [ ] Create `AndroidModule.kt` in androidMain with `androidModule` (DatabaseDriverFactory, DraftyDatabase, FolderRepository, CanvasRepository bindings)
- [ ] Create `DraftyApplication.kt` in androidApp — `Application` subclass calling `startKoin` with `androidContext`, `sharedModule`, `androidModule`
- [ ] Register `DraftyApplication` in `AndroidManifest.xml` (`android:name`)

## Phase 8: Navigation & DraftyApp Wiring

- [ ] Create `DraftyApp.kt` in `shared/src/commonMain/.../ui/` — NavHost with "library" and "canvas/{canvasId}" routes
- [ ] Wire LibraryScreen in "library" route — construct `LibraryViewModel` via `remember` + `koinInject` repos + `rememberCoroutineScope`
- [ ] Add canvas stub route ("canvas/{canvasId}") — placeholder, no implementation yet
- [ ] Update `androidApp/.../DraftyApp.kt` to delegate to shared `com.drafty.ui.DraftyApp()`

## Phase 9: expect/actual Verification

- [ ] Verify `DatabaseDriverFactory` expect declaration in commonMain matches actual in androidMain
- [ ] Ensure iOS stubs exist in iosMain (even if empty/throwing) so the build passes

## Phase 10: Build & Typecheck

- [ ] Run `./gradlew :shared:compileDebugKotlinAndroid` — fix any typecheck errors
- [ ] Run `./gradlew :androidApp:compileDebugKotlin` — fix any typecheck errors
- [ ] Run `./gradlew :androidApp:assembleDebug` — verify full debug build succeeds
- [ ] Run `./gradlew lint` — fix any critical lint warnings

---

## Manual Testing Checklist (Emulator)

- [ ] App launches without crash
- [ ] Library screen shows empty state ("No canvases yet" message)
- [ ] FAB (+) button is visible and tappable
- [ ] Create dialog opens with Canvas/Folder toggle chips
- [ ] Can switch between Canvas and Folder modes in dialog
- [ ] Title text field accepts input
- [ ] Folder color picker shows all 8 color circles
- [ ] Template picker shows all template options
- [ ] Creating a folder adds it to the grid immediately
- [ ] Creating a canvas adds it to the grid immediately
- [ ] Folder card shows correct color strip and icon
- [ ] Canvas card shows purple strip and placeholder icon
- [ ] Tapping a folder navigates into it (title changes, back button appears)
- [ ] Back button returns to parent folder
- [ ] Creating items inside a folder places them correctly
- [ ] Empty folder shows empty state message
- [ ] Grid layout adapts to screen size (adaptive columns)
- [ ] Theme colors are correct (dark background, neon accents, no Material defaults leaking)
