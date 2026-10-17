package com.soltini.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*
import com.soltini.app.util.AppLogger
import com.soltini.app.util.LogEntry

@Composable
fun LogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by AppLogger.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalMyraTokens.current.backgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // Header Floating Glass Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevation = 6.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onBack,
                            shape = CircleShape,
                            color = GlassSurfaceSubtle,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextCharcoal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        PastelIconBadge(
                            icon = Icons.AutoMirrored.Filled.Article,
                            backgroundColor = BadgePurpleBg,
                            iconTint = BadgePurpleTint,
                            size = 42.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "System & Network Logs",
                                color = TextCharcoal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "${logs.size} entries recorded",
                                color = TextMidGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                val success = AppLogger.copyToClipboard(context)
                                Toast.makeText(
                                    context,
                                    if (success) "Logs copied!" else "Failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = CircleShape,
                            color = BadgePurpleBg,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy Logs",
                                    tint = BadgePurpleTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                AppLogger.clear()
                                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                            },
                            shape = CircleShape,
                            color = Color(0xFFFDE8E8),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Clear Logs",
                                    tint = RedError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Instructions Floating Pill Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                elevation = 4.dp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PastelIconBadge(
                        icon = Icons.Default.Info,
                        backgroundColor = BadgeMintBg,
                        iconTint = SecondaryMintDark,
                        size = 32.dp,
                        iconSize = 16.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Tip: Log output is fully selectable. Press & hold any line to copy specific details, or tap Copy above.",
                        color = TextMidGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // White Rounded Outer Glass Container wrapping Dark Monospace Log Block
            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                cornerRadius = 26.dp,
                elevation = 8.dp,
                contentPadding = PaddingValues(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CodeBlockDarkBg,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No logs recorded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CodeBlockTextMuted
                            )
                        }
                    } else {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(logs, key = { it.id }) { entry ->
                                    LogItemCard(entry)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(78.dp))
        }
    }
}

@Composable
private fun LogItemCard(entry: LogEntry) {
    val levelColor = when (entry.level) {
        "ERROR" -> Color(0xFFFF6B6B)
        "WARN" -> Color(0xFFFBBF24)
        "INFO" -> Color(0xFFB993F0)
        else -> Color(0xFFA9E8C9)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CodeBlockDarkSurface)
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(levelColor.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            entry.level,
                            color = levelColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        entry.tag,
                        color = CodeBlockTextLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMonoFamily
                    )
                }

                Text(
                    entry.timestamp,
                    color = CodeBlockTextMuted,
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFamily
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                color = CodeBlockTextLight,
                fontSize = 11.sp,
                fontFamily = JetBrainsMonoFamily,
                lineHeight = 16.sp
            )
        }
    }
}
