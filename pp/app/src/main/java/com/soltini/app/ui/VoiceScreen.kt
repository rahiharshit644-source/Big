package com.soltini.app.ui

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soltini.app.network.ConnectionState
import com.soltini.app.ui.components.MyraOrbState
import com.soltini.app.ui.components.MyraStatusOrb
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassQuickActionChip
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*

@Composable
fun VoiceScreen(
    viewModel: MainViewModel,
    onOpenLogs: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenHomeAutomation: () -> Unit = {},
    onOpenApisHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val micAmplitude by viewModel.micAmplitude.collectAsStateWithLifecycle()
    val speakerAmplitude by viewModel.speakerAmplitude.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val hasMicPermission by viewModel.hasMicPermission.collectAsStateWithLifecycle()
    val isLitePaused by viewModel.isLitePaused.collectAsStateWithLifecycle()
    val isHardPaused by viewModel.isHardPaused.collectAsStateWithLifecycle()
    val isPaused = isLitePaused || isHardPaused

    LaunchedEffect(hasMicPermission, connectionState, isPaused) {
        if (hasMicPermission && !isPaused && (connectionState == ConnectionState.Disconnected || connectionState == ConnectionState.Error)) {
            viewModel.connect()
        }
    }

    val isLive = connectionState == ConnectionState.Live

    // Subtle tactile haptic cue when Myra transitions between listening and speaking
    var wasSpeaking by remember { mutableStateOf(false) }
    LaunchedEffect(isSpeaking) {
        if (isLive && isSpeaking != wasSpeaking) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            wasSpeaking = isSpeaking
        }
    }

    var hasAssistant by remember { mutableStateOf(checkAssistantRoleHome(context)) }
    var hasAccessibility by remember { mutableStateOf(checkAccessibilityHome(context)) }
    var hasBattery by remember { mutableStateOf(checkBatteryHome(context)) }

    LifecycleResumeEffect(Unit) {
        hasAssistant = checkAssistantRoleHome(context)
        hasAccessibility = checkAccessibilityHome(context)
        hasBattery = checkBatteryHome(context)
        onPauseOrDispose {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoftBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Soft pastel ambient light aura
        AmbientBackground(isLive = isLive, isSpeaking = isSpeaking)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp)
        ) {
            // Top bar
            item {
                val appSettings = remember { com.soltini.app.settings.AppSettings(context) }
                TopBar(
                    connectionState = connectionState,
                    appName = appSettings.appDisplayName,
                    onOpenLogs = onOpenLogs,
                    onOpenSettings = onOpenSettings,
                    onOpenHomeAutomation = onOpenHomeAutomation,
                    onOpenApisHub = onOpenApisHub,
                    onReconnect = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.connect()
                    }
                )
            }

            // Hero Iridescent Glass Orb Card
            item {
                HeroOrbGlassCard(
                    connectionState = connectionState,
                    isSpeaking = isSpeaking,
                    isMuted = isMuted,
                    isPaused = isPaused,
                    micAmplitude = micAmplitude,
                    speakerAmplitude = speakerAmplitude,
                    onToggleMute = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.toggleMute()
                    }
                )
            }

            // Banking Pause Mode Card
            item {
                DualPauseModeCard(
                    isLitePaused = isLitePaused,
                    isHardPaused = isHardPaused,
                    onToggleLite = { viewModel.toggleLitePauseMode() },
                    onToggleHard = { viewModel.toggleHardPauseMode() }
                )
            }

            // Quick voice tips / command chips
            item {
                CommandChipsRow(
                    onSelectCommand = { cmd -> viewModel.sendUserMessage(cmd) }
                )
            }

            // Background persistence warning card if battery optimization is killing the service
            if (!hasBattery) {
                item {
                    BackgroundPersistenceAlert(context = context)
                }
            }

            // Agent capabilities status cards
            item {
                AgentStatusRow(
                    hasAssistant = hasAssistant,
                    hasAccessibility = hasAccessibility,
                    hasBattery = hasBattery,
                    onOpenSettings = onOpenSettings,
                    onFixBattery = {
                        try {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            ).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val appDetails = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                ).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(appDetails)
                            } catch (_: Exception) {}
                        }
                    }
                )
            }

            // Error card
            if (errorMessage != null) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut()
                    ) {
                        ErrorCard(errorMessage!!) { viewModel.connect() }
                    }
                }
            }

            // Transcript
            if (transcripts.isNotEmpty()) {
                item {
                    TranscriptHeader(onClear = { viewModel.clearTranscript() })
                }
                items(transcripts, key = { it.id }) { entry ->
                    TranscriptBubble(entry)
                }
            }
        }

        // Floating Bottom Message / Command Pill Input Bar
        ChatCommandInputBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            onSendCommand = { cmd ->
                viewModel.sendUserMessage(cmd)
            }
        )
    }
}

@Composable
private fun ChatCommandInputBar(
    modifier: Modifier = Modifier,
    onSendCommand: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier.shadow(
            elevation = 10.dp,
            shape = CircleShape,
            ambientColor = PrimaryPurple.copy(alpha = 0.18f),
            spotColor = PrimaryPurple.copy(alpha = 0.24f)
        ),
        shape = CircleShape,
        color = GlassCardSolid,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = BadgePurpleTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_command_input"),
                placeholder = {
                    Text(
                        text = "Ask Myra or type a command...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMidGray
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = TextCharcoal,
                    unfocusedTextColor = TextCharcoal,
                    cursorColor = BadgePurpleTint
                ),
                maxLines = 2,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextCharcoal)
            )

            Spacer(modifier = Modifier.width(6.dp))

            val canSend = text.isNotBlank()
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .testTag("send_command_button")
                    .size(44.dp)
                    .shadow(
                        elevation = if (canSend) 6.dp else 0.dp,
                        shape = CircleShape,
                        ambientColor = PrimaryPurple.copy(alpha = 0.3f),
                        spotColor = PrimaryPink.copy(alpha = 0.35f)
                    )
                    .clip(CircleShape)
                    .background(
                        brush = if (canSend) PrimaryHorizontalBrush else Brush.horizontalGradient(
                            listOf(GlassSurfaceSubtle, GlassSurfaceSubtle)
                        ),
                        shape = CircleShape
                    )
                    .clickable(enabled = canSend) {
                        val trimmed = text.trim()
                        if (trimmed.isNotBlank()) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSendCommand(trimmed)
                            text = ""
                            focusManager.clearFocus()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = if (canSend) Color.White else TextMidGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Ambient Background Glow ────────────────────────────────────────────────────
@Composable
private fun AmbientBackground(isLive: Boolean, isSpeaking: Boolean) {
    val inf = rememberInfiniteTransition(label = "bg")
    val shift by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgShift"
    )
    val glowColor = when {
        !isLive -> PrimaryPurple.copy(alpha = 0.10f)
        isSpeaking -> PrimaryPink.copy(alpha = 0.18f)
        else -> SecondaryMint.copy(alpha = 0.16f)
    }
    val animGlow by animateColorAsState(glowColor, tween(800), label = "glow")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width * (0.3f + shift * 0.4f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(animGlow, Color.Transparent),
                center = Offset(cx, size.height * 0.24f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = Offset(cx, size.height * 0.24f)
        )
    }
}

// ── Top Bar ────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    connectionState: ConnectionState,
    appName: String = "Myra",
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHomeAutomation: () -> Unit,
    onOpenApisHub: () -> Unit = {},
    onReconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Brand Identity
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = PrimaryPurple.copy(alpha = 0.25f),
                        spotColor = PrimaryPink.copy(alpha = 0.30f)
                    )
                    .clip(CircleShape)
                    .background(PrimaryGradientBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = appName,
                    color = TextCharcoal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Text(
                    text = "AI Companion",
                    color = TextMidGray,
                    fontSize = 12.sp
                )
            }
        }

        // Actions & Status Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(connectionState)

            if (connectionState == ConnectionState.Disconnected || connectionState == ConnectionState.Error) {
                SmallGlassIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Reconnect",
                    tint = BadgePurpleTint,
                    onClick = onReconnect
                )
            }
            SmallGlassIconButton(
                icon = Icons.Default.ElectricBolt,
                contentDescription = "Smart Home Automation",
                tint = SecondaryMintDark,
                onClick = onOpenHomeAutomation
            )
            SmallGlassIconButton(
                icon = Icons.Default.BugReport,
                contentDescription = "Logs",
                tint = TextMidGray,
                onClick = onOpenLogs
            )
            SmallGlassIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextMidGray,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun SmallGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = CircleShape,
        color = GlassCardWhite,
        modifier = Modifier
            .size(38.dp)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = PrimaryPurple.copy(alpha = 0.12f),
                spotColor = PrimaryPurple.copy(alpha = 0.16f)
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StatusPill(connectionState: ConnectionState) {
    val (bg, fg, label) = when (connectionState) {
        ConnectionState.Live -> Triple(BadgeMintBg, SecondaryMintDark, "Live")
        ConnectionState.Connecting -> Triple(BadgeYellowBg, BadgeYellowTint, "Connecting")
        ConnectionState.Reconnecting -> Triple(BadgeYellowBg, BadgeYellowTint, "Reconnecting")
        ConnectionState.Disconnected -> Triple(GlassSurfaceSubtle, TextMidGray, "Offline")
        ConnectionState.Error -> Triple(Color(0xFFFDE8E8), RedError, "Error")
    }
    GlassStatusBadge(
        text = label,
        isActive = connectionState == ConnectionState.Live,
        activeBg = bg,
        activeContent = fg,
        inactiveBg = bg,
        inactiveContent = fg
    )
}

// ── Hero Iridescent Glass Orb Card ──────────────────────────────────────────────
@Composable
private fun HeroOrbGlassCard(
    connectionState: ConnectionState,
    isSpeaking: Boolean,
    isMuted: Boolean,
    isPaused: Boolean,
    micAmplitude: Float,
    speakerAmplitude: Float,
    onToggleMute: () -> Unit
) {
    val isLive = connectionState == ConnectionState.Live && !isPaused
    val isConnecting = connectionState == ConnectionState.Connecting || connectionState == ConnectionState.Reconnecting

    // One clear state for the orb, the chip and the text, so it is obvious whether Myra is on / hearing / talking.
    val orbState = when {
        isPaused -> MyraOrbState.PAUSED
        isConnecting -> MyraOrbState.CONNECTING
        !isLive -> MyraOrbState.OFF
        isMuted -> MyraOrbState.MUTED
        isSpeaking -> MyraOrbState.SPEAKING
        else -> MyraOrbState.LISTENING
    }

    val statusTitle = when (orbState) {
        MyraOrbState.PAUSED -> "Session Paused"
        MyraOrbState.CONNECTING -> "Connecting…"
        MyraOrbState.OFF -> "Myra is OFF"
        MyraOrbState.MUTED -> "Microphone Muted"
        MyraOrbState.SPEAKING -> "Myra is Speaking"
        MyraOrbState.LISTENING -> "Myra is Listening"
    }

    val statusSubtitle = when (orbState) {
        MyraOrbState.PAUSED -> "Accessibility & microphone are safely paused"
        MyraOrbState.CONNECTING -> "Starting live voice, please wait"
        MyraOrbState.OFF -> "Tap reconnect in the top bar to start live voice"
        MyraOrbState.MUTED -> "Tap the mic button below to resume listening"
        MyraOrbState.SPEAKING -> "Delivering real-time voice response"
        MyraOrbState.LISTENING -> "Speak naturally or tap a shortcut below"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        elevation = 8.dp,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top badge row inside hero card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassStatusBadge(
                    text = orbState.chipLabel,
                    isActive = true,
                    activeBg = orbState.color.copy(alpha = 0.14f),
                    activeContent = orbState.color
                )
                Text(
                    text = "Floating Orb Ready",
                    color = TextMidGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Permanent main-screen orb: Curl Noise Orb (fixed colour, reacts only to voice level)
            MyraStatusOrb(
                state = orbState,
                level = if (!isLive || isMuted) 0f else (if (isSpeaking) speakerAmplitude else micAmplitude),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom row: Status text + Primary Gradient Mic Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusTitle,
                        color = TextCharcoal,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusSubtitle,
                        color = TextMidGray,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Circular Purple-Pink Gradient Mic Button with spring scale + haptics
                val micBrush = if (isMuted) {
                    Brush.linearGradient(listOf(Color(0xFFFDE8E8), Color(0xFFFCD2D2)))
                } else {
                    PrimaryGradientBrush
                }
                val micInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isMicPressed by micInteraction.collectIsPressedAsState()
                val micScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isMicPressed) 0.90f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "micScale"
                )
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .scale(micScale)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = PrimaryPurple.copy(alpha = 0.30f),
                            spotColor = PrimaryPink.copy(alpha = 0.35f)
                        )
                        .clip(CircleShape)
                        .background(micBrush)
                        .clickable(
                            interactionSource = micInteraction,
                            indication = null,
                            onClick = onToggleMute
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) RedError else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ── Command Chips Row ──────────────────────────────────────────────────────────
private val voiceCommands = listOf(
    "Chrome me ye form pura bhar do",
    "Shopping app khol ke saree dikhao",
    "Screen pe search bar pe tap karo",
    "Niche scroll karke aur options dikhao",
    "WhatsApp pe Rahul ko message bhej do",
    "Turn on living room light",
    "Dekho screen pe kya chal raha hai",
    "Lock the screen",
    "Open YouTube"
)

@Composable
private fun CommandChipsRow(
    onSelectCommand: (String) -> Unit = {}
) {
    Column {
        Text(
            text = "Quick Voice Shortcuts",
            color = TextCharcoal,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(voiceCommands) { cmd ->
                GlassQuickActionChip(
                    label = cmd,
                    icon = Icons.Default.AutoAwesome,
                    badgeBg = BadgePurpleBg,
                    badgeTint = BadgePurpleTint,
                    onClick = { onSelectCommand(cmd) }
                )
            }
        }
    }
}

// ── Agent Status Row ───────────────────────────────────────────────────────────
@Composable
private fun AgentStatusRow(
    hasAssistant: Boolean,
    hasAccessibility: Boolean,
    hasBattery: Boolean,
    onOpenSettings: () -> Unit,
    onFixBattery: () -> Unit
) {
    Column {
        Text(
            text = "Agent Capabilities",
            color = TextCharcoal,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AgentCapCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Assistant,
                title = "Assistant",
                subtitle = if (hasAssistant) "Active" else "Enable",
                isActive = hasAssistant,
                badgeBg = BadgePurpleBg,
                badgeTint = BadgePurpleTint,
                onClick = onOpenSettings
            )
            AgentCapCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Lock,
                title = "Lock Screen",
                subtitle = if (hasAccessibility) "Active" else "Enable",
                isActive = hasAccessibility,
                badgeBg = BadgePinkBg,
                badgeTint = BadgePinkTint,
                onClick = onOpenSettings
            )
            AgentCapCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ElectricBolt,
                title = "Background",
                subtitle = if (hasBattery) "Immune" else "Unrestrict",
                isActive = hasBattery,
                badgeBg = BadgeMintBg,
                badgeTint = BadgeMintTint,
                onClick = if (hasBattery) onOpenSettings else onFixBattery
            )
        }
    }
}

@Composable
private fun BackgroundPersistenceAlert(context: Context) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = 6.dp,
        containerColor = GlassCardWhite,
        onClick = {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val appDetails = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    ).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(appDetails)
                } catch (_: Exception) {}
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PastelIconBadge(
                icon = Icons.Default.ElectricBolt,
                backgroundColor = BadgeYellowBg,
                iconTint = BadgeYellowTint,
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background Running Restricted",
                    color = TextCharcoal,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Battery saver can pause Myra when the screen is off. Tap to set Unrestricted.",
                    color = TextMidGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                color = BadgeYellowBg,
                shape = CircleShape
            ) {
                Text(
                    text = "Fix",
                    color = BadgeYellowTint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AgentCapCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean,
    badgeBg: Color,
    badgeTint: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.height(114.dp),
        cornerRadius = 22.dp,
        elevation = 5.dp,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                PastelIconBadge(
                    icon = icon,
                    backgroundColor = badgeBg,
                    iconTint = badgeTint,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SecondaryMintDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    color = TextCharcoal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    color = if (isActive) SecondaryMintDark else TextMidGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Transcript ─────────────────────────────────────────────────────────────────
@Composable
private fun TranscriptHeader(onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Conversation",
            color = TextCharcoal,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            onClick = onClear,
            shape = CircleShape,
            color = BadgePurpleBg
        ) {
            Text(
                text = "Clear",
                color = BadgePurpleTint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
    val isUser = entry.sender == TranscriptEntry.Sender.USER
    val bubbleShape = RoundedCornerShape(
        topStart = if (isUser) 22.dp else 6.dp,
        topEnd = if (isUser) 6.dp else 22.dp,
        bottomStart = 22.dp,
        bottomEnd = 22.dp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .shadow(4.dp, CircleShape, ambientColor = PrimaryPurple.copy(alpha = 0.2f))
                    .clip(CircleShape)
                    .background(PrimaryGradientBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        val bubbleBrush: Brush = if (isUser) {
            PrimaryHorizontalBrush
        } else {
            Brush.linearGradient(listOf(GlassCardSolid, GlassCardSolid))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .shadow(
                    elevation = 5.dp,
                    shape = bubbleShape,
                    ambientColor = PrimaryPurple.copy(alpha = 0.14f),
                    spotColor = PrimaryPurple.copy(alpha = 0.18f)
                )
                .clip(bubbleShape)
                .background(bubbleBrush)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = entry.text,
                color = if (isUser) Color.White else TextCharcoal,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

// ── Error Card ─────────────────────────────────────────────────────────────────
@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = 5.dp,
        containerColor = Color(0xFFFFF5F5)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PastelIconBadge(
                icon = Icons.Default.WifiOff,
                backgroundColor = Color(0xFFFDE8E8),
                iconTint = RedError,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error,
                color = RedError,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = RedError,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Pause Mode Card ────────────────────────────────────────────────────────────
@Composable
fun DualPauseModeCard(
    isLitePaused: Boolean,
    isHardPaused: Boolean,
    onToggleLite: () -> Unit,
    onToggleHard: () -> Unit
) {
    val isAnyPaused = isLitePaused || isHardPaused

    val badgeBg = when {
        isHardPaused -> Color(0xFFFDE8E8)
        isLitePaused -> BadgeYellowBg
        else -> BadgePurpleBg
    }
    val badgeTint = when {
        isHardPaused -> RedError
        isLitePaused -> BadgeYellowTint
        else -> BadgePurpleTint
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevation = 6.dp,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PastelIconBadge(
                    icon = when {
                        isHardPaused -> Icons.Default.Warning
                        isLitePaused -> Icons.Default.Pause
                        else -> Icons.Default.Lock
                    },
                    backgroundColor = badgeBg,
                    iconTint = badgeTint,
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isHardPaused -> "Force Stopped — Open app to resume"
                            isLitePaused -> "Lite Paused — Accessibility silenced"
                            else -> "Banking & Privacy Shield"
                        },
                        color = TextCharcoal,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = when {
                            isHardPaused -> "Opening Settings → Accessibility on resume"
                            isLitePaused -> "Mic & AI off. Instant resume, no re-enable."
                            else -> "Pause overlay & mic while using banking apps"
                        },
                        color = if (isAnyPaused) badgeTint else TextMidGray,
                        fontSize = 12.sp
                    )
                }
                GlassStatusBadge(
                    text = if (isAnyPaused) "Paused" else "Protected",
                    isActive = !isAnyPaused
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two pill/card buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Lite Pause button
                val liteActive = isLitePaused
                Surface(
                    onClick = onToggleLite,
                    shape = RoundedCornerShape(18.dp),
                    color = if (liteActive) BadgeYellowBg else GlassSurfaceSubtle,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (liteActive) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (liteActive) BadgeYellowTint else BadgePurpleTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (liteActive) "Resume" else "Lite Pause",
                            color = TextCharcoal,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "GPay · PhonePe · Paytm",
                            color = TextMidGray,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Hard Pause button
                val hardActive = isHardPaused
                Surface(
                    onClick = onToggleHard,
                    shape = RoundedCornerShape(18.dp),
                    color = if (hardActive) Color(0xFFFDE8E8) else GlassSurfaceSubtle,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (hardActive) Icons.Default.PlayArrow else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hardActive) RedError else BadgePinkTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (hardActive) "Resume" else "Force Stop",
                            color = TextCharcoal,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "HDFC · ICICI · SBI",
                            color = TextMidGray,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ── Permission check helpers for home ─────────────────────────────────────────
private fun checkAssistantRoleHome(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    } else false
}

private fun checkBatteryHome(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true
}

private fun checkAccessibilityHome(context: Context): Boolean {
    val enabled = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.contains(context.packageName, ignoreCase = true)
}
