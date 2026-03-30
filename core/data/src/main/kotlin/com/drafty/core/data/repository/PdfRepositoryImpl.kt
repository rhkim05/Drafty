package com.drafty.core.data.repository

import com.drafty.core.domain.repository.PdfRepository
import javax.inject.Inject

class PdfRepositoryImpl @Inject constructor() : PdfRepository {

    override suspend fun exportPageToPdf(pageId: String): String {
        // TODO: Implement page to PDF export
        return ""
    }

    override suspend fun exportNotebookToPdf(notebookId: String): String {
        // TODO: Implement notebook to PDF export
        return ""
    }

    override suspend fun importPdfAsPages(pdfPath: String, sectionId: String) {
        // TODO: Implement PDF import as pages
    }
}
