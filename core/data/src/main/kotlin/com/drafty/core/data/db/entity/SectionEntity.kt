package com.drafty.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebook_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("notebook_id")],
)
data class SectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "notebook_id") val notebookId: String,
    val title: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
