package com.jar.ui.limit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jar.ui.jar.formatRelativeTime
import com.jar.ui.jar.formatRupees
import com.jar.ui.theme.Amber
import com.jar.ui.theme.CalmGreen
import com.jar.ui.theme.WarningRed
import kotlin.math.abs

@Composable
fun LimitScreen(viewModel: LimitViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LimitScreenContent(
        state = state,
        onSaveLimit = viewModel::saveLimit,
        onResetMonth = viewModel::resetMonth
    )
}

@Composable
private fun LimitScreenContent(
    state: LimitScreenState,
    onSaveLimit: (Long) -> Unit,
    onResetMonth: () -> Unit
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var edit by remember { mutableStateOf("") }

    LaunchedEffect(state.monthlyLimitPaise) {
        edit = (state.monthlyLimitPaise / 100L).toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "THIS MONTH",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = edit,
                onValueChange = { raw -> edit = raw.filter(Char::isDigit) },
                label = { Text("Monthly limit (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { edit.toLongOrNull()?.let(onSaveLimit) },
                enabled = edit.isNotEmpty() && edit != (state.monthlyLimitPaise / 100L).toString()
            ) { Text("Save") }
        }

        LinearProgressIndicator(
            progress = state.progressFraction.coerceIn(0f, 1f),
            color = progressColor(state),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )

        Text(
            text = statusLine(state),
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.isOverLimit) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "RECENT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        if (state.recent.isEmpty()) {
            Text(
                "No transactions yet this period.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.recent, key = { it.id }) { TransactionRow(it) }
            }
        }

        TextButton(onClick = { showResetDialog = true }) { Text("Reset month") }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset this month?") },
            text = {
                Text(
                    "Your jar fills back up. Past transactions stay in history but won't " +
                            "count against the current period."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onResetMonth()
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TransactionRow(tx: RecentTransactionUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = tx.merchantRaw ?: "Unknown merchant",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = formatRelativeTime(tx.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Text(
            text = formatRupees(tx.amountPaise),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun statusLine(state: LimitScreenState): String = when {
    state.monthlyLimitPaise <= 0L -> "Set a monthly limit above."
    state.isOverLimit -> "${formatRupees(abs(state.remainingPaise))} over"
    else -> "${formatRupees(state.remainingPaise)} remaining this month"
}

private fun progressColor(state: LimitScreenState): Color = when {
    state.isOverLimit -> WarningRed
    state.progressFraction >= 0.8f -> Amber
    else -> CalmGreen
}
