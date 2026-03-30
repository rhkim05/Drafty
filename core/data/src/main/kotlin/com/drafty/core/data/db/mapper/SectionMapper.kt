package com.drafty.core.data.db.mapper

import com.drafty.core.data.db.entity.SectionEntity
import com.drafty.core.model.Section

fun SectionEntity.toDomain(): Section =
    Section(
        id = id,
        notebookId = notebookId,
        title = title,
        sortOrder = sortOrder,
    )

fun Section.toEntity(): SectionEntity =
    SectionEntity(
        id = id,
        notebookId = notebookId,
        title = title,
        sortOrder = sortOrder,
    )
