package com.drafty.feature.notebooks.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drafty.core.domain.model.Notebook
import com.drafty.core.ui.components.ConfirmDialog
import com.drafty.feature.notebooks.components.NotebookGrid

/**
 * Home screen showing a grid of canvases with search and sort.
 * FAB creates a new blank canvas and navigates to it directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNotebookClick: (notebookId: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var notebookToDelete by remember { mutableStateOf<Notebook?>(null) }
    var notebookToRename by remember { mutableStateOf<Notebook?>(null) }

    // Navigate when a new canvas is created
    LaunchedEffect(Unit) {
        viewModel.navigateToCanvas.collect { notebookId ->
            onNotebookClick(notebookId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drafty") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.SortByAlpha, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (order) {
                                                SortOrder.RECENT -> "Recent"
                                                SortOrder.NAME -> "Name"
                                                SortOrder.CREATED -> "Created"
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createAndOpenCanvas() },
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Canvas")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar
            if (showSearch) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = uiState.searchQuery,
                            onQueryChange = viewModel::setSearchQuery,
                            onSearch = { /* no-op, live search */ },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text("Search...") },
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {}
            }

            // Content
            if (uiState.notebooks.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) "No notes found" else "No notes yet. Tap + to create one!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                NotebookGrid(
                    notebooks = uiState.notebooks,
                    columns = 3,
                    onNotebookClick = onNotebookClick,
                    onRename = { notebookToRename = it },
                    onDuplicate = { viewModel.duplicateNotebook(it) },
                    onDelete = { notebookToDelete = it },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // Delete confirmation dialog
    notebookToDelete?.let { notebook ->
        ConfirmDialog(
            title = "Delete note",
            message = "Are you sure you want to delete \"${notebook.title}\"? This cannot be undone.",
            confirmText = "Delete",
            cancelText = "Cancel",
            isVisible = true,
            onConfirm = {
                viewModel.deleteNotebook(notebook.id)
                notebookToDelete = null
            },
            onCancel = { notebookToDelete = null },
        )
    }

    // Rename dialog
    notebookToRename?.let { notebook ->
        RenameDialog(
            currentTitle = notebook.title,
            onDismiss = { notebookToRename = null },
            onRename = { newTitle ->
                viewModel.renameNotebook(notebook, newTitle)
                notebookToRename = null
            },
        )
    }
}

@Composable
private fun RenameDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (title.isNotBlank()) onRename(title.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
