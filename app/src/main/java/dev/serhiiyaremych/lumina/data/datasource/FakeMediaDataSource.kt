package dev.serhiiyaremych.lumina.data.datasource

import androidx.core.net.toUri
import dev.serhiiyaremych.lumina.domain.model.Media
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MediaDataSource {
    suspend fun getMedia(): List<Media>
}

// Extract common interface out of this data source. AI!
class FakeMediaDataSource : MediaDataSource {
    override suspend fun getMedia(): List<Media> = withContext(Dispatchers.Default) {
        (1..400).map { i ->
            val isImage = Random.nextBoolean()
            val id = i.toLong()
            val displayName = if (isImage) "Image_$i.jpg" else "Video_$i.mp4"
            val path = "/storage/emulated/0/DCIM/Camera/$displayName"
            val date = System.currentTimeMillis() - Random.nextLong(1000L * 60 * 60 * 24 * 365) // within last year

            if (isImage) {
                Media.Image(
                    id = id,
                    uri = "file://$path".toUri(),
                    path = path,
                    displayName = displayName,
                    dateAdded = date,
                    size = Random.nextLong(1_000_000, 5_000_000), // 1-5 MB
                    width = Random.nextInt(1080, 4032),
                    height = Random.nextInt(1080, 4032)
                )
            } else {
                Media.Video(
                    id = id,
                    uri = "file://$path".toUri(),
                    path = path,
                    displayName = displayName,
                    dateAdded = date,
                    size = Random.nextLong(5_000_000, 50_000_000), // 5-50 MB
                    width = 1920,
                    height = 1080,
                    duration = Random.nextLong(5_000, 60_000) // 5-60 seconds
                )
            }
        }
    }
}
