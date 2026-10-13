package com.soltini.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.memory.DiaryEntry
import com.soltini.app.memory.MyraUnifiedMemory
import com.soltini.app.telephony.TtsSpeaker
import com.soltini.app.ui.components.BadgeStyle
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassCircularActionButton
import com.soltini.app.ui.components.GlassGradientButton
import com.soltini.app.ui.components.GlassPillInput
import com.soltini.app.ui.components.GlassSecondaryButton
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.AccentPurple
import com.soltini.app.ui.theme.GlassCardWhiteSolid
import com.soltini.app.ui.theme.GlassShadowAmbient
import com.soltini.app.ui.theme.GlassShadowSpot
import com.soltini.app.ui.theme.MintAccentDark
import com.soltini.app.ui.theme.PastelMintBadge
import com.soltini.app.ui.theme.PastelPinkBadge
import com.soltini.app.ui.theme.PastelPurpleBadge
import com.soltini.app.ui.theme.PastelYellowBadge
import com.soltini.app.ui.theme.PrimaryGradientBrush
import com.soltini.app.ui.theme.PurpleAccentStart
import com.soltini.app.ui.theme.RedError
import com.soltini.app.ui.theme.SoftBackgroundBrush
import com.soltini.app.ui.theme.TextCharcoal
import com.soltini.app.ui.theme.TextMidGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyDiaryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unifiedMemory = remember { MyraUnifiedMemory.getInstance(context) }
    val ttsSpeaker = remember { TtsSpeaker(context) }

    var entries by remember { mutableStateOf<List<DiaryEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<DiaryEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<DiaryEntry?>(null) }
    var isGeneratingRecap by remember { mutableStateOf(false) }

    fun refreshEntries() {
        scope.launch(Dispatchers.IO) {
            val list = if (searchQuery.isNotBlank()) {
                unifiedMemory.searchDiary(searchQuery)
            } else {
                unifiedMemory.getAllDiaryEntries()
            }
            withContext(Dispatchers.Main) {
                entries = list
            }
        }
    }

    LaunchedEffect(searchQuery) {
        refreshEntries()
    }

    val filteredEntries = remember(entries, selectedCategoryFilter) {
        if (selectedCategoryFilter == "ALL") {
            entries
        } else {
            entries.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
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
                GlassCircularActionButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Add Diary Note",
                    onClick = { showAddDialog = true },
                    size = 58.dp
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // ── Top Floating Header Card ──────────────────────────────────
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    contentPadding = 14.dp
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PastelPurpleBadge)
                                    .clickable(onClick = onBack),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = PurpleAccentStart,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Myra Memory Diary",
                                        color = TextCharcoal,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    GlassStatusBadge(
                                        text = "${entries.size} Entries",
                                        style = BadgeStyle.PURPLE
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Permanent daily reflections, notes & auto-recaps",
                                    color = TextMidGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Auto-generate recap button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isGeneratingRecap) PastelMintBadge else PastelPinkBadge)
                                .clickable {
                                    isGeneratingRecap = true
                                    scope.launch(Dispatchers.IO) {
                                        val generated = unifiedMemory.generateDailyRecapSummary()
                                        withContext(Dispatchers.Main) {
                                            isGeneratingRecap = false
                                            if (generated != null) {
                                                Toast.makeText(context, "Today's recap created!", Toast.LENGTH_SHORT).show()
                                                refreshEntries()
                                            } else {
                                                Toast.makeText(context, "No conversation turns yet today to recap.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Generate Daily Recap",
                                tint = if (isGeneratingRecap) MintAccentDark else AccentPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── Search Bar (White Pill Input) ─────────────────────────────
                GlassPillInput(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search diary, notes, or memories...",
                    leadingIcon = Icons.Default.Search,
                    trailingContent = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextMidGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                // ── Category Filter Chips ─────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiaryFilterChip("All", selected = selectedCategoryFilter == "ALL") {
                        selectedCategoryFilter = "ALL"
                    }
                    DiaryFilterChip("🌅 Recaps", selected = selectedCategoryFilter == "DAILY_SUMMARY") {
                        selectedCategoryFilter = "DAILY_SUMMARY"
                    }
                    DiaryFilterChip("📝 Notes", selected = selectedCategoryFilter == "NOTE") {
                        selectedCategoryFilter = "NOTE"
                    }
                    DiaryFilterChip("💡 Ideas", selected = selectedCategoryFilter == "IDEA") {
                        selectedCategoryFilter = "IDEA"
                    }
                    DiaryFilterChip("💖 Personal", selected = selectedCategoryFilter == "PERSONAL") {
                        selectedCategoryFilter = "PERSONAL"
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── Diary List ────────────────────────────────────────────────
                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            cornerRadius = 26.dp,
                            contentPadding = 28.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                PastelIconBadge(
                                    icon = Icons.Default.Book,
                                    badgeColor = PastelPurpleBadge,
                                    iconTint = PurpleAccentStart,
                                    size = 56.dp,
                                    iconSize = 28.dp
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "No Diary Entries Yet",
                                    color = TextCharcoal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Voice se bol kar ya '+' button dabakar notes add karein!\nJaise: \"Myra, diary me likho ki aaj mera exam bahut achha gaya\"",
                                    color = TextMidGray,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredEntries, key = { it.id }) { entry ->
                            DiaryEntryCard(
                                entry = entry,
                                onReadAloud = {
                                    val textToSpeak = "${entry.title}. ${entry.content}"
                                    ttsSpeaker.speak(textToSpeak)
                                    Toast.makeText(context, "Speaking entry...", Toast.LENGTH_SHORT).show()
                                },
                                onEdit = { entryToEdit = entry },
                                onDelete = { entryToDelete = entry }
                            )
                        }
                        item { Spacer(Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }

    // ── Add / Edit Dialog ─────────────────────────────────────────────────────
    if (showAddDialog || entryToEdit != null) {
        val isEditing = entryToEdit != null
        var titleInput by remember { mutableStateOf(entryToEdit?.title ?: "") }
        var contentInput by remember { mutableStateOf(entryToEdit?.content ?: "") }
        var moodInput by remember { mutableStateOf(entryToEdit?.mood ?: "PRODUCTIVE") }
        var categoryInput by remember { mutableStateOf(entryToEdit?.category ?: "NOTE") }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                entryToEdit = null
            },
            containerColor = GlassCardWhiteSolid,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    if (isEditing) "Edit Diary Entry" else "New Diary Note",
                    color = TextCharcoal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = { Text("Title (e.g. Aaj ka din, Exam review)", color = TextMidGray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurpleAccentStart,
                            unfocusedBorderColor = Color(0xFFE4DCF5),
                            focusedTextColor = TextCharcoal,
                            unfocusedTextColor = TextCharcoal,
                            focusedContainerColor = Color(0xFFFAF8FE),
                            unfocusedContainerColor = Color(0xFFFAF8FE)
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        placeholder = { Text("Apne vichaar, events ya notes likhein...", color = TextMidGray, fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurpleAccentStart,
                            unfocusedBorderColor = Color(0xFFE4DCF5),
                            focusedTextColor = TextCharcoal,
                            unfocusedTextColor = TextCharcoal,
                            focusedContainerColor = Color(0xFFFAF8FE),
                            unfocusedContainerColor = Color(0xFFFAF8FE)
                        )
                    )

                    // Mood Selector
                    Text("Mood / Emotion:", color = TextMidGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "PRODUCTIVE" to "🎯 Productive",
                            "HAPPY" to "😊 Happy",
                            "CALM" to "🧘 Calm",
                            "TIRED" to "😴 Tired",
                            "IDEA" to "💡 Inspired"
                        ).forEach { (key, label) ->
                            MoodPill(
                                label = label,
                                selected = moodInput == key,
                                onClick = { moodInput = key }
                            )
                        }
                    }

                    // Category Selector
                    Text("Category:", color = TextMidGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "NOTE" to "📝 Note",
                            "PERSONAL" to "💖 Personal",
                            "IDEA" to "💡 Idea",
                            "DAILY_SUMMARY" to "🌅 Recap"
                        ).forEach { (cat, label) ->
                            MoodPill(
                                label = label,
                                selected = categoryInput == cat,
                                onClick = { categoryInput = cat }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                GlassGradientButton(
                    text = if (isEditing) "Save Changes" else "Add Entry",
                    onClick = {
                        val finalTitle = titleInput.trim().ifBlank {
                            "Note - " + SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())
                        }
                        val finalContent = contentInput.trim()
                        if (finalContent.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                if (isEditing && entryToEdit != null) {
                                    unifiedMemory.updateDiaryEntry(
                                        id = entryToEdit!!.id,
                                        newTitle = finalTitle,
                                        newContent = finalContent,
                                        newMood = moodInput,
                                        newCategory = categoryInput
                                    )
                                } else {
                                    unifiedMemory.saveDiaryEntry(
                                        title = finalTitle,
                                        content = finalContent,
                                        mood = moodInput,
                                        category = categoryInput,
                                        isAutoGenerated = false
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    showAddDialog = false
                                    entryToEdit = null
                                    refreshEntries()
                                }
                            }
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    entryToEdit = null
                }) {
                    Text("Cancel", color = TextMidGray, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ───────────────────────────────────────────
    if (entryToDelete != null) {
        val entry = entryToDelete!!
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            containerColor = GlassCardWhiteSolid,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete Entry?", color = TextCharcoal, fontWeight = FontWeight.Bold) },
            text = { Text("Kya aap '${entry.title}' ko delete karna chahte hain?", color = TextMidGray) },
            confirmButton = {
                GlassSecondaryButton(
                    text = "Delete",
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            unifiedMemory.deleteDiaryEntry(entry.id)
                            withContext(Dispatchers.Main) {
                                entryToDelete = null
                                refreshEntries()
                            }
                        }
                    },
                    containerColor = Color(0xFFFFE5EA),
                    contentColor = RedError
                )
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel", color = TextMidGray)
                }
            }
        )
    }
}

@Composable
private fun DiaryEntryCard(
    entry: DiaryEntry,
    onReadAloud: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = 16.dp
    ) {
        // Header: Category, Mood, Date, Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val moodEmoji = when (entry.mood.uppercase(Locale.ROOT)) {
                    "PRODUCTIVE" -> "🎯"
                    "HAPPY" -> "😊"
                    "CALM" -> "🧘"
                    "TIRED" -> "😴"
                    "IDEA" -> "💡"
                    else -> "✨"
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (entry.isAutoGenerated) PastelMintBadge else PastelPurpleBadge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(moodEmoji, fontSize = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassStatusBadge(
                            text = if (entry.isAutoGenerated) "Daily Recap" else entry.category,
                            style = if (entry.isAutoGenerated) BadgeStyle.ACTIVE else BadgeStyle.PURPLE
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            entry.dateString,
                            color = TextMidGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PastelPurpleBadge)
                        .clickable(onClick = onReadAloud),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Read Aloud",
                        tint = PurpleAccentStart,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PastelYellowBadge)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = TextCharcoal,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFE5EA))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = RedError,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            entry.title,
            color = TextCharcoal,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            entry.content,
            color = TextMidGray,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun DiaryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .shadow(
                elevation = if (selected) 5.dp else 2.dp,
                shape = CircleShape,
                ambientColor = GlassShadowAmbient,
                spotColor = GlassShadowSpot
            )
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(PrimaryGradientBrush)
                else Modifier.background(GlassCardWhiteSolid)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextCharcoal,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun MoodPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) PastelPurpleBadge else Color(0xFFF3F0FA),
        border = BorderStroke(1.dp, if (selected) PurpleAccentStart else Color.Transparent)
    ) {
        Text(
            text = label,
            color = if (selected) PurpleAccentStart else TextMidGray,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
