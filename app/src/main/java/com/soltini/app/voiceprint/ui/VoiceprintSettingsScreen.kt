package com.soltini.app.voiceprint.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.ui.components.BadgeStyle
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassGradientButton
import com.soltini.app.ui.components.GlassSecondaryButton
import com.soltini.app.ui.components.GlassSectionHeader
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*
import com.soltini.app.voiceprint.gate.VoiceGateMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VoiceprintSettingsScreen
 *
 * Full Voice ID settings and calibration console in Soft Light Glassmorphic style.
 * Controls VoiceGate modes, sensitivity calibration, profile lifecycle, and live testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceprintSettingsScreen(
    viewModel: VoiceprintViewModel,
    onNavigateToEnrollment: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    val activeProfile by viewModel.activeProfile.collectAsState()
    val gateMode by viewModel.gateMode.collectAsState()
    val sensitivityOffset by viewModel.sensitivityOffset.collectAsState()
    val isModelAvailable by viewModel.isModelAvailable.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoftBackgroundBrush)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Voice ID & Verification",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalMyraTokens.current.textPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextCharcoal
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Model Status Notification (if not available)
                if (!isModelAvailable) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        contentPadding = 16.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PastelIconBadge(
                                icon = Icons.Default.Info,
                                badgeColor = PastelYellowBadge,
                                iconTint = Color(0xFFD97706),
                                size = 42.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ONNX Model Status",
                                    color = Color(0xFFD97706),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = modelStatus,
                                    color = LocalMyraTokens.current.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Enrolled Profile Card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    contentPadding = 18.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PastelIconBadge(
                            icon = Icons.Default.RecordVoiceOver,
                            badgeColor = if (activeProfile != null) PastelMintBadge else PastelPurpleBadge,
                            iconTint = if (activeProfile != null) MintAccentDark else PurpleAccentStart,
                            size = 46.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (activeProfile != null) "Owner Voiceprint Enrolled" else "No Voiceprint Enrolled",
                                color = LocalMyraTokens.current.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (activeProfile != null) "Encrypted with AndroidKeyStore AES-256 GCM" else "Enroll your voice to personalize security",
                                color = LocalMyraTokens.current.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        GlassStatusBadge(
                            text = if (activeProfile != null) "Active" else "Unenrolled",
                            style = if (activeProfile != null) BadgeStyle.ACTIVE else BadgeStyle.INACTIVE
                        )
                    }

                    if (activeProfile != null) {
                        val profile = activeProfile!!
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFEAE2F8))
                        Spacer(modifier = Modifier.height(12.dp))

                        val formattedDate = remember(profile.createdAt) {
                            SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(profile.createdAt))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Enrolled Date", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
                            Text(formattedDate, color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Calibrated Threshold", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
                            Text("${(profile.calibratedThreshold * 100).toInt()}%", color = MintAccentDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Embedding Vector Dim", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
                            Text("${profile.embeddingDim} dims", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassGradientButton(
                                text = "Re-enroll Voice",
                                icon = Icons.Default.Mic,
                                onClick = onNavigateToEnrollment,
                                modifier = Modifier.weight(1f)
                            )

                            GlassSecondaryButton(
                                text = "Delete",
                                icon = Icons.Default.Delete,
                                onClick = { showDeleteConfirmDialog = true },
                                containerColor = Color(0xFFFFE5EA),
                                contentColor = RedError
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        GlassGradientButton(
                            text = "Start Voice Enrollment",
                            icon = Icons.Default.Mic,
                            onClick = onNavigateToEnrollment,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Voice Gate Mode Selector
                GlassSectionHeader(
                    title = "Voice Gate Mode",
                    subtitle = "Choose how speaker verification gates voice actions"
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeSelectionItem(
                        title = "Protect Sensitive Actions (Recommended)",
                        description = "Anyone can chat with MYRAA, but sensitive actions (door locks, power relays, data wiping) require owner voice verification.",
                        selected = gateMode == VoiceGateMode.OWNER_FOR_SENSITIVE,
                        onClick = { viewModel.setGateMode(VoiceGateMode.OWNER_FOR_SENSITIVE) }
                    )

                    ModeSelectionItem(
                        title = "Owner Only (Strict Gate)",
                        description = "Complete gate: Audio is only streamed to Gemini Live after owner voice identity is verified.",
                        selected = gateMode == VoiceGateMode.OWNER_ONLY,
                        onClick = { viewModel.setGateMode(VoiceGateMode.OWNER_ONLY) }
                    )

                    ModeSelectionItem(
                        title = "Disabled (Off)",
                        description = "Microphone audio passes directly to Gemini Live without speaker verification.",
                        selected = gateMode == VoiceGateMode.OFF,
                        onClick = { viewModel.setGateMode(VoiceGateMode.OFF) }
                    )
                }

                // Sensitivity Calibration Slider
                val calibrated = activeProfile?.calibratedThreshold ?: 0.72f
                val effective = (calibrated + sensitivityOffset).coerceIn(0.50f, 0.95f)

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    contentPadding = 18.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Verification Sensitivity",
                            color = LocalMyraTokens.current.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        GlassStatusBadge(
                            text = "${(effective * 100).toInt()}% (${if (sensitivityOffset >= 0) "+" else ""}${(sensitivityOffset * 100).toInt()}%)",
                            style = BadgeStyle.PURPLE
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Calibrated base: ${(calibrated * 100).toInt()}%. Slide left for noisier rooms, right for stricter verification.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = sensitivityOffset,
                        onValueChange = { viewModel.setSensitivityOffset(it) },
                        valueRange = -0.15f..0.15f,
                        steps = 6,
                        colors = SliderDefaults.colors(
                            thumbColor = PurpleAccentStart,
                            activeTrackColor = PurpleAccentStart,
                            inactiveTrackColor = PastelPurpleBadge
                        )
                    )
                }

                // Embedded Live Test Panel
                VoiceprintTestPanel(viewModel = viewModel)

                // Security Notice / Disclaimer
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    contentPadding = 16.dp
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        PastelIconBadge(
                            icon = Icons.Default.Security,
                            badgeColor = PastelYellowBadge,
                            iconTint = Color(0xFFD97706),
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Security Notice",
                                color = LocalMyraTokens.current.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "On-device voice verification is a convenience layer against casual unauthorized access. Acoustic replay recordings could potentially mimic voice features. High-risk financial payments and system settings require device biometric authentication.",
                                color = LocalMyraTokens.current.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Delete Confirmation Dialog
            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Delete Voiceprint?", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "This will permanently erase your enrolled voiceprint embedding from the secure hardware storage. Speaker verification will be disabled until you re-enroll.",
                            color = LocalMyraTokens.current.textSecondary
                        )
                    },
                    confirmButton = {
                        GlassSecondaryButton(
                            text = "Delete",
                            onClick = {
                                viewModel.deleteProfile {
                                    showDeleteConfirmDialog = false
                                }
                            },
                            containerColor = Color(0xFFFFE5EA),
                            contentColor = RedError
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Cancel", color = LocalMyraTokens.current.textSecondary)
                        }
                    },
                    containerColor = GlassCardWhiteSolid,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeSelectionItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 16.dp,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.Top) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = PurpleAccentStart,
                    unselectedColor = TextMidGray
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = if (selected) PurpleAccentStart else TextCharcoal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
