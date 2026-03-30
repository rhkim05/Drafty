package com.drafty.core.data.repository

import com.drafty.core.data.db.dao.PageDao
import com.drafty.core.data.db.mapper.toDomain
import com.drafty.core.data.db.mapper.toEntity
import com.drafty.core.domain.repository.PageRepository
import com.drafty.core.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PageRepositoryImpl @Inject constructor(
    private val pageDao: PageDao,
) : PageRepository {

    override fun getPagesBySectionId(sectionId: String): Flow<List<Page>> =
        pageDao.getBySectionId(sectionId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getPageById(id: String): Page? =
        pageDao.getById(id)?.toDomain()

    override suspend fun createPage(page: Page) =
        pageDao.insert(page.toEntity())

    override suspend fun updatePage(page: Page) =
        pageDao.update(page.toEntity())

    override suspend fun deletePage(id: String) =
        pageDao.deleteById(id)

    override suspend fun updatePageSortOrders(pages: List<Page>) =
        pageDao.updateSortOrders(pages.map { it.toEntity() })
}
