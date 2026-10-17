package com.soltini.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.soltini.app.memory2.ConversationRecord
import com.soltini.app.memory2.Memory2Database
import com.soltini.app.memory2.MemoryItem
import com.soltini.app.memory2.MemoryType
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.theme.LocalMyraTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MemoryFilter(val label: String, val types: Set<MemoryType>?) {
    ALL("All", null),
    PERSONAL("Personal", setOf(MemoryType.USER_PROFILE, MemoryType.PREFERENCE)),
    FACTS("Facts", setOf(MemoryType.FACT, MemoryType.GENERAL, MemoryType.CORRECTION, MemoryType.CONVERSATION)),
    WORK("Projects & tasks", setOf(MemoryType.PROJECT, MemoryType.TASK)),
    ROUTINES("Routines", setOf(MemoryType.ROUTINE))
}

/**
 * Memory. Reads the app's real Memory 2.0 SQLite store (persistent long-term memories) and the conversation
 * history table. The two are separated in two tabs and labelled so persistent memory is never confused with
 * chat history. Nothing is fabricated: empty stores show empty states; DB errors are shown as errors.
 * Edit/delete go through Memory2Database and always ask before deleting.
 */
@Composable
fun MemoryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val t = LocalMyraTokens.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val db = remember { Memory2Database.getInstance(context) }
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(MemoryFilter.ALL) }
    var refresh by remember { mutableIntStateOf(0) }
    var memories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var chats by remember { mutableStateOf<List<ConversationRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var detail by remember { mutableStateOf<MemoryItem?>(null) }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleteMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var deleteChat by remember { mutableStateOf<ConversationRecord?>(null) }
    var revealed by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(refresh) {
        loading = true
        try {
            val (m, c) = withContext(Dispatchers.IO) {
                db.getAllLongTermMemories().sortedByDescending { it.updatedAt } to db.getAllConversationEntries(200)
            }
            memories = m; chats = c; error = null
        } catch (e: Exception) {
            error = "Couldn't read memory: ${e.message}"
        }
        loading = false
    }

    val q = query.trim().lowercase()
    val allowedTypes = filter.types
    val shownMemories = memories.filter { m ->
        (allowedTypes == null || m.type in allowedTypes) &&
            (q.isEmpty() || m.key.lowercase().contains(q) || m.content.lowercase().contains(q))
    }
    val shownChats = chats.filter { q.isEmpty() || it.text.lowercase().contains(q) }.sortedByDescending { it.timestamp }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.backgroundBrush)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text("Memory", color = t.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text("Recall & organize", color = t.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        // Tabs: persistent memory vs chat history
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Saved memories (${memories.size})", tab == 0) { tab = 0 }
            Chip("Chat history (${chats.size})", tab == 1) { tab = 1 }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(if (tab == 0) "Search memories" else "Search chat history", fontSize = 14.sp) },
            shape = RoundedCornerShape(26.dp),
            colors = com.soltini.app.ui.components.glassTextFieldColors()
        )
        if (tab == 0) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryFilter.entries.forEach { f -> Chip(f.label, filter == f) { filter = f } }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (tab == 0) "Persistent: kept until you delete it."
            else "A record of past messages. Deleting an entry here doesn't remove the permanent conversation archive (see Settings \u2192 Storage).",
            color = t.textSecondary, fontSize = 11.sp, lineHeight = 15.sp
        )
        Spacer(Modifier.height(8.dp))

        when {
            error != null -> GlassCard(modifier = Modifier.fillMaxWidth()) { Text(error!!, color = t.textPrimary, fontSize = 13.sp) }
            loading -> Text("Loading\u2026", color = t.textSecondary, fontSize = 13.sp)
            tab == 0 && shownMemories.isEmpty() -> EmptyState(if (memories.isEmpty()) "No saved memories yet." else "No memories match your search or filter.")
            tab == 1 && shownChats.isEmpty() -> EmptyState(if (chats.isEmpty()) "No chat history yet." else "No messages match your search.")
            tab == 0 -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                items(shownMemories, key = { it.id }) { m ->
                    val sensitive = m.sensitivityLevel.equals("SENSITIVE", true)
                    val hidden = sensitive && m.id !in revealed
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (hidden) revealed = revealed + m.id else detail = m },
                        contentPadding = 14.dp
                    ) {
                        Text(m.key, color = t.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (hidden) "\u2022\u2022\u2022\u2022\u2022\u2022 Sensitive \u2014 tap to reveal" else m.content,
                            color = t.textSecondary, fontSize = 13.sp, maxLines = 3, lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${m.type.name.lowercase().replace('_', ' ')} \u2022 ${fmt.format(Date(m.updatedAt))}", color = t.textSecondary, fontSize = 11.sp)
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                items(shownChats, key = { it.id }) { c ->
                    val mine = c.sender.equals("USER", true)
                    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { deleteChat = c }, contentPadding = 12.dp) {
                        Text(
                            "${if (mine) "You" else "Myra"} \u2022 ${fmt.format(Date(c.timestamp))}",
                            color = if (mine) t.accent else t.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(c.text, color = t.textPrimary, fontSize = 13.sp, maxLines = 4, lineHeight = 18.sp)
                    }
                }
            }
        }
    }

    // Detail
    detail?.let { m ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(m.key, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(m.content, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Type: ${m.type.name.lowercase().replace('_', ' ')}\nSaved: ${fmt.format(Date(m.createdAt))}\nUpdated: ${fmt.format(Date(m.updatedAt))}" +
                            "\nConfirmed by you: ${if (m.userConfirmed) "yes" else "no"}",
                        fontSize = 12.sp, color = t.textSecondary
                    )
                }
            },
            confirmButton = { TextButton(onClick = { editText = m.content; editing = m; detail = null }) { Text("Edit") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleteMemory = m; detail = null }) { Text("Delete", color = Color(0xFFD64545)) }
                    TextButton(onClick = { detail = null }) { Text("Close") }
                }
            }
        )
    }

    // Edit
    editing?.let { m ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit memory") },
            text = {
                OutlinedTextField(value = editText, onValueChange = { editText = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            },
            confirmButton = {
                TextButton(
                    enabled = editText.isNotBlank(),
                    onClick = {
                        val id = m.id; val text = editText.trim()
                        editing = null
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { db.updateLongTermMemory(id, text) }
                            if (!ok) error = "Couldn't save the change."
                            refresh++
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    // Delete confirmations
    deleteMemory?.let { m ->
        AlertDialog(
            onDismissRequest = { deleteMemory = null },
            title = { Text("Delete this memory?") },
            text = { Text("\u201C${m.key}\u201D will be permanently removed and Myra will no longer recall it.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = m.id; deleteMemory = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { db.deleteLongTermMemory(id) }
                        if (!ok) error = "Couldn't delete the memory."
                        refresh++
                    }
                }) { Text("Delete", color = Color(0xFFD64545)) }
            },
            dismissButton = { TextButton(onClick = { deleteMemory = null }) { Text("Cancel") } }
        )
    }
    deleteChat?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteChat = null },
            title = { Text("Delete this message from history?") },
            text = { Text(c.text.take(160)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = c.id; deleteChat = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { db.deleteConversationEntry(id) }
                        if (!ok) error = "Couldn't delete the message."
                        refresh++
                    }
                }) { Text("Delete", color = Color(0xFFD64545)) }
            },
            dismissButton = { TextButton(onClick = { deleteChat = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalMyraTokens.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) t.accentHorizontalGradient else androidx.compose.ui.graphics.SolidColor(t.cardColor))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.White else t.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun EmptyState(text: String) {
    val t = LocalMyraTokens.current
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = t.textSecondary, fontSize = 14.sp)
    }
}
