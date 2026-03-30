package com.drafty.core.domain.usecase.pdf

import com.drafty.core.domain.repository.PdfRepository
import javax.inject.Inject

/**
 * Use case for exporting a notebook to PDF.
 * Handles the business logic of converting a notebook to a PDF file.
 */
class ExportPdfUseCase @Inject constructor(
    private val pdfRepository: PdfRepository
) {

    /**
     * Exports a notebook to a PDF file.
     *
     * @param notebookId The ID of the notebook to export
     * @param outputPath The path where the PDF should be saved
     * @return A Result containing the output file path on success
     */
    suspend operator fun invoke(notebookId: String, outputPath: String): Result<String> {
        return pdfRepository.export(notebookId, outputPath)
    }
}
