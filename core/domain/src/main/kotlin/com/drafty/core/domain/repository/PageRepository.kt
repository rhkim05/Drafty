package com.drafty.core.domain.repository

import com.drafty.core.domain.model.Page
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for page operations within a section.
 */
interface PageRepository {
    fun getPagesBySectionId(sectionId: String): Flow<List<Page>>
    suspend fun getPageById(id: String): Page?
    suspend fun addPage(page: Page)
    suspend fun deletePage(id: String)
    suspend fun reorderPages(pages: List<Page>)
    suspend fun updatePage(page: Page)
}
