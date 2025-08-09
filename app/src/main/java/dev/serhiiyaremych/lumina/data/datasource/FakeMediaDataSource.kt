package dev.serhiiyaremych.lumina.data.datasource

import androidx.core.net.toUri
import dev.serhiiyaremych.lumina.domain.model.Media
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MediaDataSource {
    suspend fun getMedia(): List<Media>
}

