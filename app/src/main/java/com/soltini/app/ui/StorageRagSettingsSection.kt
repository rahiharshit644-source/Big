package com.soltini.app.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.rag.RagKnowledgeEngine
import com.soltini.app.rag.RagSearchResult
import com.soltini.app.settings.AppSettings
import com.soltini.app.storage.SafStorageManager
import com.soltini.app.ui.components.BadgeStyle
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassCodeBlockContainer
import com.soltini.app.ui.components.GlassGradientButton
import com.soltini.app.ui.components.GlassPillInput
import com.soltini.app.ui.components.GlassSecondaryButton
import com.soltini.app.ui.components.GlassSectionHeader
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.MintAccentDark
import com.soltini.app.ui.theme.PastelMintBadge
import com.soltini.app.ui.theme.PastelPinkBadge
import com.soltini.app.ui.theme.PastelPurpleBadge
import com.soltini.app.ui.theme.PastelYellowBadge
import com.soltini.app.ui.theme.PurpleAccentStart
import com.soltini.app.ui.theme.RedError
import com.soltini.app.ui.theme.TextCharcoal
import com.soltini.app.ui.theme.TextMidGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StorageRagSettingsSection(
    context: Context,
    appSettings: AppSettings?
) {
    val coroutineScope = rememberCoroutineScope()
    val storageManager = remember { SafStorageManager.getInstance(context) }
    val ragEngine = remember {
        val settings = appSettings ?: AppSettings(context)
        RagKnowledgeEngine.getInstance(context, settings)
    }

    var authorizedFolders by remember { mutableStateOf(storageManager.getAuthorizedLocations()) }
    var backups by remember { mutableStateOf(storageManager.backupManager.getAllBackups()) }
    var isIndexing by remember { mutableStateOf(false) }
    var indexingStatus by remember { mutableStateOf("") }
    var indexedDocCount by remember { mutableStateOf(0) }
    var indexedChunkCount by remember { mutableStateOf(0) }

    // Test Search State
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RagSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Refresh counts
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val stats = ragEngine.db.getStats()
                indexedDocCount = stats.totalDocuments
                indexedChunkCount = stats.totalChunks
                backups = storageManager.backupManager.getAllBackups()
            }
        } catch (e: Exception) {
            android.util.Log.e("StorageRagSettings", "Initial load failed: ${e.message}", e)
        }
    }

    // SAF Directory Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val success = storageManager.addAuthorizedTree(uri)
                if (success) {
                    authorizedFolders = storageManager.getAuthorizedLocations()
                    Toast.makeText(context, "Folder authorized successfully!", Toast.LENGTH_SHORT).show()

                    // Trigger indexing in background
                    coroutineScope.launch(Dispatchers.IO) {
                        isIndexing = true
                        indexingStatus = "Indexing authorized folder..."
                        ragEngine.indexAllAuthorizedFolders(storageManager) { current, total, name ->
                            indexingStatus = "Indexing ($current/$total): $name"
                        }
                        val stats = ragEngine.db.getStats()
                        indexedDocCount = stats.totalDocuments
                        indexedChunkCount = stats.totalChunks
                        isIndexing = false
                        indexingStatus = "Indexing complete!"
                    }
                } else {
                    Toast.makeText(context, "Failed to persist folder permissions.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error granting folder access: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Security Guarantee Banner ────────────────────────────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.VerifiedUser,
                    badgeColor = PastelMintBadge,
                    iconTint = MintAccentDark,
                    size = 44.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Zero Silent Storage Access",
                        color = TextCharcoal,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Protected via Android Storage Access Framework",
                        color = MintAccentDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "MYRA strictly accesses ONLY the folders you explicitly authorize below using Android's Storage Access Framework (SAF). Entire device storage is never silently accessed.",
                color = TextMidGray,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }

        // ── Authorized Locations ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassSectionHeader(
                title = "Authorized Folders (${authorizedFolders.size})",
                modifier = Modifier.weight(1f)
            )

            GlassGradientButton(
                text = "Add Folder",
                icon = Icons.Default.Add,
                onClick = { folderPickerLauncher.launch(null) }
            )
        }

        if (authorizedFolders.isEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                contentPadding = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PastelIconBadge(
                        icon = Icons.Default.FolderOpen,
                        badgeColor = PastelPurpleBadge,
                        iconTint = PurpleAccentStart,
                        size = 48.dp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No folders authorized yet",
                        color = TextCharcoal,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Add Folder' above to let MYRA read, search, and manage files in your selected folders.",
                        color = TextMidGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                authorizedFolders.forEach { folder ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        contentPadding = 14.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                PastelIconBadge(
                                    icon = Icons.Default.FolderOpen,
                                    badgeColor = PastelPurpleBadge,
                                    iconTint = PurpleAccentStart,
                                    size = 42.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        folder.displayName,
                                        color = TextCharcoal,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        folder.uriString.takeLast(40),
                                        color = TextMidGray,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFE5EA))
                                    .clickable {
                                        storageManager.removeAuthorizedTree(folder.uriString)
                                        authorizedFolders = storageManager.getAuthorizedLocations()
                                        Toast.makeText(context, "Folder removed", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Remove",
                                    tint = RedError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── RAG Knowledge System Status ──────────────────────────────────────
        GlassSectionHeader(
            title = "RAG Knowledge System",
            subtitle = "On-device LangChain4j semantic chunking & search"
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PastelPurpleBadge.copy(alpha = 0.6f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$indexedDocCount", color = PurpleAccentStart, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Documents", color = TextMidGray, fontSize = 11.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PastelPinkBadge.copy(alpha = 0.6f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$indexedChunkCount", color = PurpleAccentStart, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Chunks", color = TextMidGray, fontSize = 11.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PastelMintBadge.copy(alpha = 0.6f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PDF • OCR", color = MintAccentDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("TXT, MD, Code", color = TextMidGray, fontSize = 10.sp)
                    }
                }
            }

            if (indexingStatus.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(indexingStatus, color = PurpleAccentStart, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(14.dp))

            GlassGradientButton(
                text = if (isIndexing) "Indexing Documents..." else "Re-Index Authorized Knowledge",
                icon = Icons.Default.Refresh,
                enabled = !isIndexing && authorizedFolders.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        isIndexing = true
                        indexingStatus = "Re-indexing all folders..."
                        ragEngine.db.clearAll()
                        ragEngine.indexAllAuthorizedFolders(storageManager) { current, total, name ->
                            indexingStatus = "Processing ($current/$total): $name"
                        }
                        val stats = ragEngine.db.getStats()
                        indexedDocCount = stats.totalDocuments
                        indexedChunkCount = stats.totalChunks
                        isIndexing = false
                        indexingStatus = "Knowledge base up to date!"
                    }
                }
            )
        }

        // ── Knowledge Search Playground ──────────────────────────────────────
        GlassSectionHeader(
            title = "Test Knowledge Search",
            subtitle = "Query indexed document chunks in real time"
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            GlassPillInput(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search your indexed documents...",
                leadingIcon = Icons.Default.Search,
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PastelPurpleBadge)
                            .clickable {
                                if (searchQuery.isNotBlank()) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        isSearching = true
                                        searchResults = ragEngine.searchKnowledge(searchQuery, limit = 5)
                                        isSearching = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Search",
                            tint = PurpleAccentStart,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )

            if (searchResults.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Top Relevant Chunks:",
                    color = TextMidGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (hit in searchResults) {
                        GlassCodeBlockContainer(
                            title = hit.chunk.documentName,
                            headerTrailing = {
                                GlassStatusBadge(
                                    text = "Score: ${String.format(Locale.US, "%.2f", hit.score)}",
                                    style = BadgeStyle.ACTIVE
                                )
                            }
                        ) {
                            Text(
                                text = hit.matchSnippet.ifBlank { hit.chunk.text.take(200) },
                                color = Color(0xFFE6EDF3),
                                style = MaterialTheme.typography.labelMedium,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Backup Snapshots (Undo Safety) ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassSectionHeader(
                title = "Pre-Modification Backups (${backups.size})",
                modifier = Modifier.weight(1f)
            )

            if (backups.isNotEmpty()) {
                TextButton(onClick = {
                    storageManager.backupManager.clearAllBackups()
                    backups = storageManager.backupManager.getAllBackups()
                }) {
                    Text("Clear All", color = RedError, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (backups.isEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 16.dp
            ) {
                Text(
                    text = "Automatic snapshots are created whenever MYRA edits or modifies a file, allowing instant undo.",
                    color = TextMidGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                backups.take(5).forEach { backup ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        contentPadding = 14.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                PastelIconBadge(
                                    icon = Icons.Default.History,
                                    badgeColor = PastelYellowBadge,
                                    iconTint = TextCharcoal,
                                    size = 38.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        backup.originalFileName,
                                        color = TextCharcoal,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Saved: ${dateFormat.format(Date(backup.timestamp))}",
                                        color = TextMidGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            GlassSecondaryButton(
                                text = "Undo",
                                icon = Icons.Default.Undo,
                                onClick = {
                                    val res = storageManager.backupManager.restoreBackup(backup.id)
                                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    backups = storageManager.backupManager.getAllBackups()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
