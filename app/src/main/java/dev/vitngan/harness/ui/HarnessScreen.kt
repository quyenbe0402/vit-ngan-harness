package dev.vitngan.harness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vitngan.harness.core.report.ExecutionSummary
import dev.vitngan.harness.core.task.Task
import dev.vitngan.harness.core.task.TaskStatus

/**
 * The M0 shell: an observation and control surface.
 *
 * There is intentionally no approve/reject control anywhere. Permitted
 * actions run autonomously; this screen shows what happened and why.
 * It also never renders chain-of-thought - only [ExecutionSummary] fields.
 */
@Composable
fun HarnessScreen(
    state: TaskUiState,
    onCreateTask: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelectWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Vit Ngan Harness",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.workspaceName?.let { "Workspace: $it" }
                    ?: "No workspace selected",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCreateTask(title); if (title.isNotBlank()) title = "" }) {
                    Text("Create task")
                }
                Button(onClick = onSelectWorkspace) { Text("Select workspace") }
                Button(onClick = onRefresh) { Text("Refresh") }
            }

            state.lastError?.let { error ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = "Refused: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            Row(modifier = Modifier.padding(top = 8.dp)) {
                listOf("Tasks (${state.tasks.size})", "Activity (${state.activity.size})").forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            when (tab) {
                0 -> TaskList(state.tasks)
                else -> ActivityFeed(state.activity)
            }
        }
    }
}

@Composable
private fun TaskList(tasks: List<Task>) {
    if (tasks.isEmpty()) {
        Text("No tasks yet.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        items(tasks, key = { it.id }) { task ->
            TaskRow(task)
            androidx.compose.material3.HorizontalDivider()
        }
    }
}

@Composable
private fun TaskRow(task: Task) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(task.title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = "status: ${task.status}",
            style = MaterialTheme.typography.bodySmall,
            color = if (task.status == TaskStatus.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = "created: ${task.createdAtMillis}   updated: ${task.updatedAtMillis}",
            style = MaterialTheme.typography.labelSmall,
        )
        task.failureReason?.let {
            Text("reason: $it", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ActivityFeed(items: List<ActivityItem>) {
    if (items.isEmpty()) {
        Text("No activity yet.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        items(items, key = { it.id }) { item -> ActivityRow(item) }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleSmall,
            color = if (item.isViolation) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        SummaryBlock(item.summary)
        androidx.compose.material3.HorizontalDivider()
    }
}

/**
 * Renders the structured summary.
 *
 * Only the declared summary fields appear. There is no path by which private
 * reasoning reaches this composable, because [ExecutionSummary] has no field
 * that could carry it.
 */
@Composable
fun SummaryBlock(summary: ExecutionSummary, modifier: Modifier = Modifier) {
    if (summary.isEmpty) return
    Column(modifier = modifier.padding(start = 8.dp)) {
        if (summary.objective.isNotBlank()) Line("objective", summary.objective)
        summary.evidence.forEach { Line("evidence", it) }
        summary.hypothesis?.let { Line("hypothesis", it) }
        summary.inspectedSymbols.forEach { Line("inspected", it) }
        summary.approach?.let { Line("approach", it) }
        summary.changes.forEach { Line("change", it) }
        summary.errors.forEach { Line("error", it) }
        summary.recovery.forEach { Line("recovery", it) }
        summary.nextAction?.let { Line("next", it) }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 1.dp),
    )
}