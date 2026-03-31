package com.drafty.core.domain.usecase.notebook

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.model.Page
import com.drafty.core.domain.model.PageType
import com.drafty.core.domain.model.PaperTemplate
import com.drafty.core.domain.model.Section
import com.drafty.core.domain.repository.NotebookRepository
import com.drafty.core.domain.repository.PageRepository
import com.drafty.core.domain.repository.SectionRepository
import java.util.UUID

/**
 * Use case for creating a new notebook with a default section and page.
 *
 * Automatically creates:
 * - One section titled "Section 1"
 * - One blank A4 page within that section
 *
 * This ensures the user can immediately start drawing after notebook creation.
 */
class CreateNotebookUseCase(
    private val notebookRepository: NotebookRepository,
    private val sectionRepository: SectionRepository,
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(notebook: Notebook) {
        notebookRepository.createNotebook(notebook)

        val defaultSection = Section(
            id = UUID.randomUUID().toString(),
            notebookId = notebook.id,
            title = "Section 1",
            sortOrder = 0,
        )
        sectionRepository.createSection(defaultSection)

        val defaultPage = Page(
            id = UUID.randomUUID().toString(),
            sectionId = defaultSection.id,
            type = PageType.NOTEBOOK,
            template = PaperTemplate.BLANK,
            width = DEFAULT_PAGE_WIDTH,
            height = DEFAULT_PAGE_HEIGHT,
            sortOrder = 0,
        )
        pageRepository.addPage(defaultPage)
    }

    companion object {
        /** A4 portrait at 72 DPI (595 x 842 points). */
        const val DEFAULT_PAGE_WIDTH = 595f
        const val DEFAULT_PAGE_HEIGHT = 842f
    }
}
