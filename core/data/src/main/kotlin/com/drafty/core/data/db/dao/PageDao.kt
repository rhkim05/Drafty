package com.drafty.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.drafty.core.data.db.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE section_id = :sectionId ORDER BY sort_order ASC")
    fun getBySectionId(sectionId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getById(id: String): PageEntity?

    @Insert
    suspend fun insert(page: PageEntity)

    @Update
    suspend fun update(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Update
    suspend fun updateSortOrders(pages: List<PageEntity>)
}
