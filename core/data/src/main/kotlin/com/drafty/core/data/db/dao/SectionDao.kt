package com.drafty.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.drafty.core.data.db.entity.SectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections WHERE notebook_id = :notebookId ORDER BY sort_order ASC")
    fun getByNotebookId(notebookId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun getById(id: String): SectionEntity?

    @Insert
    suspend fun insert(section: SectionEntity)

    @Update
    suspend fun update(section: SectionEntity)

    @Update
    suspend fun updateSortOrders(sections: List<SectionEntity>)

    @Query("DELETE FROM sections WHERE id = :id")
    suspend fun deleteById(id: String)
}
