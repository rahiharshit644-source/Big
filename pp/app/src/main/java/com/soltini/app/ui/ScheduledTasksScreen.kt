package com.soltini.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.learning.ExperienceLearningEngine
import com.soltini.app.learning.ExperienceMemory
import com.soltini.app.scheduler.ScheduledTask
import com.soltini.app.scheduler.ScheduledTaskManager
import com.soltini.app.scheduler.ScheduledTaskStatus
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GradientPillButton
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTasksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheduler = remember { ScheduledTaskManager.getInstance(context) }
    val learningEngine = remember { ExperienceLearningEngine.getInstance(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var tasks by remember { mutableStateOf<List<ScheduledTask>>(emptyList()) }
    var experiences by remember { mutableStateOf<List<ExperienceMemory>>(emptyList()) }
    var selectedTaskForDetails by remember { mutableStateOf<ScheduledTask?>(null) }
    var taskToReschedule by remember { mutableStateOf<ScheduledTask?>(null) }

    fun refreshData() {
        try {
            tasks = scheduler.getAllTasks()
        } catch (e: Exception) {
            tasks = emptyList()
        }
        try {
            experiences = learningEngine.getAllLearnedExperiences()
        } catch (e: Exception) {
            experiences = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoftBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = GlassCardWhite,
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(4.dp, CircleShape, ambientColor = PrimaryPurple.copy(alpha = 0.14f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextCharcoal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (selectedTab == 0) "Scheduled Tasks" else "Learned Experiences",
                            color = TextCharcoal,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Autonomous routines & memory patterns",
                            color = TextMidGray,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    onClick = { refreshData() },
                    shape = CircleShape,
                    color = BadgeMintBg,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = SecondaryMintDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Segmented Pill Tabs
            Surface(
                shape = CircleShape,
                color = GlassCardWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .shadow(5.dp, CircleShape, ambientColor = PrimaryPurple.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isTab0 = selectedTab == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .then(
                                if (isTab0) Modifier.background(PrimaryHorizontalBrush, CircleShape)
                                else Modifier.background(Color.Transparent, CircleShape)
                            )
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                tint = if (isTab0) Color.White else TextMidGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Scheduled (${tasks.count { it.isPending }})",
                                color = if (isTab0) Color.White else TextMidGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val isTab1 = selectedTab == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .then(
                                if (isTab1) Modifier.background(PrimaryHorizontalBrush, CircleShape)
                                else Modifier.background(Color.Transparent, CircleShape)
                            )
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = if (isTab1) Color.White else TextMidGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Learned Skills (${experiences.size})",
                                color = if (isTab1) Color.White else TextMidGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 26.dp,
                            contentPadding = PaddingValues(28.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                PastelIconBadge(
                                    icon = Icons.Default.Schedule,
                                    backgroundColor = BadgePurpleBg,
                                    iconTint = BadgePurpleTint,
                                    size = 56.dp,
                                    iconSize = 26.dp
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "No scheduled tasks yet",
                                    color = TextCharcoal,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Say: \"Myra, shaam 7 baje Rahul ko call karna\"",
                                    color = TextMidGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 84.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            ScheduledTaskCard(
                                task = task,
                                onCancel = {
                                    scheduler.cancelTask(task.id)
                                    refreshData()
                                },
                                onRetry = {
                                    scheduler.retryTask(task.id)
                                    refreshData()
                                },
                                onDelete = {
                                    scheduler.deleteTask(task.id)
                                    refreshData()
                                },
                                onReschedule = {
                                    taskToReschedule = task
                                },
                                onClickDetails = {
                                    selectedTaskForDetails = task
                                }
                            )
                        }
                    }
                }
            } else {
                if (experiences.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 26.dp,
                            contentPadding = PaddingValues(28.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                PastelIconBadge(
                                    icon = Icons.Default.AutoFixHigh,
                                    backgroundColor = BadgePinkBg,
                                    iconTint = BadgePinkTint,
                                    size = 56.dp,
                                    iconSize = 26.dp
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "No learned patterns yet",
                                    color = TextCharcoal,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Myra learns autonomously from tasks and user corrections.",
                                    color = TextMidGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 84.dp)
                    ) {
                        items(experiences, key = { it.id }) { exp ->
                            LearnedExperienceCard(
                                experience = exp,
                                onDelete = {
                                    learningEngine.deleteExperience(exp.id)
                                    refreshData()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedTaskForDetails != null) {
        val t = selectedTaskForDetails!!
        AlertDialog(
            onDismissRequest = { selectedTaskForDetails = null },
            containerColor = GlassCardSolid,
            shape = RoundedCornerShape(26.dp),
            title = { Text(t.title, color = TextCharcoal, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Command: \"${t.command}\"", color = TextMidGray, fontSize = 13.sp)
                    Text("Status: ${t.status.name}", color = getStatusColor(t.status), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Scheduled At: ${formatTimestamp(t.scheduledAt)}", color = TextMidGray, fontSize = 13.sp)
                    if (t.parameters.isNotEmpty()) {
                        Text("Parameters:", color = TextCharcoal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        for ((k, v) in t.parameters) {
                            Text("  • $k: $v", color = TextMidGray, fontSize = 12.sp)
                        }
                    }
                    if (!t.result.isNullOrBlank()) {
                        Text("Execution Result:", color = SecondaryMintDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(t.result!!, color = TextCharcoal, fontSize = 12.sp)
                    }
                    if (!t.error.isNullOrBlank()) {
                        Text("Failure Reason:", color = RedError, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(t.error!!, color = RedError, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTaskForDetails = null }) {
                    Text("Close", color = BadgePurpleTint, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (taskToReschedule != null) {
        val t = taskToReschedule!!
        AlertDialog(
            onDismissRequest = { taskToReschedule = null },
            containerColor = GlassCardSolid,
            shape = RoundedCornerShape(26.dp),
            title = { Text("Reschedule Task", color = TextCharcoal, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a new execution time for \"${t.title}\":", color = TextMidGray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    GradientPillButton(
                        text = "In 30 Minutes",
                        onClick = {
                            val newTime = System.currentTimeMillis() + 30 * 60 * 1000L
                            scheduler.rescheduleTask(t.id, newTime)
                            taskToReschedule = null
                            refreshData()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    GradientPillButton(
                        text = "In 1 Hour",
                        onClick = {
                            val newTime = System.currentTimeMillis() + 60 * 60 * 1000L
                            scheduler.rescheduleTask(t.id, newTime)
                            taskToReschedule = null
                            refreshData()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    GradientPillButton(
                        text = "Tomorrow Same Time",
                        onClick = {
                            val newTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
                            scheduler.rescheduleTask(t.id, newTime)
                            taskToReschedule = null
                            refreshData()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { taskToReschedule = null }) {
                    Text("Cancel", color = TextMidGray)
                }
            }
        )
    }
}

@Composable
fun ScheduledTaskCard(
    task: ScheduledTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onReschedule: () -> Unit,
    onClickDetails: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = 5.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val icon = when (task.taskType) {
                    "CALL" -> Icons.Default.Phone
                    "SEND_MESSAGE" -> Icons.AutoMirrored.Filled.Send
                    "REMINDER" -> Icons.Default.Alarm
                    else -> Icons.Default.PlayArrow
                }
                PastelIconBadge(
                    icon = icon,
                    backgroundColor = BadgeMintBg,
                    iconTint = SecondaryMintDark,
                    size = 38.dp,
                    iconSize = 18.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = task.title,
                    color = TextCharcoal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusBadge(task.status)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Command: \"${task.command}\"",
            color = TextMidGray,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Event, contentDescription = null, tint = BadgePurpleTint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = formatTimestamp(task.scheduledAt),
                color = TextMidGray,
                fontSize = 12.sp
            )
        }

        if (!task.result.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Result: ${task.result}",
                color = SecondaryMintDark,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!task.error.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Error: ${task.error}",
                color = RedError,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onClickDetails, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text("Details", fontSize = 12.sp, color = BadgePurpleTint, fontWeight = FontWeight.SemiBold)
            }
            if (task.isPending) {
                TextButton(onClick = onReschedule, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("Reschedule", fontSize = 12.sp, color = SecondaryMintDark, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("Cancel", fontSize = 12.sp, color = BadgeYellowTint, fontWeight = FontWeight.SemiBold)
                }
            } else if (task.status == ScheduledTaskStatus.FAILED) {
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("Retry", fontSize = 12.sp, color = SecondaryMintDark, fontWeight = FontWeight.SemiBold)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMidGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun LearnedExperienceCard(
    experience: ExperienceMemory,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = 5.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.Lightbulb,
                    backgroundColor = BadgePurpleBg,
                    iconTint = BadgePurpleTint,
                    size = 38.dp,
                    iconSize = 18.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = experience.taskType,
                    color = TextCharcoal,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (experience.userConfirmed) {
                Surface(
                    shape = CircleShape,
                    color = BadgeMintBg
                ) {
                    Text(
                        "User Confirmed",
                        color = SecondaryMintDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Goal: \"${experience.goal}\"",
            color = TextMidGray,
            fontSize = 12.sp
        )

        if (experience.successfulMethod.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Winning Method: ${experience.successfulMethod}",
                color = TextCharcoal,
                fontSize = 12.sp
            )
        }

        if (experience.toolsUsed.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tools: ${experience.toolsUsed.joinToString(" → ")}",
                color = BadgePurpleTint,
                fontSize = 12.sp
            )
        }

        if (experience.failedMethods.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Avoided Failures: ${experience.failedMethods.joinToString(", ")}",
                color = RedError,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Confidence: ${(experience.confidence * 100).toInt()}% • Successes: ${experience.successCount} • Failures: ${experience.failureCount}",
                color = TextMidGray,
                fontSize = 11.sp
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMidGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun StatusBadge(status: ScheduledTaskStatus) {
    val color = getStatusColor(status)
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            status.name,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

private fun getStatusColor(status: ScheduledTaskStatus): Color = when (status) {
    ScheduledTaskStatus.SCHEDULED, ScheduledTaskStatus.WAITING -> SecondaryMintDark
    ScheduledTaskStatus.EXECUTING -> BadgePurpleTint
    ScheduledTaskStatus.COMPLETED -> SecondaryMintDark
    ScheduledTaskStatus.FAILED -> RedError
    ScheduledTaskStatus.CANCELLED, ScheduledTaskStatus.SKIPPED -> TextMidGray
    ScheduledTaskStatus.WAITING_FOR_CONFIRMATION -> BadgeYellowTint
}

private fun formatTimestamp(timeMs: Long): String {
    val sdf = SimpleDateFormat("h:mm a, d MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timeMs))
}
