package com.example.simquickpanel

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var shizukuReadyState by mutableStateOf(false)
    private var statusMessageState by mutableStateOf("Checking Shizuku...")

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateShizukuStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shizukuReadyState = false
        statusMessageState = "Shizuku binder disconnected"
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                shizukuReadyState = true
                statusMessageState = "Shizuku Ready"
            } else {
                shizukuReadyState = false
                statusMessageState = "Shizuku permission denied"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        updateShizukuStatus()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FloatingControlOverlay(
                    isShizukuReady = shizukuReadyState,
                    statusMessage = statusMessageState,
                    onUpdateStatus = { statusMessageState = it },
                    onRequestShizuku = {
                        updateShizukuStatus()
                        ShizukuShellHelper.requestPermission()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    private fun updateShizukuStatus() {
        when {
            !ShizukuShellHelper.isBinderAlive() -> {
                shizukuReadyState = false
                statusMessageState = "Shizuku not running — start Shizuku first"
            }
            !ShizukuShellHelper.hasPermission() -> {
                shizukuReadyState = false
                statusMessageState = "Tap any button to grant Shizuku permission"
                ShizukuShellHelper.requestPermission()
            }
            else -> {
                shizukuReadyState = true
                statusMessageState = "Shizuku Connected"
            }
        }
    }
}

@Composable
fun FloatingControlOverlay(
    isShizukuReady: Boolean,
    statusMessage: String,
    onUpdateStatus: (String) -> Unit,
    onRequestShizuku: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var isSim2On by remember { mutableStateOf(true) }
    var networkMode by remember { mutableStateOf(NetworkMode.MODE_5G) }
    var isVoLteOn by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }

    // Full-screen transparent backdrop: tapping outside closes the floating panel
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 96.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Compact live output / diagnostic pill so you immediately see what happened
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xE616171B)
            ) {
                Text(
                    text = statusMessage,
                    color = if (isShizukuReady) Color(0xFF93C5FD) else Color(0xFFFCA5A5),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Horizontal 3-Button Floating Pill [ X   X   X ]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(32.dp)),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF1E1F24),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Secondary SIM Toggle
                    ControlPillButton(
                        icon = Icons.Rounded.SimCard,
                        label = "SIM 2",
                        statusText = if (isSim2On) "ON" else "OFF",
                        isActive = isSim2On,
                        enabled = !isBusy,
                        onClick = {
                            if (!isShizukuReady) {
                                onRequestShizuku()
                                return@ControlPillButton
                            }
                            scope.launch {
                                isBusy = true
                                val nextState = !isSim2On
                                isSim2On = nextState
                                onUpdateStatus("Running SIM 2 -> ${if (nextState) "ON" else "OFF"}...")
                                val output = ShizukuShellHelper.setSecondarySimEnabled(nextState)
                                onUpdateStatus(output)
                                isBusy = false
                            }
                        }
                    )

                    // 2. Primary SIM Network Cycler (3G -> 4G -> 5G)
                    ControlPillButton(
                        icon = Icons.Rounded.CellTower,
                        label = "SIM 1",
                        statusText = networkMode.label,
                        isActive = true,
                        enabled = !isBusy,
                        onClick = {
                            if (!isShizukuReady) {
                                onRequestShizuku()
                                return@ControlPillButton
                            }
                            scope.launch {
                                isBusy = true
                                val nextMode = when (networkMode) {
                                    NetworkMode.MODE_5G -> NetworkMode.MODE_4G
                                    NetworkMode.MODE_4G -> NetworkMode.MODE_3G
                                    NetworkMode.MODE_3G -> NetworkMode.MODE_5G
                                }
                                networkMode = nextMode
                                onUpdateStatus("Switching SIM 1 -> ${nextMode.label}...")
                                val output = ShizukuShellHelper.setPrimarySimNetworkMode(nextMode)
                                onUpdateStatus(output)
                                isBusy = false
                            }
                        }
                    )

                    // 3. Primary SIM VoLTE Toggle
                    ControlPillButton(
                        icon = Icons.Rounded.Hd,
                        label = "VoLTE",
                        statusText = if (isVoLteOn) "ON" else "OFF",
                        isActive = isVoLteOn,
                        enabled = !isBusy,
                        onClick = {
                            if (!isShizukuReady) {
                                onRequestShizuku()
                                return@ControlPillButton
                            }
                            scope.launch {
                                isBusy = true
                                val nextState = !isVoLteOn
                                isVoLteOn = nextState
                                onUpdateStatus("Switching VoLTE -> ${if (nextState) "ON" else "OFF"}...")
                                val output = ShizukuShellHelper.setPrimarySimVoLte(nextState)
                                onUpdateStatus(output)
                                isBusy = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ControlPillButton(
    icon: ImageVector,
    label: String,
    statusText: String,
    isActive: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val containerColor = if (isActive) Color(0xFF3B82F6) else Color(0xFF2C2E35)
    val contentColor = if (isActive) Color.White else Color(0xFF9CA3AF)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        modifier = Modifier.width(96.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = contentColor.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = statusText,
                fontSize = 13.sp,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
