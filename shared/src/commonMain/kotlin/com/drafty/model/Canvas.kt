package com.drafty.model

data class Canvas(
    val id: String,
    val folderId: String?,
    val title: String,
    val template: Template,
    val created: Long,
    val modified: Long,
    val thumbnail: ByteArray?,
    val pdfBackingPath: String?,
    val pdfPageIndex: Int?,
) {
    data class Summary(
        val id: String,
        val folderId: String?,
        val title: String,
        val template: Template,
        val created: Long,
        val modified: Long,
        val hasThumbnail: Boolean,
    )
}
