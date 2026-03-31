package com.drafty.feature.notebooks.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drafty.core.domain.model.Section
import com.drafty.core.ui.components.ConfirmDialog
import com.drafty.core.ui.components.PageThumbnail
import com.drafty.feature.notebooks.components.SectionTabs

/**
 * Notebook detail screen with section tabs and page thumbnails.
 * Navigation args: notebookId extracted via SavedStateHandle in ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookDetailScreen(
    notebookId: String,
    onPageClick: (sectionId: String, pageId: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: NotebookDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSectionDialog by remember { mutableStateOf(false) }
    var sectionToRename by remember { mutableStateOf<Section?>(null) }
    var sectionToDelete by remember { mutableStateOf<Section?>(null) }
    var pageIdToDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.notebook?.title ?: "Notebook")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addPage() }) {
                Icon(Icons.Default.Add, contentDescription = "Add page")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Section tabs
            SectionTabs(
                sections = uiState.sections,
                selectedSectionId = uiState.selectedSectionId,
                onSectionSelected = viewModel::selectSection,
                onAddSection = { showAddSectionDialog = true },
                onRenameSection = { sectionToRename = it },
                onDeleteSection = { sectionToDelete = it },
                modifier = Modifier.fillMaxWidth(),
            )

            // Page thumbnails
            if (uiState.pages.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No pages yet. Tap + to add one!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = uiState.pages,
                        key = { _, page -> page.id },
                    ) { index, page ->
                        PageThumbnail(
                            pageNumber = index + 1,
                            isSelected = false,
                            onClick = {
                                val sectionId = viewModel.getSelectedSectionId()
                                if (sectionId != null) {
                                    onPageClick(sectionId, page.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // Add section dialog
    if (showAddSectionDialog) {
        AddSectionDialog(
            onDismiss = { showAddSectionDialog = false },
            onAdd = { title ->
                viewModel.addSection(title)
                showAddSectionDialog = false
            },
        )
    }

    // Rename section dialog
    sectionToRename?.let { section ->
        RenameSectionDialog(
            currentTitle = section.title,
            onDismiss = { sectionToRename = null },
            onRename = { newTitle ->
                viewModel.renameSection(section, newTitle)
                sectionToRename = null
            },
        )
    }

    // Delete section confirmation
    sectionToDelete?.let { section ->
        ConfirmDialog(
            title = "Delete section",
            message = "Are you sure you want to delete \"${section.title}\"? All pages in this section will be deleted.",
            confirmText = "Delete",
            cancelText = "Cancel",
            isVisible = true,
            onConfirm = {
                viewModel.deleteSection(section.id)
                sectionToDelete = null
            },
            onCancel = { sectionToDelete = null },
        )
    }

    // Delete page confirmation
    pageIdToDelete?.let { pageId ->
        ConfirmDialog(
            title = "Delete page",
            message = "Are you sure you want to delete this page?",
            confirmText = "Delete",
            cancelText = "Cancel",
            isVisible = true,
            onConfirm = {
                viewModel.deletePage(pageId)
                pageIdToDelete = null
            },
            onCancel = { pageIdToDelete = null },
        )
    }
}

@Composable
private fun AddSectionDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New section") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Section title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (title.isNotBlank()) onAdd(title.trim()) },
                enabled = title.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RenameSectionDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename section") },
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
            ) { Text("Rename") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
