# Plan — Basic Library Screen

## Goal

Implement a functional Library Screen that displays folders and canvases in a grid, supports navigation into folders, and allows creating new folders and canvases. This is the first real feature — it requires building the theme, persistence layer, ViewModel, DI wiring, and navigation infrastructure.

---

## Scope

**In scope:**
- Custom `DraftyTheme` (CompositionLocal-based, neon/cyberpunk dark theme)
- `FolderRepository` and `CanvasRepository` interfaces + Android SQLDelight implementations
- `DatabaseDriverFactory` Android implementation (with WAL mode)
- `LibraryViewModel` with state management
- `LibraryScreen` composable (grid of folders + canvases)
- Create folder dialog, create canvas dialog
- Folder navigation (tap folder → show contents, back to parent)
- Koin DI setup (`DraftyApplication`, shared + Android modules)
- Navigation wiring in `DraftyApp` (library route only — canvas route is a stub)
- Missing Gradle dependencies (Koin, Navigation)

**Out of scope:**
- Canvas screen (stub route only)
- Thumbnails (placeholder icons for now — thumbnail generation requires the canvas renderer)
- Rename/delete/move operations (v1.1)
- Drag-to-reorder (v1.1)
- Long-press context menus (v1.1)
- Search

---

## 1. Gradle Dependencies

Add missing dependencies to `shared/build.gradle.kts`:

```kotlin
// commonMain
implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
implementation("io.insert-koin:koin-core:4.0.0")
implementation("io.insert-koin:koin-compose:4.0.0")
implementation("io.insert-koin:koin-compose-viewmodel:4.0.0")

// androidMain
implementation("io.insert-koin:koin-android:4.0.0")
```

Remove `com.benasher44:uuid:0.8.4` from commonMain (research.md says to use `kotlin.uuid.Uuid` instead).

> **Note:** Exact Koin version and artifact names need verification at build time. The KMP Koin artifacts have changed naming between 3.x and 4.x. If `koin-compose-viewmodel` doesn't resolve, the fallback is `koin-androidx-compose` in androidMain only and manual ViewModel creation in commonMain.

---

## 2. DraftyTheme (`shared/src/commonMain/kotlin/com/drafty/ui/theme/DraftyTheme.kt`)

Custom CompositionLocal theme — no MaterialTheme. All colors from research.md Section 23.

```kotlin
package com.drafty.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class DraftyColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val accentPink: Color,
    val accentBlue: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
)

val DarkColors = DraftyColors(
    background = Color(0xFF0D0D11),
    surface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFF13131A),
    primary = Color(0xFF7C3AED),
    accentPink = Color(0xFFE040A0),
    accentBlue = Color(0xFF3B82F6),
    accentGreen = Color(0xFF22C55E),
    accentRed = Color(0xFFEF4444),
    textPrimary = Color(0xFFE8E8EE),
    textSecondary = Color(0xFF6B6B80),
    border = Color(0xFF2A2A3C),
)

val LocalDraftyColors = staticCompositionLocalOf { DarkColors }

object DraftyTheme {
    val colors: DraftyColors
        @Composable get() = LocalDraftyColors.current
}

@Composable
fun DraftyTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDraftyColors provides DarkColors) {
        // Minimal MaterialTheme needed because Material3 composables (AlertDialog,
        // FloatingActionButton, FilterChip, OutlinedTextField, etc.) internally
        // read from MaterialTheme for shapes, typography, and fallback colors.
        MaterialTheme(colorScheme = darkColorScheme(
            background = DarkColors.background,
            surface = DarkColors.surface,
            primary = DarkColors.primary,
            onPrimary = DarkColors.textPrimary,
            onSurface = DarkColors.textPrimary,
            onBackground = DarkColors.textPrimary,
        )) {
            content()
        }
    }
}
```

### FolderColor Mapping

Add a `toColor()` extension to `FolderColor` in the theme package, mapping each enum value to its neon accent hex:

```kotlin
// shared/src/commonMain/kotlin/com/drafty/ui/theme/FolderColorMapping.kt
package com.drafty.ui.theme

import androidx.compose.ui.graphics.Color
import com.drafty.model.FolderColor

fun FolderColor.toColor(): Color = when (this) {
    FolderColor.Red -> Color(0xFFEF4444)
    FolderColor.Orange -> Color(0xFFF97316)
    FolderColor.Yellow -> Color(0xFFEAB308)
    FolderColor.Green -> Color(0xFF22C55E)
    FolderColor.Blue -> Color(0xFF3B82F6)
    FolderColor.Purple -> Color(0xFF7C3AED)
    FolderColor.Pink -> Color(0xFFE040A0)
    FolderColor.Gray -> Color(0xFF6B7280)
}
```

---

## 3. Repository Interfaces (`shared/src/commonMain/kotlin/com/drafty/persistence/`)

These interfaces were designed in research.md Section 8. The list queries return `Flow` for reactive updates.

### FolderRepository.kt

```kotlin
package com.drafty.persistence

import com.drafty.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getRootFolders(): Flow<List<Folder>>
    fun getFoldersByParent(parentId: String): Flow<List<Folder>>
    suspend fun getFolderById(id: String): Folder?
    suspend fun insert(folder: Folder)
    suspend fun update(folder: Folder)
    suspend fun delete(id: String)
}
```

### CanvasRepository.kt

```kotlin
package com.drafty.persistence

import com.drafty.model.Canvas
import kotlinx.coroutines.flow.Flow

interface CanvasRepository {
    fun getRootCanvasSummaries(): Flow<List<Canvas.Summary>>
    fun getCanvasSummariesByFolder(folderId: String): Flow<List<Canvas.Summary>>
    suspend fun getCanvasById(id: String): Canvas?
    suspend fun getThumbnail(canvasId: String): ByteArray?
    suspend fun insert(canvas: Canvas)
    suspend fun updateMetadata(canvas: Canvas)
    suspend fun updateThumbnail(canvasId: String, thumbnail: ByteArray)
    suspend fun delete(id: String)
}
```

---

## 4. Android Repository Implementations

### DatabaseDriverFactory (`shared/src/androidMain/kotlin/com/drafty/persistence/DatabaseDriverFactoryAndroid.kt`)

```kotlin
package com.drafty.persistence

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.drafty.db.DraftyDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver {
        return AndroidSqliteDriver(
            schema = DraftyDatabase.Schema,
            context = context,
            name = "drafty.db",
            useNoBackupDirectory = false,
        )
    }
}
```

WAL mode is enabled by default on API 29+ (our minSdk), so no explicit configuration needed.

### SqlDelightFolderRepository

Uses SQLDelight's `.asFlow().mapToList()` for reactive queries. The `DraftyDatabase` generated code provides typed query methods. We need to map between SQL row types and our domain `Folder` model.

```kotlin
package com.drafty.persistence

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.drafty.db.DraftyDatabase
import com.drafty.model.Folder
import com.drafty.model.FolderColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlDelightFolderRepository(
    private val database: DraftyDatabase,
) : FolderRepository {

    private val queries get() = database.draftyQueries

    override fun getRootFolders(): Flow<List<Folder>> =
        queries.selectRootFolders { id, parent_id, title, color, created, modified, sort_order ->
            Folder(id, parent_id, title, FolderColor.valueOf(color), created, modified, sort_order.toInt())
        }.asFlow().mapToList(Dispatchers.IO)

    override fun getFoldersByParent(parentId: String): Flow<List<Folder>> =
        queries.selectFoldersByParent(parentId) { id, parent_id, title, color, created, modified, sort_order ->
            Folder(id, parent_id, title, FolderColor.valueOf(color), created, modified, sort_order.toInt())
        }.asFlow().mapToList(Dispatchers.IO)

    override suspend fun getFolderById(id: String): Folder? = withContext(Dispatchers.IO) {
        queries.selectFolderById(id).executeAsOneOrNull()?.let {
            Folder(it.id, it.parent_id, it.title, FolderColor.valueOf(it.color), it.created, it.modified, it.sort_order.toInt())
        }
    }

    override suspend fun insert(folder: Folder) = withContext(Dispatchers.IO) {
        queries.insertFolder(
            id = folder.id,
            parent_id = folder.parentId,
            title = folder.title,
            color = folder.color.name,
            created = folder.created,
            modified = folder.modified,
            sort_order = folder.sortOrder.toLong(),
        )
    }

    override suspend fun update(folder: Folder) = withContext(Dispatchers.IO) {
        queries.updateFolder(
            title = folder.title,
            color = folder.color.name,
            modified = folder.modified,
            parent_id = folder.parentId,
            sort_order = folder.sortOrder.toLong(),
            id = folder.id,
        )
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        queries.deleteFolder(id)
    }
}
```

> **Note on SQLDelight mapper syntax:** The lambda mapper form (`queries.selectRootFolders { id, parent_id, ... -> }`) depends on how SQLDelight generates the query. If it generates a typed data class instead, we'll use `.executeAsList().map { row -> Folder(...) }` and wrap the Flow manually. The exact approach will be verified after building generated sources.

### SqlDelightCanvasRepository

Same pattern. Maps `has_thumbnail` (SQL boolean expression `thumbnail IS NOT NULL`) to `Canvas.Summary.hasThumbnail`.

```kotlin
package com.drafty.persistence

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.drafty.db.DraftyDatabase
import com.drafty.model.Canvas
import com.drafty.model.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlDelightCanvasRepository(
    private val database: DraftyDatabase,
) : CanvasRepository {

    private val queries get() = database.draftyQueries

    override fun getRootCanvasSummaries(): Flow<List<Canvas.Summary>> =
        queries.selectRootCanvasSummaries { id, folder_id, title, template, created, modified, has_thumbnail ->
            Canvas.Summary(id, folder_id, title, Template.valueOf(template), created, modified, has_thumbnail == 1L)
        }.asFlow().mapToList(Dispatchers.IO)

    override fun getCanvasSummariesByFolder(folderId: String): Flow<List<Canvas.Summary>> =
        queries.selectCanvasSummariesByFolder(folderId) { id, folder_id, title, template, created, modified, has_thumbnail ->
            Canvas.Summary(id, folder_id, title, Template.valueOf(template), created, modified, has_thumbnail == 1L)
        }.asFlow().mapToList(Dispatchers.IO)

    override suspend fun getCanvasById(id: String): Canvas? = withContext(Dispatchers.IO) {
        queries.selectCanvasById(id).executeAsOneOrNull()?.let {
            Canvas(it.id, it.folder_id, it.title, Template.valueOf(it.template),
                it.created, it.modified, it.thumbnail, it.pdf_backing_path, it.pdf_page_index?.toInt())
        }
    }

    override suspend fun getThumbnail(canvasId: String): ByteArray? = withContext(Dispatchers.IO) {
        queries.selectCanvasThumbnail(canvasId).executeAsOneOrNull()?.thumbnail
    }

    override suspend fun insert(canvas: Canvas) = withContext(Dispatchers.IO) {
        queries.insertCanvas(
            id = canvas.id, folder_id = canvas.folderId, title = canvas.title,
            template = canvas.template.name, created = canvas.created, modified = canvas.modified,
            thumbnail = canvas.thumbnail, pdf_backing_path = canvas.pdfBackingPath,
            pdf_page_index = canvas.pdfPageIndex?.toLong(),
        )
    }

    override suspend fun updateMetadata(canvas: Canvas) = withContext(Dispatchers.IO) {
        queries.updateCanvasMetadata(
            title = canvas.title, template = canvas.template.name,
            modified = canvas.modified, folder_id = canvas.folderId, id = canvas.id,
        )
    }

    override suspend fun updateThumbnail(canvasId: String, thumbnail: ByteArray) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        queries.updateCanvasThumbnail(thumbnail = thumbnail, modified = now, id = canvasId)
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        queries.deleteCanvas(id)
    }
}
```

---

## 5. LibraryViewModel (`shared/src/commonMain/kotlin/com/drafty/ui/library/LibraryViewModel.kt`)

The ViewModel manages:
- Current folder (null = root)
- Navigation stack (for breadcrumb/back behavior)
- Combined list of folders + canvases at the current level
- Creating new folders and canvases

```kotlin
package com.drafty.ui.library

import com.drafty.model.Canvas
import com.drafty.model.Folder
import com.drafty.model.FolderColor
import com.drafty.model.Template
import com.drafty.persistence.CanvasRepository
import com.drafty.persistence.FolderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class LibraryState(
    val currentFolderId: String? = null,
    val currentFolderTitle: String? = null,
    val folders: List<Folder> = emptyList(),
    val canvases: List<Canvas.Summary> = emptyList(),
    val isLoading: Boolean = true,
    val navigationStack: List<FolderBreadcrumb> = emptyList(),
)

data class FolderBreadcrumb(
    val id: String?,
    val title: String?,
)

class LibraryViewModel(
    private val folderRepository: FolderRepository,
    private val canvasRepository: CanvasRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        observeCurrentFolder()
    }

    private fun observeCurrentFolder() {
        // Combine folders + canvases for current location, reactively
        scope.launch {
            _state.map { it.currentFolderId }
                .distinctUntilChanged()
                .collectLatest { folderId ->
                    val foldersFlow = if (folderId == null) {
                        folderRepository.getRootFolders()
                    } else {
                        folderRepository.getFoldersByParent(folderId)
                    }
                    val canvasesFlow = if (folderId == null) {
                        canvasRepository.getRootCanvasSummaries()
                    } else {
                        canvasRepository.getCanvasSummariesByFolder(folderId)
                    }

                    combine(foldersFlow, canvasesFlow) { folders, canvases ->
                        _state.update { it.copy(folders = folders, canvases = canvases, isLoading = false) }
                    }.collect()
                }
        }
    }

    fun navigateToFolder(folder: Folder) {
        _state.update { current ->
            current.copy(
                currentFolderId = folder.id,
                currentFolderTitle = folder.title,
                isLoading = true,
                navigationStack = current.navigationStack + FolderBreadcrumb(
                    id = current.currentFolderId,
                    title = current.currentFolderTitle,
                ),
            )
        }
    }

    fun navigateBack(): Boolean {
        val stack = _state.value.navigationStack
        if (stack.isEmpty()) return false
        val parent = stack.last()
        _state.update {
            it.copy(
                currentFolderId = parent.id,
                currentFolderTitle = parent.title,
                isLoading = true,
                navigationStack = stack.dropLast(1),
            )
        }
        return true
    }

    val isAtRoot: Boolean get() = _state.value.currentFolderId == null

    @OptIn(ExperimentalUuidApi::class)
    fun createFolder(title: String, color: FolderColor) {
        scope.launch {
            val now = currentTimeMillis()
            val folder = Folder(
                id = Uuid.random().toString(),
                parentId = _state.value.currentFolderId,
                title = title,
                color = color,
                created = now,
                modified = now,
                sortOrder = 0,
            )
            folderRepository.insert(folder)
            // Flow will automatically emit the updated list
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createCanvas(title: String, template: Template) {
        scope.launch {
            val now = currentTimeMillis()
            val canvas = Canvas(
                id = Uuid.random().toString(),
                folderId = _state.value.currentFolderId,
                title = title,
                template = template,
                created = now,
                modified = now,
                thumbnail = null,
                pdfBackingPath = null,
                pdfPageIndex = null,
            )
            canvasRepository.insert(canvas)
        }
    }

    private fun currentTimeMillis(): Long = com.drafty.util.currentTimeMillis()
}
```

### Design decisions

- **`CoroutineScope` injected, not hardcoded** — allows Koin to provide `viewModelScope` on Android or a test scope in tests. The ViewModel is a plain class in commonMain (not `androidx.lifecycle.ViewModel`).
- **`collectLatest`** on folder ID changes — cancels the previous folder's Flow collection when navigating to a new folder.
- **Navigation stack** is in-memory, not persisted. Back press walks the stack. Reaching empty stack = at root.
- **Reactive via Flow** — inserting a folder/canvas triggers SQLDelight to re-emit the query, so the UI updates automatically without manual refresh.

---

## 6. LibraryScreen Composable (`shared/src/commonMain/kotlin/com/drafty/ui/library/LibraryScreen.kt`)

### Layout

```
┌──────────────────────────────────────────────┐
│  [←]  Library  /  Folder Name           [+]  │  ← Top bar with breadcrumb + FAB
├──────────────────────────────────────────────┤
│                                              │
│  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐       │
│  │     │  │     │  │     │  │     │       │  ← Grid: folders first, then canvases
│  │ 📁  │  │ 📁  │  │ 📄  │  │ 📄  │       │
│  │     │  │     │  │     │  │     │       │
│  └─────┘  └─────┘  └─────┘  └─────┘       │
│  "Work"   "Study"  "Note 1" "Note 2"       │
│                                              │
│  ┌─────┐  ┌─────┐                           │
│  │     │  │     │                           │
│  │ 📄  │  │ 📄  │                           │
│  │     │  │     │                           │
│  └─────┘  └─────┘                           │
│  "Note 3" "Note 4"                          │
│                                              │
│                                              │
│                                    [+ FAB]  │
└──────────────────────────────────────────────┘
```

### Grid item design

**Folder card:**
- Dark surface card (`DraftyTheme.colors.surface`) with thin top border in the folder's `FolderColor`
- Folder icon (Material icon, tinted to folder color)
- Title below in `textPrimary`

**Canvas card:**
- Dark surface card
- Thin top border in `primary` (purple)
- Template icon or placeholder (no thumbnail yet)
- Title in `textPrimary`, modified date in `textSecondary`

### Code structure

```kotlin
package com.drafty.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drafty.model.Canvas
import com.drafty.model.Folder
import com.drafty.model.FolderColor
import com.drafty.model.Template
import com.drafty.ui.theme.DraftyTheme
import com.drafty.ui.theme.toColor

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onCanvasClick: (canvasId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DraftyTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            LibraryTopBar(
                title = state.currentFolderTitle ?: "Library",
                showBack = !viewModel.isAtRoot,
                onBack = { viewModel.navigateBack() },
            )

            // Grid content
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DraftyTheme.colors.primary)
                }
            } else if (state.folders.isEmpty() && state.canvases.isEmpty()) {
                // Empty state
                EmptyLibraryMessage()
            } else {
                LibraryGrid(
                    folders = state.folders,
                    canvases = state.canvases,
                    onFolderClick = { viewModel.navigateToFolder(it) },
                    onCanvasClick = { onCanvasClick(it.id) },
                )
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = DraftyTheme.colors.primary,
            contentColor = DraftyTheme.colors.textPrimary,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create")
        }
    }

    if (showCreateDialog) {
        CreateDialog(
            onDismiss = { showCreateDialog = false },
            onCreateFolder = { title, color ->
                viewModel.createFolder(title, color)
                showCreateDialog = false
            },
            onCreateCanvas = { title, template ->
                viewModel.createCanvas(title, template)
                showCreateDialog = false
            },
        )
    }
}
```

### LibraryTopBar

Simple custom top bar (not Material TopAppBar, to avoid Material theming):

```kotlin
@Composable
private fun LibraryTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(DraftyTheme.colors.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = DraftyTheme.colors.textPrimary,
                )
            }
        }
        Text(
            text = title,
            color = DraftyTheme.colors.textPrimary,
            fontSize = 20.sp,
            modifier = Modifier.padding(start = if (showBack) 4.dp else 0.dp),
        )
    }
}
```

### LibraryGrid

Uses `LazyVerticalGrid` with adaptive columns. Folders appear first, then canvases.

```kotlin
@Composable
private fun LibraryGrid(
    folders: List<Folder>,
    canvases: List<Canvas.Summary>,
    onFolderClick: (Folder) -> Unit,
    onCanvasClick: (Canvas.Summary) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            FolderCard(folder = folder, onClick = { onFolderClick(folder) })
        }
        items(canvases, key = { it.id }) { canvas ->
            CanvasCard(canvas = canvas, onClick = { onCanvasClick(canvas) })
        }
    }
}
```

### FolderCard

```kotlin
@Composable
private fun FolderCard(folder: Folder, onClick: () -> Unit) {
    val folderColor = folder.color.toColor()
    Column(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(DraftyTheme.colors.surface)
            .clickable(onClick = onClick)
    ) {
        // Neon accent strip at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(folderColor)
        )
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = folderColor,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = folder.title,
                color = DraftyTheme.colors.textPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

### CanvasCard

```kotlin
@Composable
private fun CanvasCard(canvas: Canvas.Summary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(DraftyTheme.colors.surface)
            .clickable(onClick = onClick)
    ) {
        // Neon accent strip — purple for canvases
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(DraftyTheme.colors.primary)
        )
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Placeholder — no thumbnails yet
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = DraftyTheme.colors.textSecondary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = canvas.title,
                color = DraftyTheme.colors.textPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatDate(canvas.modified),
                color = DraftyTheme.colors.textSecondary,
                fontSize = 11.sp,
            )
        }
    }
}
```

### CreateDialog

A simple dialog with two tabs/options: "Folder" and "Canvas". Folder creation asks for name + color. Canvas creation asks for name + template.

```kotlin
@Composable
private fun CreateDialog(
    onDismiss: () -> Unit,
    onCreateFolder: (title: String, color: FolderColor) -> Unit,
    onCreateCanvas: (title: String, template: Template) -> Unit,
) {
    var isFolder by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(FolderColor.Blue) }
    var selectedTemplate by remember { mutableStateOf(Template.Blank) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DraftyTheme.colors.surface,
        titleContentColor = DraftyTheme.colors.textPrimary,
        textContentColor = DraftyTheme.colors.textPrimary,
        title = { Text(if (isFolder) "New Folder" else "New Canvas") },
        text = {
            Column {
                // Toggle: Folder vs Canvas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !isFolder,
                        onClick = { isFolder = false },
                        label = { Text("Canvas") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DraftyTheme.colors.primary,
                            selectedLabelColor = DraftyTheme.colors.textPrimary,
                        ),
                    )
                    FilterChip(
                        selected = isFolder,
                        onClick = { isFolder = true },
                        label = { Text("Folder") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DraftyTheme.colors.primary,
                            selectedLabelColor = DraftyTheme.colors.textPrimary,
                        ),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Title input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DraftyTheme.colors.primary,
                        unfocusedBorderColor = DraftyTheme.colors.border,
                        focusedTextColor = DraftyTheme.colors.textPrimary,
                        unfocusedTextColor = DraftyTheme.colors.textPrimary,
                        cursorColor = DraftyTheme.colors.primary,
                        focusedLabelColor = DraftyTheme.colors.primary,
                        unfocusedLabelColor = DraftyTheme.colors.textSecondary,
                    ),
                )

                Spacer(Modifier.height(12.dp))

                // Folder: color picker row
                if (isFolder) {
                    Text("Color", color = DraftyTheme.colors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    FolderColorPicker(
                        selected = selectedColor,
                        onSelect = { selectedColor = it },
                    )
                }

                // Canvas: template picker
                if (!isFolder) {
                    Text("Template", color = DraftyTheme.colors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    TemplatePicker(
                        selected = selectedTemplate,
                        onSelect = { selectedTemplate = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = title.ifBlank { if (isFolder) "Untitled Folder" else "Untitled" }
                    if (isFolder) onCreateFolder(name, selectedColor)
                    else onCreateCanvas(name, selectedTemplate)
                },
            ) {
                Text("Create", color = DraftyTheme.colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DraftyTheme.colors.textSecondary)
            }
        },
    )
}
```

### FolderColorPicker

A row of small colored circles for each `FolderColor` value:

```kotlin
@Composable
private fun FolderColorPicker(selected: FolderColor, onSelect: (FolderColor) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FolderColor.entries.forEach { color ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color.toColor())
                    .then(
                        if (color == selected) Modifier.border(2.dp, DraftyTheme.colors.textPrimary, CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(color) },
            )
        }
    }
}
```

### TemplatePicker

A row of selectable chips for each `Template`:

```kotlin
@Composable
private fun TemplatePicker(selected: Template, onSelect: (Template) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Template.entries.forEach { template ->
            FilterChip(
                selected = template == selected,
                onClick = { onSelect(template) },
                label = { Text(template.name, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DraftyTheme.colors.primary,
                    selectedLabelColor = DraftyTheme.colors.textPrimary,
                ),
            )
        }
    }
}
```

### EmptyLibraryMessage

```kotlin
@Composable
private fun EmptyLibraryMessage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No canvases yet",
                color = DraftyTheme.colors.textSecondary,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap + to create one",
                color = DraftyTheme.colors.textSecondary,
                fontSize = 14.sp,
            )
        }
    }
}
```

### Date formatting utility

```kotlin
// shared/src/commonMain/kotlin/com/drafty/ui/library/DateFormat.kt
package com.drafty.ui.library

fun formatDate(epochMillis: Long): String {
    // Simple relative format for now — platform-specific formatting can come later
    val now = com.drafty.util.currentTimeMillis()
    val diff = now - epochMillis
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 30 -> "${days / 30}mo ago"
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}
```

---

## 7. Koin DI Setup

### Koin Module Definition (`shared/src/commonMain/kotlin/com/drafty/di/KoinModules.kt`)

```kotlin
package com.drafty.di

import com.drafty.persistence.CanvasRepository
import com.drafty.persistence.FolderRepository
import com.drafty.ui.library.LibraryViewModel
import org.koin.dsl.module

// Platform module is defined in androidMain/iosMain — provides DatabaseDriverFactory + DraftyDatabase
// This expect declaration lets commonMain reference the platform module
expect val platformModule: org.koin.core.module.Module

val sharedModule = module {
    single<FolderRepository> { com.drafty.persistence.SqlDelightFolderRepository(get()) }
    single<CanvasRepository> { com.drafty.persistence.SqlDelightCanvasRepository(get()) }
    factory { LibraryViewModel(get(), get(), get()) }
}
```

> **Note on `expect val platformModule`:** This may not compile cleanly — `expect` on a Koin `Module` val is unusual. Alternative: pass the platform module from the Android side during `startKoin` setup and don't use expect/actual for modules at all. The Android-side approach is simpler and more common.

**Revised approach (no expect/actual for modules):**

```kotlin
// commonMain — just the shared module
package com.drafty.di

import com.drafty.ui.library.LibraryViewModel
import org.koin.dsl.module

val sharedModule = module {
    // No LibraryViewModel here — it takes a CoroutineScope that must come
    // from the composable (rememberCoroutineScope), so it's constructed
    // manually in DraftyApp, not via Koin injection.
}
```

### Android Platform Module (`shared/src/androidMain/kotlin/com/drafty/di/AndroidModule.kt`)

```kotlin
package com.drafty.di

import com.drafty.db.DraftyDatabase
import com.drafty.persistence.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single { DatabaseDriverFactory(androidContext()).create() }
    single { DraftyDatabase(get()) }
    single<FolderRepository> { SqlDelightFolderRepository(get()) }
    single<CanvasRepository> { SqlDelightCanvasRepository(get()) }
}
```

> **Decision:** Repository bindings live in `androidModule` (not `sharedModule`) because the implementations are Android-specific. `sharedModule` only contains things that are truly common (like `LibraryViewModel`).

### DraftyApplication wiring (`androidApp/.../DraftyApplication.kt`)

```kotlin
package com.drafty.android

import android.app.Application
import com.drafty.di.androidModule
import com.drafty.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DraftyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DraftyApplication)
            modules(sharedModule, androidModule)
        }
    }
}
```

---

## 8. Navigation & DraftyApp

Move `DraftyApp` from `androidApp` to `shared/src/commonMain/kotlin/com/drafty/ui/DraftyApp.kt` so navigation lives in shared code. The `androidApp` `DraftyApp.kt` becomes a thin wrapper.

### DraftyApp (commonMain)

```kotlin
package com.drafty.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drafty.ui.library.LibraryScreen
import com.drafty.ui.library.LibraryViewModel
import com.drafty.ui.theme.DraftyTheme
import org.koin.compose.koinInject

@Composable
fun DraftyApp() {
    val navController = rememberNavController()
    DraftyTheme {
        NavHost(navController, startDestination = "library") {
            composable("library") {
                val scope = rememberCoroutineScope()
                val folderRepo: FolderRepository = koinInject()
                val canvasRepo: CanvasRepository = koinInject()
                val viewModel = remember { LibraryViewModel(folderRepo, canvasRepo, scope) }
                LibraryScreen(
                    viewModel = viewModel,
                    onCanvasClick = { canvasId ->
                        navController.navigate("canvas/$canvasId")
                    },
                )
            }
            composable("canvas/{canvasId}") {
                // Stub — will be implemented later
                // val canvasId = it.arguments?.getString("canvasId")
            }
        }
    }
}
```

> **ViewModel lifecycle:** The ViewModel is constructed manually via `remember` with a `rememberCoroutineScope()`. This ties its lifetime to the nav destination composable — it survives recomposition but is recreated when navigating away and back, which is acceptable for v1.

### androidApp DraftyApp.kt update

```kotlin
package com.drafty.android

import androidx.compose.runtime.Composable

@Composable
fun DraftyApp() {
    com.drafty.ui.DraftyApp()
}
```

---

## 9. `expect/actual` for `DatabaseDriverFactory`

The expect declaration already exists. Verify the actual Android implementation matches the expected signature:

```kotlin
// commonMain
package com.drafty.persistence

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}
```

```kotlin
// androidMain
package com.drafty.persistence

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.drafty.db.DraftyDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver {
        return AndroidSqliteDriver(
            schema = DraftyDatabase.Schema,
            context = context,
            name = "drafty.db",
        )
    }
}
```

---

## 10. SQLDelight Coroutines Extension

The `.asFlow().mapToList()` extensions require the `sqldelight-coroutines-extensions` dependency:

```kotlin
// shared/build.gradle.kts — commonMain
implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
```

This is a **critical missing dependency** that needs to be added alongside the other changes.

---

## Considerations & Trade-offs

### ViewModel scope
`LibraryViewModel` takes a `CoroutineScope` parameter. On Android, this should be tied to the composable lifecycle. Using `rememberCoroutineScope()` + Koin factory injection means the scope dies when leaving the library screen, which is correct — we don't need background folder polling.

### No Koin ViewModel integration
The KMP-compatible Koin ViewModel APIs are still evolving. For v1, using `koinInject()` with `remember` is simpler and avoids dependency on unstable APIs. If this causes lifecycle issues, we can switch to `koinViewModel()` with the Android-specific artifact.

### Material3 components with custom theming
We use Material3 structural components (`AlertDialog`, `FloatingActionButton`, `FilterChip`, `OutlinedTextField`) but override their colors explicitly using `DraftyTheme.colors`. This avoids wrapping in `MaterialTheme` while still getting the layout/interaction patterns. If any Material component ignores explicit color parameters and falls back to Material defaults, we'll need to wrap in a minimal `MaterialTheme` with our dark colors mapped to its slots.

### SQLDelight query mapper types
The plan shows lambda-mapper syntax for SQLDelight queries. If the generated code uses typed data classes instead (depends on SQLDelight version/config), the mapping will use `.executeAsList().map { ... }` with a manual `asFlow()` wrapper. This will be determined at build time.

---

## Files Modified/Created

| File | Action |
|------|--------|
| `shared/build.gradle.kts` | Modify — add Koin, Navigation, SQLDelight coroutines deps; remove benasher44/uuid |
| `shared/src/commonMain/.../ui/theme/DraftyTheme.kt` | Fill — theme colors + composable |
| `shared/src/commonMain/.../ui/theme/FolderColorMapping.kt` | **Create** — `FolderColor.toColor()` |
| `shared/src/commonMain/.../persistence/FolderRepository.kt` | Fill — interface |
| `shared/src/commonMain/.../persistence/CanvasRepository.kt` | Fill — interface |
| `shared/src/commonMain/.../persistence/DatabaseDriverFactory.kt` | Verify expect declaration |
| `shared/src/androidMain/.../persistence/DatabaseDriverFactoryAndroid.kt` | Fill — actual implementation |
| `shared/src/androidMain/.../persistence/SqlDelightFolderRepository.kt` | Fill — implementation |
| `shared/src/androidMain/.../persistence/SqlDelightCanvasRepository.kt` | Fill — implementation |
| `shared/src/commonMain/.../ui/library/LibraryViewModel.kt` | Fill — state + navigation + create |
| `shared/src/commonMain/.../ui/library/LibraryScreen.kt` | Fill — full composable |
| `shared/src/commonMain/.../ui/library/DateFormat.kt` | **Create** — `formatDate()` utility |
| `shared/src/commonMain/.../di/KoinModules.kt` | **Create** — shared module |
| `shared/src/androidMain/.../di/AndroidModule.kt` | **Create** — Android module |
| `shared/src/commonMain/.../ui/DraftyApp.kt` | **Create** — navigation host (commonMain) |
| `androidApp/.../DraftyApp.kt` | Modify — delegate to shared DraftyApp |
| `androidApp/.../DraftyApplication.kt` | Modify — Koin startup |

---

## Task checklist

See [TODO.md](TODO.md) for the implementation task list and manual testing checklist.
