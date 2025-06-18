package dev.serhiiyaremych.lumina.data.repository

import dev.serhiiyaremych.lumina.data.datasource.MediaDataSource
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.repository.MediaRepository

class MediaRepositoryImpl(
    private val fakeMediaDataSource: MediaDataSource
) : MediaRepository {
    override suspend fun getMedia(): List<Media> = fakeMediaDataSource.getMedia()
}
