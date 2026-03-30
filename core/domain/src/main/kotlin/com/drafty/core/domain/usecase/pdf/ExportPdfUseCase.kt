package com.drafty.core.domain.usecase.pdf

import com.drafty.core.domain.repository.PdfRepository

/**
 * Use case for exporting a notebook to PDF.
 */
class ExportPdfUseCase(
    private val pdfRepository: PdfRepository
) {
    suspend operator fun invoke(notebookId: String, outputPath: String): Result<String> {
        return pdfRepository.exportAnnotatedPdf(notebookId, outputPath)
    }
}
