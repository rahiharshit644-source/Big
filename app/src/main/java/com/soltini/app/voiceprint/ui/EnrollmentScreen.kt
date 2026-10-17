package com.soltini.app.voiceprint.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GradientPillButton
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*
import com.soltini.app.voiceprint.domain.EnrollmentManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollmentScreen(
    viewModel: VoiceprintViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.cancelRecording()
        onNavigateBack()
    }

    val currentStep by viewModel.enrollmentManager.currentStep.collectAsState()
    val stepStatus by viewModel.enrollmentManager.stepStatus.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val liveLevel by viewModel.liveInputLevel.collectAsState()

    val totalSteps = EnrollmentManager.REQUIRED_PHRASE_COUNT
    val currentPhrase = if (currentStep < totalSteps) {
        EnrollmentManager.ENROLLMENT_PHRASES[currentStep]
    } else ""

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoftBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        viewModel.cancelRecording()
                        onNavigateBack()
                    },
                    shape = CircleShape,
                    color = GlassCardWhite,
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(4.dp, CircleShape, ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.14f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextCharcoal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "VoicePrint Enrollment",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalMyraTokens.current.textPrimary
                    )
                    Text(
                        text = "Personalize speaker verification",
                        fontSize = 12.sp,
                        color = LocalMyraTokens.current.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (stepStatus !is EnrollmentManager.StepResult.Completed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = BadgePurpleBg,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Step ${currentStep + 1} of $totalSteps",
                            color = BadgePurpleTint,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "Hold phone ~1 foot away",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { ((currentStep.toFloat()) / totalSteps).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = LocalMyraTokens.current.accent,
                    trackColor = SoftDivider,
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Phrase Prompt Floating Glass Card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 26.dp,
                    elevation = 8.dp,
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PastelIconBadge(
                            icon = Icons.Default.RecordVoiceOver,
                            backgroundColor = BadgePurpleBg,
                            iconTint = BadgePurpleTint,
                            size = 48.dp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Read aloud clearly:",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "\"$currentPhrase\"",
                            color = LocalMyraTokens.current.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 27.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            color = BadgeMintBg,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Speak naturally for at least 3 seconds",
                                color = SecondaryMintDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Live Mic Level Meter
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    elevation = 4.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isRecording) "Listening — Microphone Level" else "Ready to Record",
                            color = if (isRecording) SecondaryMintDark else TextMidGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(GlassSurfaceSubtle)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(if (isRecording) liveLevel.coerceIn(0.05f, 1f) else 0f)
                                    .clip(CircleShape)
                                    .background(LocalMyraTokens.current.accentHorizontalGradient)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Step Status Messages
                when (val status = stepStatus) {
                    is EnrollmentManager.StepResult.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = BadgePurpleTint,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Validating speech quality...",
                                color = LocalMyraTokens.current.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    is EnrollmentManager.StepResult.Accepted -> {
                        Surface(
                            color = BadgeMintBg,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SecondaryMintDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Phrase accepted! Moving to next phrase...",
                                    color = SecondaryMintDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    is EnrollmentManager.StepResult.Rejected -> {
                        Surface(
                            color = Color(0xFFFDE8E8),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = RedError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.reason,
                                    color = RedError,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    else -> {}
                }

                Spacer(modifier = Modifier.height(20.dp))

                GradientPillButton(
                    text = if (isRecording) "Done Speaking (Process)" else "Tap to Start Recording",
                    icon = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    brush = if (isRecording) Brush.horizontalGradient(listOf(RedError, Color(0xFFF43F5E))) else LocalMyraTokens.current.accentHorizontalGradient,
                    onClick = {
                        if (isRecording) {
                            viewModel.stopRecordingAndProcessPhrase()
                        } else {
                            viewModel.startRecording()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 54.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    onClick = {
                        viewModel.cancelRecording()
                        viewModel.enrollmentManager.reset()
                    },
                    shape = CircleShape,
                    color = GlassCardWhite,
                    modifier = Modifier.shadow(4.dp, CircleShape, ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = TextMidGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restart Enrollment", color = LocalMyraTokens.current.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                val completed = stepStatus as EnrollmentManager.StepResult.Completed
                Spacer(modifier = Modifier.height(20.dp))

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28.dp,
                    elevation = 8.dp,
                    contentPadding = PaddingValues(28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PastelIconBadge(
                            icon = Icons.Default.CheckCircle,
                            backgroundColor = BadgeMintBg,
                            iconTint = SecondaryMintDark,
                            size = 72.dp,
                            iconSize = 40.dp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "VoicePrint Enrolled!",
                            color = LocalMyraTokens.current.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Myra has created and encrypted your acoustic voiceprint.",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Surface(
                            color = GlassSurfaceSubtle,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Calibrated Threshold", color = LocalMyraTokens.current.textSecondary, fontSize = 13.sp)
                                    Text(
                                        "${(completed.calibratedThreshold * 100).toInt()}%",
                                        color = SecondaryMintDark,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Phrases Recorded", color = LocalMyraTokens.current.textSecondary, fontSize = 13.sp)
                                    Text("5 of 5", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Storage Security", color = LocalMyraTokens.current.textSecondary, fontSize = 13.sp)
                                    Text("Hardware AES-GCM", color = BadgePurpleTint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        GradientPillButton(
                            text = "Done & Return to Settings",
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth(),
                            height = 50.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
