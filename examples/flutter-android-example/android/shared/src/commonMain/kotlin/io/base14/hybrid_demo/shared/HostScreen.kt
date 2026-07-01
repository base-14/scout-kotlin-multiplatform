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
 * The native host UI, written with **Compose Multiplatform** (`org.jetbrains.compose`)
 * and living in the KMP `:shared` module's `commonMain`. It is pure UI: every
 * platform-specific effect (ANR, native crash, HTTP, launching the Flutter
 * screen) is supplied by the caller as a lambda, so this composable has no
 * Android dependencies and could be reused from other Compose Multiplatform
 * targets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostScreen(
    onNativeAnr: () -> Unit,
    onNativeCrash: () -> Unit,
    onNativeException: () -> Unit,
    onHttpCall: () -> Unit,
    onOpenFlutter: () -> Unit,
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
                Text(
                    text = "Native Kotlin (Compose Multiplatform) host",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "This screen is a Compose Multiplatform composable from the " +
                        "KMP :shared module, rendered by a ComponentActivity. The " +
                        "buttons below fire native trigger code passed in as " +
                        "callbacks. \"Open Flutter Screen\" starts a FlutterActivity " +
                        "in the same process.",
                    color = Color.Gray,
                )

                Text(
                    text = "NATIVE TEST TRIGGERS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TriggerButton("Native ANR (5s)", Color(0xFFFF9800), onNativeAnr)
                    TriggerButton("Native Crash", Color(0xFF000000), onNativeCrash)
                    TriggerButton("Native Exception", Color(0xFF9C27B0), onNativeException)
                    TriggerButton("HTTP Call", Color(0xFF3F51B5), onHttpCall)
                }

                Button(
                    onClick = onOpenFlutter,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Flutter Screen")
                }
            }
        }
    }
}

@Composable
private fun TriggerButton(label: String, color: Color, onClick: () -> Unit) {
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
