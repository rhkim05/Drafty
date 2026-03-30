package com.drafty.core.data.di

import android.content.Context
import androidx.room.Room
import com.drafty.core.data.db.DraftyDatabase
import com.drafty.core.data.db.dao.NotebookDao
import com.drafty.core.data.db.dao.PageDao
import com.drafty.core.data.db.dao.SectionDao
import com.drafty.core.data.db.dao.TagDao
import com.drafty.core.data.repository.NotebookRepositoryImpl
import com.drafty.core.data.repository.PageRepositoryImpl
import com.drafty.core.data.repository.PdfRepositoryImpl
import com.drafty.core.data.repository.StrokeRepositoryImpl
import com.drafty.core.domain.repository.NotebookRepository
import com.drafty.core.domain.repository.PageRepository
import com.drafty.core.domain.repository.PdfRepository
import com.drafty.core.domain.repository.StrokeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Singleton
    @Provides
    fun provideDraftyDatabase(
        @ApplicationContext context: Context,
    ): DraftyDatabase =
        Room.databaseBuilder(
            context,
            DraftyDatabase::class.java,
            DraftyDatabase.DATABASE_NAME,
        ).build()

    @Singleton
    @Provides
    fun provideNotebookDao(database: DraftyDatabase): NotebookDao =
        database.notebookDao()

    @Singleton
    @Provides
    fun provideSectionDao(database: DraftyDatabase): SectionDao =
        database.sectionDao()

    @Singleton
    @Provides
    fun providePageDao(database: DraftyDatabase): PageDao =
        database.pageDao()

    @Singleton
    @Provides
    fun provideTagDao(database: DraftyDatabase): TagDao =
        database.tagDao()

    @Singleton
    @Provides
    fun provideNotebookRepository(impl: NotebookRepositoryImpl): NotebookRepository =
        impl

    @Singleton
    @Provides
    fun providePageRepository(impl: PageRepositoryImpl): PageRepository =
        impl

    @Singleton
    @Provides
    fun provideStrokeRepository(impl: StrokeRepositoryImpl): StrokeRepository =
        impl

    @Singleton
    @Provides
    fun providePdfRepository(impl: PdfRepositoryImpl): PdfRepository =
        impl
}
