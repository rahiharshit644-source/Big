package com.soltini.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.security.*
import com.soltini.app.ui.components.BadgeStyle
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassCodeBlockContainer
import com.soltini.app.ui.components.GlassGradientButton
import com.soltini.app.ui.components.GlassSecondaryButton
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.GlassSwitch
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val AmberWarning = Color(0xFFD97706)

/**
 * VoiceProfilesSection
 *
 * Dedicated Soft Light Glassmorphic interface for Multi-User Voice Profiles,
 * Profile-Scoped Access Matrices, and Emergency Biometric Controls.
 */
@Composable
fun VoiceProfilesSection(context: Context) {
    val profileManager = remember { VoiceProfileManager.getInstance(context) }
    var profiles by remember { mutableStateOf(profileManager.getAllProfiles()) }
    var isEmergencyDisabled by remember { mutableStateOf(profileManager.isEmergencyDisabled()) }
    var voiceCommandsActive by remember { mutableStateOf(profileManager.areVoiceCommandsActive()) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showAuditLogs by remember { mutableStateOf(false) }
    var auditLogs by remember { mutableStateOf(profileManager.getAuditLogs()) }

    fun refreshState() {
        profiles = profileManager.getAllProfiles()
        isEmergencyDisabled = profileManager.isEmergencyDisabled()
        voiceCommandsActive = profileManager.areVoiceCommandsActive()
        auditLogs = profileManager.getAuditLogs()
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.RecordVoiceOver,
                badgeColor = PastelPurpleBadge,
                iconTint = PurpleAccentStart,
                size = 46.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Voice Profiles & Permission System",
                    color = LocalMyraTokens.current.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "Multi-user speaker verification, profile permissions & Keystore encryption",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Non-bypass Security Guarantee notice
        Surface(
            color = PastelYellowBadge.copy(alpha = 0.65f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 1.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Security Principle: Voice biometrics identifies the speaker to enforce Myra's internal application permissions. Myra never bypasses or attempts to unlock the Android system lock screen.",
                    color = LocalMyraTokens.current.textPrimary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profiles List Header + Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Enrolled Voice Profiles (${profiles.size})",
                color = LocalMyraTokens.current.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            GlassSecondaryButton(
                text = "Add Profile",
                icon = Icons.Default.PersonAdd,
                onClick = { showAddProfileDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Display Profiles
        profiles.forEach { profile ->
            VoiceProfileItemCard(
                profile = profile,
                onDelete = {
                    profileManager.deleteProfile(profile.id)
                    refreshState()
                },
                onEnroll = {
                    VoiceUnlockActivity.startEnrollment(context, profile.id)
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Test Speaker ID Button
        GlassSecondaryButton(
            text = "Test Live Speaker Identification",
            icon = Icons.Default.Mic,
            onClick = {
                VoiceUnlockActivity.startForBankingToggle(context)
            },
            modifier = Modifier.fillMaxWidth(),
            containerColor = PastelPurpleBadge,
            contentColor = PurpleAccentStart
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Emergency & Security Controls
        Text(
            "Emergency & Safety Controls",
            color = LocalMyraTokens.current.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Emergency Disable Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    "Emergency Disable Myra",
                    color = if (isEmergencyDisabled) RedError else TextCharcoal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Instantly locks all actions and blocks all assistant voice commands",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 11.sp
                )
            }
            GlassSwitch(
                checked = isEmergencyDisabled,
                onCheckedChange = { checked ->
                    profileManager.setEmergencyDisabled(checked)
                    refreshState()
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Voice Commands Active Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    "Voice Commands Processing",
                    color = LocalMyraTokens.current.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Toggle speech command processing without resetting enrolled models",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 11.sp
                )
            }
            GlassSwitch(
                checked = voiceCommandsActive,
                onCheckedChange = { checked ->
                    profileManager.setVoiceCommandsActive(checked)
                    refreshState()
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lock / Wipe Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassSecondaryButton(
                text = "Lock Profiles",
                icon = Icons.Default.Lock,
                onClick = {
                    profileManager.lockAllProfiles()
                    refreshState()
                },
                modifier = Modifier.weight(1f),
                containerColor = PastelPurpleBadge,
                contentColor = PurpleAccentStart
            )

            GlassSecondaryButton(
                text = "Wipe Biometrics",
                icon = Icons.Default.DeleteForever,
                onClick = { showWipeConfirmDialog = true },
                modifier = Modifier.weight(1f),
                containerColor = Color(0xFFFFE5EA),
                contentColor = RedError
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audit Log Expandable Section (Dark monospace inside light glass container)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF6F3FC)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAuditLogs = !showAuditLogs },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PastelIconBadge(
                            icon = Icons.Default.History,
                            badgeColor = PastelPurpleBadge,
                            iconTint = PurpleAccentStart,
                            size = 32.dp,
                            iconSize = 16.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Minimal Security Audit Log (${auditLogs.size})",
                            color = LocalMyraTokens.current.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        if (showAuditLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMidGray
                    )
                }

                AnimatedVisibility(visible = showAuditLogs) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        GlassCodeBlockContainer(title = "SECURITY AUDIT STREAM") {
                            if (auditLogs.isEmpty()) {
                                Text(
                                    "No voice authentication attempts logged yet.",
                                    color = Color(0xFF9EA7B3),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
                                val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                                auditLogs.take(10).forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (entry.isSuccess) Color(0xFF4ADE80) else Color(0xFFFBBF24))
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    "${entry.profileName ?: "Unknown"}: ${entry.actionType}",
                                                    color = Color(0xFFE6EDF3),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Text(
                                                entry.reason,
                                                color = Color(0xFF9EA7B3),
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            sdf.format(Date(entry.timestamp)),
                                            color = Color(0xFF9EA7B3),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "🔒 Zero leakage: Voice recordings, embeddings, and message texts are NEVER recorded in logs.",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Dialog: Add New Profile
    if (showAddProfileDialog) {
        var newName by remember { mutableStateOf("") }
        var newRelationship by remember { mutableStateOf("Family Member") }

        AlertDialog(
            onDismissRequest = { showAddProfileDialog = false },
            containerColor = GlassCardWhiteSolid,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "Add Voice Profile",
                    color = LocalMyraTokens.current.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "New profiles default strictly to Family/Restricted access. Private owner data is never exposed.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name (e.g. Papa, Rohan)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newRelationship,
                        onValueChange = { newRelationship = it },
                        label = { Text("Relationship / Role") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                GlassGradientButton(
                    text = "Create Profile",
                    onClick = {
                        if (newName.isNotBlank()) {
                            profileManager.createProfile(newName, newRelationship, ProfileRole.FAMILY)
                            refreshState()
                            showAddProfileDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddProfileDialog = false }) {
                    Text("Cancel", color = LocalMyraTokens.current.textSecondary)
                }
            }
        )
    }

    // Dialog: Confirm Wipe Biometrics
    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            containerColor = GlassCardWhiteSolid,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Wipe All Voice Biometrics?", color = RedError, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will delete all encrypted voice embeddings stored in the Android Keystore. Users will need to re-enroll with 10 phonetic sentences.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                GlassSecondaryButton(
                    text = "Wipe All",
                    onClick = {
                        profileManager.wipeAllBiometrics()
                        refreshState()
                        showWipeConfirmDialog = false
                    },
                    containerColor = Color(0xFFFFE5EA),
                    contentColor = RedError
                )
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Cancel", color = LocalMyraTokens.current.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun VoiceProfileItemCard(
    profile: VoiceProfile,
    onDelete: () -> Unit,
    onEnroll: () -> Unit
) {
    val isOwner = profile.role == ProfileRole.OWNER
    val badgeBg = when (profile.role) {
        ProfileRole.OWNER -> PastelMintBadge
        ProfileRole.FAMILY -> PastelPurpleBadge
        ProfileRole.GUEST -> PastelYellowBadge
    }
    val roleColor = when (profile.role) {
        ProfileRole.OWNER -> MintAccentDark
        ProfileRole.FAMILY -> PurpleAccentStart
        ProfileRole.GUEST -> AmberWarning
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFAF8FE),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAE2F8))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PastelIconBadge(
                        icon = if (isOwner) Icons.Default.VerifiedUser else Icons.Default.Person,
                        badgeColor = badgeBg,
                        iconTint = roleColor,
                        size = 40.dp,
                        iconSize = 20.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                profile.name,
                                color = LocalMyraTokens.current.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            GlassStatusBadge(
                                text = profile.role.name,
                                style = if (isOwner) BadgeStyle.ACTIVE else BadgeStyle.PURPLE
                            )
                        }
                        Text(
                            "${profile.relationship} • ${if (profile.isEnrolled) "Enrolled (${profile.sampleCount}/10 samples)" else "Not Enrolled"}",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (!isOwner) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = RedError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Permission Summary Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PermissionBadge(
                    label = "Low Risk",
                    status = "ALLOW",
                    bgColor = PastelMintBadge,
                    textColor = MintAccentDark
                )
                PermissionBadge(
                    label = "Medium Risk",
                    status = if (isOwner) "ALLOW" else "CONFIRM",
                    bgColor = if (isOwner) PastelMintBadge else PastelPurpleBadge,
                    textColor = if (isOwner) MintAccentDark else PurpleAccentStart
                )
                PermissionBadge(
                    label = "Private Vault",
                    status = if (isOwner) "BIOMETRIC" else "DENIED",
                    bgColor = if (isOwner) PastelYellowBadge else Color(0xFFFFE5EA),
                    textColor = if (isOwner) AmberWarning else RedError
                )
            }

            Spacer(Modifier.height(12.dp))

            // Enroll / Re-enroll Button
            GlassGradientButton(
                text = if (profile.isEnrolled) "Re-enroll Voice (10 Phrases)" else "Enroll Voice (10 Phrases)",
                icon = Icons.Default.Mic,
                onClick = onEnroll,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionBadge(label: String, status: String, bgColor: Color, textColor: Color) {
    Surface(
        color = bgColor.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(label, color = LocalMyraTokens.current.textSecondary, fontSize = 9.sp)
            Text(status, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
