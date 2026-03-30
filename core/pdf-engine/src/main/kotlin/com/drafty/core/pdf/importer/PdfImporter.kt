package com.drafty.core.pdf.importer

import java.io.File

/**
 * Imports PDF documents into the app's internal storage for annotation and editing.
 */
class PdfImporter {

    /**
     * Imports a PDF from an external source to internal app storage.
     * TODO: Implement PDF import with file copying and metadata extraction
     */
    fun importPdf(sourcePdfFile: File, internalStorageDir: File): File {
        // TODO: Implementation
        return File("")
    }

    /**
     * Gets metadata about an imported PDF.
     * TODO: Implement PDF metadata extraction
     */
    fun getPdfMetadata(pdfFile: File): PdfMetadata {
        // TODO: Implementation
        return PdfMetadata()
    }
}

/**
 * Metadata about a PDF document.
 */
data class PdfMetadata(
    val pageCount: Int = 0,
    val title: String = "",
    val author: String = "",
    val creationDate: Long = 0,
)
