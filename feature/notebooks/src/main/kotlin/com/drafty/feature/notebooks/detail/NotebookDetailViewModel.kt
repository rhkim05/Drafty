package com.drafty.feature.notebooks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drafty.core.domain.model.Page
import com.drafty.core.domain.model.PageType
import com.drafty.core.domain.model.PaperTemplate
import com.drafty.core.domain.model.Section
import com.drafty.core.domain.repository.NotebookRepository
import com.drafty.core.domain.usecase.page.AddPageUseCase
import com.drafty.core.domain.usecase.page.DeletePageUseCase
import com.drafty.core.domain.usecase.page.GetPagesUseCase
import com.drafty.core.domain.usecase.section.CreateSectionUseCase
import com.drafty.core.domain.usecase.section.DeleteSectionUseCase
import com.drafty.core.domain.usecase.section.GetSectionsUseCase
import com.drafty.core.domain.usecase.section.RenameSectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for notebook detail (sections and page thumbnails).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotebookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notebookRepository: NotebookRepository,
    private val getSectionsUseCase: GetSectionsUseCase,
    private val getPagesUseCase: GetPagesUseCase,
    private val createSectionUseCase: CreateSectionUseCase,
    private val renameSectionUseCase: RenameSectionUseCase,
    private val deleteSectionUseCase: DeleteSectionUseCase,
    private val addPageUseCase: AddPageUseCase,
    private val deletePageUseCase: DeletePageUseCase,
) : ViewModel() {

    val notebookId: String = savedStateHandle.get<String>("notebookId") ?: ""

    private val _selectedSectionId = MutableStateFlow<String?>(null)

    private val notebookFlow = notebookRepository.getNotebookById(notebookId)
    private val sectionsFlow = getSectionsUseCase(notebookId)

    private val pagesFlow = _selectedSectionId.flatMapLatest { sectionId ->
        if (sectionId != null) {
            getPagesUseCase(sectionId)
        } else {
            flowOf(emptyList())
        }
    }

    val uiState: StateFlow<NotebookDetailUiState> = combine(
        notebookFlow,
        sectionsFlow,
        _selectedSectionId,
        pagesFlow,
    ) { notebook, sections, selectedId, pages ->
        // Auto-select first section if none selected
        val effectiveSelectedId = selectedId ?: sections.firstOrNull()?.id
        if (selectedId == null && effectiveSelectedId != null) {
            _selectedSectionId.value = effectiveSelectedId
        }
        NotebookDetailUiState(
            notebook = notebook,
            sections = sections,
            selectedSectionId = effectiveSelectedId,
            pages = pages,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotebookDetailUiState(),
    )

    fun selectSection(sectionId: String) {
        _selectedSectionId.value = sectionId
    }

    fun addSection(title: String) {
        viewModelScope.launch {
            val sections = sectionsFlow.first()
            val section = Section(
                id = UUID.randomUUID().toString(),
                notebookId = notebookId,
                title = title,
                sortOrder = sections.size,
            )
            createSectionUseCase(section)
            _selectedSectionId.value = section.id
        }
    }

    fun renameSection(section: Section, newTitle: String) {
        viewModelScope.launch {
            renameSectionUseCase(section.copy(title = newTitle))
        }
    }

    fun deleteSection(sectionId: String) {
        viewModelScope.launch {
            deleteSectionUseCase(sectionId)
            // If we deleted the selected section, clear selection (auto-select will kick in)
            if (_selectedSectionId.value == sectionId) {
                _selectedSectionId.value = null
            }
        }
    }

    fun addPage() {
        viewModelScope.launch {
            val sectionId = _selectedSectionId.value ?: return@launch
            val pages = pagesFlow.first()
            val page = Page(
                id = UUID.randomUUID().toString(),
                sectionId = sectionId,
                type = PageType.NOTEBOOK,
                template = PaperTemplate.BLANK,
                width = DEFAULT_PAGE_WIDTH,
                height = DEFAULT_PAGE_HEIGHT,
                sortOrder = pages.size,
            )
            addPageUseCase(page)
        }
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch {
            deletePageUseCase(pageId)
        }
    }

    /**
     * Returns the sectionId for the currently selected section,
     * used when navigating to the canvas.
     */
    fun getSelectedSectionId(): String? = _selectedSectionId.value

    companion object {
        const val DEFAULT_PAGE_WIDTH = 595f
        const val DEFAULT_PAGE_HEIGHT = 842f
    }
}
