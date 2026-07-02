package io.base14.hybrid_demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Extra native screens for the demo, all Compose Multiplatform. Each screen emits
 * a `screen_view` when the host calls `Scout.setScreen(...)` on navigation, plus
 * whatever RUM signal its action buttons trigger (all supplied as lambdas so the
 * `:shared` module stays free of any Scout/Android dependency).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoScaffold(
    title: String,
    subtitle: String,
    navTargets: List<Pair<String, String>>,
    onNavigate: (String) -> Unit,
    actions: @Composable () -> Unit,
) {
    MaterialTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = Color.Gray)

                Text(
                    text = "ACTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    actions()
                }

                Text(
                    text = "GO TO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    navTargets.forEach { (label, route) ->
                        DemoButton(label, Color(0xFF455A64)) { onNavigate(route) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DemoButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
        ),
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit, onLogEvent: () -> Unit) {
    DemoScaffold(
        title = "Dashboard",
        subtitle = "Native Compose screen. Tapping an action emits a RUM signal under base14.scout.android.",
        navTargets = listOf("Details" to "details", "Home" to "home"),
        onNavigate = onNavigate,
    ) {
        DemoButton("Log Event", Color(0xFF3F51B5), onLogEvent)
    }
}

@Composable
fun DetailsScreen(onNavigate: (String) -> Unit, onReportError: () -> Unit) {
    DemoScaffold(
        title = "Details",
        subtitle = "Report a handled error — captured as an `error` span with stack trace, not a crash.",
        navTargets = listOf("Settings" to "settings", "Home" to "home"),
        onNavigate = onNavigate,
    ) {
        DemoButton("Report Handled Error", Color(0xFFE91E63), onReportError)
    }
}

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onSetUser: () -> Unit,
    onBreadcrumb: () -> Unit,
) {
    DemoScaffold(
        title = "Settings",
        subtitle = "Set the RUM user identity and drop a breadcrumb into the session timeline.",
        navTargets = listOf("Profile" to "profile", "Home" to "home"),
        onNavigate = onNavigate,
    ) {
        DemoButton("Set User", Color(0xFF009688), onSetUser)
        DemoButton("Add Breadcrumb", Color(0xFF795548), onBreadcrumb)
    }
}

@Composable
fun ProfileScreen(
    onNavigate: (String) -> Unit,
    onLogInfo: () -> Unit,
    onHttpCall: () -> Unit,
) {
    DemoScaffold(
        title = "Profile",
        subtitle = "Emit an info log and an outbound HTTP request (http.request span).",
        navTargets = listOf("Home" to "home", "Dashboard" to "dashboard"),
        onNavigate = onNavigate,
    ) {
        DemoButton("Log Info", Color(0xFF607D8B), onLogInfo)
        DemoButton("HTTP Call", Color(0xFF3F51B5), onHttpCall)
    }
}
