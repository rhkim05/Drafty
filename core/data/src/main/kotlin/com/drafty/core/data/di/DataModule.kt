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
import com.drafty.core.data.repository.SectionRepositoryImpl
import com.drafty.core.data.repository.StrokeRepositoryImpl
import com.drafty.core.domain.repository.NotebookRepository
import com.drafty.core.domain.repository.PageRepository
import com.drafty.core.domain.repository.PdfRepository
import com.drafty.core.domain.repository.SectionRepository
import com.drafty.core.domain.repository.StrokeRepository
import com.drafty.core.domain.usecase.notebook.CreateNotebookUseCase
import com.drafty.core.domain.usecase.notebook.DeleteNotebookUseCase
import com.drafty.core.domain.usecase.notebook.GetNotebooksUseCase
import com.drafty.core.domain.usecase.notebook.UpdateNotebookUseCase
import com.drafty.core.domain.usecase.page.AddPageUseCase
import com.drafty.core.domain.usecase.page.DeletePageUseCase
import com.drafty.core.domain.usecase.page.GetPagesUseCase
import com.drafty.core.domain.usecase.page.ReorderPagesUseCase
import com.drafty.core.domain.usecase.search.SearchNotebooksUseCase
import com.drafty.core.domain.usecase.section.CreateSectionUseCase
import com.drafty.core.domain.usecase.section.DeleteSectionUseCase
import com.drafty.core.domain.usecase.section.GetSectionsUseCase
import com.drafty.core.domain.usecase.section.RenameSectionUseCase
import com.drafty.core.domain.usecase.section.ReorderSectionsUseCase
import com.drafty.core.domain.usecase.stroke.LoadStrokesUseCase
import com.drafty.core.domain.usecase.stroke.SaveStrokesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // ==================== Database ====================

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

    // ==================== DAOs ====================

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

    // ==================== Repositories ====================

    @Singleton
    @Provides
    fun provideNotebookRepository(impl: NotebookRepositoryImpl): NotebookRepository =
        impl

    @Singleton
    @Provides
    fun provideSectionRepository(impl: SectionRepositoryImpl): SectionRepository =
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

    // ==================== Notebook use cases ====================

    @Provides
    fun provideGetNotebooksUseCase(notebookRepository: NotebookRepository): GetNotebooksUseCase =
        GetNotebooksUseCase(notebookRepository)

    @Provides
    fun provideCreateNotebookUseCase(
        notebookRepository: NotebookRepository,
        sectionRepository: SectionRepository,
        pageRepository: PageRepository,
    ): CreateNotebookUseCase =
        CreateNotebookUseCase(notebookRepository, sectionRepository, pageRepository)

    @Provides
    fun provideUpdateNotebookUseCase(notebookRepository: NotebookRepository): UpdateNotebookUseCase =
        UpdateNotebookUseCase(notebookRepository)

    @Provides
    fun provideDeleteNotebookUseCase(notebookRepository: NotebookRepository): DeleteNotebookUseCase =
        DeleteNotebookUseCase(notebookRepository)

    @Provides
    fun provideSearchNotebooksUseCase(notebookRepository: NotebookRepository): SearchNotebooksUseCase =
        SearchNotebooksUseCase(notebookRepository)

    // ==================== Section use cases ====================

    @Provides
    fun provideGetSectionsUseCase(sectionRepository: SectionRepository): GetSectionsUseCase =
        GetSectionsUseCase(sectionRepository)

    @Provides
    fun provideCreateSectionUseCase(sectionRepository: SectionRepository): CreateSectionUseCase =
        CreateSectionUseCase(sectionRepository)

    @Provides
    fun provideRenameSectionUseCase(sectionRepository: SectionRepository): RenameSectionUseCase =
        RenameSectionUseCase(sectionRepository)

    @Provides
    fun provideDeleteSectionUseCase(sectionRepository: SectionRepository): DeleteSectionUseCase =
        DeleteSectionUseCase(sectionRepository)

    @Provides
    fun provideReorderSectionsUseCase(sectionRepository: SectionRepository): ReorderSectionsUseCase =
        ReorderSectionsUseCase(sectionRepository)

    // ==================== Page use cases ====================

    @Provides
    fun provideGetPagesUseCase(pageRepository: PageRepository): GetPagesUseCase =
        GetPagesUseCase(pageRepository)

    @Provides
    fun provideAddPageUseCase(pageRepository: PageRepository): AddPageUseCase =
        AddPageUseCase(pageRepository)

    @Provides
    fun provideDeletePageUseCase(pageRepository: PageRepository): DeletePageUseCase =
        DeletePageUseCase(pageRepository)

    @Provides
    fun provideReorderPagesUseCase(pageRepository: PageRepository): ReorderPagesUseCase =
        ReorderPagesUseCase(pageRepository)

    // ==================== Stroke use cases ====================

    @Provides
    fun provideLoadStrokesUseCase(strokeRepository: StrokeRepository): LoadStrokesUseCase =
        LoadStrokesUseCase(strokeRepository)

    @Provides
    fun provideSaveStrokesUseCase(strokeRepository: StrokeRepository): SaveStrokesUseCase =
        SaveStrokesUseCase(strokeRepository)
}
