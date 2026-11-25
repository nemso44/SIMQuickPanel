package com.example.simquickpanel

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prompt for Shizuku permission if not yet granted
        if (!ShizukuShellHelper.isShizukuReady()) {
            ShizukuShellHelper.requestPermission()
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FloatingControlOverlay(
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun FloatingControlOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current

    // Local UI states (can also be initialized by reading current settings via Shizuku)
    var isSim2On by remember { mutableStateOf(true) }
    var networkMode by remember { mutableStateOf(NetworkMode.MODE_5G) }
    var isVoLteOn by remember { mutableStateOf(true) }

    // Full-screen transparent backdrop: tapping outside the horizontal pill closes the activity
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
        // Horizontal Pill Container matching your sketch [ X   X   X ]
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 96.dp)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(32.dp))
                // Prevent clicks inside the pill from dismissing the overlay
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
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
                    onClick = {
                        if (!ShizukuShellHelper.isShizukuReady()) {
                            ShizukuShellHelper.requestPermission()
                            Toast.makeText(context, "Grant Shizuku permission first", Toast.LENGTH_SHORT).show()
                            return@ControlPillButton
                        }
                        isSim2On = !isSim2On
                        val result = ShizukuShellHelper.setSecondarySimEnabled(isSim2On)
                        if (result.isNotBlank()) Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                    }
                )

                // 2. Primary SIM Network Cycler (3G -> 4G -> 5G)
                ControlPillButton(
                    icon = Icons.Rounded.CellTower,
                    label = "SIM 1",
                    statusText = networkMode.label,
                    isActive = true,
                    onClick = {
                        if (!ShizukuShellHelper.isShizukuReady()) {
                            ShizukuShellHelper.requestPermission()
                            Toast.makeText(context, "Grant Shizuku permission first", Toast.LENGTH_SHORT).show()
                            return@ControlPillButton
                        }
                        networkMode = when (networkMode) {
                            NetworkMode.MODE_5G -> NetworkMode.MODE_4G
                            NetworkMode.MODE_4G -> NetworkMode.MODE_3G
                            NetworkMode.MODE_3G -> NetworkMode.MODE_5G
                        }
                        val result = ShizukuShellHelper.setPrimarySimNetworkMode(networkMode)
                        if (result.isNotBlank()) Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                    }
                )

                // 3. Primary SIM VoLTE Toggle
                ControlPillButton(
                    icon = Icons.Rounded.Hd,
                    label = "VoLTE",
                    statusText = if (isVoLteOn) "ON" else "OFF",
                    isActive = isVoLteOn,
                    onClick = {
                        if (!ShizukuShellHelper.isShizukuReady()) {
                            ShizukuShellHelper.requestPermission()
                            Toast.makeText(context, "Grant Shizuku permission first", Toast.LENGTH_SHORT).show()
                            return@ControlPillButton
                        }
                        isVoLteOn = !isVoLteOn
                        val result = ShizukuShellHelper.setPrimarySimVoLte(isVoLteOn)
                        if (result.isNotBlank()) Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                    }
                )
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
    onClick: () -> Unit
) {
    val containerColor = if (isActive) Color(0xFF3B82F6) else Color(0xFF2C2E35)
    val contentColor = if (isActive) Color.White else Color(0xFF9CA3AF)

    Surface(
        onClick = onClick,
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
