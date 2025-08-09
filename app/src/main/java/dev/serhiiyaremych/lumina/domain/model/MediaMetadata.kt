package dev.serhiiyaremych.lumina.domain.model

import android.location.Location
import kotlinx.serialization.Serializable

/**
 * Represents metadata for media files, including EXIF data and file information.
 * Uses progressive loading structure for performance optimization.
 */
@Serializable
data class MediaMetadata(
    val essential: EssentialMetadata,
    val technical: TechnicalMetadata? = null,
    val advanced: AdvancedMetadata? = null
) {
    /**
     * Essential metadata that loads quickly and is always needed.
     */
    @Serializable
    data class EssentialMetadata(
        val cameraMake: String? = null,
        val cameraModel: String? = null,
        val dateTime: String? = null,
        val location: SerializableLocation? = null,
        val orientation: Int? = null,
        val fileSizeFormatted: String
    )

    /**
     * Technical metadata that provides detailed camera settings.
     */
    @Serializable
    data class TechnicalMetadata(
        val aperture: String? = null,
        val iso: String? = null,
        val focalLength: String? = null,
        val exposureTime: String? = null,
        val whiteBalance: String? = null,
        val flash: String? = null,
        val imageLength: Int? = null,
        val imageWidth: Int? = null
    )

    /**
     * Advanced metadata with detailed camera and lens information.
     */
    @Serializable
    data class AdvancedMetadata(
        val lensMake: String? = null,
        val lensModel: String? = null,
        val sceneMode: String? = null,
        val meteringMode: String? = null,
        val colorSpace: String? = null,
        val software: String? = null,
        val gpsAltitude: Double? = null,
        val gpsTimestamp: String? = null
    )
}

/**
 * Represents the loading state of metadata.
 */
sealed class MetadataLoadingState {
    object NotLoaded : MetadataLoadingState()
    object Loading : MetadataLoadingState()
    data class Loaded(val metadata: MediaMetadata) : MetadataLoadingState()
    data class Error(val exception: Exception) : MetadataLoadingState()
}

/**
 * Serializable version of Android's Location class for caching.
 */
@Serializable
data class SerializableLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f
) {
    companion object {
        fun fromLocation(location: Location): SerializableLocation = SerializableLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy
        )
    }

    fun toLocation(): Location = Location("").apply {
        latitude = this@SerializableLocation.latitude
        longitude = this@SerializableLocation.longitude
        accuracy = this@SerializableLocation.accuracy
    }
}
