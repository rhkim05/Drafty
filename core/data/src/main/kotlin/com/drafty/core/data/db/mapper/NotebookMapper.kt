package com.drafty.core.data.db.mapper

import com.drafty.core.data.db.entity.NotebookEntity
import com.drafty.core.domain.model.Notebook

fun NotebookEntity.toDomain(): Notebook =
    Notebook(
        id = id,
        title = title,
        coverColor = coverColor,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        isFavorite = isFavorite,
        sortOrder = sortOrder,
    )

fun Notebook.toEntity(): NotebookEntity =
    NotebookEntity(
        id = id,
        title = title,
        coverColor = coverColor,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        isFavorite = isFavorite,
        sortOrder = sortOrder,
    )
