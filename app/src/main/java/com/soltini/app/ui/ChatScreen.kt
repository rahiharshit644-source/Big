package com.soltini.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soltini.app.network.ConnectionState
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.theme.LocalMyraTokens

/**
 * AI Chat. Uses the REAL conversation pipeline: [MainViewModel.sendUserMessage] and the live
 * [MainViewModel.transcripts] flow (the same engine the Voice screen uses). No canned replies.
 *
 * States shown are only ones the app actually knows: connection (Live / Connecting / Offline / Error),
 * Myra speaking, mic muted. There is no fake "typing" indicator.
 */
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalMyraTokens.current
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) listState.animateScrollToItem(transcripts.lastIndex)
    }

    val lastUserText = transcripts.lastOrNull { it.sender == TranscriptEntry.Sender.USER }?.text
    val lastAssistantId = transcripts.lastOrNull { it.sender == TranscriptEntry.Sender.GEMINI }?.id
    val statusText = when {
        connectionState == ConnectionState.Live && isSpeaking -> "Speaking"
        connectionState == ConnectionState.Live -> "Live"
        connectionState == ConnectionState.Connecting || connectionState == ConnectionState.Reconnecting -> "Connecting"
        connectionState == ConnectionState.Error -> "Error"
        else -> "Offline"
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        viewModel.sendUserMessage(text)
        draft = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.backgroundBrush)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(t.accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Myra", color = t.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("AI Companion \u2022 $statusText", color = t.textSecondary, fontSize = 12.sp)
            }
            if (transcripts.isNotEmpty()) {
                Text(
                    "Clear",
                    color = t.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { viewModel.clearTranscript() } // on-screen only; permanent archive is untouched
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Offline / error banner (real state)
        if (connectionState == ConnectionState.Disconnected || connectionState == ConnectionState.Error) {
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { viewModel.connect() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = t.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (connectionState == ConnectionState.Error) "Voice connection error" else "Live voice is offline",
                            color = t.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            errorMessage ?: "Typed messages still work. Tap to reconnect live voice.",
                            color = t.textSecondary, fontSize = 11.sp, maxLines = 2
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Messages
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (transcripts.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Ask Myra anything", color = t.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Type below, tap the mic, or try a shortcut.", color = t.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    listOf("Open YouTube", "Turn on living room light", "What's on my screen?").forEach { s ->
                        SuggestionChip(s) { viewModel.sendUserMessage(s) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(transcripts, key = { it.id }) { entry ->
                        val isUser = entry.sender == TranscriptEntry.Sender.USER
                        ChatBubble(
                            text = entry.text,
                            isUser = isUser,
                            onCopy = { clipboard.setText(AnnotatedString(entry.text)) },
                            onRetry = if (!isUser && entry.id == lastAssistantId && lastUserText != null) {
                                { viewModel.sendUserMessage(lastUserText) }
                            } else null
                        )
                    }
                }
            }
        }

        // Composer
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Myra or type a message...", fontSize = 14.sp) },
                shape = RoundedCornerShape(26.dp),
                maxLines = 4,
                colors = com.soltini.app.ui.components.glassTextFieldColors()
            )
            Spacer(Modifier.width(8.dp))
            RoundAction(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                description = if (isMuted) "Unmute microphone" else "Microphone",
                filled = false,
                onClick = onMicClick
            )
            Spacer(Modifier.width(8.dp))
            RoundAction(
                icon = Icons.AutoMirrored.Filled.Send,
                description = "Send message",
                filled = draft.isNotBlank(),
                onClick = { send() }
            )
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    val t = LocalMyraTokens.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(t.cardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, color = t.textPrimary, fontSize = 13.sp) }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, filled: Boolean, onClick: () -> Unit) {
    val t = LocalMyraTokens.current
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (filled) t.accentGradient else androidx.compose.ui.graphics.SolidColor(t.cardColor))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (filled) Color.White else t.accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ChatBubble(text: String, isUser: Boolean, onCopy: () -> Unit, onRetry: (() -> Unit)?) {
    val t = LocalMyraTokens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
        ) {
            if (isUser) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                        .background(t.accentGradient)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) { Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp) }
            } else {
                GlassCard(
                    modifier = Modifier.align(Alignment.CenterStart),
                    cornerRadius = 20.dp,
                    elevation = 2.dp,
                    contentPadding = 14.dp
                ) { Text(text, color = t.textPrimary, fontSize = 14.sp, lineHeight = 20.sp) }
            }
        }
        if (!isUser) {
            Row(modifier = Modifier.padding(start = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallAction(Icons.Default.ContentCopy, "Copy message", onCopy)
                if (onRetry != null) SmallAction(Icons.Default.Refresh, "Retry last request", onRetry)
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val t = LocalMyraTokens.current
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = description, tint = t.textSecondary, modifier = Modifier.size(16.dp)) }
}
