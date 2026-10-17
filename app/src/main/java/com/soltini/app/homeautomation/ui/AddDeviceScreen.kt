package com.soltini.app.homeautomation.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.homeautomation.data.DeviceType
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GradientPillButton
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.components.glassTextFieldColors
import com.soltini.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    viewModel: HomeAutomationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val boardIds by viewModel.boardIds.collectAsState()
    val generatedCode by viewModel.generatedCode.collectAsState()
    val activeBoardId by viewModel.activeBoardForCode.collectAsState()

    var deviceName by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DeviceType.LIGHT) }
    var gpioPinText by remember { mutableStateOf("2") }
    var esp32ClientId by remember { mutableStateOf("esp32-living-room") }

    var typeExpanded by remember { mutableStateOf(false) }

    val pinNumber = gpioPinText.toIntOrNull()
    val isPinValid = pinNumber != null && pinNumber in 0..39
    val isFormValid = deviceName.isNotBlank() && roomName.isNotBlank() && isPinValid && esp32ClientId.isNotBlank()

    val pinWarning = when {
        pinNumber == null -> null
        pinNumber in 6..11 -> "⚠️ Pins 6-11 are reserved for internal SPI Flash memory. Choosing this will cause ESP32 boot loops."
        pinNumber in 34..39 -> "⚠️ Pins 34-39 are input-only on ESP32 (cannot drive relays or LEDs)."
        pinNumber == 0 || pinNumber == 2 || pinNumber == 15 -> "ℹ️ Pin $pinNumber is a strapping pin; may toggle briefly at power-on."
        else -> null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoftBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onNavigateBack,
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
                        text = "Add Smart Device",
                        color = LocalMyraTokens.current.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure hardware pin & generate firmware",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Form Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 26.dp,
                elevation = 7.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PastelIconBadge(
                        icon = Icons.Default.Memory,
                        backgroundColor = BadgePurpleBg,
                        iconTint = BadgePurpleTint,
                        size = 42.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hardware Configuration",
                            color = LocalMyraTokens.current.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Map your relay or actuator to an ESP32 pin",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Device Name") },
                        placeholder = { Text("e.g. Ceiling Light, Fan, Kitchen Relay") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it },
                        label = { Text("Room Name") },
                        placeholder = { Text("e.g. Living Room, Master Bedroom") },
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
                            label = { Text("Device Classification") },
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
                        onValueChange = { if (it.length <= 2) gpioPinText = it.filter { c -> c.isDigit() } },
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
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    OutlinedTextField(
                        value = esp32ClientId,
                        onValueChange = { esp32ClientId = it },
                        label = { Text("ESP32 Board ID / Group") },
                        placeholder = { Text("e.g. esp32-living-room") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (boardIds.isNotEmpty()) {
                        Text(
                            text = "Existing Boards:",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            boardIds.forEach { bid ->
                                Surface(
                                    onClick = { esp32ClientId = bid },
                                    shape = CircleShape,
                                    color = if (esp32ClientId == bid) BadgePurpleBg else GlassSurfaceSubtle
                                ) {
                                    Text(
                                        text = bid,
                                        color = if (esp32ClientId == bid) BadgePurpleTint else TextCharcoal,
                                        fontSize = 12.sp,
                                        fontFamily = JetBrainsMonoFamily,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Save & Generate Button
            GradientPillButton(
                text = "Save Device & Generate Firmware",
                icon = Icons.Default.Code,
                enabled = isFormValid,
                onClick = {
                    if (isFormValid) {
                        viewModel.saveDevice(deviceName, roomName, selectedType, pinNumber!!, esp32ClientId) { saved ->
                            viewModel.generateFirmwareForBoard(saved.esp32ClientId)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp
            )

            Spacer(modifier = Modifier.height(84.dp))
        }
    }

    if (generatedCode != null && activeBoardId != null) {
        CodePreviewDialog(
            boardId = activeBoardId!!,
            code = generatedCode!!,
            onDismiss = {
                viewModel.dismissCodeDialog()
                onNavigateBack()
            }
        )
    }
}
