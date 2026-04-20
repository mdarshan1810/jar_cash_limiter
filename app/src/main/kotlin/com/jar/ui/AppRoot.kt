package com.jar.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jar.AppContainer
import com.jar.settings.Settings
import com.jar.ui.jar.JarScreen
import com.jar.ui.jar.JarViewModel
import com.jar.ui.limit.LimitScreen
import com.jar.ui.limit.LimitViewModel
import com.jar.ui.onboarding.OnboardingFlow
import com.jar.ui.onboarding.OnboardingViewModel
import com.jar.ui.theme.JarTheme

@Composable
fun AppRoot(container: AppContainer) {
    JarTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val settings by container.settingsStore.flow
                .collectAsStateWithLifecycle(initialValue = Settings.DEFAULT)
            if (settings.trackedAccountLast4 == null) {
                OnboardingRoute(container)
            } else {
                MainRoute(container)
            }
        }
    }
}

@Composable
private fun OnboardingRoute(container: AppContainer) {
    val context = LocalContext.current
    val viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.factory(container) {
            isNotificationAccessGranted(context)
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNotificationAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OnboardingFlow(
        viewModel = viewModel,
        onRequestNotificationAccess = {
            context.startActivity(
                Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainRoute(container: AppContainer) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> {
                val jarVm: JarViewModel = viewModel(factory = JarViewModel.factory(container))
                JarScreen(viewModel = jarVm)
            }
            1 -> {
                val limitVm: LimitViewModel = viewModel(factory = LimitViewModel.factory(container))
                LimitScreen(viewModel = limitVm)
            }
        }
    }
}

private fun isNotificationAccessGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
