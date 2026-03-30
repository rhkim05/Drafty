package com.drafty.core.domain.usecase.pdf

import com.drafty.core.domain.repository.PdfRepository
import javax.inject.Inject

/**
 * Use case for importing a PDF.
 * Handles the business logic of importing a PDF file and converting it to pages in a notebook.
 */
class ImportPdfUseCase @Inject constructor(
    private val pdfRepository: PdfRepository
) {

    /**
     * Imports a PDF from a source URI into a notebook.
     *
     * @param sourceUri The URI of the PDF file to import
     * @param notebookId The ID of the notebook to import into
     * @return A Result containing the number of pages imported on success
     */
    suspend operator fun invoke(sourceUri: String, notebookId: String): Result<Int> {
        return pdfRepository.import(sourceUri, notebookId)
    }
}
