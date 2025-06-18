package dev.serhiiyaremych.lumina.domain.repository

import dev.serhiiyaremych.lumina.domain.model.Media

interface MediaRepository {
    suspend fun getMedia(): List<Media>
}
