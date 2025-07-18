package dev.serhiiyaremych.lumina.domain.model

import android.net.Uri

sealed class Media(
    open val id: Long,
    open val uri: Uri,
    open val path: String,
    open val displayName: String,
    open val dateAdded: Long,
    open val dateModified: Long,
    open val size: Long,
    open val width: Int,
    open val height: Int,
    open val metadataState: MetadataLoadingState = MetadataLoadingState.NotLoaded
) {
    data class Image(
        override val id: Long,
        override val uri: Uri,
        override val path: String,
        override val displayName: String,
        override val dateAdded: Long,
        override val dateModified: Long,
        override val size: Long,
        override val width: Int,
        override val height: Int,
        override val metadataState: MetadataLoadingState = MetadataLoadingState.NotLoaded
    ) : Media(id, uri, path, displayName, dateAdded, dateModified, size, width, height, metadataState)

    data class Video(
        override val id: Long,
        override val uri: Uri,
        override val path: String,
        override val displayName: String,
        override val dateAdded: Long,
        override val dateModified: Long,
        override val size: Long,
        override val width: Int,
        override val height: Int,
        val duration: Long,
        override val metadataState: MetadataLoadingState = MetadataLoadingState.NotLoaded
    ) : Media(id, uri, path, displayName, dateAdded, dateModified, size, width, height, metadataState)
}
