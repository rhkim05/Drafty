package com.drafty.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "notebook_tags",
    primaryKeys = ["notebook_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebook_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("notebook_id"), Index("tag_id")],
)
data class NotebookTagCrossRef(
    @ColumnInfo(name = "notebook_id") val notebookId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)
