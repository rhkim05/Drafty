# Plan — Library Screen

This plan covers implementing the Library Screen: the home screen of Drafty. It shows a grid of folders and canvases, supports navigation into folders, and provides creation/deletion actions. This is the first user-facing feature — it requires standing up the persistence layer, DI, navigation, and theming to support it.

Detailed task checklist: [TODO.md](TODO.md)

---

## Scope

**In scope:**
- DraftyTheme (custom dark theme with CompositionLocal, no Material 3 color scheme)
- DatabaseDriverFactory (actual Android implementation)
- Repository implementations (SqlDelightFolderRepository, SqlDelightCanvasRepository)
- LibraryViewModel + LibraryState
- LibraryScreen composable (folder/canvas grid, empty state, FAB, navigation breadcrumb)
- Navigation shell (DraftyApp with simple screen routing)
- Create folder dialog, create canvas dialog
- Delete confirmation dialog
- Rename dialog

**Out of scope (deferred to later plans):**
- CanvasScreen implementation (tapping a canvas will be a no-op / placeholder)
- StrokeRepository implementation
- Export/import
- Context menu actions beyond rename/delete (duplicate, move)
- Sort options (last edited is the only sort for now)
- Thumbnail loading (canvases show template icon placeholder — thumbnail generation requires the rendering pipeline)

---

## 1. DraftyTheme (Neon/Cyberpunk Minimalism)

Custom `CompositionLocal`-based theme — no Material 3 color scheme. Dark-first with neon accents. See `research.md` §15 for full color rationale.

### File: `shared/src/commonMain/kotlin/com/drafty/ui/theme/DraftyTheme.kt`

```kotlin
package com.drafty.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

data class DraftyTypography(
    val titleMedium: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
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

val DefaultTypography = DraftyTypography(
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

val LocalDraftyColors = staticCompositionLocalOf { DarkColors }
val LocalDraftyTypography = staticCompositionLocalOf { DefaultTypography }

object DraftyTheme {
    val colors: DraftyColors
        @Composable get() = LocalDraftyColors.current
    val typography: DraftyTypography
        @Composable get() = LocalDraftyTypography.current
}

@Composable
fun DraftyTheme(
    colors: DraftyColors = DarkColors,
    typography: DraftyTypography = DefaultTypography,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDraftyColors provides colors,
        LocalDraftyTypography provides typography,
    ) {
        content()
    }
}
```

### FolderColor → Compose Color mapping

Neon-bright colors that pop against the dark background:

```kotlin
import com.drafty.model.FolderColor

fun FolderColor.toComposeColor(): Color = when (this) {
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

## 2. Persistence Layer

### 2a. DatabaseDriverFactory

The `expect class` already exists. We need the `actual` implementation that creates an `AndroidSqliteDriver`.

#### File: `shared/src/commonMain/kotlin/com/drafty/persistence/DatabaseDriverFactory.kt`

Change from bare `expect class` to:

```kotlin
package com.drafty.persistence

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

#### File: `shared/src/androidMain/kotlin/com/drafty/persistence/DatabaseDriverFactoryAndroid.kt`

```kotlin
package com.drafty.persistence

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.drafty.db.DraftyDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(DraftyDatabase.Schema, context, "drafty.db")
    }
}
```

### 2b. FolderRepository Interface

The interface file is currently a bare package declaration. Implement the interface as documented in research.md:

#### File: `shared/src/commonMain/kotlin/com/drafty/persistence/FolderRepository.kt`

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

### 2c. CanvasRepository Interface

#### File: `shared/src/commonMain/kotlin/com/drafty/persistence/CanvasRepository.kt`

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

### 2d. SqlDelightFolderRepository

SQLDelight generates `DraftyDatabase` with typed query methods. The repository maps generated row types → domain `Folder` model.

#### File: `shared/src/androidMain/kotlin/com/drafty/persistence/SqlDelightFolderRepository.kt`

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

    private val queries = database.draftyQueries

    override fun getRootFolders(): Flow<List<Folder>> =
        queries.selectRootFolders { id, parent_id, title, color, created, modified, sort_order ->
            Folder(
                id = id,
                parentId = parent_id,
                title = title,
                color = FolderColor.valueOf(color),
                created = created,
                modified = modified,
                sortOrder = sort_order.toInt(),
            )
        }.asFlow().mapToList(Dispatchers.IO)

    override fun getFoldersByParent(parentId: String): Flow<List<Folder>> =
        queries.selectFoldersByParent(parentId) { id, parent_id, title, color, created, modified, sort_order ->
            Folder(
                id = id,
                parentId = parent_id,
                title = title,
                color = FolderColor.valueOf(color),
                created = created,
                modified = modified,
                sortOrder = sort_order.toInt(),
            )
        }.asFlow().mapToList(Dispatchers.IO)

    override suspend fun getFolderById(id: String): Folder? = withContext(Dispatchers.IO) {
        queries.selectFolderById(id).executeAsOneOrNull()?.let { row ->
            Folder(
                id = row.id,
                parentId = row.parent_id,
                title = row.title,
                color = FolderColor.valueOf(row.color),
                created = row.created,
                modified = row.modified,
                sortOrder = row.sort_order.toInt(),
            )
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

**Note on mapping:** SQLDelight generates `sort_order` as `Long` (SQL `INTEGER` maps to Kotlin `Long`). We convert to/from `Int` at the repository boundary since the domain model uses `Int`.

### 2e. SqlDelightCanvasRepository

#### File: `shared/src/androidMain/kotlin/com/drafty/persistence/SqlDelightCanvasRepository.kt`

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

    private val queries = database.draftyQueries

    override fun getRootCanvasSummaries(): Flow<List<Canvas.Summary>> =
        queries.selectRootCanvasSummaries { id, folder_id, title, template, created, modified, has_thumbnail ->
            Canvas.Summary(
                id = id,
                folderId = folder_id,
                title = title,
                template = Template.valueOf(template),
                created = created,
                modified = modified,
                hasThumbnail = has_thumbnail,
            )
        }.asFlow().mapToList(Dispatchers.IO)

    override fun getCanvasSummariesByFolder(folderId: String): Flow<List<Canvas.Summary>> =
        queries.selectCanvasSummariesByFolder(folderId) { id, folder_id, title, template, created, modified, has_thumbnail ->
            Canvas.Summary(
                id = id,
                folderId = folder_id,
                title = title,
                template = Template.valueOf(template),
                created = created,
                modified = modified,
                hasThumbnail = has_thumbnail,
            )
        }.asFlow().mapToList(Dispatchers.IO)

    override suspend fun getCanvasById(id: String): Canvas? = withContext(Dispatchers.IO) {
        queries.selectCanvasById(id).executeAsOneOrNull()?.let { row ->
            Canvas(
                id = row.id,
                folderId = row.folder_id,
                title = row.title,
                template = Template.valueOf(row.template),
                created = row.created,
                modified = row.modified,
                thumbnail = row.thumbnail,
                pdfBackingPath = row.pdf_backing_path,
                pdfPageIndex = row.pdf_page_index?.toInt(),
            )
        }
    }

    override suspend fun getThumbnail(canvasId: String): ByteArray? = withContext(Dispatchers.IO) {
        queries.selectCanvasThumbnail(canvasId).executeAsOneOrNull()?.thumbnail
    }

    override suspend fun insert(canvas: Canvas) = withContext(Dispatchers.IO) {
        queries.insertCanvas(
            id = canvas.id,
            folder_id = canvas.folderId,
            title = canvas.title,
            template = canvas.template.name,
            created = canvas.created,
            modified = canvas.modified,
            thumbnail = canvas.thumbnail,
            pdf_backing_path = canvas.pdfBackingPath,
            pdf_page_index = canvas.pdfPageIndex?.toLong(),
        )
    }

    override suspend fun updateMetadata(canvas: Canvas) = withContext(Dispatchers.IO) {
        queries.updateCanvasMetadata(
            title = canvas.title,
            template = canvas.template.name,
            modified = canvas.modified,
            folder_id = canvas.folderId,
            id = canvas.id,
        )
    }

    override suspend fun updateThumbnail(canvasId: String, thumbnail: ByteArray) =
        withContext(Dispatchers.IO) {
            queries.updateCanvasThumbnail(
                thumbnail = thumbnail,
                modified = System.currentTimeMillis(),
                id = canvasId,
            )
        }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        queries.deleteCanvas(id)
    }
}
```

**Note on `has_thumbnail`:** SQLDelight generates `(thumbnail IS NOT NULL) AS has_thumbnail` as a `Boolean` since it recognizes the `IS NOT NULL` pattern. The mapping is direct.

---

## 3. Dependency Injection

No DI framework — manual construction in `DraftyApplication`. The app is small enough that a service locator pattern keeps things simple without adding a Koin/Hilt dependency.

### File: `androidApp/src/main/kotlin/com/drafty/android/DraftyApplication.kt`

```kotlin
package com.drafty.android

import android.app.Application
import com.drafty.db.DraftyDatabase
import com.drafty.persistence.CanvasRepository
import com.drafty.persistence.DatabaseDriverFactory
import com.drafty.persistence.FolderRepository
import com.drafty.persistence.SqlDelightCanvasRepository
import com.drafty.persistence.SqlDelightFolderRepository

class DraftyApplication : Application() {

    lateinit var database: DraftyDatabase
        private set
    lateinit var folderRepository: FolderRepository
        private set
    lateinit var canvasRepository: CanvasRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val driver = DatabaseDriverFactory(this).createDriver()
        database = DraftyDatabase(driver)
        folderRepository = SqlDelightFolderRepository(database)
        canvasRepository = SqlDelightCanvasRepository(database)
    }
}
```

Access from composables via `LocalContext`:

```kotlin
val app = LocalContext.current.applicationContext as DraftyApplication
```

This is the standard pattern for KMP apps without a DI framework. When the app grows, Koin can be introduced without changing the repository layer.

---

## 4. LibraryViewModel

### State Model

A sealed hierarchy for library items lets the grid render folders and canvases uniformly:

```kotlin
sealed interface LibraryItem {
    val id: String
    val title: String
    val modified: Long

    data class FolderItem(
        override val id: String,
        override val title: String,
        override val modified: Long,
        val color: FolderColor,
    ) : LibraryItem

    data class CanvasItem(
        override val id: String,
        override val title: String,
        override val modified: Long,
        val template: Template,
        val hasThumbnail: Boolean,
    ) : LibraryItem
}
```

```kotlin
data class LibraryState(
    val currentFolderId: String? = null,       // null = root
    val currentFolderTitle: String? = null,     // null = "Drafty" (root title)
    val breadcrumbs: List<BreadcrumbEntry> = emptyList(),
    val items: List<LibraryItem> = emptyList(),
    val isLoading: Boolean = true,
)

data class BreadcrumbEntry(
    val id: String?,     // null = root
    val title: String,   // "Drafty" for root
)
```

### ViewModel

#### File: `shared/src/commonMain/kotlin/com/drafty/ui/library/LibraryViewModel.kt`

```kotlin
package com.drafty.ui.library

import com.drafty.model.Canvas
import com.drafty.model.Folder
import com.drafty.model.FolderColor
import com.drafty.model.Template
import com.drafty.persistence.CanvasRepository
import com.drafty.persistence.FolderRepository
import com.drafty.util.currentTimeMillis
import com.drafty.util.generateUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
        // Combine folder and canvas flows for the current location
        val folderId = _state.value.currentFolderId

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

        scope.launch {
            combine(foldersFlow, canvasesFlow) { folders, canvases ->
                val folderItems = folders.map { it.toLibraryItem() }
                val canvasItems = canvases.map { it.toLibraryItem() }
                folderItems + canvasItems
            }.collect { items ->
                _state.update { it.copy(items = items, isLoading = false) }
            }
        }
    }

    fun navigateToFolder(folderId: String, title: String) {
        val current = _state.value
        val newBreadcrumbs = current.breadcrumbs + BreadcrumbEntry(
            id = current.currentFolderId,
            title = current.currentFolderTitle ?: "Drafty",
        )
        _state.update {
            it.copy(
                currentFolderId = folderId,
                currentFolderTitle = title,
                breadcrumbs = newBreadcrumbs,
                isLoading = true,
            )
        }
        observeCurrentFolder()
    }

    fun navigateToBreadcrumb(entry: BreadcrumbEntry) {
        val current = _state.value
        val index = current.breadcrumbs.indexOf(entry)
        if (index < 0) return
        val newBreadcrumbs = current.breadcrumbs.subList(0, index)
        _state.update {
            it.copy(
                currentFolderId = entry.id,
                currentFolderTitle = if (entry.id == null) null else entry.title,
                breadcrumbs = newBreadcrumbs,
                isLoading = true,
            )
        }
        observeCurrentFolder()
    }

    fun navigateUp(): Boolean {
        val current = _state.value
        if (current.breadcrumbs.isEmpty()) return false
        navigateToBreadcrumb(current.breadcrumbs.last())
        return true
    }

    fun createFolder(title: String, color: FolderColor) {
        val now = currentTimeMillis()
        scope.launch {
            folderRepository.insert(
                Folder(
                    id = generateUuid(),
                    parentId = _state.value.currentFolderId,
                    title = title,
                    color = color,
                    created = now,
                    modified = now,
                    sortOrder = 0,
                )
            )
        }
    }

    fun createCanvas(title: String, template: Template) {
        val now = currentTimeMillis()
        scope.launch {
            canvasRepository.insert(
                Canvas(
                    id = generateUuid(),
                    folderId = _state.value.currentFolderId,
                    title = title,
                    template = template,
                    created = now,
                    modified = now,
                    thumbnail = null,
                    pdfBackingPath = null,
                    pdfPageIndex = null,
                )
            )
        }
    }

    fun renameItem(item: LibraryItem, newTitle: String) {
        val now = currentTimeMillis()
        scope.launch {
            when (item) {
                is LibraryItem.FolderItem -> {
                    folderRepository.getFolderById(item.id)?.let { folder ->
                        folderRepository.update(folder.copy(title = newTitle, modified = now))
                    }
                }
                is LibraryItem.CanvasItem -> {
                    canvasRepository.getCanvasById(item.id)?.let { canvas ->
                        canvasRepository.updateMetadata(canvas.copy(title = newTitle, modified = now))
                    }
                }
            }
        }
    }

    fun deleteItem(item: LibraryItem) {
        scope.launch {
            when (item) {
                is LibraryItem.FolderItem -> folderRepository.delete(item.id)
                is LibraryItem.CanvasItem -> canvasRepository.delete(item.id)
            }
        }
    }

    private fun Folder.toLibraryItem() = LibraryItem.FolderItem(
        id = id, title = title, modified = modified, color = color,
    )

    private fun Canvas.Summary.toLibraryItem() = LibraryItem.CanvasItem(
        id = id, title = title, modified = modified,
        template = template, hasThumbnail = hasThumbnail,
    )
}
```

**Design decisions:**

- `observeCurrentFolder()` uses `combine()` to merge the folder and canvas flows into a single items list. When either changes (e.g., a folder is inserted), the grid updates reactively.
- Navigation is stack-based via breadcrumbs. `navigateToFolder` pushes, `navigateUp` pops, `navigateToBreadcrumb` jumps to any ancestor.
- No `viewModelScope` — the caller passes a `CoroutineScope` since this is a plain class in `commonMain`, not an `androidx.lifecycle.ViewModel`. On Android, the scope will be tied to the composable's lifecycle via `rememberCoroutineScope()`.

**Known limitation:** Each call to `observeCurrentFolder()` launches a new `combine` collection. The previous one should be cancelled. We need a `Job` reference:

```kotlin
private var observeJob: Job? = null

private fun observeCurrentFolder() {
    observeJob?.cancel()
    observeJob = scope.launch {
        // ... combine + collect
    }
}
```

---

## 5. LibraryScreen Composable

### Layout Structure

```
┌──────────────────────────────────────────────────────┐
│  TopAppBar: "Drafty" or folder title                 │
│  Breadcrumbs: Home > Folder A > Subfolder            │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ 📁 Red   │  │ 📁 Blue  │  │ 📄 Note  │          │
│  │ "Work"   │  │ "School" │  │ "Ideas"  │          │
│  │ Mar 28   │  │ Mar 25   │  │ Mar 20   │          │
│  └──────────┘  └──────────┘  └──────────┘          │
│                                                      │
│  ┌──────────┐  ┌──────────┐                          │
│  │ 📄 Note  │  │ 📄 Note  │                          │
│  │ "Draft"  │  │ "Meet."  │                          │
│  │ Mar 18   │  │ Mar 15   │                          │
│  └──────────┘  └──────────┘                          │
│                                                      │
│                                          [ + FAB ]   │
└──────────────────────────────────────────────────────┘
```

When empty:

```
┌──────────────────────────────────────────────────────┐
│  TopAppBar: "Drafty"                                 │
├──────────────────────────────────────────────────────┤
│                                                      │
│                                                      │
│              No canvases yet                         │
│       Tap + to create your first canvas              │
│                                                      │
│                                                      │
│                                          [ + FAB ]   │
└──────────────────────────────────────────────────────┘
```

### Grid: Adaptive columns

Use `LazyVerticalGrid` with `GridCells.Adaptive(160.dp)` — this gives 2-3 columns on tablets depending on screen width, matching the idea.md spec of "2-3 columns on tablet."

### File: `shared/src/commonMain/kotlin/com/drafty/ui/library/LibraryScreen.kt`

```kotlin
package com.drafty.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drafty.model.FolderColor
import com.drafty.model.Template
import com.drafty.ui.theme.toComposeColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onCanvasSelected: (canvasId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var itemForDeletion by remember { mutableStateOf<LibraryItem?>(null) }
    var itemForRename by remember { mutableStateOf<LibraryItem?>(null) }

    Scaffold(
        containerColor = DraftyTheme.colors.background,
        topBar = {
            TopAppBar(
                title = { Text(state.currentFolderTitle ?: "Drafty", color = DraftyTheme.colors.textPrimary) },
                navigationIcon = {
                    if (state.currentFolderId != null) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DraftyTheme.colors.textPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DraftyTheme.colors.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = DraftyTheme.colors.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Breadcrumbs
            if (state.breadcrumbs.isNotEmpty()) {
                BreadcrumbBar(
                    breadcrumbs = state.breadcrumbs,
                    currentTitle = state.currentFolderTitle ?: "Drafty",
                    onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) },
                )
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.items.isEmpty()) {
                EmptyState(isRoot = state.currentFolderId == null)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        LibraryItemCard(
                            item = item,
                            onClick = {
                                when (item) {
                                    is LibraryItem.FolderItem ->
                                        viewModel.navigateToFolder(item.id, item.title)
                                    is LibraryItem.CanvasItem ->
                                        onCanvasSelected(item.id)
                                }
                            },
                            onLongClick = {
                                // Show context actions — for now just rename/delete
                                itemForRename = item
                            },
                            onDeleteClick = { itemForDeletion = item },
                        )
                    }
                }
            }
        }
    }

    // Dialogs
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

    itemForDeletion?.let { item ->
        DeleteConfirmationDialog(
            itemTitle = item.title,
            isFolder = item is LibraryItem.FolderItem,
            onConfirm = {
                viewModel.deleteItem(item)
                itemForDeletion = null
            },
            onDismiss = { itemForDeletion = null },
        )
    }

    itemForRename?.let { item ->
        RenameDialog(
            currentTitle = item.title,
            onConfirm = { newTitle ->
                viewModel.renameItem(item, newTitle)
                itemForRename = null
            },
            onDismiss = { itemForRename = null },
        )
    }
}
```

### LibraryItemCard

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryItemCard(
    item: LibraryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = DraftyTheme.colors.surface),
        border = BorderStroke(1.dp, DraftyTheme.colors.border),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top: visual indicator
            when (item) {
                is LibraryItem.FolderItem -> {
                    FolderIcon(color = item.color)
                }
                is LibraryItem.CanvasItem -> {
                    CanvasPreview(template = item.template)
                }
            }

            // Bottom: title + date
            Column {
                Text(
                    text = item.title,
                    style = DraftyTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatRelativeDate(item.modified),
                    style = DraftyTheme.typography.bodySmall,
                    color = DraftyTheme.colors.textSecondary,
                )
            }
        }
    }
}
```

### FolderIcon

A colored rounded rectangle with a folder shape — simple Box with the folder's color:

```kotlin
@Composable
private fun FolderIcon(color: FolderColor) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .background(
                color = color.toComposeColor().copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = color.toComposeColor(),
                    shape = RoundedCornerShape(8.dp),
                ),
        )
    }
}
```

### CanvasPreview

Shows a miniature template pattern (lines/grid/dots) as a placeholder until thumbnails are generated:

```kotlin
@Composable
private fun CanvasPreview(template: Template) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .background(DraftyTheme.colors.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, DraftyTheme.colors.border, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (template) {
                Template.Blank -> ""
                Template.Lined -> "───\n───\n───"
                Template.Grid -> "┼──┼\n┼──┼"
                Template.Dotted -> "· · ·\n· · ·\n· · ·"
            },
            style = DraftyTheme.typography.bodySmall,
            color = DraftyTheme.colors.textSecondary,
        )
    }
}
```

### BreadcrumbBar

Horizontal scrollable row of clickable breadcrumb labels:

```kotlin
@Composable
private fun BreadcrumbBar(
    breadcrumbs: List<BreadcrumbEntry>,
    currentTitle: String,
    onBreadcrumbClick: (BreadcrumbEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        breadcrumbs.forEach { entry ->
            TextButton(onClick = { onBreadcrumbClick(entry) }) {
                Text(entry.title, style = DraftyTheme.typography.bodySmall)
            }
            Text(" / ", style = DraftyTheme.typography.bodySmall, color = DraftyTheme.colors.textSecondary)
        }
        Text(
            currentTitle,
            style = DraftyTheme.typography.bodySmall,
            color = DraftyTheme.colors.textPrimary,
        )
    }
}
```

### EmptyState

```kotlin
@Composable
private fun EmptyState(isRoot: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isRoot) "No canvases yet" else "This folder is empty",
                style = DraftyTheme.typography.titleMedium,
                color = DraftyTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap + to create your first canvas",
                style = DraftyTheme.typography.bodyMedium,
                color = DraftyTheme.colors.textSecondary,
            )
        }
    }
}
```

### Date Formatting

```kotlin
private fun formatRelativeDate(epochMs: Long): String {
    val now = com.drafty.util.currentTimeMillis()
    val diffMs = now - epochMs
    val diffMinutes = diffMs / 60_000
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24
    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays < 7 -> "${diffDays}d ago"
        else -> {
            // Simple date: "Mar 15"
            val date = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs)
                // ... format
            // For v1 simplicity, just show days ago for everything
            "${diffDays}d ago"
        }
    }
}
```

**Consideration:** `kotlinx-datetime` is not a current dependency. For v1, use the simple relative format above (no absolute dates). If we want "Mar 15" formatting, we'd need to add `kotlinx-datetime` to commonMain dependencies. Recommendation: keep the simple relative format — it's sufficient and avoids adding a dependency.

---

## 6. Dialogs

### CreateDialog

A dialog with two tabs/options: "Folder" and "Canvas".

```kotlin
@Composable
private fun CreateDialog(
    onDismiss: () -> Unit,
    onCreateFolder: (title: String, color: FolderColor) -> Unit,
    onCreateCanvas: (title: String, template: Template) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(1) }  // default to Canvas
    var title by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(FolderColor.Blue) }
    var selectedTemplate by remember { mutableStateOf(Template.Blank) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New") },
        text = {
            Column {
                // Tab row: Folder | Canvas
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Folder", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Canvas", modifier = Modifier.padding(12.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // Folder color picker
                    Text("Color", style = DraftyTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    FolderColorPicker(
                        selected = selectedColor,
                        onSelect = { selectedColor = it },
                    )
                } else {
                    // Template picker
                    Text("Template", style = DraftyTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
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
                    val finalTitle = title.ifBlank {
                        if (selectedTab == 0) "Untitled Folder" else "Untitled Canvas"
                    }
                    if (selectedTab == 0) onCreateFolder(finalTitle, selectedColor)
                    else onCreateCanvas(finalTitle, selectedTemplate)
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
```

### FolderColorPicker

Horizontal row of 8 colored circles:

```kotlin
@Composable
private fun FolderColorPicker(
    selected: FolderColor,
    onSelect: (FolderColor) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FolderColor.entries.forEach { color ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.toComposeColor(), CircleShape)
                    .then(
                        if (color == selected) Modifier.border(2.dp, DraftyTheme.colors.primary, CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(color) },
            )
        }
    }
}
```

### TemplatePicker

Row of 4 small preview boxes:

```kotlin
@Composable
private fun TemplatePicker(
    selected: Template,
    onSelect: (Template) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Template.entries.forEach { template ->
            Card(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.75f)
                    .clickable { onSelect(template) },
                border = if (template == selected) {
                    BorderStroke(2.dp, DraftyTheme.colors.primary)
                } else null,
                colors = CardDefaults.cardColors(containerColor = DraftyTheme.colors.surfaceVariant),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = template.name,
                        style = DraftyTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
```

### DeleteConfirmationDialog

```kotlin
@Composable
private fun DeleteConfirmationDialog(
    itemTitle: String,
    isFolder: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${if (isFolder) "folder" else "canvas"}?") },
        text = {
            Text(
                if (isFolder)
                    "\"$itemTitle\" and all its subfolders will be deleted. Canvases inside will be moved to the root level."
                else
                    "\"$itemTitle\" and all its strokes will be permanently deleted."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = DraftyTheme.colors.accentRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
```

### RenameDialog

```kotlin
@Composable
private fun RenameDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title) },
                enabled = title.isNotBlank(),
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
```

---

## 7. Navigation Shell (DraftyApp)

Simple screen routing without Jetpack Navigation — the app only has two screens and no deep linking requirements. A sealed class for routes and a `remember`'d state machine is simpler and avoids adding a navigation dependency.

### File: `androidApp/src/main/kotlin/com/drafty/android/DraftyApp.kt`

```kotlin
package com.drafty.android

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.drafty.ui.library.LibraryScreen
import com.drafty.ui.library.LibraryViewModel
import com.drafty.ui.theme.DraftyTheme

sealed interface Screen {
    data object Library : Screen
    data class Canvas(val canvasId: String) : Screen
}

@Composable
fun DraftyApp() {
    val app = LocalContext.current.applicationContext as DraftyApplication
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Library) }

    val libraryViewModel = remember {
        LibraryViewModel(
            folderRepository = app.folderRepository,
            canvasRepository = app.canvasRepository,
            scope = /* see below */
        )
    }

    DraftyTheme {
        when (currentScreen) {
            is Screen.Library -> {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onCanvasSelected = { id -> currentScreen = Screen.Canvas(id) },
                )
            }
            is Screen.Canvas -> {
                // Placeholder until CanvasScreen is implemented
                // Back press will return to Library
            }
        }
    }
}
```

**Scope for LibraryViewModel:** Use `rememberCoroutineScope()` at the `DraftyApp` level. This scope lives as long as the composable — it survives screen transitions within the app but is cancelled when the Activity is destroyed.

```kotlin
val scope = rememberCoroutineScope()
val libraryViewModel = remember {
    LibraryViewModel(
        folderRepository = app.folderRepository,
        canvasRepository = app.canvasRepository,
        scope = scope,
    )
}
```

---

## 8. SQLDelight `has_thumbnail` Type Issue

The query `(thumbnail IS NOT NULL) AS has_thumbnail` will be generated by SQLDelight as `Long` (since SQLite doesn't have a boolean type — `IS NOT NULL` evaluates to `0` or `1` as `INTEGER`). The `Canvas.Summary` model expects `Boolean`.

Two options:
1. Cast in the mapper: `has_thumbnail != 0L`
2. Keep the current query and handle in the repository mapper

Going with option 1 in the repository mapper — it's explicit and clear:

```kotlin
// In SqlDelightCanvasRepository mapper:
hasThumbnail = has_thumbnail != 0L,
```

**Update:** If using SQLDelight's lambda mapper (as shown in 2e above), the generated type for `has_thumbnail` may already be `Boolean` depending on SQLDelight version. We'll verify during implementation and adjust if needed.

---

## 9. Files Modified / Created

| File | Action | Module |
|------|--------|--------|
| `shared/.../ui/theme/DraftyTheme.kt` | Implement | commonMain |
| `shared/.../persistence/DatabaseDriverFactory.kt` | Add `createDriver()` method | commonMain |
| `shared/.../persistence/DatabaseDriverFactoryAndroid.kt` | Implement `actual` | androidMain |
| `shared/.../persistence/FolderRepository.kt` | Add interface body | commonMain |
| `shared/.../persistence/CanvasRepository.kt` | Add interface body | commonMain |
| `shared/.../persistence/SqlDelightFolderRepository.kt` | Implement | androidMain |
| `shared/.../persistence/SqlDelightCanvasRepository.kt` | Implement | androidMain |
| `shared/.../ui/library/LibraryViewModel.kt` | Implement | commonMain |
| `shared/.../ui/library/LibraryScreen.kt` | Implement | commonMain |
| `androidApp/.../DraftyApplication.kt` | Add DI initialization | androidApp |
| `androidApp/.../DraftyApp.kt` | Add navigation + theming | androidApp |

No new files created — all files already exist as stubs.

---

## 10. Considerations & Trade-offs

**No Jetpack Navigation:** Two screens don't justify the dependency. The sealed class approach is type-safe, has zero overhead, and doesn't require learning Navigation Compose's DSL. If deep linking or complex back stack management is needed later, migrating is straightforward.

**No DI framework:** Manual construction in `DraftyApplication` is sufficient. Repositories are singletons by nature (one DB connection). ViewModels are created in composable scope. Adding Koin later is a 30-minute migration.

**ViewModel lifecycle:** `LibraryViewModel` is a plain class held in `remember {}` at the `DraftyApp` level. It survives screen transitions (Library ↔ Canvas) but is recreated on configuration changes. For the library screen this is acceptable — state is backed by SQLDelight flows and will repopulate instantly. For `CanvasViewModel`, this is a bigger concern (addressed in research.md risks), but that's out of scope for this plan.

**No kotlinx-datetime dependency:** Relative date formatting ("5m ago", "3d ago") avoids the dependency. Absolute dates ("Mar 15") can be added later if needed.

**Thumbnail placeholder:** Canvas cards show a text-based template indicator instead of actual thumbnails. Generating thumbnails requires the rendering pipeline (CommittedStrokeView → Bitmap → PNG), which is out of scope. The `hasThumbnail` flag is already wired for when that's implemented.

**Long-press vs dropdown menu:** The current design uses long-press to trigger rename, with a separate delete action. A more complete context menu (long-press → dropdown with rename/delete/duplicate/move) is deferred — the interaction model is in place and easy to extend.
