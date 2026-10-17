package com.soltini.app.voiceprint.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*
import com.soltini.app.voiceprint.domain.VoiceprintVerifier

/**
 * VoiceprintTestPanel
 *
 * Interactive testing panel with live score bar and state chips
 * allowing the user to verify themselves or test against other voices in real time.
 */
@Composable
fun VoiceprintTestPanel(
    viewModel: VoiceprintViewModel,
    modifier: Modifier = Modifier
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val liveLevel by viewModel.liveInputLevel.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Mic,
                badgeColor = PastelPurpleBadge,
                iconTint = PurpleAccentStart,
                size = 42.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VoicePrint Live Test",
                    color = LocalMyraTokens.current.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Speak a sentence to test speaker verification score",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeProfile == null) {
            Surface(
                color = Color(0xFFF6F3FC),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Please enroll your voice profile first to enable live testing.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
            return@GlassCard
        }

        // Test Button & Mic Meter
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isRecording) {
                GlassSecondaryButton(
                    text = "Stop & Verify",
                    icon = Icons.Default.Stop,
                    onClick = { viewModel.stopRecordingAndTest() },
                    containerColor = Color(0xFFFFE5EA),
                    contentColor = RedError
                )
            } else {
                GlassGradientButton(
                    text = "Test My Voice",
                    icon = Icons.Default.Mic,
                    onClick = { viewModel.startRecording(forTestPanel = true) }
                )
            }

            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = "Listening...",
                        color = PurpleAccentStart,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PastelPurpleBadge)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(liveLevel.coerceIn(0.08f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(LocalMyraTokens.current.accentGradient)
                        )
                    }
                }
            }
        }

        // Results Section
        AnimatedVisibility(visible = testResult != null) {
            testResult?.let { res ->
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF6F3FC))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Similarity Score",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        val (badgeStyle, badgeText) = when (res.state) {
                            VoiceprintVerifier.VerificationStateEnum.OWNER ->
                                BadgeStyle.ACTIVE to "OWNER VERIFIED"
                            VoiceprintVerifier.VerificationStateEnum.NOT_OWNER ->
                                BadgeStyle.ERROR to "NOT OWNER"
                            VoiceprintVerifier.VerificationStateEnum.UNKNOWN ->
                                BadgeStyle.WARNING to "UNKNOWN"
                        }

                        GlassStatusBadge(text = badgeText, style = badgeStyle)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Score percentage
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${(res.score * 100).toInt()}%",
                            color = if (res.state == VoiceprintVerifier.VerificationStateEnum.OWNER) MintAccentDark else TextCharcoal,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Threshold: ${(res.effectiveThreshold * 100).toInt()}%",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Score Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFE4DCF5))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(res.score.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(5.dp))
                                .then(
                                    if (res.state == VoiceprintVerifier.VerificationStateEnum.OWNER)
                                        Modifier.background(MintAccentDark)
                                    else
                                        Modifier.background(LocalMyraTokens.current.accentGradient)
                                )
                        )
                    }
                }
            }
        }
    }
}
