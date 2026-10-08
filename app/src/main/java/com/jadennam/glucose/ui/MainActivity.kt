package com.jadennam.glucose.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jadennam.glucose.notify.Notifier
import com.jadennam.glucose.ui.onboarding.OnboardingScreen
import com.jadennam.glucose.ui.settings.SettingsScreen
import com.jadennam.glucose.ui.theme.GlucoseTheme
import com.jadennam.glucose.ui.today.TodayScreen
import com.jadennam.glucose.ui.trend.TrendScreen

enum class Tab(val label: String, val icon: String) { TODAY("오늘", "🩸"), TREND("추세", "📈"), SETTINGS("설정", "⚙️") }

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequest.value = intent?.getStringExtra(Notifier.EXTRA_OPEN)
        setContent {
            GlucoseTheme { AppRoot(vm, openRequest.value) { openRequest.value = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra(Notifier.EXTRA_OPEN)
    }
}

@Composable
private fun AppRoot(vm: MainViewModel, openRequest: String?, consumeOpen: () -> Unit) {
    val profileState by vm.profileState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    when (val s = profileState) {
        ProfileState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        ProfileState.Missing -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.padding(pad)) { OnboardingScreen(vm) }
        }
        is ProfileState.Ready -> {
            var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
            var focus by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(openRequest) {
                when (openRequest) {
                    null -> Unit
                    Notifier.OPEN_SETTINGS -> tab = Tab.SETTINGS
                    else -> { tab = Tab.TODAY; focus = openRequest }
                }
                if (openRequest != null) consumeOpen()
            }
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Text(t.icon) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tab) {
                        Tab.TODAY -> TodayScreen(vm, s.profile, focus) { focus = null }
                        Tab.TREND -> TrendScreen(vm, s.profile)
                        Tab.SETTINGS -> SettingsScreen(vm, s.profile)
                    }
                }
            }
        }
    }
}
