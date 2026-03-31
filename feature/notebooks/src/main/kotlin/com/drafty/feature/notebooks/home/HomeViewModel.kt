package com.drafty.feature.notebooks.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.usecase.notebook.CreateNotebookUseCase
import com.drafty.core.domain.usecase.notebook.DeleteNotebookUseCase
import com.drafty.core.domain.usecase.notebook.GetNotebooksUseCase
import com.drafty.core.domain.usecase.notebook.UpdateNotebookUseCase
import com.drafty.core.domain.usecase.search.SearchNotebooksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the home screen (canvas grid).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getNotebooksUseCase: GetNotebooksUseCase,
    private val searchNotebooksUseCase: SearchNotebooksUseCase,
    private val createNotebookUseCase: CreateNotebookUseCase,
    private val deleteNotebookUseCase: DeleteNotebookUseCase,
    private val updateNotebookUseCase: UpdateNotebookUseCase,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.RECENT)

    /** Emits a notebookId when a new canvas is created (for navigation). */
    private val _navigateToCanvas = MutableSharedFlow<String>()
    val navigateToCanvas = _navigateToCanvas.asSharedFlow()

    private val notebooksFlow = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            getNotebooksUseCase()
        } else {
            searchNotebooksUseCase(query)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        notebooksFlow,
        _searchQuery,
        _sortOrder,
    ) { notebooks, query, sortOrder ->
        val sorted = applySortOrder(notebooks, sortOrder)
        HomeUiState(
            notebooks = sorted,
            isLoading = false,
            searchQuery = query,
            sortOrder = sortOrder,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private fun applySortOrder(notebooks: List<Notebook>, sortOrder: SortOrder): List<Notebook> {
        return when (sortOrder) {
            SortOrder.RECENT -> notebooks.sortedByDescending { it.modifiedAt }
            SortOrder.NAME -> notebooks.sortedBy { it.title.lowercase() }
            SortOrder.CREATED -> notebooks.sortedByDescending { it.createdAt }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    /**
     * Creates a new blank canvas and emits its ID for navigation.
     * The FAB on the home screen calls this directly.
     */
    fun createAndOpenCanvas() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val notebook = Notebook(
                id = UUID.randomUUID().toString(),
                title = "Untitled",
                coverColor = COVER_COLORS.random(),
                createdAt = now,
                modifiedAt = now,
            )
            createNotebookUseCase(notebook)
            _navigateToCanvas.emit(notebook.id)
        }
    }

    fun deleteNotebook(id: String) {
        viewModelScope.launch {
            deleteNotebookUseCase(id)
        }
    }

    fun toggleFavorite(notebook: Notebook) {
        viewModelScope.launch {
            updateNotebookUseCase(
                notebook.copy(
                    isFavorite = !notebook.isFavorite,
                    modifiedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun duplicateNotebook(notebook: Notebook) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val copy = Notebook(
                id = UUID.randomUUID().toString(),
                title = "${notebook.title} (Copy)",
                coverColor = notebook.coverColor,
                createdAt = now,
                modifiedAt = now,
            )
            createNotebookUseCase(copy)
        }
    }

    fun renameNotebook(notebook: Notebook, newTitle: String) {
        viewModelScope.launch {
            updateNotebookUseCase(
                notebook.copy(
                    title = newTitle,
                    modifiedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    companion object {
        private val COVER_COLORS = listOf(
            0xFF4A6FA5, // Blue
            0xFF5B8C5A, // Green
            0xFFD4724E, // Orange
            0xFF8B5E83, // Purple
            0xFFCB6B6B, // Red
            0xFF4EADD4, // Teal
            0xFFD4A84E, // Gold
            0xFF6B6B6B, // Gray
        )
    }
}
