package com.soltini.app.browser

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soltini.app.media.MyraPlaybackContext
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassQuickActionChip
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.GradientCircleButton
import com.soltini.app.ui.components.GradientPillButton
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.components.glassTextFieldColors
import com.soltini.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QuickShortcut(
    val name: String,
    val url: String,
    val iconLabel: String,
    val gradient: List<Color>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyraBrowserScreen(
    onOpenVoiceAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val browserController = remember { BrowserController.getInstance(context) }
    val themeManager = remember { BrowserThemeManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentTheme by themeManager.currentTheme.collectAsStateWithLifecycle()
    val currentWallpaper by themeManager.currentWallpaper.collectAsStateWithLifecycle()
    val wallpaperDim by themeManager.wallpaperDim.collectAsStateWithLifecycle()
    val adBlockCount by themeManager.adBlockerCount.collectAsStateWithLifecycle()

    val currentUrl by browserController.currentUrl.collectAsStateWithLifecycle()
    val pageTitle by browserController.pageTitle.collectAsStateWithLifecycle()
    val isLoading by browserController.isLoading.collectAsStateWithLifecycle()
    val statusMessage by browserController.statusMessage.collectAsStateWithLifecycle()
    val canGoBack by browserController.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by browserController.canGoForward.collectAsStateWithLifecycle()
    val isDesktopMode by browserController.isDesktopMode.collectAsStateWithLifecycle()

    val playbackInfo by MyraPlaybackContext.state.collectAsStateWithLifecycle()

    var urlInputText by remember { mutableStateOf("") }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var showAddShortcutDialog by remember { mutableStateOf(false) }

    var currentTimeStr by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            currentDateStr = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
            delay(1000L)
        }
    }

    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
            urlInputText = currentUrl
        }
    }

    BackHandler(enabled = canGoBack) {
        browserController.goBackManual()
    }

    val customShortcuts = remember {
        mutableStateListOf(
            QuickShortcut("YouTube", "https://m.youtube.com", "▶", listOf(Color(0xFFFF7A8A), Color(0xFFF43F5E))),
            QuickShortcut("Google", "https://www.google.com", "G", listOf(Color(0xFF7CB8F7), Color(0xFF5B8DEF))),
            QuickShortcut("Spotify", "https://open.spotify.com", "♫", listOf(Color(0xFF8BE2B5), Color(0xFF249E6B))),
            QuickShortcut("Instagram", "https://www.instagram.com", "📷", listOf(Color(0xFFB993F0), Color(0xFFE8A0E0))),
            QuickShortcut("ChatGPT", "https://chatgpt.com", "AI", listOf(Color(0xFFA9E8C9), Color(0xFF34D399))),
            QuickShortcut("Netflix", "https://www.netflix.com", "N", listOf(Color(0xFFFB7185), Color(0xFFE11D48))),
            QuickShortcut("GitHub", "https://github.com", "GH", listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))),
            QuickShortcut("Amazon", "https://www.amazon.in", "🛍", listOf(Color(0xFFFBBF24), Color(0xFFF59E0B)))
        )
    }

    val voiceAiPrompts = remember {
        listOf(
            "🎵 Arijit Singh ke songs chalao",
            "🚀 Latest AI technology news",
            "🐍 Python tutorial YouTube",
            "🌦️ Delhi weather report",
            "🎬 Trending movie trailers",
            "🪙 Bitcoin & Crypto price"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(SoftBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // 0. Background Wallpaper Layer (soft pastel gradient or frosted art)
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentWallpaper.type) {
                WallpaperType.DRAWABLE_RES -> {
                    currentWallpaper.drawableRes?.let { resId ->
                        Image(
                            painter = painterResource(id = resId),
                            contentDescription = "Wallpaper",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Frosted light veil over dark art wallpapers so light text/cards stay airy
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BgLavender.copy(alpha = (1f - wallpaperDim).coerceIn(0.65f, 0.92f)))
                    )
                }
                WallpaperType.GRADIENT -> {
                    val colors = currentWallpaper.gradientColors ?: listOf(BgLavender, BgMidLilac, BgMint)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(colors))
                    )
                }
                WallpaperType.SOLID -> {
                    val col = currentWallpaper.solidColor ?: BgLavender
                    Box(modifier = Modifier.fillMaxSize().background(col))
                }
            }
        }

        // Main Column Content
        Column(modifier = Modifier.fillMaxSize()) {

            // 1. Floating Glass Top Omnibox & Navigation Header
            Surface(
                color = GlassCardWhite,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = PrimaryPurple.copy(alpha = 0.16f),
                        spotColor = PrimaryPurple.copy(alpha = 0.22f)
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Back
                        IconButton(
                            onClick = { browserController.goBackManual() },
                            enabled = canGoBack,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (canGoBack) TextCharcoal else TextMidGray.copy(alpha = 0.35f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Forward
                        IconButton(
                            onClick = { browserController.goForwardManual() },
                            enabled = canGoForward,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (canGoForward) TextCharcoal else TextMidGray.copy(alpha = 0.35f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Reload / Stop
                        IconButton(
                            onClick = {
                                if (isLoading) browserController.stop() else browserController.reload()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                contentDescription = if (isLoading) "Stop" else "Reload",
                                tint = BadgePurpleTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // White Pill Omnibox Search Field
                        Surface(
                            shape = CircleShape,
                            color = GlassSurfaceSubtle,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            OutlinedTextField(
                                value = urlInputText,
                                onValueChange = { urlInputText = it },
                                placeholder = {
                                    Text(
                                        "Search or enter address...",
                                        fontSize = 12.sp,
                                        color = TextMidGray,
                                        maxLines = 1
                                    )
                                },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (currentUrl.startsWith("https")) Icons.Default.Lock else Icons.Default.Search,
                                        contentDescription = "Security",
                                        tint = if (currentUrl.startsWith("https")) SecondaryMintDark else BadgePurpleTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (urlInputText.isNotBlank()) {
                                        IconButton(
                                            onClick = { urlInputText = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = TextMidGray,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        keyboardController?.hide()
                                        browserController.navigateUserUrl(urlInputText)
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = TextCharcoal,
                                    unfocusedTextColor = TextCharcoal
                                ),
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("browser_omnibox")
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Theme / Wallpaper Customizer
                        IconButton(
                            onClick = { showThemeDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Themes & Wallpaper",
                                tint = BadgePurpleTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Desktop Mode Toggle
                        IconButton(
                            onClick = { browserController.toggleDesktopMode() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isDesktopMode) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                                contentDescription = "Desktop Site",
                                tint = if (isDesktopMode) SecondaryMintDark else TextMidGray,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Home Button
                        IconButton(
                            onClick = {
                                urlInputText = ""
                                browserController.navigateUserUrl("about:blank")
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = TextMidGray,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Status Bar & Ad-Shield Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 6.dp, end = 6.dp)
                    ) {
                        Surface(
                            color = BadgeMintBg,
                            shape = CircleShape
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Ad Shield",
                                    tint = SecondaryMintDark,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Shield Active ($adBlockCount blocked)",
                                    color = SecondaryMintDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (statusMessage.isNotBlank()) statusMessage else "Myra Browser Ready",
                            color = TextMidGray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { showVaultDialog = true },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Vault",
                                tint = BadgePurpleTint,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (isLoading) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape),
                            color = PrimaryPurple,
                            trackColor = SoftDivider
                        )
                    }
                }
            }

            // 2. Live Media Mini-Player Floating Card
            AnimatedVisibility(visible = playbackInfo.title.isNotBlank()) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    cornerRadius = 20.dp,
                    elevation = 5.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryGradientBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackInfo.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playbackInfo.title.ifBlank { "Media Playback" },
                                color = TextCharcoal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = playbackInfo.artist.ifBlank { "Myra Internal Browser" },
                                color = SecondaryMintDark,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { browserController.togglePlayPauseMedia() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = BadgePurpleTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { browserController.nextMediaDirect() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = TextCharcoal,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { browserController.stopMediaDirect() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = RedError,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 3. Main Viewport (WebView OR Start Dashboard)
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                val isBlank = currentUrl.isBlank() || currentUrl == "about:blank"

                // 3.A Android WebView
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            val wv = browserController.obtainWebView()
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            browserController.attachContext(ctx)
                            addView(
                                wv,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            wv.onResume()
                        }
                    },
                    onRelease = { container ->
                        container.removeAllViews()
                        browserController.detachContext()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isBlank) Color.Transparent else Color.White)
                )

                // 3.B Start / Home Dashboard
                if (isBlank) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
                    ) {
                        item {
                            // Live Clock & Aesthetic Greeting
                            Text(
                                text = currentTimeStr,
                                color = TextCharcoal,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            GlassStatusBadge(
                                text = currentDateStr.ifBlank { "Myra Companion Browser" },
                                isActive = true
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Floating White Pill Search Bar
                            Surface(
                                shape = CircleShape,
                                color = GlassCardSolid,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = CircleShape,
                                        ambientColor = PrimaryPurple.copy(alpha = 0.16f),
                                        spotColor = PrimaryPurple.copy(alpha = 0.22f)
                                    )
                            ) {
                                OutlinedTextField(
                                    value = urlInputText,
                                    onValueChange = { urlInputText = it },
                                    placeholder = {
                                        Text(
                                            "Search Google or enter website URL...",
                                            fontSize = 14.sp,
                                            color = TextMidGray
                                        )
                                    },
                                    leadingIcon = {
                                        PastelIconBadge(
                                            icon = Icons.Default.Search,
                                            backgroundColor = BadgePurpleBg,
                                            iconTint = BadgePurpleTint,
                                            size = 34.dp,
                                            iconSize = 18.dp
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            keyboardController?.hide()
                                            browserController.navigateUserUrl(urlInputText)
                                        }
                                    ),
                                    shape = CircleShape,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = TextCharcoal,
                                        unfocusedTextColor = TextCharcoal
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Quick Shortcuts Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Quick Launch Shortcuts",
                                    color = TextCharcoal,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Surface(
                                    onClick = { showAddShortcutDialog = true },
                                    shape = CircleShape,
                                    color = BadgePurpleBg
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = BadgePurpleTint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Add",
                                            color = BadgePurpleTint,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Shortcuts Grid inside a Floating Glass Card
                        item {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 26.dp,
                                elevation = 6.dp,
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                customShortcuts.chunked(4).forEachIndexed { idx, rowItems ->
                                    if (idx > 0) Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        for (sc in rowItems) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { browserController.navigateUserUrl(sc.url) }
                                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(50.dp)
                                                        .shadow(
                                                            elevation = 5.dp,
                                                            shape = CircleShape,
                                                            ambientColor = PrimaryPurple.copy(alpha = 0.2f)
                                                        )
                                                        .clip(CircleShape)
                                                        .background(Brush.linearGradient(sc.gradient)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = sc.iconLabel,
                                                        color = Color.White,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = sc.name,
                                                    color = TextCharcoal,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(22.dp))

                            // Voice AI Quick Action Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = BadgePurpleTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "One-Tap Voice Prompts",
                                    color = TextCharcoal,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (prompt in voiceAiPrompts) {
                                    GlassQuickActionChip(
                                        label = prompt,
                                        icon = Icons.Default.AutoAwesome,
                                        badgeBg = BadgePurpleBg,
                                        badgeTint = BadgePurpleTint,
                                        onClick = {
                                            if (prompt.contains("Arijit") || prompt.contains("songs")) {
                                                scope.launch { browserController.playMediaOnYouTube("Arijit Singh songs") }
                                            } else if (prompt.contains("AI technology")) {
                                                scope.launch { browserController.searchWeb("latest AI news") }
                                            } else if (prompt.contains("Python")) {
                                                scope.launch { browserController.playMediaOnYouTube("Python tutorial full course") }
                                            } else if (prompt.contains("weather")) {
                                                scope.launch { browserController.searchWeb("Delhi weather today") }
                                            } else if (prompt.contains("trailers")) {
                                                scope.launch { browserController.playMediaOnYouTube("new movie trailers 2026") }
                                            } else {
                                                scope.launch { browserController.searchWeb("Bitcoin crypto price today") }
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            // Theme & Wallpaper Customization Floating Card
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 24.dp,
                                elevation = 6.dp,
                                onClick = { showThemeDialog = true }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PastelIconBadge(
                                        icon = Icons.Default.Palette,
                                        backgroundColor = BadgePinkBg,
                                        iconTint = BadgePinkTint,
                                        size = 46.dp
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Browser Style & Wallpapers",
                                            color = TextCharcoal,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${currentTheme.emoji} ${currentTheme.name} • ${currentWallpaper.name}",
                                            color = TextMidGray,
                                            fontSize = 12.sp
                                        )
                                    }

                                    GradientPillButton(
                                        text = "Customize",
                                        onClick = { showThemeDialog = true },
                                        height = 36.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Floating Glass Action Toolbar
            Surface(
                shape = CircleShape,
                color = GlassCardWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = PrimaryPurple.copy(alpha = 0.14f)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            urlInputText = ""
                            browserController.navigateUserUrl("about:blank")
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = TextCharcoal, modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = "Wallpaper", tint = BadgePinkTint, modifier = Modifier.size(20.dp))
                    }

                    GradientCircleButton(
                        icon = Icons.Default.Mic,
                        contentDescription = "Voice Assistant",
                        onClick = onOpenVoiceAi,
                        size = 46.dp,
                        iconSize = 22.dp
                    )

                    IconButton(
                        onClick = { browserController.reload() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TextCharcoal, modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { showVaultDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = "Vault", tint = BadgePurpleTint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    // Dialog 1: Theme & Wallpaper Customizer
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = GlassCardSolid,
            shape = RoundedCornerShape(26.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PastelIconBadge(
                        icon = Icons.Default.Palette,
                        backgroundColor = BadgePurpleBg,
                        iconTint = BadgePurpleTint,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Themes & Wallpapers",
                        color = TextCharcoal,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                    item {
                        Text(
                            "1. Color Accent Theme",
                            color = BadgePurpleTint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(BrowserThemeManager.THEMES) { theme ->
                        val isSelected = theme.id == currentTheme.id
                        Surface(
                            onClick = { themeManager.setTheme(theme) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) BadgePurpleBg else GlassSurfaceSubtle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(theme.emoji, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        theme.name,
                                        color = TextCharcoal,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(theme.gradientColors))
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "2. Wallpaper & Background",
                            color = BadgePurpleTint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(BrowserThemeManager.WALLPAPERS) { wp ->
                        val isSelected = wp.id == currentWallpaper.id
                        Surface(
                            onClick = { themeManager.setWallpaper(wp) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) BadgeMintBg else GlassSurfaceSubtle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(wp.previewColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Wallpaper,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        wp.name,
                                        color = TextCharcoal,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(wp.category, color = TextMidGray, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "3. Wallpaper Softness",
                            color = BadgePurpleTint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = wallpaperDim,
                            onValueChange = { themeManager.setWallpaperDim(it) },
                            valueRange = 0.05f..0.75f,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryPurple,
                                activeTrackColor = PrimaryPurple,
                                inactiveTrackColor = SoftDivider
                            )
                        )
                    }
                }
            },
            confirmButton = {
                GradientPillButton(
                    text = "Done",
                    onClick = { showThemeDialog = false },
                    height = 40.dp
                )
            }
        )
    }

    // Dialog 2: Add Custom Shortcut
    if (showAddShortcutDialog) {
        var newName by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddShortcutDialog = false },
            containerColor = GlassCardSolid,
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    "Add Website Shortcut",
                    color = TextCharcoal,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Site Name (e.g. Cricbuzz)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("URL (e.g. cricbuzz.com)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                GradientPillButton(
                    text = "Add Shortcut",
                    onClick = {
                        if (newName.isNotBlank() && newUrl.isNotBlank()) {
                            val fullUrl = if (!newUrl.startsWith("http")) "https://$newUrl" else newUrl
                            val initial = newName.take(2).uppercase()
                            customShortcuts.add(
                                QuickShortcut(
                                    name = newName,
                                    url = fullUrl,
                                    iconLabel = initial,
                                    gradient = listOf(PrimaryPurple, PrimaryPink)
                                )
                            )
                            showAddShortcutDialog = false
                        }
                    },
                    height = 40.dp
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddShortcutDialog = false }) {
                    Text("Cancel", color = TextMidGray)
                }
            }
        )
    }

    // Dialog 3: Credential Vault Dialog
    if (showVaultDialog) {
        val creds = remember { browserController.getAllCredentials() }
        AlertDialog(
            onDismissRequest = { showVaultDialog = false },
            containerColor = GlassCardSolid,
            shape = RoundedCornerShape(26.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PastelIconBadge(
                        icon = Icons.Default.Key,
                        backgroundColor = BadgePurpleBg,
                        iconTint = BadgePurpleTint,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Password & ID Vault",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal
                    )
                }
            },
            text = {
                if (creds.isEmpty()) {
                    Text(
                        "No saved login credentials yet. Tell Myra: 'Is site ke credentials save kar lo' to store logins securely.",
                        color = TextMidGray,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                        items(creds.entries.toList()) { (domain, obj) ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = GlassSurfaceSubtle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            domain,
                                            color = BadgePurpleTint,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "User: " + obj.optString("username"),
                                            color = TextCharcoal,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "Pass: ••••••••",
                                            color = TextMidGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            browserController.deleteCredential(domain)
                                            showVaultDialog = false
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = RedError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVaultDialog = false }) {
                    Text("Close", color = BadgePurpleTint, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}
