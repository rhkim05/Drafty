package com.drafty.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.drafty.core.data.db.dao.NotebookDao
import com.drafty.core.data.db.dao.SectionDao
import com.drafty.core.data.db.dao.PageDao
import com.drafty.core.data.db.dao.TagDao
import com.drafty.core.data.db.entity.NotebookEntity
import com.drafty.core.data.db.entity.SectionEntity
import com.drafty.core.data.db.entity.PageEntity
import com.drafty.core.data.db.entity.TagEntity
import com.drafty.core.data.db.entity.NotebookTagCrossRef

/**
 * Room database for Drafty metadata.
 * Stroke data is stored separately as protobuf files.
 */
@Database(
    entities = [
        NotebookEntity::class,
        SectionEntity::class,
        PageEntity::class,
        TagEntity::class,
        NotebookTagCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DraftyDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun sectionDao(): SectionDao
    abstract fun pageDao(): PageDao
    abstract fun tagDao(): TagDao

    companion object {
        const val DATABASE_NAME = "drafty.db"
    }
}
