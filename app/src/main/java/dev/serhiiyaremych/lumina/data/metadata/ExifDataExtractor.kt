package dev.serhiiyaremych.lumina.data.metadata

import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dev.serhiiyaremych.lumina.domain.model.MediaMetadata
import dev.serhiiyaremych.lumina.domain.model.SerializableLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts EXIF data from media files using Android's ExifInterface.
 * Uses progressive loading to optimize performance.
 */
@Singleton
class ExifDataExtractor @Inject constructor(
    private val context: Context
) {
    
    /**
     * Extracts metadata from a media file using progressive loading.
     */
    suspend fun extractMetadata(
        uri: Uri,
        fileSize: Long,
        loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL
    ): MediaMetadata? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                
                val essential = extractEssentialMetadata(exif, fileSize)
                val technical = if (loadLevel >= MetadataLoadLevel.TECHNICAL) {
                    extractTechnicalMetadata(exif)
                } else null
                val advanced = if (loadLevel >= MetadataLoadLevel.ADVANCED) {
                    extractAdvancedMetadata(exif)
                } else null
                
                MediaMetadata(
                    essential = essential,
                    technical = technical,
                    advanced = advanced
                )
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
    
    private fun extractEssentialMetadata(exif: ExifInterface, fileSize: Long): MediaMetadata.EssentialMetadata {
        return MediaMetadata.EssentialMetadata(
            cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
            dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)?.let { formatDateTime(it) },
            location = extractLocation(exif),
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
            fileSizeFormatted = formatFileSize(fileSize)
        )
    }
    
    private fun extractTechnicalMetadata(exif: ExifInterface): MediaMetadata.TechnicalMetadata {
        return MediaMetadata.TechnicalMetadata(
            aperture = exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)?.let { "f/$it" },
            iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
            focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { "${it}mm" },
            exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { "${it}s" },
            whiteBalance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)?.let { mapWhiteBalance(it) },
            flash = exif.getAttribute(ExifInterface.TAG_FLASH)?.let { mapFlash(it) },
            imageLength = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1).takeIf { it != -1 },
            imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1).takeIf { it != -1 }
        )
    }
    
    private fun extractAdvancedMetadata(exif: ExifInterface): MediaMetadata.AdvancedMetadata {
        return MediaMetadata.AdvancedMetadata(
            lensMake = exif.getAttribute(ExifInterface.TAG_LENS_MAKE),
            lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
            sceneMode = exif.getAttribute(ExifInterface.TAG_SCENE_TYPE),
            meteringMode = exif.getAttribute(ExifInterface.TAG_METERING_MODE)?.let { mapMeteringMode(it) },
            colorSpace = exif.getAttribute(ExifInterface.TAG_COLOR_SPACE)?.let { mapColorSpace(it) },
            software = exif.getAttribute(ExifInterface.TAG_SOFTWARE),
            gpsAltitude = exif.getAltitude(0.0).takeIf { it != 0.0 },
            gpsTimestamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP)
        )
    }
    
    private fun extractLocation(exif: ExifInterface): SerializableLocation? {
        val latLong = exif.latLong
        return if (latLong != null) {
            SerializableLocation(
                latitude = latLong[0],
                longitude = latLong[1],
                accuracy = 0f
            )
        } else null
    }
    
    private fun formatDateTime(dateTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateTime)
            if (date != null) outputFormat.format(date) else dateTime
        } catch (e: Exception) {
            dateTime
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1.0 -> "%.1f GB".format(gb)
            mb >= 1.0 -> "%.1f MB".format(mb)
            kb >= 1.0 -> "%.1f KB".format(kb)
            else -> "$bytes bytes"
        }
    }
    
    private fun mapWhiteBalance(value: String): String = when (value) {
        "0" -> "Auto"
        "1" -> "Manual"
        else -> "Unknown"
    }
    
    private fun mapFlash(value: String): String = when (value) {
        "0" -> "No Flash"
        "1" -> "Flash Fired"
        "5" -> "Flash Fired, Return not detected"
        "7" -> "Flash Fired, Return detected"
        "16" -> "Flash Off"
        "24" -> "Flash Auto"
        "25" -> "Flash Auto, Flash Fired"
        "31" -> "Flash Auto, Flash Fired, Return detected"
        else -> "Unknown"
    }
    
    private fun mapMeteringMode(value: String): String = when (value) {
        "0" -> "Unknown"
        "1" -> "Average"
        "2" -> "Center Weighted"
        "3" -> "Spot"
        "4" -> "Multi-spot"
        "5" -> "Multi-segment"
        "6" -> "Partial"
        else -> "Unknown"
    }
    
    private fun mapColorSpace(value: String): String = when (value) {
        "1" -> "sRGB"
        "2" -> "Adobe RGB"
        "65535" -> "Uncalibrated"
        else -> "Unknown"
    }
}

/**
 * Defines the level of metadata to load for performance optimization.
 */
enum class MetadataLoadLevel {
    ESSENTIAL,    // Basic info (camera, date, location) - fast loading
    TECHNICAL,    // Camera settings (aperture, ISO, etc.) - medium loading
    ADVANCED      // Full metadata including lens info - slower loading
}