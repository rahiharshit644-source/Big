package com.soltini.app.ui

import com.soltini.app.ui.theme.LocalMyraTokens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.soltini.app.overlay.OverlayService
import com.soltini.app.overlay.ParticleSphereOrbView
import com.soltini.app.overlay.RingIndicatorView
import com.soltini.app.overlay.RingState
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassSectionHeader
import com.soltini.app.ui.theme.PrimaryGradientBrush
import com.soltini.app.ui.theme.PrimaryPurple
import com.soltini.app.ui.theme.TextCharcoal
import com.soltini.app.ui.theme.TextMidGray

/**
 * Settings → "Floating Orb": pick which orb floats over other apps.
 *
 *  • Classic Wireframe — the original rotating 3-ring globe.
 *  • Particle Sphere   — 360° neon-particle sphere (from the "untitled" file).
 *
 * Both orbs change colour exactly the same way (rainbow ramp, saturation by Myra's state), and the
 * change is applied instantly to the running overlay.
 */
@Composable
fun OrbStyleSettingsSection(appSettings: AppSettings?) {
    val context = LocalContext.current
    val settings = remember(appSettings) { appSettings ?: AppSettings(context) }
    var selected by remember { mutableStateOf(settings.floatingOrbStyle) }

    fun choose(style: String) {
        selected = style
        settings.floatingOrbStyle = style
        // Swap the live orb right away (no-op if the overlay isn't running yet — it reads the pref on start).
        OverlayService.getInstance()?.applyOrbStyle()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        GlassSectionHeader(
            title = "Floating Orb",
            subtitle = "Choose the orb that floats over your other apps"
        )
        Spacer(Modifier.height(12.dp))

        OrbOptionCard(
            title = "Classic Wireframe",
            subtitle = "Original rotating 3-ring globe",
            isSelected = selected == AppSettings.ORB_STYLE_CLASSIC,
            onClick = { choose(AppSettings.ORB_STYLE_CLASSIC) },
            preview = {
                AndroidView(
                    modifier = Modifier.size(84.dp),
                    factory = { ctx -> RingIndicatorView(ctx).apply { state = RingState.LISTENING } }
                )
            }
        )

        Spacer(Modifier.height(12.dp))

        OrbOptionCard(
            title = "Particle Sphere",
            subtitle = "360° sphere made of thousands of glowing particles",
            isSelected = selected == AppSettings.ORB_STYLE_PARTICLE,
            onClick = { choose(AppSettings.ORB_STYLE_PARTICLE) },
            preview = {
                AndroidView(
                    modifier = Modifier.size(84.dp),
                    factory = { ctx -> ParticleSphereOrbView(ctx).apply { state = RingState.LISTENING } }
                )
            }
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Both orbs change colour with Myra's state — dim when sleeping, richer while listening / thinking, most vivid while speaking. Tap the floating orb to wake Myra, drag it anywhere, long-press to snap it to the edge.",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun OrbOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1B1530)),
                contentAlignment = Alignment.Center
            ) { preview() }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = LocalMyraTokens.current.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.background(LocalMyraTokens.current.accentGradient)
                        else Modifier.background(Color(0xFFF1EDF9))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
