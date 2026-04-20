package com.jar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jar.settings.RolloverMode
import com.jar.settings.Settings
import com.jar.ui.jar.formatRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        SettingsContent(
            settings = settings,
            onSetStartingAmount = viewModel::setStartingAmountRupees,
            onSetMonthlyLimit = viewModel::setMonthlyLimitRupees,
            onSetPeriodStartDay = viewModel::setPeriodStartDay,
            onSetRolloverMode = viewModel::setRolloverMode,
            onRelinkAccount = viewModel::relinkAccount,
            onResetMonth = viewModel::resetMonth,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun SettingsContent(
    settings: Settings,
    onSetStartingAmount: (Long) -> Unit,
    onSetMonthlyLimit: (Long) -> Unit,
    onSetPeriodStartDay: (Int) -> Unit,
    onSetRolloverMode: (RolloverMode) -> Unit,
    onRelinkAccount: () -> Unit,
    onResetMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showRelinkDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SectionLabel("JAR")
        RupeeEditRow(
            label = "Starting amount",
            storedPaise = settings.startingAmount,
            onSave = onSetStartingAmount
        )
        IntEditRow(
            label = "Period start day (1–28)",
            storedValue = settings.periodStartDay,
            onSave = onSetPeriodStartDay
        )

        SectionLabel("LIMIT")
        RupeeEditRow(
            label = "Monthly limit",
            storedPaise = settings.monthlyLimit,
            onSave = onSetMonthlyLimit
        )

        SectionLabel("ROLLOVER")
        RolloverMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = settings.rolloverMode == mode,
                        onClick = { onSetRolloverMode(mode) }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.rolloverMode == mode,
                    onClick = { onSetRolloverMode(mode) }
                )
                Spacer(Modifier.height(4.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        text = when (mode) {
                            RolloverMode.RESET -> "Reset (default)"
                            RolloverMode.ROLLOVER -> "Roll over unused amount"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when (mode) {
                            RolloverMode.RESET -> "Jar refills fully each period."
                            RolloverMode.ROLLOVER -> "Unspent jar carries into next period."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        SectionLabel("ACCOUNT")
        InfoRow(label = "Bank", value = settings.trackedBank)
        InfoRow(
            label = "Account",
            value = settings.trackedAccountLast4?.let { "••$it" } ?: "(not linked)"
        )
        TextButton(onClick = { showRelinkDialog = true }) { Text("Re-link account") }

        SectionLabel("DANGER")
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

    if (showRelinkDialog) {
        AlertDialog(
            onDismissRequest = { showRelinkDialog = false },
            title = { Text("Re-link bank account?") },
            text = {
                Text(
                    "You'll be taken back to the account-linking screen. Your transaction " +
                            "history is preserved, but new transactions are ignored until you link again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRelinkAccount()
                    showRelinkDialog = false
                }) { Text("Re-link") }
            },
            dismissButton = {
                TextButton(onClick = { showRelinkDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

@Composable
private fun RupeeEditRow(label: String, storedPaise: Long, onSave: (Long) -> Unit) {
    val storedRupees = (storedPaise / 100L).toString()
    var edit by remember { mutableStateOf(storedRupees) }
    LaunchedEffect(storedPaise) { edit = storedRupees }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = edit,
            onValueChange = { raw -> edit = raw.filter(Char::isDigit) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            supportingText = {
                Text("currently ${formatRupees(storedPaise)}")
            }
        )
        Button(
            onClick = { edit.toLongOrNull()?.let(onSave) },
            enabled = edit.isNotEmpty() && edit != storedRupees
        ) { Text("Save") }
    }
}

@Composable
private fun IntEditRow(label: String, storedValue: Int, onSave: (Int) -> Unit) {
    val stored = storedValue.toString()
    var edit by remember { mutableStateOf(stored) }
    LaunchedEffect(storedValue) { edit = stored }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = edit,
            onValueChange = { raw -> edit = raw.filter(Char::isDigit).take(2) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { edit.toIntOrNull()?.let(onSave) },
            enabled = edit.isNotEmpty() && edit != stored
        ) { Text("Save") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
