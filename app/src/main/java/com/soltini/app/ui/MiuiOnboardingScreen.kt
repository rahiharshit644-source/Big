package com.soltini.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GradientPillButton
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*

@Composable
fun MiuiOnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = remember {
        listOf(
            MiuiStep(
                icon = Icons.Default.PowerSettingsNew,
                badgeBg = BadgeYellowBg,
                badgeTint = BadgeYellowTint,
                title = "Enable Autostart",
                subtitle = "MIUI kills apps that can't autostart — this is the #1 fix",
                instructions = listOf(
                    "Open Security app on your phone",
                    "Tap Permissions",
                    "Tap Autostart",
                    "Find Myra and toggle it ON"
                ),
                actionLabel = "Open Security App",
                hasAction = true
            ),
            MiuiStep(
                icon = Icons.Default.Battery5Bar,
                badgeBg = BadgeMintBg,
                badgeTint = SecondaryMintDark,
                title = "No Battery Restrictions",
                subtitle = "Set battery saver to \"No restrictions\" for Myra",
                instructions = listOf(
                    "Tap the button below to open Myra's app settings",
                    "Scroll down and tap Battery saver",
                    "Select No restrictions",
                    "Come back here and continue"
                ),
                actionLabel = "Open App Battery Settings",
                hasAction = true
            ),
            MiuiStep(
                icon = Icons.Default.Lock,
                badgeBg = BadgePurpleBg,
                badgeTint = BadgePurpleTint,
                title = "Lock in Recent Apps",
                subtitle = "Prevents \"Clear All\" from force-stopping Myra",
                instructions = listOf(
                    "Press the Recents (\u25a1) button on your phone",
                    "Find the Myra app card",
                    "Long-press the Myra card",
                    "Tap the \ud83d\udd12 lock icon that appears"
                ),
                actionLabel = null,
                hasAction = false
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalMyraTokens.current.backgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MiuiOnboardingHeader()

            Spacer(modifier = Modifier.height(24.dp))

            StepProgressDots(current = currentStep, total = steps.size)

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                },
                label = "step_content"
            ) { step ->
                MiuiStepCard(
                    step = steps[step],
                    stepNumber = step + 1,
                    onAction = { launchMiuiAction(context, step) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            val isLastStep = currentStep == steps.size - 1

            GradientPillButton(
                text = if (isLastStep) "✓  All Done — Start Myra" else "Next Step →",
                onClick = {
                    if (isLastStep) {
                        markMiuiOnboardingComplete(context)
                        onComplete()
                    } else {
                        currentStep++
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnimatedVisibility(visible = currentStep > 0) {
                    Surface(
                        onClick = { currentStep-- },
                        shape = CircleShape,
                        color = GlassCardWhite,
                        modifier = Modifier.shadow(3.dp, CircleShape, ambientColor = PrimaryPurple.copy(alpha = 0.12f))
                    ) {
                        Text(
                            "← Back",
                            color = TextCharcoal,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    onClick = {
                        markMiuiOnboardingComplete(context)
                        onComplete()
                    },
                    shape = CircleShape,
                    color = GlassCardWhite,
                    modifier = Modifier.shadow(3.dp, CircleShape, ambientColor = PrimaryPurple.copy(alpha = 0.12f))
                ) {
                    Text(
                        "Skip All",
                        color = TextMidGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "You can re-open this guide anytime from Settings → Permissions",
                style = MaterialTheme.typography.bodySmall,
                color = TextMidGray,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun MiuiOnboardingHeader() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(84.dp)
            .scale(scale)
            .shadow(12.dp, CircleShape, ambientColor = PrimaryPurple.copy(alpha = 0.3f))
            .clip(CircleShape)
            .background(PrimaryGradientBrush),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        "Xiaomi Background Setup",
        style = MaterialTheme.typography.headlineSmall,
        color = TextCharcoal,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "MIUI aggressively pauses background apps.\nComplete these quick steps so Myra stays responsive.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextMidGray,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
    )
}

@Composable
private fun StepProgressDots(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            val isActive = i == current
            val isPast = i < current
            Box(
                modifier = Modifier
                    .size(if (isActive) 28.dp else 10.dp, 10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isActive -> PrimaryPurple
                            isPast -> SecondaryMint
                            else -> SoftDivider
                        }
                    )
            )
        }
    }
}

private data class MiuiStep(
    val icon: ImageVector,
    val badgeBg: Color,
    val badgeTint: Color,
    val title: String,
    val subtitle: String,
    val instructions: List<String>,
    val actionLabel: String?,
    val hasAction: Boolean
)

@Composable
private fun MiuiStepCard(
    step: MiuiStep,
    stepNumber: Int,
    onAction: () -> Unit
) {
    var actionTapped by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 26.dp,
        elevation = 7.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = step.icon,
                backgroundColor = step.badgeBg,
                iconTint = step.badgeTint,
                size = 52.dp,
                iconSize = 26.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = step.badgeBg
                ) {
                    Text(
                        "Step $stepNumber",
                        color = step.badgeTint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    step.title,
                    color = TextCharcoal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    step.subtitle,
                    color = TextMidGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            color = GlassSurfaceSubtle,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                step.instructions.forEachIndexed { idx, instruction ->
                    Row(
                        modifier = Modifier.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(step.badgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${idx + 1}",
                                color = step.badgeTint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            instruction,
                            color = TextCharcoal,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        if (step.hasAction && step.actionLabel != null) {
            Spacer(modifier = Modifier.height(16.dp))
            GradientPillButton(
                text = if (actionTapped) "✓ Opened — Come back when done" else step.actionLabel,
                onClick = {
                    onAction()
                    actionTapped = true
                },
                modifier = Modifier.fillMaxWidth(),
                height = 46.dp
            )
        }
    }
}

private fun launchMiuiAction(context: Context, step: Int) {
    when (step) {
        0 -> launchAutoStartSettings(context)
        1 -> launchBatterySettings(context)
    }
}

private fun launchAutoStartSettings(context: Context) {
    val intents = listOf(
        Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        },
        Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.securitycenter.MainActivity"
            )
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
    tryStartActivities(context, intents)
}

private fun launchBatterySettings(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val intents = mutableListOf<Intent>()

    if (manufacturer == "xiaomi" || manufacturer == "poco" || manufacturer == "redmi") {
        intents.add(Intent().apply {
            component = ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        intents.add(Intent().apply {
            action = "miui.intent.action.POWER_HIDE_MODE_APP_LIST"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })

    tryStartActivities(context, intents)
}

private fun tryStartActivities(context: Context, intents: List<Intent>) {
    for (intent in intents) {
        try {
            if (intent.component != null) {
                val resolved = context.packageManager.resolveActivity(intent, 0)
                if (resolved != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            } else {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
            continue
        }
    }
}

fun isMiuiOnboardingNeeded(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val isMiui = manufacturer == "xiaomi" || manufacturer == "poco" || manufacturer == "redmi"
    if (!isMiui) return false

    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return !prefs.getBoolean("miui_onboarding_complete", false)
}

fun markMiuiOnboardingComplete(context: Context) {
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("miui_onboarding_complete", true)
        .apply()
}
