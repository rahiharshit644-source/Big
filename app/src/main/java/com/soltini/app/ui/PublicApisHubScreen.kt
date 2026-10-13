package com.soltini.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.apis.PublicApiEntry
import com.soltini.app.apis.PublicApiExecutor
import com.soltini.app.apis.PublicApiRegistry
import com.soltini.app.ui.components.*
import com.soltini.app.ui.theme.*
import kotlinx.coroutines.launch

private data class QuickLiveTestAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeBg: Color,
    val badgeTint: Color
)

@Composable
fun PublicApisHubScreen(
    onBack: () -> Unit,
    onAskMyra: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val registry = remember { PublicApiRegistry.getInstance(context) }
    val executor = remember { PublicApiExecutor(context) }

    val totalCount = remember { registry.getTotalApisCount() }
    val categories = remember {
        registry.getCategories().map {
            it.optString("category", "General") to it.optInt("count", 0)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var zeroAuthOnly by remember { mutableStateOf(false) }

    // Live Tester State
    var isTesting by remember { mutableStateOf(false) }
    var activeTestLabel by remember { mutableStateOf<String?>(null) }
    var liveResponseJson by remember { mutableStateOf<String?>(null) }
    var customEndpointInput by remember { mutableStateOf("") }
    var showCustomEndpointTester by remember { mutableStateOf(false) }

    val quickLiveTests = remember {
        listOf(
            QuickLiveTestAction("btc", "Bitcoin Price", "CoinGecko USD/24h", Icons.Default.CurrencyBitcoin, BadgeYellowBg, BadgeYellowTint),
            QuickLiveTestAction("weather", "Delhi Weather", "wttr.in Live Forecast", Icons.Default.Cloud, BadgeBlueBg, BadgeBlueTint),
            QuickLiveTestAction("forex", "USD → INR Rate", "Frankfurter Forex", Icons.Default.CurrencyExchange, BadgeMintBg, SecondaryMintDark),
            QuickLiveTestAction("cat", "Cat Fact", "catfact.ninja", Icons.Default.Pets, BadgePurpleBg, BadgePurpleTint),
            QuickLiveTestAction("joke", "Random Joke", "JokeAPI v2 Safe", Icons.Default.SentimentVerySatisfied, BadgePinkBg, BadgePinkTint),
            QuickLiveTestAction("advice", "Daily Advice", "Advice Slip API", Icons.Default.Lightbulb, BadgeYellowBg, BadgeYellowTint),
            QuickLiveTestAction("dict", "Define 'Serendipity'", "Free Dictionary API", Icons.Default.MenuBook, BadgePurpleBg, BadgePurpleTint),
            QuickLiveTestAction("ip", "My IP & Geo", "ip-api.com", Icons.Default.Public, BadgeMintBg, SecondaryMintDark)
        )
    }

    fun runLiveTest(actionId: String, label: String) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        isTesting = true
        activeTestLabel = label
        scope.launch {
            val result = when (actionId) {
                "btc" -> executor.fetchCryptoPrice("bitcoin", "usd")
                "weather" -> executor.fetchWeather("Delhi")
                "forex" -> executor.fetchCurrencyRate("USD", "INR")
                "cat" -> executor.fetchCatFact()
                "joke" -> executor.fetchRandomJoke()
                "advice" -> executor.fetchRandomAdvice()
                "dict" -> executor.lookupDictionaryWord("serendipity")
                "ip" -> executor.lookupIp()
                else -> executor.executeApi(actionId)
            }
            liveResponseJson = try {
                result.toString(2)
            } catch (_: Exception) {
                result.toString()
            }
            isTesting = false
        }
    }

    val displayedApis = remember(searchQuery, selectedCategory, zeroAuthOnly) {
        val catFilter = if (selectedCategory == "All") null else selectedCategory
        if (searchQuery.isNotBlank()) {
            registry.searchApis(
                query = searchQuery,
                categoryFilter = catFilter,
                requireNoAuth = zeroAuthOnly,
                limit = 80
            )
        } else {
            val baseList = if (catFilter != null) {
                registry.getApisByCategory(catFilter)
            } else {
                categories.take(8).flatMap { registry.getApisByCategory(it.first).take(10) }
            }
            if (zeroAuthOnly) baseList.filter { !it.requiresAuth } else baseList
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoftBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 104.dp)
        ) {
            // 1. Top Floating Glass Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
                            shape = CircleShape,
                            color = GlassCardWhite,
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(4.dp, CircleShape, ambientColor = PrimaryPurple.copy(alpha = 0.14f))
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
                                text = "Public APIs Explorer",
                                color = TextCharcoal,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$totalCount APIs across ${categories.size} categories",
                                color = TextMidGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    GlassStatusBadge(
                        text = "${categories.size} Categories",
                        isActive = true
                    )
                }
            }

            // 2. Instant Zero-Auth Live Playground Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 26.dp,
                    elevation = 7.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PastelIconBadge(
                                icon = Icons.Default.Bolt,
                                backgroundColor = BadgeMintBg,
                                iconTint = SecondaryMintDark,
                                size = 42.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "1-Tap Live API Tester",
                                    color = TextCharcoal,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Test real zero-auth endpoints right now",
                                    color = TextMidGray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showCustomEndpointTester = !showCustomEndpointTester
                            },
                            shape = CircleShape,
                            color = BadgePurpleBg
                        ) {
                            Text(
                                text = if (showCustomEndpointTester) "Hide URL" else "Custom URL",
                                color = BadgePurpleTint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(quickLiveTests, key = { it.id }) { item ->
                            GlassQuickActionChip(
                                label = item.title,
                                icon = item.icon,
                                badgeBg = item.badgeBg,
                                badgeTint = item.badgeTint,
                                isSelected = activeTestLabel == item.title,
                                onClick = { runLiveTest(item.id, item.title) }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showCustomEndpointTester,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            GlassPillInput(
                                value = customEndpointInput,
                                onValueChange = { customEndpointInput = it },
                                placeholder = "https://api.coingecko.com/api/v3/ping",
                                leadingIcon = Icons.Default.Http
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            GradientPillButton(
                                text = "Execute GET Request",
                                icon = Icons.Default.PlayArrow,
                                enabled = customEndpointInput.startsWith("http") && !isTesting,
                                onClick = {
                                    runLiveTest(customEndpointInput.trim(), "Custom GET")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                height = 44.dp
                            )
                        }
                    }

                    if (isTesting) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = BadgePurpleTint
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Fetching live response for $activeTestLabel...",
                                color = TextMidGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = liveResponseJson != null && !isTesting,
                        enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Live Response: ${activeTestLabel ?: ""}",
                                    color = SecondaryMintDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("API Response", liveResponseJson ?: ""))
                                            Toast.makeText(context, "Copied JSON!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = CircleShape,
                                        color = BadgePurpleBg
                                    ) {
                                        Text(
                                            text = "Copy",
                                            color = BadgePurpleTint,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = { liveResponseJson = null },
                                        shape = CircleShape,
                                        color = GlassSurfaceSubtle
                                    ) {
                                        Text(
                                            text = "Clear",
                                            color = TextMidGray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                color = CodeBlockDarkBg,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = liveResponseJson ?: "",
                                        color = CodeBlockTextLight,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier
                                            .padding(14.dp)
                                            .horizontalScroll(rememberScrollState())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. White Pill Search Bar + No-Auth Toggle Filter
            item {
                GlassPillSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search 1,756+ APIs (e.g. weather, nasa, anime, stock)...",
                    leadingIcon = Icons.Default.Search,
                    trailingContent = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextMidGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )
            }

            // 4. Category Filter Pills + Zero-Auth Switch Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Browse Categories",
                            color = TextCharcoal,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Zero-Auth Only",
                                color = TextMidGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassSwitch(
                                checked = zeroAuthOnly,
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    zeroAuthOnly = it
                                }
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            CategoryFilterPill(
                                label = "All ($totalCount)",
                                selected = selectedCategory == "All",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCategory = "All"
                                }
                            )
                        }
                        items(categories, key = { it.first }) { (catName, count) ->
                            CategoryFilterPill(
                                label = "$catName ($count)",
                                selected = selectedCategory == catName,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCategory = catName
                                }
                            )
                        }
                    }
                }
            }

            // 5. Results Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "Matching APIs (${displayedApis.size})"
                        } else if (selectedCategory != "All") {
                            "$selectedCategory APIs (${displayedApis.size})"
                        } else {
                            "Featured Catalog Preview (${displayedApis.size})"
                        },
                        color = TextCharcoal,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 6. API Entry Cards
            items(displayedApis, key = { "${it.category}_${it.name}_${it.link}" }) { api ->
                PublicApiItemCard(
                    api = api,
                    onOpenDocs = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (api.link.isNotBlank()) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.link)))
                            } catch (_: Exception) {}
                        }
                    },
                    onTryWithMyra = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val prompt = "Use ${api.name} API (${api.category}) to fetch live information: ${api.description}"
                        if (onAskMyra != null) {
                            onAskMyra(prompt)
                        } else {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Myra API Prompt", prompt))
                            Toast.makeText(context, "Prompt copied for Myra Voice!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .shadow(
                elevation = if (selected) 5.dp else 3.dp,
                shape = CircleShape,
                ambientColor = PrimaryPurple.copy(alpha = 0.16f)
            )
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(PrimaryHorizontalBrush, CircleShape)
                else Modifier.background(GlassCardWhite, CircleShape)
            )
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextCharcoal,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PublicApiItemCard(
    api: PublicApiEntry,
    onOpenDocs: () -> Unit,
    onTryWithMyra: () -> Unit
) {
    val isNoAuth = !api.requiresAuth

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = 5.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            PastelIconBadge(
                icon = if (isNoAuth) Icons.Default.LockOpen else Icons.Default.VpnKey,
                backgroundColor = if (isNoAuth) BadgeMintBg else BadgePurpleBg,
                iconTint = if (isNoAuth) SecondaryMintDark else BadgePurpleTint,
                size = 44.dp,
                iconSize = 20.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = api.name,
                        color = TextCharcoal,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassStatusBadge(
                        text = if (isNoAuth) "No Auth" else api.auth,
                        isActive = isNoAuth,
                        showDot = isNoAuth
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = api.description,
                    color = TextMidGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = BadgePurpleBg
                        ) {
                            Text(
                                text = api.category,
                                color = BadgePurpleTint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                        if (api.https.equals("Yes", ignoreCase = true)) {
                            Surface(
                                shape = CircleShape,
                                color = GlassSurfaceSubtle
                            ) {
                                Text(
                                    text = "HTTPS",
                                    color = TextMidGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = onOpenDocs,
                            shape = CircleShape,
                            color = GlassSurfaceSubtle
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Docs",
                                    tint = TextCharcoal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Docs",
                                    color = TextCharcoal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            onClick = onTryWithMyra,
                            shape = CircleShape,
                            color = BadgePurpleBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Ask Myra",
                                    tint = BadgePurpleTint,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ask Myra",
                                    color = BadgePurpleTint,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
