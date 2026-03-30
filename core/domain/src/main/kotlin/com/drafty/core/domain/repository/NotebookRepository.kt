package com.drafty.core.domain.repository

import com.drafty.core.domain.model.Notebook
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for notebook CRUD operations.
 */
interface NotebookRepository {
    fun getNotebooks(): Flow<List<Notebook>>
    fun getNotebookById(id: String): Flow<Notebook?>
    fun getFavoriteNotebooks(): Flow<List<Notebook>>
    suspend fun createNotebook(notebook: Notebook)
    suspend fun updateNotebook(notebook: Notebook)
    suspend fun deleteNotebook(id: String)
    fun searchNotebooks(query: String): Flow<List<Notebook>>
}
