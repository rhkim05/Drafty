package com.drafty.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["section_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("section_id")],
)
data class PageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "section_id") val sectionId: String,
    val type: String,
    val template: String = "BLANK",
    val width: Float,
    val height: Float,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
