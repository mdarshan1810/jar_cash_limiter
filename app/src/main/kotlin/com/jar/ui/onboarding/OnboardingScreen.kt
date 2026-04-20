package com.jar.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingFlow(
    viewModel: OnboardingViewModel,
    onRequestNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state.step) {
            OnboardingStep.Welcome -> WelcomeStep(onNext = viewModel::proceedFromWelcome)
            OnboardingStep.Permission -> PermissionStep(
                granted = state.notificationAccessGranted,
                onGrant = onRequestNotificationAccess,
                onVerifyAndContinue = viewModel::proceedFromPermission,
                onBack = viewModel::goBack
            )
            OnboardingStep.Amounts -> AmountsStep(
                state = state,
                onStartingChange = viewModel::updateStartingAmount,
                onPeriodDayChange = viewModel::updatePeriodStartDay,
                onNext = viewModel::saveAmountsAndAdvance,
                onBack = viewModel::goBack
            )
            OnboardingStep.Limit -> LimitStep(
                state = state,
                onLimitChange = viewModel::updateMonthlyLimit,
                onNext = viewModel::saveLimitAndAdvance,
                onBack = viewModel::goBack
            )
            OnboardingStep.Waiting -> WaitingStep(
                state = state,
                onLast4Change = viewModel::updateManualLast4,
                onConfirmLast4 = viewModel::confirmManualLast4,
                onBack = viewModel::goBack
            )
        }
    }
}

@Composable
private fun ColumnScope.WelcomeStep(onNext: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Text("Jar", style = MaterialTheme.typography.displayLarge)
    Text(
        text = "Watch your spending the way cash empties a jar.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Spacer(Modifier.weight(1f))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
}

@Composable
private fun ColumnScope.PermissionStep(
    granted: Boolean,
    onGrant: () -> Unit,
    onVerifyAndContinue: () -> Unit,
    onBack: () -> Unit
) {
    Text("Notification access", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "Jar reads bank notifications on your device to track your spending. " +
                "No data ever leaves your phone.",
        style = MaterialTheme.typography.bodyLarge
    )
    if (!granted) {
        Text(
            text = "Not granted yet. Tap Open settings, enable Jar, then return here.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Spacer(Modifier.weight(1f))
    Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
        Text(if (granted) "Open settings again" else "Open settings")
    }
    Button(
        onClick = onVerifyAndContinue,
        enabled = granted,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Continue") }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun ColumnScope.AmountsStep(
    state: OnboardingState,
    onStartingChange: (String) -> Unit,
    onPeriodDayChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Text("Set your jar", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "How much do you want to set aside this period, and what day should the period reset?",
        style = MaterialTheme.typography.bodyLarge
    )
    OutlinedTextField(
        value = state.startingAmountRupees,
        onValueChange = onStartingChange,
        label = { Text("Starting amount (₹)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.periodStartDay,
        onValueChange = onPeriodDayChange,
        label = { Text("Period start day (1–28)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    state.amountsError?.let { err ->
        Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.weight(1f))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun ColumnScope.LimitStep(
    state: OnboardingState,
    onLimitChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Text("Monthly limit", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "A stricter target to stay under, even if your jar still has money left. " +
                "We've suggested 80% of your starting amount.",
        style = MaterialTheme.typography.bodyLarge
    )
    OutlinedTextField(
        value = state.monthlyLimitRupees,
        onValueChange = onLimitChange,
        label = { Text("Monthly limit (₹)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    state.limitError?.let { err ->
        Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.weight(1f))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun ColumnScope.WaitingStep(
    state: OnboardingState,
    onLast4Change: (String) -> Unit,
    onConfirmLast4: () -> Unit,
    onBack: () -> Unit
) {
    var showManualEntry by remember { mutableStateOf(false) }

    Text("Waiting for your first transaction…", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "As soon as we see a bank notification, we'll link your account and start " +
                "filling your jar. This usually happens next time you pay someone.",
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(8.dp))
    if (!showManualEntry) {
        TextButton(onClick = { showManualEntry = true }) { Text("I know my account number") }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.manualLast4,
                onValueChange = onLast4Change,
                label = { Text("Last 4 digits") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = onConfirmLast4) { Text("Link") }
        }
        state.last4Error?.let { err ->
            Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.weight(1f))
    TextButton(onClick = onBack) { Text("Back") }
}
