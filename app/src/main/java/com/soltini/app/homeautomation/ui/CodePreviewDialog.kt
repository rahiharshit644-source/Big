package com.soltini.app.homeautomation.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soltini.app.homeautomation.generator.FirmwareFileUtils
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*

/**
 * CodePreviewDialog
 *
 * Keeps a dark monospace inner card for readability, wrapped inside a white rounded
 * outer glass container with soft shadow so it fits the light theme.
 */
@Composable
fun CodePreviewDialog(
    boardId: String,
    code: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = PrimaryPurple.copy(alpha = 0.25f),
                    spotColor = PrimaryPurple.copy(alpha = 0.30f)
                ),
            color = GlassCardSolid,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                                text = "ESP32 Firmware (.ino)",
                                color = TextCharcoal,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Board: $boardId",
                                color = SecondaryMintDark,
                                fontSize = 12.sp,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = GlassSurfaceSubtle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMidGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Action Pill Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            val ok = FirmwareFileUtils.copyToClipboard(context, code, "ESP32_$boardId")
                            Toast.makeText(
                                context,
                                if (ok) "Copied to clipboard!" else "Failed to copy",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        color = BadgePurpleBg,
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = BadgePurpleTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Copy",
                                color = BadgePurpleTint,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            FirmwareFileUtils.shareSketch(context, code, "$boardId.ino")
                        },
                        modifier = Modifier.weight(1f),
                        color = BadgePinkBg,
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = BadgePinkTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Share",
                                color = BadgePinkTint,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            val result = FirmwareFileUtils.saveToDownloads(context, code, "$boardId.ino")
                            result.onSuccess { path ->
                                Toast.makeText(context, path, Toast.LENGTH_LONG).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "Save error: ${err.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        color = BadgeMintBg,
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = SecondaryMintDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Save",
                                color = SecondaryMintDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dark Monospace Code Block inside the White Rounded Outer Container
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CodeBlockDarkBg
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        SelectionContainer {
                            Text(
                                text = code,
                                color = CodeBlockTextLight,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(vScroll)
                                    .horizontalScroll(hScroll)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "💡 Paste into Arduino IDE (Speed 115200) or VS Code PlatformIO.",
                    color = TextMidGray,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
