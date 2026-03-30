package com.drafty.core.domain.repository

/**
 * Repository interface for PDF import, rendering, and export.
 */
interface PdfRepository {
    suspend fun importPdf(sourceUri: String, notebookId: String): Result<Int>
    suspend fun getPageCount(notebookId: String): Int
    suspend fun exportAnnotatedPdf(notebookId: String, outputPath: String): Result<String>
    suspend fun deletePdf(notebookId: String)
}
