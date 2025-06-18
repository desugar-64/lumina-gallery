package dev.serhiiyaremych.lumina.domain.usecase

import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.repository.MediaRepository

class GetMediaUseCase(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(): List<Media> = repository.getMedia().sortedByDescending { it.dateAdded }
}
