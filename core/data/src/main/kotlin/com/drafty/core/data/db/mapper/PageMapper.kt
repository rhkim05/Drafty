package com.drafty.core.data.db.mapper

import com.drafty.core.data.db.entity.PageEntity
import com.drafty.core.domain.model.Page
import com.drafty.core.domain.model.PageType
import com.drafty.core.domain.model.PaperTemplate

fun PageEntity.toDomain(): Page =
    Page(
        id = id,
        sectionId = sectionId,
        type = PageType.valueOf(type),
        template = PaperTemplate.valueOf(template),
        width = width,
        height = height,
        sortOrder = sortOrder,
    )

fun Page.toEntity(): PageEntity =
    PageEntity(
        id = id,
        sectionId = sectionId,
        type = type.name,
        template = template.name,
        width = width,
        height = height,
        sortOrder = sortOrder,
    )
