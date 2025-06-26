package dev.serhiiyaremych.lumina.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.R

enum class PermissionFlowState {
    INITIAL,
    REQUESTING,
    GRANTED,
    DENIED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPermissionFlow(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionManager = rememberMediaPermissionManager()
    var flowState by remember { mutableStateOf(PermissionFlowState.INITIAL) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var permissionStatus by remember { mutableStateOf(permissionManager.checkPermissionStatus()) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.handlePermissionResult(permissions)
        permissionStatus = permissionManager.checkPermissionStatus()

        when {
            permissionStatus.hasMediaAccess -> {
                flowState = PermissionFlowState.GRANTED
                onPermissionGranted()
            }
            else -> {
                flowState = PermissionFlowState.DENIED
                onPermissionDenied()
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionStatus = permissionManager.checkPermissionStatus()

        when {
            permissionStatus.hasMediaAccess -> {
                flowState = PermissionFlowState.GRANTED
                onPermissionGranted()
            }
            else -> {
                flowState = PermissionFlowState.INITIAL
                showBottomSheet = true
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (flowState) {
            PermissionFlowState.INITIAL -> {
                // Show permission rationale UI
                PermissionRationaleContent(
                    onRequestPermission = {
                        flowState = PermissionFlowState.REQUESTING
                        showBottomSheet = true
                    }
                )
            }

            PermissionFlowState.REQUESTING -> {
                CircularProgressIndicator()
            }

            PermissionFlowState.DENIED -> {
                PermissionDeniedContent(
                    onRetry = {
                        flowState = PermissionFlowState.REQUESTING
                        permissionLauncher.launch(permissionManager.getRequiredPermissions())
                    }
                )
            }

            PermissionFlowState.GRANTED -> {
                // This state should trigger onPermissionGranted and not show UI
            }
        }
    }

    PermissionSelectionBottomSheet(
        isVisible = showBottomSheet,
        sheetState = sheetState,
        onDismissRequest = {
            showBottomSheet = false
            if (flowState == PermissionFlowState.INITIAL) {
                onPermissionDenied()
            }
        },
        onPermissionSelected = { selection ->
            showBottomSheet = false
            flowState = PermissionFlowState.REQUESTING

            when (selection) {
                PermissionSelection.FULL_ACCESS -> {
                    permissionLauncher.launch(permissionManager.getAllPermissions())
                }
                PermissionSelection.SELECTIVE_ACCESS -> {
                    // For selective access, we still need basic permissions
                    // The "selective" part would be handled by the photo picker
                    permissionLauncher.launch(permissionManager.getRequiredPermissions())
                }
            }
        }
    )
}

@Composable
private fun PermissionRationaleContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.photo_library_24px),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Access Your Photos",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "LuminaGallery needs access to your photos and videos to create beautiful gallery visualizations and manage your media collection.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Access")
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.security_24px),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Permission Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Without media permissions, LuminaGallery cannot access your photos and videos. Please grant the necessary permissions to continue.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try Again")
        }
    }
}


