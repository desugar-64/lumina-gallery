package dev.serhiiyaremych.lumina.data.datasource

import android.content.ContentResolver
import android.os.Bundle
import android.provider.MediaStore
import dev.serhiiyaremych.lumina.domain.model.Media
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaStoreDataSource @Inject constructor(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher
) : MediaDataSource {

    override suspend fun getMedia(): List<Media> = withContext(dispatcher) {
        val media = mutableListOf<Media>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Video.Media.DURATION
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val queryUri = MediaStore.Files.getContentUri("external")

        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")
        }

        contentResolver.query(
            queryUri,
            projection,
            queryArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)
                val uri = MediaStore.Files.getContentUri("external", id)
                val displayName = cursor.getString(displayNameColumn)
                val dateAdded = cursor.getLong(dateAddedColumn) * 1000
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val mediaType = cursor.getInt(mediaTypeColumn)

                if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                    media.add(
                        Media.Image(
                            id = id,
                            uri = uri,
                            path = path,
                            displayName = displayName,
                            dateAdded = dateAdded,
                            size = size,
                            width = width,
                            height = height
                        )
                    )
                } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    val duration = cursor.getLong(durationColumn)
                    media.add(
                        Media.Video(
                            id = id,
                            uri = uri,
                            path = path,
                            displayName = displayName,
                            dateAdded = dateAdded,
                            size = size,
                            width = width,
                            height = height,
                            duration = duration
                        )
                    )
                }
            }
        }
        media
    }
}
