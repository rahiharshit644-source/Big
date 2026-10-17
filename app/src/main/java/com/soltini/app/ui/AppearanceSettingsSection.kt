package com.soltini.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassSectionHeader
import com.soltini.app.ui.theme.LocalMyraTokens
import com.soltini.app.ui.theme.MyraThemeMode
import com.soltini.app.ui.theme.MyraTokens
import com.soltini.app.ui.theme.ThemeController
import com.soltini.app.ui.theme.tokensFor

/**
 * Settings → Appearance & Themes.
 * Two selectable cards (Classic MYRA / Blue Glass). Selecting one applies it immediately and persists it
 * through [ThemeController]; nothing else (data, keys, permissions) is touched.
 * The screen itself is drawn with the active theme's tokens.
 */
@Composable
fun AppearanceSettingsSection() {
    val context = LocalContext.current
    val current by ThemeController.mode.collectAsState()
    val tokens = LocalMyraTokens.current

    Column(modifier = Modifier.fillMaxWidth()) {
        GlassSectionHeader(
            title = "Appearance & Themes",
            subtitle = "Pick how MYRA looks. You can switch any time; your data and settings are not affected."
        )
        Spacer(Modifier.height(12.dp))

        MyraThemeMode.entries.forEach { mode ->
            ThemeOptionCard(
                mode = mode,
                isSelected = current == mode,
                onSelect = { ThemeController.set(context, mode) }
            )
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = "Classic MYRA is the original look and stays fully supported. Blue Glass is optional and currently styles the shared backgrounds and glass cards; more screens are being migrated.",
            color = tokens.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ThemeOptionCard(mode: MyraThemeMode, isSelected: Boolean, onSelect: () -> Unit) {
    val active = LocalMyraTokens.current
    val preview = tokensFor(mode)
    val stateText = if (isSelected) "selected" else "not selected"

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${mode.displayName} theme, $stateText" },
        onClick = onSelect,
        contentPadding = 14.dp
    ) {
        ThemePreview(preview)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mode.displayName, color = active.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(mode.description, color = active.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            // Explicit selection action + state indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (isSelected) Modifier.background(active.accentGradient)
                        else Modifier.border(1.dp, active.accent, RoundedCornerShape(50))
                    )
                    .clickable(role = Role.Button, onClick = onSelect)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isSelected) "Selected" else "Use this",
                        color = if (isSelected) Color.White else active.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Small, real-Compose mock of a screen in the given theme (not a screenshot). */
@Composable
private fun ThemePreview(t: MyraTokens) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(t.backgroundBrush)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(t.accentGradient))
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.width(54.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(t.textPrimary.copy(alpha = 0.75f)))
                    Box(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(4.dp)).background(t.textSecondary.copy(alpha = 0.6f)))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(t.cardColor)
                    .then(t.cardBorder?.let { Modifier.border(it, RoundedCornerShape(14.dp)) } ?: Modifier)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(t.accent.copy(alpha = 0.85f)))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(t.textPrimary.copy(alpha = 0.55f)))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(26.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(t.accentGradient))
            }
        }
    }
}
