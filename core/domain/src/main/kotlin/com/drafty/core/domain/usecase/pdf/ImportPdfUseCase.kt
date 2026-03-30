package com.drafty.core.domain.usecase.pdf

import com.drafty.core.domain.repository.PdfRepository

/**
 * Use case for importing a PDF.
 */
class ImportPdfUseCase(
    private val pdfRepository: PdfRepository
) {
    suspend operator fun invoke(sourceUri: String, notebookId: String): Result<Int> {
        return pdfRepository.importPdf(sourceUri, notebookId)
    }
}
