package com.soltini.app.ui

import com.soltini.app.ui.theme.LocalMyraTokens
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.memory2.ConversationArchive
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.theme.TextCharcoal
import com.soltini.app.ui.theme.TextMidGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings card for Myra's permanent conversation memory:
 * shows how many messages are saved, where the file lives, and lets you export / delete it.
 */
@Composable
fun ConversationMemoryCard(context: Context, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val archive = remember { ConversationArchive.get() ?: ConversationArchive.init(context) }
    var count by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { count = withContext(Dispatchers.IO) { archive.count() } }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevation = 6.dp,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conversation Memory", color = LocalMyraTokens.current.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Every voice and typed conversation is saved on this phone so Myra can remember and tell you what was discussed earlier.",
                color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp
            )
            Text("Saved messages: ${count ?: "…"}", color = LocalMyraTokens.current.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("Folder: ${archive.folderPath}", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        scope.launch {
                            val path = withContext(Dispatchers.IO) { archive.exportToDownloads() }
                            Toast.makeText(
                                context,
                                if (path != null) "Saved to $path" else "Nothing to export yet",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Export to Downloads", fontSize = 12.sp) }

                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Delete archive", fontSize = 12.sp) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all saved conversations?") },
            text = { Text("Myra will forget everything that was discussed before. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { archive.deleteAll() }
                        count = 0
                        Toast.makeText(context, "Conversation archive deleted", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
