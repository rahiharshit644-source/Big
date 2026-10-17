package com.soltini.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.soltini.app.agent.SoltiniAccessibilityService
import com.soltini.app.memory2.MemoryItem
import com.soltini.app.memory2.MemoryType
import com.soltini.app.notifications.NotificationRepository
import com.soltini.app.notifications.SoltiniNotificationListener
import com.soltini.app.security.MemoryBackupManager
import com.soltini.app.security.VoiceBiometricsManager
import com.soltini.app.security.VoiceUnlockActivity
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.components.BadgeStyle
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassCodeBlockContainer
import com.soltini.app.ui.components.GlassGradientButton
import com.soltini.app.ui.components.GlassListItemCard
import com.soltini.app.ui.components.GlassPillInput
import com.soltini.app.ui.components.GlassSecondaryButton
import com.soltini.app.ui.components.GlassSectionHeader
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.GlassSwitch
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.AccentPurple
import com.soltini.app.ui.theme.GlassCardWhiteSolid
import com.soltini.app.ui.theme.GlassShadowAmbient
import com.soltini.app.ui.theme.GlassShadowSpot
import com.soltini.app.ui.theme.MintAccent
import com.soltini.app.ui.theme.MintAccentDark
import com.soltini.app.ui.theme.PastelBlueBadge
import com.soltini.app.ui.theme.PastelMintBadge
import com.soltini.app.ui.theme.PastelPinkBadge
import com.soltini.app.ui.theme.PastelPurpleBadge
import com.soltini.app.ui.theme.PastelYellowBadge
import com.soltini.app.ui.theme.PinkAccentEnd
import com.soltini.app.ui.theme.PrimaryGradientBrush
import com.soltini.app.ui.theme.PurpleAccentStart
import com.soltini.app.ui.theme.RedError
import com.soltini.app.ui.theme.SoftBackgroundBrush
import com.soltini.app.ui.theme.LocalMyraTokens
import com.soltini.app.ui.theme.TextCharcoal
import com.soltini.app.ui.theme.TextMidGray

private val AmberWarning = Color(0xFFD97706)
private val MiuiOrange = Color(0xFFEA580C)

enum class SettingsRoute {
    MAIN,
    PERMISSIONS,
    SCREEN_COMPANION,
    API,
    MEMORY,
    NOTIFICATIONS,
    ARCHITECTURE,
    STORAGE_RAG,
    SCHEDULED_TASKS,
    VOICE_ID,
    PUBLIC_APIS_HUB,
    ORB_STYLE,
    AGENT_LOG,
    APPEARANCE
}

@Composable
fun SettingsMenuCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    badgeColor: Color = PastelPurpleBadge,
    iconTint: Color = PurpleAccentStart,
    onClick: () -> Unit
) {
    GlassListItemCard(
        title = title,
        subtitle = subtitle,
        icon = icon,
        badgeColor = badgeColor,
        iconTint = iconTint,
        showChevron = true,
        onClick = onClick
    )
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel? = null,
    onOpenHomeAutomation: () -> Unit = {},
    onOpenVoiceEnrollment: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    var currentRoute by remember { mutableStateOf(SettingsRoute.MAIN) }

    BackHandler(enabled = currentRoute != SettingsRoute.MAIN) {
        currentRoute = SettingsRoute.MAIN
    }

    // --- Permission states ---
    var hasMic by remember { mutableStateOf(SettingsPermChecks.checkMic(context)) }
    var hasOverlay by remember { mutableStateOf(SettingsPermChecks.checkOverlay(context)) }
    var hasBattery by remember { mutableStateOf(SettingsPermChecks.checkBattery(context)) }
    var hasAccessibility by remember { mutableStateOf(SettingsPermChecks.checkAccessibility(context)) }
    var hasAssistantRole by remember { mutableStateOf(SettingsPermChecks.checkAssistantRole(context)) }
    var hasNotificationAccess by remember { mutableStateOf(SettingsPermChecks.checkNotificationAccess(context)) }
    var hasPhoneControl by remember { mutableStateOf(SettingsPermChecks.checkPhoneCallControl(context)) }
    var hasRestrictedSettings by remember { mutableStateOf(SettingsPermChecks.checkRestrictedSettings(context)) }
    val requiresAutoStart by remember { mutableStateOf(SettingsPermChecks.checkRequiresAutoStart(context)) }
    var autoStartRequested by remember {
        mutableStateOf(
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("autostart_requested", false)
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMic = granted }

    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPhoneControl = SettingsPermChecks.checkPhoneCallControl(context)
        if (hasPhoneControl) {
            com.soltini.app.telephony.CallNotificationManager.getInstance(context).startListening()
        }
    }

    val roleLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            hasAssistantRole = SettingsPermChecks.checkAssistantRole(context)
        }
    } else null

    // Refresh all states when user comes back from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMic = SettingsPermChecks.checkMic(context)
                hasOverlay = SettingsPermChecks.checkOverlay(context)
                hasBattery = SettingsPermChecks.checkBattery(context)
                hasAccessibility = SettingsPermChecks.checkAccessibility(context)
                hasAssistantRole = SettingsPermChecks.checkAssistantRole(context)
                hasNotificationAccess = SettingsPermChecks.checkNotificationAccess(context)
                hasPhoneControl = SettingsPermChecks.checkPhoneCallControl(context)
                if (hasNotificationAccess) {
                    SoltiniNotificationListener.rebindIfNeeded(context)
                }
                hasRestrictedSettings = SettingsPermChecks.checkRestrictedSettings(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalMyraTokens.current.backgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val selfScrolling = currentRoute == SettingsRoute.VOICE_ID ||
            currentRoute == SettingsRoute.SCHEDULED_TASKS ||
            currentRoute == SettingsRoute.ARCHITECTURE ||
            currentRoute == SettingsRoute.PUBLIC_APIS_HUB

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (selfScrolling) Modifier else Modifier.verticalScroll(scrollState))
                .padding(
                    horizontal = if (selfScrolling) 0.dp else 18.dp,
                    vertical = if (selfScrolling) 8.dp else 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Navigation Pill Bar (Back / Done) ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (selfScrolling) 18.dp else 0.dp,
                        end = if (selfScrolling) 18.dp else 0.dp,
                        bottom = 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            ambientColor = GlassShadowAmbient,
                            spotColor = GlassShadowSpot
                        )
                        .clip(CircleShape)
                        .background(GlassCardWhiteSolid)
                        .clickable {
                            if (currentRoute == SettingsRoute.MAIN) onBack()
                            else currentRoute = SettingsRoute.MAIN
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = PurpleAccentStart,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (currentRoute == SettingsRoute.MAIN) "Done" else "Back",
                            color = LocalMyraTokens.current.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }

                if (currentRoute != SettingsRoute.MAIN) {
                    val routeTitle = when (currentRoute) {
                        SettingsRoute.SCREEN_COMPANION -> "Screen Companion"
                        SettingsRoute.PERMISSIONS -> "Permissions & Agent"
                        SettingsRoute.VOICE_ID -> "Voice ID"
                        SettingsRoute.NOTIFICATIONS -> "Notifications"
                        SettingsRoute.API -> "API & Persona"
                        SettingsRoute.MEMORY -> "Memory System"
                        SettingsRoute.STORAGE_RAG -> "Storage & RAG"
                        SettingsRoute.ARCHITECTURE -> "Core Architecture"
                        SettingsRoute.SCHEDULED_TASKS -> "Scheduled Tasks"
                        SettingsRoute.PUBLIC_APIS_HUB -> "Public APIs Hub"
                        SettingsRoute.ORB_STYLE -> "Floating Orb"
                        SettingsRoute.AGENT_LOG -> "Agent Log"
                        SettingsRoute.APPEARANCE -> "Appearance & Themes"
                        else -> ""
                    }
                    GlassStatusBadge(text = routeTitle, style = BadgeStyle.PURPLE)
                }
            }

            if (currentRoute == SettingsRoute.MAIN) {
                SettingsHeader()
                Spacer(modifier = Modifier.height(22.dp))
            }

            when (currentRoute) {
                SettingsRoute.MAIN -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsMenuCard(
                            title = "Appearance & Themes",
                            subtitle = "Classic MYRA or Blue Glass",
                            icon = Icons.Default.Palette
                        ) { currentRoute = SettingsRoute.APPEARANCE }

                        SettingsMenuCard(
                            title = "1,750+ Public APIs Hub",
                            subtitle = "51 categories, zero-auth filter & live API playground",
                            icon = Icons.Default.Language,
                            badgeColor = PastelPurpleBadge,
                            iconTint = PurpleAccentStart
                        ) { currentRoute = SettingsRoute.PUBLIC_APIS_HUB }

                        SettingsMenuCard(
                            title = "Live Screen Companion",
                            subtitle = "Proactive visual context & privacy shield",
                            icon = Icons.Default.Visibility,
                            badgeColor = PastelMintBadge,
                            iconTint = MintAccentDark
                        ) { currentRoute = SettingsRoute.SCREEN_COMPANION }

                        SettingsMenuCard(
                            title = "Permissions & Agent",
                            subtitle = "Mic, overlay, accessibility & phone control",
                            icon = Icons.Default.Security,
                            badgeColor = PastelPurpleBadge,
                            iconTint = PurpleAccentStart
                        ) { currentRoute = SettingsRoute.PERMISSIONS }

                        SettingsMenuCard(
                            title = "Voice ID & Verification",
                            subtitle = "Biometric speaker gate & live test console",
                            icon = Icons.Default.RecordVoiceOver,
                            badgeColor = PastelPinkBadge,
                            iconTint = AccentPurple
                        ) { currentRoute = SettingsRoute.VOICE_ID }

                        SettingsMenuCard(
                            title = "Notifications & Direct Reply",
                            subtitle = "Read & reply to incoming messages by voice",
                            icon = Icons.Default.Notifications,
                            badgeColor = PastelYellowBadge,
                            iconTint = AmberWarning
                        ) { currentRoute = SettingsRoute.NOTIFICATIONS }

                        SettingsMenuCard(
                            title = "API & Persona",
                            subtitle = "Gemini key, assistant voice, persona & VAD",
                            icon = Icons.Default.Person,
                            badgeColor = PastelPurpleBadge,
                            iconTint = PurpleAccentStart
                        ) { currentRoute = SettingsRoute.API }

                        SettingsMenuCard(
                            title = "Memory System",
                            subtitle = "Facts, conversation logs & encrypted backup",
                            icon = Icons.Default.Storage,
                            badgeColor = PastelMintBadge,
                            iconTint = MintAccentDark
                        ) { currentRoute = SettingsRoute.MEMORY }

                        SettingsMenuCard(
                            title = "Storage & RAG Knowledge",
                            subtitle = "Authorized SAF folders & document search",
                            icon = Icons.Default.FolderOpen,
                            badgeColor = PastelBlueBadge,
                            iconTint = PurpleAccentStart
                        ) { currentRoute = SettingsRoute.STORAGE_RAG }

                        SettingsMenuCard(
                            title = "Modular Architecture & Registry",
                            subtitle = "Orchestrator, Memory 2.0 & 1,750+ APIs",
                            icon = Icons.Default.Memory,
                            badgeColor = PastelPinkBadge,
                            iconTint = AccentPurple
                        ) { currentRoute = SettingsRoute.ARCHITECTURE }

                        SettingsMenuCard(
                            title = "Scheduled Tasks & Experience",
                            subtitle = "Proactive routines, geofences & learned flows",
                            icon = Icons.Default.Schedule,
                            badgeColor = PastelYellowBadge,
                            iconTint = AmberWarning
                        ) { currentRoute = SettingsRoute.SCHEDULED_TASKS }

                        SettingsMenuCard(
                            title = "Floating Orb",
                            subtitle = "Choose the orb that floats over other apps",
                            icon = Icons.Default.AutoAwesome
                        ) { currentRoute = SettingsRoute.ORB_STYLE }

                        SettingsMenuCard(
                            title = "Agent Log",
                            subtitle = "Failures, learned routines & interrupted tasks",
                            icon = Icons.Default.Schedule
                        ) { currentRoute = SettingsRoute.AGENT_LOG }

                        SettingsMenuCard(
                            title = "Home Automation (ESP32)",
                            subtitle = "Smart home MQTT relays & firmware generator",
                            icon = Icons.Default.ElectricBolt,
                            badgeColor = PastelMintBadge,
                            iconTint = MintAccentDark
                        ) { onOpenHomeAutomation() }

                        Spacer(Modifier.height(90.dp))
                    }
                }

                SettingsRoute.PUBLIC_APIS_HUB -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        PublicApisHubScreen(
                            onBack = { currentRoute = SettingsRoute.MAIN },
                            onAskMyra = { prompt ->
                                viewModel?.sendUserMessage(prompt)
                                onBack()
                            }
                        )
                    }
                }

                SettingsRoute.VOICE_ID -> {
                    val voiceprintViewModel: com.soltini.app.voiceprint.ui.VoiceprintViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        com.soltini.app.voiceprint.ui.VoiceprintSettingsScreen(
                            viewModel = voiceprintViewModel,
                            onNavigateToEnrollment = { onOpenVoiceEnrollment() },
                            onNavigateBack = { currentRoute = SettingsRoute.MAIN }
                        )
                    }
                }

                SettingsRoute.SCHEDULED_TASKS -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ScheduledTasksScreen(
                            onBack = { currentRoute = SettingsRoute.MAIN }
                        )
                    }
                }

                SettingsRoute.STORAGE_RAG -> {
                    StorageRagSettingsSection(
                        context = context,
                        appSettings = viewModel?.getAppSettings()
                    )
                    Spacer(Modifier.height(14.dp))
                    ConversationMemoryCard(
                        context = context,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.ARCHITECTURE -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp)) {
                        ModularArchitectureScreen(
                            viewModel = viewModel,
                            context = context,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                SettingsRoute.APPEARANCE -> {
                    AppearanceSettingsSection()
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.ORB_STYLE -> {
                    OrbStyleSettingsSection(appSettings = viewModel?.getAppSettings())
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.AGENT_LOG -> {
                    AgentLogSection(context = context)
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.SCREEN_COMPANION -> {
                    ScreenCompanionSection(context = context, appSettings = viewModel?.getAppSettings())
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.NOTIFICATIONS -> {
                    NotificationsSettingsSection(context = context)
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.API -> {
                    AiConfigSection(
                        appSettings = viewModel?.getAppSettings(),
                        onSaveAndReconnect = { viewModel?.reconnect() }
                    )
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.MEMORY -> {
                    MemorySection(
                        memory2Engine = viewModel?.getMemory2Engine(),
                        appSettings = viewModel?.getAppSettings()
                    )
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.PERMISSIONS -> {
                    // ── Section: Required for Basic Function ────────────────
                    SectionLabel("Required Permissions")
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionCard(
                        icon = Icons.Default.Mic,
                        title = "Microphone",
                        description = "Capture your voice for real-time AI conversation.",
                        isGranted = hasMic,
                        tier = PermTier.REQUIRED,
                        onGrant = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionCard(
                        icon = Icons.Default.Layers,
                        title = "Display Over Other Apps",
                        description = "Show the Myra floating orb on top of any screen.",
                        isGranted = hasOverlay,
                        tier = PermTier.REQUIRED,
                        onGrant = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Section: Agentic (AI Agent Capabilities) ─────────────
                    SectionLabel("AI Agent Capabilities")
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionCard(
                        icon = Icons.Default.Assistant,
                        title = "Default Device Assistant",
                        description = "Replace Google Assistant / Gemini. Long-press home to trigger Myra — exactly like Gemini.",
                        isGranted = hasAssistantRole,
                        tier = PermTier.AGENT,
                        onGrant = {
                            var launched = false
                            if (!launched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                                    val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                                    roleLauncher?.launch(roleIntent)
                                    launched = true
                                } catch (_: Exception) {}
                            }
                            if (!launched) {
                                try {
                                    context.startActivity(
                                        Intent("com.android.settings.action.MANAGE_VOICE_INPUT_SETTINGS")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                    launched = true
                                } catch (_: Exception) {}
                            }
                            if (!launched) {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                    launched = true
                                } catch (_: Exception) {}
                            }
                            if (!launched) {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionCard(
                        icon = Icons.Default.Accessibility,
                        title = "Accessibility Service",
                        description = "Enables locking the device screen by voice. Myra only uses this to perform lock/unlock actions.",
                        isGranted = hasAccessibility,
                        tier = PermTier.AGENT,
                        onGrant = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionCard(
                        icon = Icons.Default.Notifications,
                        title = "Notification Access",
                        description = "Enables Myra to read incoming messages and notifications on-demand and reply directly.",
                        isGranted = hasNotificationAccess,
                        tier = PermTier.AGENT,
                        onGrant = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionCard(
                        icon = Icons.Default.Call,
                        title = "Phone & Call Control",
                        description = "Enables Myra to announce incoming callers aloud ('Boss, Rahul ka call aa raha hai') and answer or reject calls hands-free by voice.",
                        isGranted = hasPhoneControl,
                        tier = PermTier.AGENT,
                        onGrant = {
                            phonePermLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.READ_CALL_LOG,
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.ANSWER_PHONE_CALLS,
                                    Manifest.permission.CALL_PHONE
                                )
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    VoiceProfilesSection(context = context)

                    Spacer(modifier = Modifier.height(14.dp))
                    BankingProtectionCard(context = context)

                    Spacer(modifier = Modifier.height(14.dp))
                    BusyModeCard(context = context)

                    if (!hasRestrictedSettings && !hasAccessibility) {
                        Spacer(modifier = Modifier.height(12.dp))
                        RestrictedSettingsCard()
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Section: Background & System ────────────────────────
                    SectionLabel("Background & System")
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionCard(
                        icon = Icons.Default.BatteryAlert,
                        title = "Ignore Battery Optimization",
                        description = "Prevents Android from killing the assistant in the background.",
                        isGranted = hasBattery,
                        tier = PermTier.RECOMMENDED,
                        onGrant = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )

                    if (requiresAutoStart) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PermissionCard(
                            icon = Icons.Default.PowerSettingsNew,
                            title = "Auto-Start (${Build.MANUFACTURER})",
                            description = "Allow Myra to automatically start in the background on your device.",
                            isGranted = autoStartRequested,
                            tier = PermTier.RECOMMENDED,
                            onGrant = {
                                SettingsPermChecks.launchAutoStart(context)
                                autoStartRequested = true
                                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("autostart_requested", true).apply()
                            }
                        )
                    }

                    val isMiuiDevice = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                        Build.MANUFACTURER.equals("POCO", ignoreCase = true) ||
                        Build.MANUFACTURER.equals("Redmi", ignoreCase = true)

                    if (isMiuiDevice) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PermissionCard(
                            icon = Icons.Default.BatteryAlert,
                            title = "MIUI Battery — No Restrictions",
                            description = "Android battery optimization often isn't enough on MIUI. Set Battery saver to \"No restrictions\" in your app's settings.",
                            isGranted = false,
                            tier = PermTier.RECOMMENDED,
                            onGrant = {
                                val miuiIntent = Intent().apply {
                                    component = ComponentName(
                                        "com.miui.powerkeeper",
                                        "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
                                    )
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                val resolved = context.packageManager.resolveActivity(miuiIntent, 0)
                                if (resolved != null) {
                                    try { context.startActivity(miuiIntent) } catch (_: Exception) {}
                                } else {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        XiaomiActionsCard(context = context)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val totalRequired = 2
                    val grantedRequired = listOf(hasMic, hasOverlay).count { it }
                    val totalAgent = 3
                    val grantedAgent = listOf(hasAssistantRole, hasAccessibility, hasNotificationAccess).count { it }

                    PermissionSummaryBar(
                        grantedRequired = grantedRequired,
                        totalRequired = totalRequired,
                        grantedAgent = grantedAgent,
                        totalAgent = totalAgent
                    )

                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsHeader() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "scale"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        contentPadding = 22.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(scale)
                    .shadow(8.dp, CircleShape, ambientColor = GlassShadowAmbient, spotColor = GlassShadowSpot)
                    .clip(CircleShape)
                    .background(LocalMyraTokens.current.accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Myra Control Center",
                style = MaterialTheme.typography.headlineSmall,
                color = LocalMyraTokens.current.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Configure AI persona, voice biometrics, memory tiers,\nsmart home relays, and autonomous capabilities.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalMyraTokens.current.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// ── Section Label ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LocalMyraTokens.current.accentGradient)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text,
            color = LocalMyraTokens.current.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

// ── Permission Tiers ─────────────────────────────────────────────────────────
enum class PermTier { REQUIRED, AGENT, RECOMMENDED }

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    tier: PermTier,
    onGrant: () -> Unit
) {
    val (badgeBg, iconTint, badgeStyle, tierLabel) = when (tier) {
        PermTier.REQUIRED -> Quad(Color(0xFFFFE5EA), RedError, BadgeStyle.ERROR, "Required")
        PermTier.AGENT -> Quad(PastelPurpleBadge, PurpleAccentStart, BadgeStyle.PURPLE, "Agent")
        PermTier.RECOMMENDED -> Quad(PastelYellowBadge, AmberWarning, BadgeStyle.WARNING, "Recommended")
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PastelIconBadge(
                icon = if (isGranted) Icons.Default.CheckCircle else icon,
                badgeColor = if (isGranted) PastelMintBadge else badgeBg,
                iconTint = if (isGranted) MintAccentDark else iconTint,
                size = 48.dp,
                iconSize = 24.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassStatusBadge(
                        text = if (isGranted) "Active" else tierLabel,
                        style = if (isGranted) BadgeStyle.ACTIVE else badgeStyle
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.width(10.dp))
                GlassGradientButton(
                    text = "Grant",
                    onClick = onGrant
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ── Restricted Settings Warning ───────────────────────────────────────────────
@Composable
private fun RestrictedSettingsCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PastelIconBadge(
                icon = Icons.Default.Warning,
                badgeColor = PastelYellowBadge,
                iconTint = AmberWarning,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "Enable Restricted Settings",
                    color = AmberWarning,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "On Android 13+, sideloaded apps need extra permission to access Accessibility.\n\n" +
                        "1. Go to Settings → Apps → Myra\n" +
                        "2. Tap the three-dot menu (⋮) at the top right\n" +
                        "3. Tap \"Allow restricted settings\"\n" +
                        "4. Come back and tap Grant on Accessibility Service above.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ── Xiaomi Actions Card ───────────────────────────────────────────────────────
@Composable
private fun XiaomiActionsCard(context: Context) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Warning,
                badgeColor = PastelYellowBadge,
                iconTint = MiuiOrange,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "MIUI Background Kill Protection",
                color = MiuiOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your phone aggressively kills background apps. Use the buttons below to open each setting:",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        MiuiActionRow(
            label = "1. Open Autostart Settings",
            hint = "Security → Permissions → Autostart → toggle ON",
            buttonLabel = "Open"
        ) {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved != null) {
                try { context.startActivity(intent) } catch (_: Exception) {}
            } else {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MiuiActionRow(
            label = "2. Lock in Recent Apps",
            hint = "Recents → Long-press Myra card → tap 🔒 lock icon",
            buttonLabel = null
        ) {}

        Spacer(modifier = Modifier.height(12.dp))

        GlassSecondaryButton(
            text = "Re-open Xiaomi Setup Guide",
            icon = Icons.Default.AutoAwesome,
            onClick = {
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("miui_onboarding_complete", false).apply()
                Toast.makeText(
                    context,
                    "Relaunch Myra to see the full setup guide",
                    Toast.LENGTH_LONG
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
            containerColor = PastelYellowBadge,
            contentColor = MiuiOrange
        )
    }
}

@Composable
private fun MiuiActionRow(
    label: String,
    hint: String,
    buttonLabel: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = LocalMyraTokens.current.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (buttonLabel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            GlassSecondaryButton(
                text = buttonLabel,
                onClick = onClick
            )
        }
    }
}

// ── Summary Bar ───────────────────────────────────────────────────────────────
@Composable
private fun PermissionSummaryBar(
    grantedRequired: Int,
    totalRequired: Int,
    grantedAgent: Int,
    totalAgent: Int
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem(
                label = "Core Permissions",
                granted = grantedRequired,
                total = totalRequired,
                color = if (grantedRequired == totalRequired) MintAccentDark else RedError
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(42.dp)
                    .background(Color(0xFFE4DCF5))
            )
            SummaryItem(
                label = "Agent Capabilities",
                granted = grantedAgent,
                total = totalAgent,
                color = if (grantedAgent == totalAgent) MintAccentDark else PurpleAccentStart
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, granted: Int, total: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$granted / $total",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Text(label, color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
    }
}

// ── Permission Check Helpers ──────────────────────────────────────────────────
object SettingsPermChecks {

    fun checkPhoneCallControl(context: Context): Boolean {
        val phoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val answerCalls = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        return phoneState && contacts && answerCalls
    }

    fun checkNotificationAccess(context: Context): Boolean {
        return androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun checkMic(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun checkOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun checkBattery(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    fun checkAccessibility(context: Context): Boolean {
        if (SoltiniAccessibilityService.isActive()) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(context.packageName, ignoreCase = true)
    }

    fun checkAssistantRole(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        } else {
            val setting = Settings.Secure.getString(
                context.contentResolver,
                "assistant"
            )
            setting?.contains(context.packageName, ignoreCase = true) == true
        }
    }

    fun checkRestrictedSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkAccessibility(context)
        } else {
            true
        }
    }

    fun checkRequiresAutoStart(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = Intent()
        when (manufacturer) {
            "xiaomi" -> intent.component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            "oppo" -> intent.component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            "vivo" -> intent.component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            "huawei", "honor" -> intent.component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
            else -> return false
        }
        return context.packageManager.resolveActivity(intent, 0) != null
    }

    fun launchAutoStart(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = Intent()
        when (manufacturer) {
            "xiaomi" -> intent.component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            "oppo" -> intent.component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            "vivo" -> intent.component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            "huawei", "honor" -> intent.component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        }
        try { context.startActivity(intent) } catch (_: Exception) {}
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Configuration Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiConfigSection(
    appSettings: AppSettings?,
    onSaveAndReconnect: () -> Unit
) {
    val devName = appSettings?.developerName ?: AppSettings.DEFAULT_DEVELOPER_NAME
    var username by remember { mutableStateOf(appSettings?.ownerUsername ?: "") }
    var appName by remember { mutableStateOf(appSettings?.appDisplayName ?: AppSettings.DEFAULT_APP_NAME) }
    var apiKey by remember { mutableStateOf(appSettings?.geminiApiKey ?: "") }
    var browserUseServerUrl by remember { mutableStateOf(appSettings?.browserUseServerUrl ?: "") }
    var persona by remember { mutableStateOf(appSettings?.aiPersona ?: AppSettings.DEFAULT_PERSONA) }
    var voice by remember { mutableStateOf(appSettings?.voiceName ?: AppSettings.DEFAULT_VOICE) }
    var isDataSaver by remember { mutableStateOf(appSettings?.isDataSaverEnabled ?: true) }
    var vadSens by remember { mutableStateOf(appSettings?.vadSensitivity ?: AppSettings.SENSITIVITY_BALANCED) }
    var micGain by remember { mutableStateOf(appSettings?.micGain ?: 1.5f) }
    var autoSleep by remember { mutableStateOf(appSettings?.autoSleepMinutes ?: 3) }
    var isWakeWord by remember { mutableStateOf(appSettings?.isWakeWordEnabled ?: true) }
    var isSilentExec by remember { mutableStateOf(appSettings?.isSilentExecutionMode ?: false) }
    var isRiskConfirm by remember { mutableStateOf(appSettings?.isAgentRiskConfirmEnabled ?: true) }
    var isOtpReader by remember { mutableStateOf(appSettings?.isOtpReaderEnabled ?: false) }
    var isHabitLearning by remember { mutableStateOf(appSettings?.isHabitLearningEnabled ?: true) }
    var isScreenRecMode by remember { mutableStateOf(appSettings?.isScreenRecordingModeEnabled ?: false) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    SectionLabel("AI Configuration & Persona")
    Spacer(Modifier.height(12.dp))

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 26.dp,
        contentPadding = 20.dp
    ) {
        // ── Developer & Creator Identity (permanent — not editable) ──────
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Person,
                badgeColor = PastelPurpleBadge,
                iconTint = PurpleAccentStart,
                size = 36.dp,
                iconSize = 18.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Developer / Creator Name", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Kept strictly confidential. When asked \"Kisne banaya?\", \"Developer kaun hai?\" or \"Who made this app?\", the AI will reveal this name. Permanent — cannot be changed from this screen.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF6F3FC),
            shape = CircleShape
        ) {
            Text(
                devName,
                color = LocalMyraTokens.current.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        // ── Username ──────────────────────────────────────────────────
        Text("Username", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "What the AI calls you during conversation.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        GlassPillInput(
            value = username,
            onValueChange = {
                username = it
                saved = false
                persona = AppSettings.generatePersona(devName, appName, it.ifBlank { "Boss" })
            },
            placeholder = "Your name (e.g. Boss)",
            leadingIcon = Icons.Default.Person
        )

        Spacer(Modifier.height(18.dp))

        // ── Assistant / App Name ─────────────────────────────────────────
        Text("Assistant / App Name", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "The name of your personal AI companion.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        GlassPillInput(
            value = appName,
            onValueChange = {
                appName = it
                saved = false
                persona = AppSettings.generatePersona(devName, it, username.ifBlank { "Boss" })
            },
            placeholder = "e.g. Myra",
            leadingIcon = Icons.Default.Assistant
        )

        Spacer(Modifier.height(18.dp))

        // ── Browser-Use Bridge Server ────────────────────────────────────
        Text("Browser-Use Server URL", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Optional. Address of your soltini_bridge_server.py running on a PC/VPS with Browser-Use + Playwright installed (e.g. http://192.168.1.23:8787). Leave blank to disable.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        GlassPillInput(
            value = browserUseServerUrl,
            onValueChange = {
                browserUseServerUrl = it
                saved = false
            },
            placeholder = "http://192.168.1.23:8787",
            leadingIcon = Icons.Default.Language
        )

        Spacer(Modifier.height(18.dp))

        // ── API Key ─────────────────────────────────────────────────────
        Text("Gemini API Key", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        GlassPillInput(
            value = apiKey,
            onValueChange = { apiKey = it; saved = false },
            placeholder = "Paste your API key here…",
            leadingIcon = Icons.Default.Key,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingContent = {
                IconButton(
                    onClick = { showKey = !showKey },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle key visibility",
                        tint = TextMidGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (apiKey.isBlank()) "Using built-in key" else "Custom API key configured",
            color = if (apiKey.isBlank()) TextMidGray else MintAccentDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(18.dp))

        // ── AI Persona ──────────────────────────────────────────────────
        Text("AI Persona", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = persona,
            onValueChange = { persona = it; saved = false },
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp),
            placeholder = { Text("Describe Myra's personality…", color = LocalMyraTokens.current.textSecondary, fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PurpleAccentStart,
                unfocusedBorderColor = Color(0xFFE4DCF5),
                focusedTextColor = TextCharcoal,
                unfocusedTextColor = TextCharcoal,
                focusedContainerColor = Color(0xFFFAF8FE),
                unfocusedContainerColor = Color(0xFFFAF8FE),
                cursorColor = PurpleAccentStart
            ),
            shape = RoundedCornerShape(18.dp)
        )
        Text(
            "${persona.length} chars",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
        )

        Spacer(Modifier.height(18.dp))

        // ── Voice Model ─────────────────────────────────────────────────
        Text("Voice", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppSettings.AVAILABLE_VOICES.forEach { v ->
                GlassSelectablePill(
                    label = v,
                    selected = v == voice,
                    onClick = { voice = v; saved = false }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Mic Volume Soft Boost ───────────────────────────────────────
        Text("Microphone Volume Boost", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Amplifies soft speech so Gemini hears you loud and clear every time.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                1.0f to "1.0x (Normal)",
                1.5f to "1.5x (Boosted)",
                2.0f to "2.0x (Loud)"
            ).forEach { (g, label) ->
                GlassSelectablePill(
                    label = label,
                    selected = micGain == g,
                    onClick = { micGain = g; saved = false }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Auto-Sleep Idle Timeout ─────────────────────────────────────
        Text("Auto-Sleep Idle Timeout", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Goes to standby after inactivity: Gemini disconnects and uses no data. Say \"Myra\" to wake her.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                1 to "1 Min",
                3 to "3 Mins",
                5 to "5 Mins",
                0 to "Disabled"
            ).forEach { (mins, label) ->
                GlassSelectablePill(
                    label = label,
                    selected = autoSleep == mins,
                    onClick = { autoSleep = mins; saved = false }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Wake Word ("Myra") ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Wake Word (\"Hey Myra\")", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "In standby an offline listener waits for \"Myra\" and wakes her. The mic stays on locally; nothing is sent until she wakes.",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
                )
            }
            GlassSwitch(
                checked = isWakeWord,
                onCheckedChange = { isWakeWord = it; saved = false }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Silent Execution Mode ───────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Silent Execution", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Myra does not narrate while carrying out tasks. Normal conversation stays audible.",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
                )
            }
            GlassSwitch(
                checked = isSilentExec,
                onCheckedChange = { isSilentExec = it; saved = false }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Agent: confirm risky actions ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Agent: Ask Before Risky Steps", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "The phone agent pauses and asks you before payments, orders, deletions and password/PIN fields. Recommended ON.",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
                )
            }
            GlassSwitch(
                checked = isRiskConfirm,
                onCheckedChange = { isRiskConfirm = it; saved = false }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Agent: OTP reader (opt-in) ──────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("OTP Reader (opt-in)", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Lets Myra read a fresh one-time code from your notifications (last 2 minutes) when you ask, or while filling a login. Needs notification access. Off by default.",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
                )
            }
            GlassSwitch(
                checked = isOtpReader,
                onCheckedChange = { isOtpReader = it; saved = false }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Learn my habits (favourites & routines) ─────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Learn My Habits", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Myra notices favourite songs, apps and contacts and daily routines from what you ask her to do, and saves them as memories you can see, edit or delete in Memory. Stays on this phone.",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
                )
            }
            GlassSwitch(
                checked = isHabitLearning,
                onCheckedChange = { isHabitLearning = it; saved = false }
            )
        }

        Spacer(Modifier.height(16.dp))

        AppIdentityRow()

        Spacer(Modifier.height(18.dp))

        // ── Data Saver & Voice Activity Detection (VAD) ─────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Data Saver (Silence Suppression)", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Stops streaming mic data during silence.", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
            }
            GlassSwitch(
                checked = isDataSaver,
                onCheckedChange = { isDataSaver = it; saved = false }
            )
        }

        if (isDataSaver) {
            Spacer(Modifier.height(12.dp))
            Text("VAD Mic Sensitivity", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppSettings.AVAILABLE_SENSITIVITIES.forEach { s ->
                    GlassSelectablePill(
                        label = s,
                        selected = s == vadSens,
                        onClick = { vadSens = s; saved = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Screen Recording Mode ───────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Screen Recording Mode", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Fixes echo loops when recording internal audio.",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp
                )
            }
            GlassSwitch(
                checked = isScreenRecMode,
                onCheckedChange = { isScreenRecMode = it }
            )
        }

        Spacer(Modifier.height(22.dp))

        // ── Save & Reconnect button ──────────────────────────────────────
        GlassGradientButton(
            text = if (saved) "Saved & Reconnecting…" else "Save & Reconnect",
            icon = if (saved) Icons.Default.Check else null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                appSettings?.ownerUsername = username
                appSettings?.appDisplayName = appName
                appSettings?.geminiApiKey = apiKey
                appSettings?.browserUseServerUrl = browserUseServerUrl
                val resolvedAddr = appSettings?.currentAddressName() ?: "Boss"
                appSettings?.aiPersona = AppSettings.generatePersona(devName, appName, resolvedAddr)
                appSettings?.voiceName = voice
                appSettings?.isDataSaverEnabled = isDataSaver
                appSettings?.vadSensitivity = vadSens
                appSettings?.micGain = micGain
                appSettings?.autoSleepMinutes = autoSleep
                appSettings?.isWakeWordEnabled = isWakeWord
                appSettings?.isSilentExecutionMode = isSilentExec
                appSettings?.isAgentRiskConfirmEnabled = isRiskConfirm
                appSettings?.isOtpReaderEnabled = isOtpReader
                appSettings?.isHabitLearningEnabled = isHabitLearning
                appSettings?.isScreenRecordingModeEnabled = isScreenRecMode
                username = appSettings?.ownerUsername ?: ""
                persona = appSettings?.aiPersona ?: persona
                saved = true
                onSaveAndReconnect()
            }
        )

        Text(
            "Settings saved to device. A new Gemini session will start.",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GlassSelectablePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .shadow(
                elevation = if (selected) 4.dp else 1.dp,
                shape = CircleShape,
                ambientColor = GlassShadowAmbient,
                spotColor = GlassShadowSpot
            )
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(LocalMyraTokens.current.accentGradient)
                else Modifier.background(Color(0xFFF6F3FC))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextCharcoal,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Memory Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MemorySection(
    memory2Engine: com.soltini.app.memory2.Memory2Engine? = null,
    appSettings: AppSettings? = null
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupPassphrase by remember { mutableStateOf("") }
    var restorePendingUri by remember { mutableStateOf<Uri?>(null) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var restorePasswordInput by remember { mutableStateOf("") }

    var unifiedMemories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var sessionLogEntries by remember { mutableStateOf(memory2Engine?.db?.getRecentSessionLogEntries(50) ?: emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<MemoryType?>(null) }
    var editingItem by remember { mutableStateOf<MemoryItem?>(null) }
    var editTextValue by remember { mutableStateOf("") }
    var editTypeValue by remember { mutableStateOf(MemoryType.FACT) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addTextValue by remember { mutableStateOf("") }
    var addTypeValue by remember { mutableStateOf(MemoryType.FACT) }

    fun refresh() {
        unifiedMemories = if (searchQuery.isNotBlank()) {
            memory2Engine?.searchMemoriesUnified(searchQuery) ?: emptyList()
        } else {
            memory2Engine?.getAllMemoriesUnified() ?: emptyList()
        }
        sessionLogEntries = memory2Engine?.db?.getRecentSessionLogEntries(50) ?: emptyList()
    }

    DisposableEffect(Unit) {
        refresh()
        onDispose { }
    }

    fun executeRestore(uri: Uri, pass: String?) {
        val m2Db = memory2Engine?.db
        if (m2Db != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val stats = MemoryBackupManager.importFromStream(inputStream, m2Db, pass)
                    if (stats.isSuccess) {
                        backupStatusMessage = "Restored ${stats.totalCount} records (Facts: ${stats.longTermCount}, Notes: ${stats.knowledgeCount}, Legacy: ${stats.legacyMigratedCount})"
                        refresh()
                    } else {
                        backupStatusMessage = "Restore failed: ${stats.errorMessage}"
                    }
                }
            } catch (e: Exception) {
                backupStatusMessage = "Restore error: ${e.message}"
            }
        } else {
            backupStatusMessage = "Memory engine not initialized."
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            restorePendingUri = uri
            restorePasswordInput = ""
            showRestorePasswordDialog = true
        }
    }

    SectionLabel("Memory Engine & Knowledge")
    Spacer(Modifier.height(12.dp))

    // Unified Memory Engine stats card
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Storage,
                badgeColor = PastelPurpleBadge,
                iconTint = PurpleAccentStart,
                size = 44.dp
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Memory Engine", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Facts + conversation history, deduplicated automatically", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PastelPurpleBadge.copy(alpha = 0.65f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${unifiedMemories.size}", color = PurpleAccentStart, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Saved Facts", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PastelMintBadge.copy(alpha = 0.65f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${sessionLogEntries.size}", color = MintAccentDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Past Conversations", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
                }
            }
        }

        if (sessionLogEntries.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Recently discussed:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sessionLogEntries.take(5).forEach { entry ->
                    Text(
                        "• ${entry.topic}",
                        color = LocalMyraTokens.current.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // Stored Memories Card & Interactive Memory Manager
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.Storage,
                    badgeColor = PastelPinkBadge,
                    iconTint = AccentPurple,
                    size = 40.dp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Memory Manager", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${unifiedMemories.size} items stored", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassGradientButton(
                    text = "Add",
                    icon = Icons.Default.Add,
                    onClick = {
                        addTextValue = ""
                        addTypeValue = MemoryType.FACT
                        showAddDialog = true
                    }
                )

                IconButton(
                    onClick = {
                        expanded = !expanded
                        if (expanded) refresh()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle memory list",
                        tint = TextMidGray
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                // Search Bar
                GlassPillInput(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        unifiedMemories = if (it.isNotBlank()) {
                            memory2Engine?.searchMemoriesUnified(it) ?: emptyList()
                        } else {
                            memory2Engine?.getAllMemoriesUnified() ?: emptyList()
                        }
                    },
                    placeholder = "Search memories, facts, preferences...",
                    leadingIcon = Icons.Default.Search,
                    trailingContent = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    refresh()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMidGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                val filterTypes = listOf(
                    null to "All",
                    MemoryType.PREFERENCE to "Preferences",
                    MemoryType.CORRECTION to "Corrections",
                    MemoryType.USER_PROFILE to "Profile",
                    MemoryType.FACT to "Facts",
                    MemoryType.PROJECT to "Projects",
                    MemoryType.ROUTINE to "Routines"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterTypes.forEach { (type, label) ->
                        GlassSelectablePill(
                            label = label,
                            selected = selectedTypeFilter == type,
                            onClick = { selectedTypeFilter = type }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val displayItems = unifiedMemories.filter {
                    selectedTypeFilter == null || it.type == selectedTypeFilter
                }

                if (displayItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (searchQuery.isNotBlank()) "No memories matching \"$searchQuery\""
                            else "No memories recorded yet in this category.",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayItems.take(50).forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFF6F3FC),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            GlassStatusBadge(text = item.type.name, style = BadgeStyle.PURPLE)

                                            if (item.sensitivityLevel.equals("SENSITIVE", ignoreCase = true)) {
                                                GlassStatusBadge(text = "Sensitive", style = BadgeStyle.ERROR)
                                            }

                                            if (item.userConfirmed) {
                                                GlassStatusBadge(text = "✓ Verified", style = BadgeStyle.ACTIVE)
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            item.text,
                                            color = LocalMyraTokens.current.textPrimary,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                editingItem = item
                                                editTextValue = item.text
                                                editTypeValue = item.type
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Memory", tint = PurpleAccentStart, modifier = Modifier.size(16.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                memory2Engine?.deleteMemory(item.id)
                                                refresh()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = RedError, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (displayItems.isNotEmpty()) {
                    if (showClearConfirm) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassSecondaryButton(
                                text = "Yes, clear all",
                                onClick = {
                                    memory2Engine?.clearAllMemories()
                                    showClearConfirm = false
                                    refresh()
                                },
                                modifier = Modifier.weight(1f),
                                containerColor = Color(0xFFFFE5EA),
                                contentColor = RedError
                            )
                            GlassSecondaryButton(
                                text = "Cancel",
                                onClick = { showClearConfirm = false },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        GlassSecondaryButton(
                            text = "Clear All Memories",
                            icon = Icons.Default.Delete,
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0xFFFFE5EA),
                            contentColor = RedError
                        )
                    }
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = GlassCardWhiteSolid,
            shape = RoundedCornerShape(24.dp),
            confirmButton = {
                GlassGradientButton(
                    text = "Save Memory",
                    onClick = {
                        if (addTextValue.isNotBlank()) {
                            memory2Engine?.saveExplicitMemory(addTextValue.trim(), addTypeValue, confirmed = true)
                            refresh()
                        }
                        showAddDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = LocalMyraTokens.current.textSecondary)
                }
            },
            title = { Text("Add Memory or Rule", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Record a personal fact, preference, user rule, or project detail for MYRA:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = addTextValue,
                        onValueChange = { addTextValue = it },
                        placeholder = { Text("e.g. User prefers concise answers", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Text("Memory Category:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(MemoryType.PREFERENCE, MemoryType.FACT, MemoryType.CORRECTION, MemoryType.USER_PROFILE, MemoryType.PROJECT).forEach { t ->
                            GlassSelectablePill(
                                label = t.name,
                                selected = addTypeValue == t,
                                onClick = { addTypeValue = t }
                            )
                        }
                    }
                }
            }
        )
    }

    // Edit Memory Dialog
    if (editingItem != null) {
        val item = editingItem!!
        AlertDialog(
            onDismissRequest = { editingItem = null },
            containerColor = GlassCardWhiteSolid,
            shape = RoundedCornerShape(24.dp),
            confirmButton = {
                GlassGradientButton(
                    text = "Update",
                    onClick = {
                        if (editTextValue.isNotBlank()) {
                            memory2Engine?.updateMemory(item.id, newContent = editTextValue.trim(), newType = editTypeValue)
                            refresh()
                        }
                        editingItem = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel", color = LocalMyraTokens.current.textSecondary)
                }
            },
            title = { Text("Edit Memory", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Modify this stored memory or rule:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editTextValue,
                        onValueChange = { editTextValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Text("Memory Category:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(MemoryType.PREFERENCE, MemoryType.FACT, MemoryType.CORRECTION, MemoryType.USER_PROFILE, MemoryType.PROJECT).forEach { t ->
                            GlassSelectablePill(
                                label = t.name,
                                selected = editTypeValue == t,
                                onClick = { editTypeValue = t }
                            )
                        }
                    }
                }
            }
        )
    }

    Spacer(Modifier.height(14.dp))

    // Encrypted Memory Backup & Restore Card
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Security,
                badgeColor = PastelMintBadge,
                iconTint = MintAccentDark,
                size = 42.dp
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Encrypted Memory Backup", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("AES-256 Android Keystore Protected Backup", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Export persistent memories (facts, personal context, knowledge, and experience) as an encrypted backup to restore across devices or sync with Google Drive.",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(12.dp))
        GlassPillInput(
            value = backupPassphrase,
            onValueChange = { backupPassphrase = it },
            placeholder = "Optional passphrase for PBKDF2 AES-256",
            leadingIcon = Icons.Default.Key
        )

        val isKeystoreAvailable = remember { MemoryBackupManager.isKeyStoreAvailable() }
        if (backupPassphrase.isBlank() && !isKeystoreAvailable) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color(0xFFFFE5EA),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RedError, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Warning: Hardware KeyStore unavailable on this device. Add a passphrase above for strong encryption.",
                        color = RedError,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        } else if (backupPassphrase.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "🛡️ Strong PBKDF2 (100,000 rounds) AES-256 encryption active.",
                color = MintAccentDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (backupStatusMessage != null) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = PastelPurpleBadge,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    backupStatusMessage!!,
                    color = PurpleAccentStart,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassGradientButton(
                text = "Backup Memory",
                icon = Icons.Default.Storage,
                modifier = Modifier.weight(1f),
                onClick = {
                    val m2Db = memory2Engine?.db
                    if (m2Db != null) {
                        val passToUse = backupPassphrase.ifBlank { null }
                        val res = MemoryBackupManager.exportEncryptedBackup(context, m2Db, passToUse)
                        if (res.isSuccess) {
                            val file = res.getOrNull()!!
                            backupStatusMessage = "Backup saved: ${file.name} (${file.length()} bytes)"
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "com.soltini.app.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Save Backup to Google Drive / Files"))
                            } catch (_: Exception) {}
                        } else {
                            backupStatusMessage = "Backup failed: ${res.exceptionOrNull()?.message}"
                        }
                    } else {
                        backupStatusMessage = "Memory database not initialized."
                    }
                }
            )

            GlassSecondaryButton(
                text = "Restore",
                icon = Icons.Default.FolderOpen,
                modifier = Modifier.weight(1f),
                onClick = { restoreLauncher.launch("*/*") }
            )
        }

        if (showRestorePasswordDialog && restorePendingUri != null) {
            AlertDialog(
                onDismissRequest = {
                    showRestorePasswordDialog = false
                    restorePendingUri = null
                },
                containerColor = GlassCardWhiteSolid,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Restore Memory Backup", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Agar ye backup kisi password se encrypt kiya gaya tha, toh password daaliye. Agar bina password (KeyStore) backup tha, toh khali chhod kar Restore dabayein.",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = restorePasswordInput,
                            onValueChange = { restorePasswordInput = it },
                            label = { Text("Backup Passphrase") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    GlassGradientButton(
                        text = "Restore",
                        onClick = {
                            val uri = restorePendingUri
                            val pass = restorePasswordInput.ifBlank { null }
                            showRestorePasswordDialog = false
                            restorePendingUri = null
                            if (uri != null) {
                                executeRestore(uri, pass)
                            }
                        }
                    )
                },
                dismissButton = {
                    TextButton(onClick = {
                        showRestorePasswordDialog = false
                        restorePendingUri = null
                    }) {
                        Text("Cancel", color = LocalMyraTokens.current.textSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun BankingProtectionCard(context: Context) {
    val appSettings = remember { AppSettings(context) }
    val voiceManager = remember { VoiceBiometricsManager.getInstance(context) }
    var autoHideEnabled by remember { mutableStateOf(appSettings.isBankingProtectionEnabled) }
    var isPaused by remember {
        mutableStateOf(
            appSettings.isBankingModePaused ||
                (com.soltini.app.overlay.OverlayService.getInstance()?.isHiddenForBanking() == true)
        )
    }
    var isVoiceLockEnrolled by remember { mutableStateOf(voiceManager.isEnrolled()) }
    var isVoiceLockActive by remember { mutableStateOf(voiceManager.isVoiceLockEnabled()) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Security,
                badgeColor = PastelMintBadge,
                iconTint = MintAccentDark,
                size = 46.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Banking App Protection", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Fixes banking apps (GPay, PhonePe, Paytm, YONO, SBI, HDFC) closing or blocking launch",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp, lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Auto-Hide Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Auto-Hide Overlay & A11y", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "Auto-hides floating orb & pauses screen reading whenever a banking app comes to foreground",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            GlassSwitch(
                checked = autoHideEnabled,
                onCheckedChange = { checked ->
                    autoHideEnabled = checked
                    appSettings.isBankingProtectionEnabled = checked
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Manual Pause Toggle Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Manual Pause (Banking Mode)", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    if (isPaused) "Overlay is PAUSED right now" else "Overlay is ACTIVE",
                    color = if (isPaused) AmberWarning else MintAccentDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            GlassSecondaryButton(
                text = if (isPaused) "Resume" else "Pause Now",
                onClick = {
                    if (voiceManager.isEnrolled() && voiceManager.isVoiceLockEnabled() && !voiceManager.isRecentAuthValid()) {
                        VoiceUnlockActivity.startForBankingToggle(context)
                    } else {
                        val newState = !isPaused
                        isPaused = newState
                        appSettings.isBankingModePaused = newState
                        com.soltini.app.overlay.OverlayService.getInstance()?.setHiddenForBanking(newState)
                    }
                },
                containerColor = if (isPaused) PastelMintBadge else PastelYellowBadge,
                contentColor = if (isPaused) MintAccentDark else AmberWarning
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Voice-Lock Banking Mode Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF6F3FC)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        PastelIconBadge(
                            icon = Icons.Default.Mic,
                            badgeColor = PastelPurpleBadge,
                            iconTint = PurpleAccentStart,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Voice-Lock Banking Mode", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                if (isVoiceLockEnrolled) "Enrolled (${voiceManager.getEnrolledSampleCount()} samples)" else "Not enrolled (Tap below to enroll)",
                                color = if (isVoiceLockEnrolled) MintAccentDark else AmberWarning,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isVoiceLockEnrolled) {
                        GlassSwitch(
                            checked = isVoiceLockActive,
                            onCheckedChange = { checked ->
                                isVoiceLockActive = checked
                                voiceManager.setVoiceLockEnabled(checked)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Protects sensitive actions (overlay resume/unpause and confirmed destructive file operations) with acoustic voice embedding verification.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(10.dp))
                GlassGradientButton(
                    text = if (isVoiceLockEnrolled) "Re-enroll Voice Signature" else "Enroll Boss Voice",
                    icon = Icons.Default.Mic,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { VoiceUnlockActivity.startEnrollment(context) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassSecondaryButton(
            text = "Open Accessibility Settings",
            icon = Icons.Default.Accessibility,
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Autonomous Messaging & Busy Mode Card ────────────────────────────────────
@Composable
private fun BusyModeCard(context: Context) {
    val busyManager = remember { com.soltini.app.messaging.BusyModeManager.getInstance(context) }
    var isBusy by remember { mutableStateOf(busyManager.isBusyModeActive()) }
    var reasonText by remember { mutableStateOf(busyManager.getBusyReason()) }
    var recentLogs by remember { mutableStateOf(busyManager.getRecentHistory(10)) }
    var showLogs by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                PastelIconBadge(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    badgeColor = if (isBusy) PastelMintBadge else PastelPurpleBadge,
                    iconTint = if (isBusy) MintAccentDark else PurpleAccentStart,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Autonomous Messaging (Busy Mode)",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    GlassStatusBadge(
                        text = if (isBusy) "ACTIVE • Drafts (confirmation ke baad hi send)" else "INACTIVE",
                        style = if (isBusy) BadgeStyle.ACTIVE else BadgeStyle.INACTIVE
                    )
                }
            }

            GlassSwitch(
                checked = isBusy,
                onCheckedChange = { checked ->
                    if (checked) {
                        busyManager.enableBusyMode(reasonText)
                        isBusy = true
                    } else {
                        busyManager.disableBusyMode()
                        isBusy = false
                    }
                    recentLogs = busyManager.getRecentHistory(10)
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            "Jab aap busy hon (\"Myra main busy hoon\"), Myra WhatsApp, Instagram, Telegram aur SMS ke naye messages ka polite aur contextual reply khud bhejti hai.",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlassPillInput(
            value = reasonText,
            onValueChange = {
                reasonText = it
                if (isBusy) busyManager.enableBusyMode(it)
            },
            placeholder = "Busy Reason (e.g. driving, meeting, studying)",
            leadingIcon = Icons.Default.Edit
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = PastelMintBadge.copy(alpha = 0.55f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MintAccentDark,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Loop & Self-Reply Protection Active: Myra apne ya user ke messages ka reply nahi karegi. 3-minute cooldown active.",
                    color = LocalMyraTokens.current.textPrimary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        if (recentLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogs = !showLogs },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Recent Auto-Replies (${recentLogs.size})",
                    color = PurpleAccentStart,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Icon(
                    if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = PurpleAccentStart,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (showLogs) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recentLogs.forEach { log ->
                        Surface(
                            color = Color(0xFFF6F3FC),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${log.appName}: ${log.sender}",
                                        color = LocalMyraTokens.current.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    GlassStatusBadge(text = "Auto-replied", style = BadgeStyle.ACTIVE)
                                }
                                if (log.incomingText.isNotBlank()) {
                                    Text(
                                        "In: \"${log.incomingText}\"",
                                        color = LocalMyraTokens.current.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    "Out: \"${log.replyText}\"",
                                    color = MintAccentDark,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Notifications & Direct Reply Section (Option B) ───────────────────────────
@Composable
private fun NotificationsSettingsSection(context: Context) {
    val notifRepo = remember { NotificationRepository.getInstance(context) }
    val isGranted = SettingsPermChecks.checkNotificationAccess(context)
    val ignoredApps by notifRepo.ignoredApps.collectAsState()
    val recentNotifs by notifRepo.notifications.collectAsState()
    var newAppToIgnore by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    badgeColor = if (isGranted) PastelMintBadge else PastelYellowBadge,
                    iconTint = if (isGranted) MintAccentDark else AmberWarning,
                    size = 46.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Notification Listener Access",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        if (isGranted) "Active • Myra can read & reply to notifications" else "Permission Required • Needed to read incoming messages",
                        color = if (isGranted) MintAccentDark else AmberWarning,
                        fontSize = 12.sp
                    )
                }
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(14.dp))
                GlassGradientButton(
                    text = "Enable Notification Access",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {}
                    }
                )
            }
        }

        // How it works card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            contentPadding = 16.dp
        ) {
            Text(
                "💡 Voice Commands (On-Demand):",
                color = PurpleAccentStart,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "• \"Koi notification aaya kya?\" / \"Read my notifications\"\n" +
                    "• \"Rahul ko reply karo: Main 10 minute mein aa raha hoon\"\n" +
                    "• \"WhatsApp pe reply kar do: Theek hai\"\n" +
                    "• \"Chrome ke notifications ignore karo\"",
                color = LocalMyraTokens.current.textSecondary,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
        }

        // Ignored Apps Management Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Text(
                "Selective App Ignore List",
                color = LocalMyraTokens.current.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                "By default, all apps are allowed. Only apps you explicitly tell Myra or add below will be ignored.",
                color = LocalMyraTokens.current.textSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPillInput(
                    value = newAppToIgnore,
                    onValueChange = { newAppToIgnore = it },
                    placeholder = "App name (e.g. Chrome)",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                GlassGradientButton(
                    text = "Ignore",
                    icon = Icons.Default.Add,
                    enabled = newAppToIgnore.isNotBlank(),
                    onClick = {
                        if (newAppToIgnore.isNotBlank()) {
                            notifRepo.ignoreApp(newAppToIgnore)
                            newAppToIgnore = ""
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (ignoredApps.isEmpty()) {
                Text(
                    "No apps ignored. Myra reads notifications from all messaging apps when asked.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    "Currently Ignored Apps (${ignoredApps.size}):",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ignoredApps.forEach { appName ->
                        Surface(
                            color = Color(0xFFFFE5EA),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    appName.replaceFirstChar { it.uppercase() },
                                    color = RedError,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = RedError,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { notifRepo.unignoreApp(appName) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Captured Notifications Preview
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Recent Captured Notifications",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "${recentNotifs.size} in memory",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 11.sp
                    )
                }
                if (recentNotifs.isNotEmpty()) {
                    TextButton(onClick = { notifRepo.clearAllNotifications() }) {
                        Text("Clear", color = RedError, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (recentNotifs.isEmpty()) {
                Text(
                    if (isGranted) "No active notifications right now. Any notification received will appear here."
                    else "Grant Notification Access above to start capturing notifications.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentNotifs.take(8).forEach { item ->
                        Surface(
                            color = Color(0xFFF6F3FC),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            item.appName,
                                            color = PurpleAccentStart,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "• ${item.getTimeAgo()}",
                                            color = LocalMyraTokens.current.textSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (item.canReply) {
                                        GlassStatusBadge(text = "Direct Reply", style = BadgeStyle.ACTIVE)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    item.title,
                                    color = LocalMyraTokens.current.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                if (item.text.isNotBlank()) {
                                    Text(
                                        item.text,
                                        color = LocalMyraTokens.current.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Screen Companion Settings Section ─────────────────────────────────────────
@Composable
fun ScreenCompanionSection(
    context: Context,
    appSettings: AppSettings?
) {
    val companionManager = remember { com.soltini.app.companion.ScreenCompanionManager.getInstance(context) }
    var isLiveCompanionOn by remember { mutableStateOf(companionManager.isLiveCompanionEnabled.value) }
    var isBankingProtected by remember { mutableStateOf(appSettings?.isBankingProtectionEnabled ?: true) }
    var snapshotResult by remember { mutableStateOf<com.soltini.app.companion.ScreenCompanionManager.ScreenSnapshot?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 26.dp,
            contentPadding = 22.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PastelIconBadge(
                    icon = Icons.Default.Visibility,
                    badgeColor = PastelMintBadge,
                    iconTint = MintAccentDark,
                    size = 68.dp,
                    iconSize = 34.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Live Screen Companion",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalMyraTokens.current.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Myra sits next to you like a real companion, watching your screen and giving helpful tips, corrections, and thoughts as you work or type.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMyraTokens.current.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        // Proactive Companion Toggle Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Proactive Human Eye",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "When ON, Myra notices when you are typing or stuck on a screen and speaks up with sweet, helpful advice naturally.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                GlassSwitch(
                    checked = isLiveCompanionOn,
                    onCheckedChange = { newState ->
                        isLiveCompanionOn = newState
                        companionManager.setLiveCompanionEnabled(newState)
                    }
                )
            }
        }

        // Banking & Privacy Protection Toggle Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Banking & Privacy Shield",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Instantly hides observation and clears screen memory whenever banking, UPI, or password fields are open.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                GlassSwitch(
                    checked = isBankingProtected,
                    onCheckedChange = { newState ->
                        isBankingProtected = newState
                        appSettings?.isBankingProtectionEnabled = newState
                    }
                )
            }
        }

        // Live Test Button
        GlassGradientButton(
            text = "Test Screen Observation Now",
            icon = Icons.Default.Visibility,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                snapshotResult = companionManager.captureCurrentScreenContext()
            }
        )

        // Test Output Preview
        snapshotResult?.let { snap ->
            GlassCodeBlockContainer(
                title = if (snap.isAvailable) "ACTIVE APP: ${snap.currentApp}" else "OBSERVATION STATUS",
                headerTrailing = {
                    GlassStatusBadge(
                        text = if (snap.isAvailable) "Captured" else "Unavailable",
                        style = if (snap.isAvailable) BadgeStyle.ACTIVE else BadgeStyle.WARNING
                    )
                }
            ) {
                if (!snap.isAvailable) {
                    Text(
                        snap.errorMessage ?: "Screen not available",
                        color = Color(0xFFFBBF24),
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    Text(
                        snap.toHumanSummary(),
                        color = Color(0xFFE6EDF3),
                        style = MaterialTheme.typography.labelMedium,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Example Voice Prompts
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            contentPadding = 16.dp
        ) {
            Text(
                "Try saying to Myra:",
                color = PurpleAccentStart,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("• \"Dekho screen pe kya chal raha hai\"", color = LocalMyraTokens.current.textPrimary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("• \"Yeh email check karke batao kaisa likha hai\"", color = LocalMyraTokens.current.textPrimary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("• \"Mera code dekh ke batao kya issue hai\"", color = LocalMyraTokens.current.textPrimary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("• \"Screen companion mode on karo\"", color = LocalMyraTokens.current.textPrimary, fontSize = 12.sp)
        }
    }
}


/** Settings row: shows the app's signing identity and whether it matches the pinned official certificate. */
@androidx.compose.runtime.Composable
private fun AppIdentityRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var show by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("App Identity & Security", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Shows which certificate this app is signed with and whether it matches your official build. Saved API keys and website passwords are encrypted on this phone.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(onClick = { show = true }) { Text("View details") }
    }

    if (show) {
        val report = remember { com.soltini.app.security.AppIdentityVerifier.report(ctx) }
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("App Identity & Security") },
            text = { Text(report, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp) },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Myra app identity", report))
                }) { Text("Copy") }
            }
        )
    }
}
