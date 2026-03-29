package com.drafty.model

data class Folder(
    val id: String,
    val parentId: String?,
    val title: String,
    val color: FolderColor,
    val created: Long,
    val modified: Long,
    val sortOrder: Int,
)
