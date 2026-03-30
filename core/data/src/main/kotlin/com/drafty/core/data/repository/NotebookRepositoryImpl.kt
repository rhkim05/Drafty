package com.drafty.core.data.repository

import com.drafty.core.data.db.dao.NotebookDao
import com.drafty.core.data.db.mapper.toDomain
import com.drafty.core.data.db.mapper.toEntity
import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.repository.NotebookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NotebookRepositoryImpl @Inject constructor(
    private val notebookDao: NotebookDao,
) : NotebookRepository {

    override fun getNotebooks(): Flow<List<Notebook>> =
        notebookDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getNotebookById(id: String): Flow<Notebook?> =
        notebookDao.getAll().map { entities ->
            entities.find { it.id == id }?.toDomain()
        }

    override fun getFavoriteNotebooks(): Flow<List<Notebook>> =
        notebookDao.getAll().map { entities ->
            entities.filter { it.isFavorite }.map { it.toDomain() }
        }

    override suspend fun createNotebook(notebook: Notebook) =
        notebookDao.insert(notebook.toEntity())

    override suspend fun updateNotebook(notebook: Notebook) =
        notebookDao.update(notebook.toEntity())

    override suspend fun deleteNotebook(id: String) =
        notebookDao.deleteById(id)

    override fun searchNotebooks(query: String): Flow<List<Notebook>> =
        notebookDao.searchByTitle(query).map { entities ->
            entities.map { it.toDomain() }
        }
}
