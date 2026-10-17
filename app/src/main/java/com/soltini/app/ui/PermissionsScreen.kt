package com.soltini.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.GradientPillButton
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*

@Composable
fun PermissionsScreen(
    onAllPermissionsGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMic = granted
        if (granted) onAllPermissionsGranted()
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoftBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Iridescent Gradient Mic Orb
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .scale(if (hasMic) 1f else scale)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.35f),
                        spotColor = LocalMyraTokens.current.accentSecondary.copy(alpha = 0.40f)
                    )
                    .clip(CircleShape)
                    .background(LocalMyraTokens.current.accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasMic) Icons.Default.Mic else Icons.Default.MicNone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "One Permission to Start",
                style = MaterialTheme.typography.headlineMedium,
                color = LocalMyraTokens.current.textPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Myra only needs your microphone to begin. You can unlock AI Agent capabilities like device lock, app launching, and the Digital Assistant role from Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMyraTokens.current.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Permission Floating Glass Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PastelIconBadge(
                        icon = Icons.Default.Mic,
                        backgroundColor = if (hasMic) BadgeMintBg else BadgePurpleBg,
                        iconTint = if (hasMic) SecondaryMintDark else BadgePurpleTint,
                        size = 46.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Microphone",
                            color = LocalMyraTokens.current.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "For live voice conversation",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    GlassStatusBadge(
                        text = if (hasMic) "Granted" else "Required",
                        isActive = hasMic
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            GradientPillButton(
                text = if (hasMic) "Continue →" else "Allow Microphone",
                icon = Icons.Default.Mic,
                onClick = {
                    if (hasMic) {
                        onAllPermissionsGranted()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You can grant additional agent permissions anytime from Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalMyraTokens.current.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
