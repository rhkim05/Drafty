package com.drafty.core.data.repository

import com.drafty.core.data.db.dao.SectionDao
import com.drafty.core.data.db.mapper.toDomain
import com.drafty.core.data.db.mapper.toEntity
import com.drafty.core.domain.model.Section
import com.drafty.core.domain.repository.SectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SectionRepositoryImpl @Inject constructor(
    private val sectionDao: SectionDao,
) : SectionRepository {

    override fun getSectionsByNotebookId(notebookId: String): Flow<List<Section>> =
        sectionDao.getByNotebookId(notebookId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getSectionById(id: String): Section? =
        sectionDao.getById(id)?.toDomain()

    override suspend fun createSection(section: Section) =
        sectionDao.insert(section.toEntity())

    override suspend fun updateSection(section: Section) =
        sectionDao.update(section.toEntity())

    override suspend fun deleteSection(id: String) =
        sectionDao.deleteById(id)

    override suspend fun reorderSections(sections: List<Section>) =
        sectionDao.updateSortOrders(sections.map { it.toEntity() })
}
