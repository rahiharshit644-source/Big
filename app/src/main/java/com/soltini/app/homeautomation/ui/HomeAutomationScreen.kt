package com.soltini.app.homeautomation.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ModeFanOff
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.homeautomation.data.DeviceEntity
import com.soltini.app.homeautomation.data.DeviceType
import com.soltini.app.homeautomation.mqtt.MqttConnectionStatus
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.GradientCircleButton
import com.soltini.app.ui.components.GradientPillButton
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.components.glassSwitchColors
import com.soltini.app.ui.components.glassTextFieldColors
import com.soltini.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAutomationScreen(
    viewModel: HomeAutomationViewModel,
    onBack: () -> Unit,
    onNavigateToAddDevice: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val boardIds by viewModel.boardIds.collectAsState()
    val liveStates by viewModel.liveStates.collectAsState()
    val mqttStatus by viewModel.mqttStatus.collectAsState()
    val mqttStatusMsg by viewModel.mqttStatusMessage.collectAsState()
    val generatedCode by viewModel.generatedCode.collectAsState()
    val activeBoardId by viewModel.activeBoardForCode.collectAsState()
    val userMsg by viewModel.userMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userMsg) {
        userMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoftBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            floatingActionButton = {
                if (selectedTab == 0) {
                    GradientCircleButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Add Device",
                        onClick = {
                            if (onNavigateToAddDevice != null) {
                                onNavigateToAddDevice()
                            } else {
                                showAddDialog = true
                            }
                        },
                        size = 58.dp,
                        iconSize = 26.dp
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onBack,
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
                                text = "Smart Home",
                                color = LocalMyraTokens.current.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ESP32 Mesh & Gemini Live",
                                color = LocalMyraTokens.current.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // MQTT Connection Status Pill
                    MqttStatusBadge(
                        status = mqttStatus,
                        statusText = mqttStatusMsg,
                        onRetry = { viewModel.retryMqttConnection() }
                    )
                }

                // Segmented Pill Tabs
                Surface(
                    shape = CircleShape,
                    color = GlassCardWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .shadow(5.dp, CircleShape, ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.14f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassTabPill(
                            selected = selectedTab == 0,
                            label = "Devices (${devices.size})",
                            icon = Icons.Default.DeviceHub,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        GlassTabPill(
                            selected = selectedTab == 1,
                            label = "WiFi & Broker",
                            icon = Icons.Default.Settings,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Tab Content
                if (selectedTab == 0) {
                    DeviceListTab(
                        devices = devices,
                        liveStates = liveStates,
                        onToggle = { viewModel.toggleDevice(it) },
                        onDelete = { viewModel.deleteDevice(it) },
                        onGenerateCode = { boardId -> viewModel.generateFirmwareForBoard(boardId) },
                        onAddNew = {
                            if (onNavigateToAddDevice != null) {
                                onNavigateToAddDevice()
                            } else {
                                showAddDialog = true
                            }
                        }
                    )
                } else {
                    BrokerConfigTab(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Add Device Dialog
    if (showAddDialog) {
        AddDeviceDialog(
            existingBoardIds = boardIds,
            onDismiss = { showAddDialog = false },
            onSave = { name, room, type, pin, boardId ->
                viewModel.saveDevice(name, room, type, pin, boardId) { savedDevice ->
                    showAddDialog = false
                    viewModel.generateFirmwareForBoard(savedDevice.esp32ClientId)
                }
            }
        )
    }

    // Generated Code Preview Dialog
    if (generatedCode != null && activeBoardId != null) {
        CodePreviewDialog(
            boardId = activeBoardId!!,
            code = generatedCode!!,
            onDismiss = { viewModel.dismissCodeDialog() }
        )
    }
}

@Composable
private fun GlassTabPill(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .shadow(4.dp, CircleShape, ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.25f))
                        .background(LocalMyraTokens.current.accentHorizontalGradient, CircleShape)
                } else {
                    Modifier.background(Color.Transparent, CircleShape)
                }
            )
            .clickable(onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            })
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else TextMidGray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) Color.White else TextMidGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MqttStatusBadge(
    status: MqttConnectionStatus,
    statusText: String,
    onRetry: () -> Unit
) {
    val (bgColor, fgColor, label) = when (status) {
        MqttConnectionStatus.CONNECTED -> Triple(BadgeMintBg, SecondaryMintDark, "MQTT Online")
        MqttConnectionStatus.CONNECTING -> Triple(BadgePurpleBg, BadgePurpleTint, "Connecting")
        MqttConnectionStatus.RECONNECTING -> Triple(BadgePinkBg, BadgePinkTint, "Reconnecting")
        MqttConnectionStatus.DISCONNECTED -> Triple(GlassSurfaceSubtle, TextMidGray, "Disconnected")
        MqttConnectionStatus.ERROR -> Triple(Color(0xFFFDE8E8), RedError, "Offline")
    }

    Surface(
        onClick = {
            if (status == MqttConnectionStatus.ERROR || status == MqttConnectionStatus.DISCONNECTED) {
                onRetry()
            }
        },
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier.shadow(3.dp, CircleShape, ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(fgColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = fgColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (status == MqttConnectionStatus.ERROR || status == MqttConnectionStatus.DISCONNECTED) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = fgColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceListTab(
    devices: List<DeviceEntity>,
    liveStates: Map<String, String>,
    onToggle: (DeviceEntity) -> Unit,
    onDelete: (DeviceEntity) -> Unit,
    onGenerateCode: (String) -> Unit,
    onAddNew: () -> Unit
) {
    if (devices.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
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
                        icon = Icons.Default.ElectricBolt,
                        backgroundColor = BadgePurpleBg,
                        iconTint = BadgePurpleTint,
                        size = 64.dp,
                        iconSize = 30.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Smart Devices Yet",
                        color = LocalMyraTokens.current.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add a relay, light, fan, or smart lock to control it via voice and auto-generate ESP32 firmware.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    GradientPillButton(
                        text = "Add First Device",
                        icon = Icons.Default.Add,
                        onClick = onAddNew
                    )
                }
            }
        }
        return
    }

    val groupedByBoard = devices.groupBy { it.esp32ClientId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        groupedByBoard.forEach { (boardId, boardDevices) ->
            item(key = "board_$boardId") {
                BoardHeaderCard(
                    boardId = boardId,
                    deviceCount = boardDevices.size,
                    onGenerateCode = { onGenerateCode(boardId) }
                )
            }

            items(boardDevices, key = { it.id }) { device ->
                val state = liveStates[device.mqttTopicBase] ?: "OFF"
                val isOn = state.equals("ON", ignoreCase = true)

                DeviceItemCard(
                    device = device,
                    isOn = isOn,
                    onToggle = { onToggle(device) },
                    onDelete = { onDelete(device) }
                )
            }
        }
    }
}

@Composable
private fun BoardHeaderCard(
    boardId: String,
    deviceCount: Int,
    onGenerateCode: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = 4.dp,
        containerColor = GlassSurfaceSubtle,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.DeviceHub,
                    backgroundColor = BadgePurpleBg,
                    iconTint = BadgePurpleTint,
                    size = 38.dp,
                    iconSize = 18.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = boardId,
                        color = LocalMyraTokens.current.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JetBrainsMonoFamily
                    )
                    Text(
                        text = "$deviceCount pins mapped",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                onClick = onGenerateCode,
                shape = CircleShape,
                color = BadgePurpleBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = BadgePurpleTint,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Firmware",
                        color = BadgePurpleTint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceItemCard(
    device: DeviceEntity,
    isOn: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val (badgeBg, badgeTint) = when (device.deviceType) {
        DeviceType.LIGHT -> BadgeYellowBg to BadgeYellowTint
        DeviceType.FAN -> BadgeMintBg to BadgeMintTint
        DeviceType.LOCK -> BadgePinkBg to BadgePinkTint
        DeviceType.RELAY -> BadgePurpleBg to BadgePurpleTint
        DeviceType.CUSTOM -> BadgeBlueBg to BadgeBlueTint
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevation = 6.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                PastelIconBadge(
                    icon = getDeviceIcon(device.deviceType),
                    backgroundColor = if (isOn) BadgeMintBg else badgeBg,
                    iconTint = if (isOn) SecondaryMintDark else badgeTint,
                    size = 46.dp,
                    iconSize = 22.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.deviceName,
                            color = LocalMyraTokens.current.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = BadgeMintBg
                        ) {
                            Text(
                                text = "GPIO ${device.gpioPin}",
                                color = SecondaryMintDark,
                                fontSize = 10.sp,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "${device.roomName} • ${device.mqttTopicBase}",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = isOn,
                    onCheckedChange = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onToggle()
                    },
                    colors = glassSwitchColors()
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onDelete()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete device",
                        tint = TextMidGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getDeviceIcon(type: DeviceType): ImageVector {
    return when (type) {
        DeviceType.LIGHT -> Icons.Default.Lightbulb
        DeviceType.FAN -> Icons.Default.ModeFanOff
        DeviceType.LOCK -> Icons.Default.Lock
        DeviceType.RELAY -> Icons.Default.Power
        DeviceType.CUSTOM -> Icons.Default.ElectricBolt
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceDialog(
    existingBoardIds: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, room: String, type: DeviceType, pin: Int, boardId: String) -> Unit
) {
    var deviceName by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DeviceType.LIGHT) }
    var gpioPinText by remember { mutableStateOf("2") }
    var esp32ClientId by remember { mutableStateOf(existingBoardIds.firstOrNull() ?: "esp32-living-room") }

    var typeExpanded by remember { mutableStateOf(false) }

    val pinNumber = gpioPinText.toIntOrNull()
    val isPinValid = pinNumber != null && pinNumber in 0..39
    val isNameValid = deviceName.isNotBlank()
    val isRoomValid = roomName.isNotBlank()
    val isBoardValid = esp32ClientId.isNotBlank()
    val isFormValid = isNameValid && isRoomValid && isPinValid && isBoardValid

    val pinWarning = when {
        pinNumber == null -> null
        pinNumber in 6..11 -> "⚠️ Pins 6-11 are reserved for ESP32 internal flash memory. Do not use."
        pinNumber in 34..39 -> "⚠️ Pins 34-39 are input-only (cannot drive output relays)."
        pinNumber == 0 || pinNumber == 2 || pinNumber == 15 -> "ℹ️ Pin $pinNumber is a strapping pin; relay might pulse at boot."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassCardSolid,
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.Add,
                    backgroundColor = BadgePurpleBg,
                    iconTint = BadgePurpleTint,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Add Smart Device",
                    color = LocalMyraTokens.current.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Device Name") },
                    placeholder = { Text("e.g. Ceiling Light, Desk Fan") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    placeholder = { Text("e.g. Living Room, Bedroom") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Device Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        shape = RoundedCornerShape(16.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                        modifier = Modifier.background(GlassCardSolid)
                    ) {
                        DeviceType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name, color = LocalMyraTokens.current.textPrimary) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = gpioPinText,
                    onValueChange = { if (it.length <= 2) gpioPinText = it.filter { char -> char.isDigit() } },
                    label = { Text("ESP32 GPIO Pin (0-39)") },
                    placeholder = { Text("e.g. 2, 4, 16, 17, 18, 19, 21, 22") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isPinValid,
                    shape = RoundedCornerShape(16.dp),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (pinWarning != null) {
                    Text(
                        text = pinWarning,
                        color = if (pinNumber in 6..11 || pinNumber in 34..39) RedError else SecondaryMintDark,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                OutlinedTextField(
                    value = esp32ClientId,
                    onValueChange = { esp32ClientId = it },
                    label = { Text("ESP32 Board ID (Group)") },
                    placeholder = { Text("e.g. esp32-living-room") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = glassTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (existingBoardIds.isNotEmpty()) {
                    Text(
                        text = "Or pick existing board:",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        existingBoardIds.take(3).forEach { bid ->
                            Surface(
                                onClick = { esp32ClientId = bid },
                                shape = CircleShape,
                                color = if (esp32ClientId == bid) BadgePurpleBg else GlassSurfaceSubtle
                            ) {
                                Text(
                                    text = bid,
                                    color = if (esp32ClientId == bid) BadgePurpleTint else TextCharcoal,
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GradientPillButton(
                text = "Save & Generate Code",
                enabled = isFormValid,
                onClick = {
                    if (isFormValid) {
                        onSave(deviceName, roomName, selectedType, pinNumber!!, esp32ClientId)
                    }
                },
                height = 42.dp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LocalMyraTokens.current.textSecondary)
            }
        }
    )
}

@Composable
private fun BrokerConfigTab(
    viewModel: HomeAutomationViewModel
) {
    val store = viewModel.credStore

    var wifiSsid by remember { mutableStateOf(store.wifiSsid) }
    var wifiPass by remember { mutableStateOf(store.wifiPass) }
    var mqttHost by remember { mutableStateOf(store.mqttHost) }
    var mqttPort by remember { mutableStateOf(store.mqttPort.toString()) }
    var mqttUser by remember { mutableStateOf(store.mqttUser) }
    var mqttPass by remember { mutableStateOf(store.mqttPass) }

    var showWifiPass by remember { mutableStateOf(false) }
    var showMqttPass by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // WiFi Section
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            elevation = 6.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.Wifi,
                    backgroundColor = BadgeMintBg,
                    iconTint = SecondaryMintDark,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Target 2.4 GHz WiFi",
                        color = LocalMyraTokens.current.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "ESP32 microcontrollers connect to 2.4 GHz WiFi bands.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = wifiSsid,
                onValueChange = { wifiSsid = it },
                label = { Text("WiFi SSID") },
                shape = RoundedCornerShape(16.dp),
                colors = glassTextFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = wifiPass,
                onValueChange = { wifiPass = it },
                label = { Text("WiFi Password") },
                shape = RoundedCornerShape(16.dp),
                colors = glassTextFieldColors(),
                singleLine = true,
                visualTransformation = if (showWifiPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showWifiPass = !showWifiPass }) {
                        Icon(
                            if (showWifiPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null,
                            tint = TextMidGray
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // MQTT Section
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            elevation = 6.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.Key,
                    backgroundColor = BadgePurpleBg,
                    iconTint = BadgePurpleTint,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "HiveMQ Cloud / MQTT Broker",
                        color = LocalMyraTokens.current.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Encrypted in Android Keystore for TLS voice control.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = mqttHost,
                onValueChange = { mqttHost = it },
                label = { Text("MQTT Broker Host (TLS)") },
                placeholder = { Text("e.g. xxxxxx.s1.eu.hivemq.cloud") },
                shape = RoundedCornerShape(16.dp),
                colors = glassTextFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = mqttPort,
                onValueChange = { mqttPort = it },
                label = { Text("Port (8883 for TLS)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                colors = glassTextFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = mqttUser,
                onValueChange = { mqttUser = it },
                label = { Text("MQTT Username") },
                shape = RoundedCornerShape(16.dp),
                colors = glassTextFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = mqttPass,
                onValueChange = { mqttPass = it },
                label = { Text("MQTT Password") },
                shape = RoundedCornerShape(16.dp),
                colors = glassTextFieldColors(),
                singleLine = true,
                visualTransformation = if (showMqttPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showMqttPass = !showMqttPass }) {
                        Icon(
                            if (showMqttPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null,
                            tint = TextMidGray
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Save Button
        GradientPillButton(
            text = "Save & Connect Broker",
            icon = Icons.Default.CheckCircle,
            onClick = {
                val portInt = mqttPort.toIntOrNull() ?: 8883
                viewModel.saveCredentials(wifiSsid, wifiPass, mqttHost, portInt, mqttUser, mqttPass)
            },
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp
        )

        Spacer(modifier = Modifier.height(84.dp))
    }
}
