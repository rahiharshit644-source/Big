package com.soltini.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.agent.AgentFailureLog
import com.soltini.app.agent.MacroStore
import com.soltini.app.agent.TaskCheckpointStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings > Agent Log: what failed (and where), learned routines, and an interrupted task if there is one. */
@Composable
fun AgentLogSection(context: Context) {
    var entries by remember { mutableStateOf(AgentFailureLog.all(context)) }
    var macroCount by remember { mutableStateOf(MacroStore.count(context)) }
    val checkpoint = remember { TaskCheckpointStore.load(context) }
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Agent Log", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Jahan Myra phone chalate waqt atki, wo yahan dikhta hai: kaunsa app, kaunsa step aur kya problem.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Seekhe hue routines: $macroCount", fontWeight = FontWeight.SemiBold)
                Text(
                    if (checkpoint != null) "Adhura kaam: \"${checkpoint.goal.take(60)}\" (step ${checkpoint.step}). 'adhura kaam jaari rakho' bolo."
                    else "Koi adhura kaam nahi.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { entries = AgentFailureLog.all(context); macroCount = MacroStore.count(context) }) { Text("Refresh") }
            OutlinedButton(onClick = { AgentFailureLog.clear(context); entries = emptyList() }) { Text("Log saaf karo") }
            Button(onClick = { MacroStore.clear(context); macroCount = 0 }) { Text("Routines bhoolo") }
        }

        if (entries.isEmpty()) {
            Text("Abhi tak koi failure record nahi hua.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val byApp = entries.groupingBy { it.app.ifBlank { "unknown" } }.eachCount().entries.sortedByDescending { it.value }.take(3)
            Text(
                "Sabse zyada problem: " + byApp.joinToString(", ") { "${it.key} (${it.value})" },
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entries.take(40).forEach { e ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${fmt.format(Date(e.time))}  |  ${e.tool}  |  ${e.app.ifBlank { "unknown app" }}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(e.problem, fontSize = 13.sp)
                        if (e.goal.isNotBlank()) Text("Kaam: ${e.goal}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
