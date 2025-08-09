package dev.serhiiyaremych.lumina.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// Android 14+ permission constant (not available in older compileSdkVersions)
private const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

enum class MediaPermissionState {
    GRANTED,
    DENIED,
    NOT_REQUESTED
}

data class MediaPermissionStatus(
    val imagesPermissionState: MediaPermissionState,
    val videosPermissionState: MediaPermissionState,
    val storagePermissionState: MediaPermissionState,
    val locationPermissionState: MediaPermissionState
) {
    val hasMediaAccess: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            imagesPermissionState == MediaPermissionState.GRANTED ||
                videosPermissionState == MediaPermissionState.GRANTED
        } else {
            storagePermissionState == MediaPermissionState.GRANTED
        }
}

@Composable
fun rememberMediaPermissionManager(
    onPermissionResult: (MediaPermissionStatus) -> Unit = {}
): MediaPermissionManager {
    val context = LocalContext.current

    return remember {
        MediaPermissionManager(context, onPermissionResult)
    }
}

class MediaPermissionManager(
    private val context: Context,
    private val onPermissionResult: (MediaPermissionStatus) -> Unit
) {
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Android 14+ - include the new visual user selected permission
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            READ_MEDIA_VISUAL_USER_SELECTED
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        // Android 10-12
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private val optionalPermissions = arrayOf(
        Manifest.permission.ACCESS_MEDIA_LOCATION
    )

    fun checkPermissionStatus(): MediaPermissionStatus = detectActualPermissionState()

    private fun getPermissionState(permission: String): MediaPermissionState = when (ContextCompat.checkSelfPermission(context, permission)) {
        PackageManager.PERMISSION_GRANTED -> MediaPermissionState.GRANTED
        PackageManager.PERMISSION_DENIED -> {
            // For now, we'll treat all denials as regular denials
            // In a real implementation, you might want to track permanent denials
            MediaPermissionState.DENIED
        }
        else -> MediaPermissionState.NOT_REQUESTED
    }

    private fun detectActualPermissionState(): MediaPermissionStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Android 14+ - check for either full access OR limited access
        val imagesGranted = getPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
        val videosGranted = getPermissionState(Manifest.permission.READ_MEDIA_VIDEO)
        val limitedGranted = getPermissionState(READ_MEDIA_VISUAL_USER_SELECTED)

        // If we have limited access, treat images/videos as granted
        val effectiveImagesState = if (limitedGranted == MediaPermissionState.GRANTED) {
            MediaPermissionState.GRANTED
        } else {
            imagesGranted
        }

        val effectiveVideosState = if (limitedGranted == MediaPermissionState.GRANTED) {
            MediaPermissionState.GRANTED
        } else {
            videosGranted
        }

        MediaPermissionStatus(
            imagesPermissionState = effectiveImagesState,
            videosPermissionState = effectiveVideosState,
            storagePermissionState = MediaPermissionState.NOT_REQUESTED,
            locationPermissionState = getPermissionState(Manifest.permission.ACCESS_MEDIA_LOCATION)
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13 - check granular media permissions only
        MediaPermissionStatus(
            imagesPermissionState = getPermissionState(Manifest.permission.READ_MEDIA_IMAGES),
            videosPermissionState = getPermissionState(Manifest.permission.READ_MEDIA_VIDEO),
            storagePermissionState = MediaPermissionState.NOT_REQUESTED,
            locationPermissionState = getPermissionState(Manifest.permission.ACCESS_MEDIA_LOCATION)
        )
    } else {
        // Android 10-12 - use legacy storage permission
        MediaPermissionStatus(
            imagesPermissionState = MediaPermissionState.NOT_REQUESTED,
            videosPermissionState = MediaPermissionState.NOT_REQUESTED,
            storagePermissionState = getPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE),
            locationPermissionState = getPermissionState(Manifest.permission.ACCESS_MEDIA_LOCATION)
        )
    }

    fun getRequiredPermissions(): Array<String> = requiredPermissions
    fun getOptionalPermissions(): Array<String> = optionalPermissions
    fun getAllPermissions(): Array<String> = requiredPermissions + optionalPermissions

    fun handlePermissionResult(
        permissions: Map<String, Boolean>
    ) {
        val status = checkPermissionStatus()
        onPermissionResult(status)
    }
}
