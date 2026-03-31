package com.drafty.core.domain.repository

import com.drafty.core.domain.model.Section
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for section CRUD operations within a notebook.
 */
interface SectionRepository {
    fun getSectionsByNotebookId(notebookId: String): Flow<List<Section>>
    suspend fun getSectionById(id: String): Section?
    suspend fun createSection(section: Section)
    suspend fun updateSection(section: Section)
    suspend fun deleteSection(id: String)
    suspend fun reorderSections(sections: List<Section>)
}
