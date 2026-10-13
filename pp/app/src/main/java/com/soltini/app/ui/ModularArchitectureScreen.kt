package com.soltini.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.memory2.FactAction
import com.soltini.app.memory2.FactResolution
import com.soltini.app.memory2.FactResolver
import com.soltini.app.memory2.MemoryItem
import com.soltini.app.memory2.RetrievedMemoryContext
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.components.BadgeStyle
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassGradientButton
import com.soltini.app.ui.components.GlassPillInput
import com.soltini.app.ui.components.GlassSecondaryButton
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ModularArchitectureScreen(
    viewModel: MainViewModel?,
    context: Context,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val memory2 = viewModel?.getMemory2Engine()
    val appSettings = viewModel?.getAppSettings()
    val pluginRegistry = viewModel?.getPluginRegistry()
    val orchestrator = viewModel?.getMyraOrchestrator()

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Orchestrator", "Memory 2.0", "Plugin Registry")

    var longTermMemories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var sessionLogEntries by remember { mutableStateOf<List<com.soltini.app.memory2.Memory2Database.SessionLogEntry>>(emptyList()) }
    var experienceRecords by remember { mutableStateOf<List<com.soltini.app.memory2.Memory2Database.ExperienceRecord>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshMemories() {
        if (memory2 != null) {
            try { longTermMemories = memory2.db.getAllLongTermMemories() }
            catch (e: Exception) { android.util.Log.e("ModularArchitecture", "long-term load failed: ${e.message}", e); longTermMemories = emptyList() }
            try { experienceRecords = memory2.db.getAllExperiences() }
            catch (e: Exception) { android.util.Log.e("ModularArchitecture", "experience load failed: ${e.message}", e); experienceRecords = emptyList() }
            try { sessionLogEntries = memory2.db.getRecentSessionLogEntries(50) }
            catch (e: Exception) { android.util.Log.e("ModularArchitecture", "session log load failed: ${e.message}", e); sessionLogEntries = emptyList() }
        }
    }

    LaunchedEffect(selectedTab) {
        refreshMemories()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Header
        PastelIconBadge(
            icon = Icons.Default.Memory,
            badgeColor = PastelPurpleBadge,
            iconTint = PurpleAccentStart,
            size = 72.dp,
            iconSize = 36.dp
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "MYRA Core Architecture",
            style = MaterialTheme.typography.titleLarge,
            color = TextCharcoal,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Unified Orchestrator • Memory 2.0 • Plugin Registry",
            style = MaterialTheme.typography.bodySmall,
            color = TextMidGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        // Floating Pill Tab Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 5.dp,
                    shape = CircleShape,
                    ambientColor = GlassShadowAmbient,
                    spotColor = GlassShadowSpot
                )
                .clip(CircleShape)
                .background(GlassCardWhiteSolid)
                .padding(5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.background(PrimaryGradientBrush)
                            else Modifier.background(Color.Transparent)
                        )
                        .clickable { selectedTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        color = if (isSelected) Color.White else TextMidGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        when (selectedTab) {
            0 -> OrchestratorTab(orchestrator, memory2, pluginRegistry)
            1 -> Memory2Tab(
                memory2 = memory2,
                appSettings = appSettings,
                longTermMemories = longTermMemories,
                sessionLogEntries = sessionLogEntries,
                experienceRecords = experienceRecords,
                onMemoryChanged = { refreshMemories() },
                onClearAll = {
                    memory2?.clearAllMemories()
                    refreshMemories()
                    statusMessage = "All memories cleared."
                }
            )
            2 -> PluginRegistryTab(pluginRegistry)
        }

        statusMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            GlassStatusBadge(text = msg, style = BadgeStyle.ACTIVE)
        }
    }
}

@Composable
private fun OrchestratorTab(
    orchestrator: com.soltini.app.orchestrator.MyraOrchestrator?,
    memory2: com.soltini.app.memory2.Memory2Engine?,
    pluginRegistry: com.soltini.app.plugins.PluginRegistry?
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Status Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassStatusBadge(text = "Master Decision Engine Active", style = BadgeStyle.ACTIVE)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "MYRA Orchestrator acts as the central brain. It classifies user intent, queries Memory 2.0 for context, discovers capabilities from the Plugin Registry, executes steps, validates outputs, triggers fallbacks when needed, and updates persistent learning.",
                color = TextMidGray,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }

        // Execution Loop Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Text(
                "8-Stage Intelligence Loop",
                color = TextCharcoal,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(12.dp))

            val loopStages = listOf(
                "1. UNDERSTAND" to "Classify conversational vs actionable vs multi-step plan",
                "2. REMEMBER" to "Selectively retrieve relevant Working, Session & Long-term context",
                "3. PLAN" to "Decompose goal into sequential and conditional task steps",
                "4. DISCOVER" to "Resolve capable plugins & tools from dynamic Registry",
                "5. EXECUTE" to "Run plugin actions safely with structured parameters",
                "6. VERIFY & FALLBACK" to "Inspect output; trigger alternative plugin if failed",
                "7. LEARN & EVALUATE" to "Save durable preferences & learned tool sequences",
                "8. RESPOND" to "Synthesize warm, concise Hinglish voice/text response"
            )

            loopStages.forEach { (stage, desc) ->
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp, end = 10.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PurpleAccentStart)
                    )
                    Column {
                        Text(stage, color = TextCharcoal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(desc, color = TextMidGray, fontSize = 12.sp)
                    }
                }
            }
        }

        // Active State Summary
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Text(
                "Current Working State",
                color = TextCharcoal,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(8.dp))

            val activeGoal = memory2?.working?.activeGoal ?: "Idle (Listening for user prompt)"
            val currentStep = (memory2?.working?.activeStepIndex ?: 0) + 1
            val workingItems = memory2?.working?.getActiveWorkingContext() ?: emptyList()

            Text("Active Task: $activeGoal", color = PurpleAccentStart, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (memory2?.working?.activeGoal != null) {
                Text("Current Step: #$currentStep", color = TextMidGray, fontSize = 12.sp)
            }
            if (workingItems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Scratchpad Variables:", color = TextMidGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                workingItems.forEach { item ->
                    Text("  ${item.key}: ${item.content}", color = TextMidGray, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Memory2Tab(
    memory2: com.soltini.app.memory2.Memory2Engine?,
    appSettings: AppSettings?,
    longTermMemories: List<MemoryItem>,
    sessionLogEntries: List<com.soltini.app.memory2.Memory2Database.SessionLogEntry>,
    experienceRecords: List<com.soltini.app.memory2.Memory2Database.ExperienceRecord>,
    onMemoryChanged: () -> Unit,
    onClearAll: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var expandedFactsList by remember { mutableStateOf(true) }
    var expandedTiers by remember { mutableStateOf(false) }
    var expandedExperience by remember { mutableStateOf(false) }

    // Conflict Resolution Lab state
    var labStatementInput by remember { mutableStateOf("") }
    var labResolutionResult by remember { mutableStateOf<FactResolution?>(null) }
    var isLabResolving by remember { mutableStateOf(false) }

    // Selective Retrieval Tester state
    var searchTestQuery by remember { mutableStateOf("") }
    var searchTestResults by remember { mutableStateOf<RetrievedMemoryContext?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // ── 1. Memory Engine Metrics Card ───────────────────────
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
                    size = 42.dp
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Unified Memory Engine", color = TextCharcoal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Local SQLite • Offline dedup • No parallel stores", color = TextMidGray, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox("Active Facts", "${longTermMemories.size}", PastelPurpleBadge, PurpleAccentStart, Modifier.weight(1f))
                MetricBox("Past Chats", "${sessionLogEntries.size}", PastelMintBadge, MintAccentDark, Modifier.weight(1f))
                MetricBox("Patterns", "${experienceRecords.size}", PastelPinkBadge, PurpleAccentStart, Modifier.weight(1f))
            }
        }

        // ── 2. Conflict Resolution & Deduplication Lab ───────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Text("Conflict & Deduplication Lab", color = TextCharcoal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Test the decision engine (ADD, UPDATE outdated memories, DELETE contradictions, NOOP duplicates) — runs offline:",
                color = TextMidGray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            GlassPillInput(
                value = labStatementInput,
                onValueChange = { labStatementInput = it },
                placeholder = "e.g. \"I moved to Bangalore\" or \"I like black coffee\"",
                leadingIcon = Icons.Default.Edit
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassGradientButton(
                    text = if (isLabResolving) "Resolving..." else "Resolve",
                    icon = Icons.Default.Bolt,
                    enabled = !isLabResolving && labStatementInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (labStatementInput.isNotBlank() && memory2 != null) {
                            isLabResolving = true
                            coroutineScope.launch {
                                val existing = memory2.db.getAllLongTermMemories()
                                val res = FactResolver.resolve(labStatementInput.trim(), existing)
                                labResolutionResult = res
                                memory2.saveLongTerm(key = "lab_test", fact = labStatementInput.trim())
                                isLabResolving = false
                                onMemoryChanged()
                            }
                        }
                    }
                )

                GlassSecondaryButton(
                    text = "Sample",
                    onClick = { labStatementInput = "I like black coffee without sugar" }
                )
            }

            labResolutionResult?.let { res ->
                Spacer(Modifier.height(12.dp))
                ResolutionCard(res)
            }
        }

        // ── 3. Selective Context Retrieval Simulator ─────────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Text("Selective Query Retrieval", color = TextCharcoal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Simulate how MYRA filters context. Only relevant memories are returned; irrelevant ones are omitted.",
                color = TextMidGray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            GlassPillInput(
                value = searchTestQuery,
                onValueChange = { searchTestQuery = it },
                placeholder = "e.g. \"Where does Boss live?\"",
                leadingIcon = Icons.Default.Search
            )

            Spacer(Modifier.height(12.dp))

            GlassGradientButton(
                text = if (isSearching) "Searching..." else "Test Selective Retrieval",
                icon = Icons.Default.Search,
                enabled = !isSearching && searchTestQuery.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (searchTestQuery.isNotBlank() && memory2 != null) {
                        isSearching = true
                        coroutineScope.launch {
                            searchTestResults = memory2.retrieveContext(searchTestQuery.trim(), maxItems = 5)
                            isSearching = false
                        }
                    }
                }
            )

            searchTestResults?.let { ctx ->
                if (ctx.relevantItems.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Injected Context Block (${ctx.relevantItems.size} matches):",
                        color = MintAccentDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    ctx.relevantItems.forEach { mem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF6F3FC))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassStatusBadge(text = mem.type.name, style = BadgeStyle.PURPLE)
                            Spacer(Modifier.width(8.dp))
                            Text(mem.content, color = TextCharcoal, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text("No relevant matches found for this query.", color = TextMidGray, fontSize = 12.sp)
                }
            }
        }

        // ── 4. Active Memories List ─────────────────────────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Long-Term Memory (${longTermMemories.size})", color = TextCharcoal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                IconButton(onClick = { expandedFactsList = !expandedFactsList }) {
                    Icon(
                        if (expandedFactsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMidGray
                    )
                }
            }

            if (longTermMemories.isEmpty()) {
                Text("No memories saved yet. Teach MYRA above or talk naturally.", color = TextMidGray, fontSize = 12.sp)
            } else {
                AnimatedVisibility(visible = expandedFactsList) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        longTermMemories.forEach { mem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF6F3FC))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GlassStatusBadge(text = mem.type.name, style = BadgeStyle.PURPLE)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Importance: ${(mem.importance * 100).toInt()}% • Accessed: ${mem.accessCount}x",
                                            color = TextMidGray,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(mem.content, color = TextCharcoal, fontSize = 13.sp)
                                }

                                IconButton(
                                    onClick = {
                                        memory2?.deleteMemory(mem.id)
                                        onMemoryChanged()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = RedError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (longTermMemories.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                GlassSecondaryButton(
                    text = "Clear All Memories",
                    icon = Icons.Default.Delete,
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color(0xFFFFE5EA),
                    contentColor = RedError
                )
            }
        }

        // ── 5. 5 Memory Tiers Overview Card ──────────────────────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("5 Structured Memory Tiers", color = TextCharcoal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                IconButton(onClick = { expandedTiers = !expandedTiers }) {
                    Icon(if (expandedTiers) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = TextMidGray)
                }
            }

            AnimatedVisibility(visible = expandedTiers) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    TierRow("1. Working Memory", "Transient scratchpad variables & active task steps", MintAccentDark)
                    TierRow("2. Session Log", "${sessionLogEntries.size} persisted, timestamped conversation topics — survives restarts", PurpleAccentStart)
                    TierRow("3. Long-Term Memory", "${longTermMemories.size} durable user facts with offline duplicate prevention", AccentPurple)
                    TierRow("4. Knowledge Memory", "Document facts, user notes, persistent references", Color(0xFFD97706))
                    TierRow("5. Skill / Experience", "${experienceRecords.size} learned tool execution patterns & success rates", MintAccentDark)
                }
            }
        }

        // ── 6. Learned Experiences Card ──────────────────────────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Learned Skill & Experience Patterns (${experienceRecords.size})",
                    color = TextCharcoal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expandedExperience = !expandedExperience }) {
                    Icon(
                        if (expandedExperience) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMidGray
                    )
                }
            }

            if (experienceRecords.isEmpty()) {
                Text(
                    "MYRA learns execution patterns automatically as you perform voice commands and phone tasks.",
                    color = TextMidGray,
                    fontSize = 12.sp
                )
            } else {
                AnimatedVisibility(visible = expandedExperience) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        experienceRecords.forEach { rec ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF6F3FC))
                                    .padding(12.dp)
                            ) {
                                Text("Task: ${rec.taskType}", color = TextCharcoal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Proven Tool Flow: ${rec.toolSequence.joinToString(" ➔ ")}", color = PurpleAccentStart, fontSize = 11.sp)
                                Text("Success Rate: ${(rec.successRate * 100).toInt()}% (${rec.successCount}/${rec.totalCount})", color = MintAccentDark, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    bgColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor.copy(alpha = 0.65f))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, color = TextMidGray, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ResolutionCard(res: FactResolution) {
    val (bgColor, textColor, badge) = when (res.action) {
        FactAction.ADD -> Triple(PastelMintBadge, MintAccentDark, "ADD: NEW FACT STORED")
        FactAction.UPDATE -> Triple(PastelPurpleBadge, PurpleAccentStart, "UPDATE: OUTDATED FACT REPLACED")
        FactAction.DELETE -> Triple(Color(0xFFFFE5EA), RedError, "DELETE: FACT CONTRADICTED")
        FactAction.NOOP -> Triple(PastelYellowBadge, Color(0xFFD97706), "NOOP: DUPLICATE PREVENTED")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor.copy(alpha = 0.7f))
            .padding(14.dp)
    ) {
        Text(badge, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(res.fact, color = TextCharcoal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(res.reason, color = TextMidGray, fontSize = 11.sp)
    }
}

@Composable
private fun PluginRegistryTab(registry: com.soltini.app.plugins.PluginRegistry?) {
    val plugins = registry?.getAllPlugins() ?: emptyList()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Summary Header Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Text("Dynamic Capability System", color = TextCharcoal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "The Plugin Registry decouples capabilities from the brain. MYRA queries this registry dynamically without hardcoded switches. Includes ${plugins.size} modular tools & agents + 1,756+ public APIs across 51 categories.",
                color = TextMidGray,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }

        // Plugins List
        Text(
            "Active Registered Capabilities (${plugins.size})",
            color = TextCharcoal,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        plugins.forEach { plugin ->
            val meta = plugin.metadata
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(meta.name, color = TextCharcoal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    GlassStatusBadge(text = meta.category.name, style = BadgeStyle.PURPLE)
                }

                Spacer(Modifier.height(6.dp))
                Text(meta.description, color = TextMidGray, fontSize = 12.sp, lineHeight = 17.sp)

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ID: ${meta.id}", color = TextMidGray, fontSize = 11.sp)
                    if (meta.fallbackPluginId != null) {
                        Text("Fallback: ${meta.fallbackPluginId}", color = PurpleAccentStart, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TierRow(title: String, desc: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = TextCharcoal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(desc, color = TextMidGray, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
