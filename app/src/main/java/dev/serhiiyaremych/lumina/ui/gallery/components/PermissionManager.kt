package dev.serhiiyaremych.lumina.ui.gallery.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.serhiiyaremych.lumina.ui.components.MediaPermissionFlow

@Composable
fun PermissionManager(
    permissionGranted: Boolean,
    onPermissionGranted: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!permissionGranted) {
        // Permission flow - shown when permissions are not granted
        MediaPermissionFlow(
            onPermissionGranted = { onPermissionGranted(true) },
            onPermissionDenied = {
                Log.w("StreamingApp", "Media permissions denied")
            },
            modifier = modifier
        )
    }
}
