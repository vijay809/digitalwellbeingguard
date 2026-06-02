package com.digitalwellbeingguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.digitalwellbeingguard.ui.theme.DigitalWellbeingGuardTheme
import com.digitalwellbeingguard.viewmodel.MainViewModel
import com.digitalwellbeingguard.viewmodel.PermissionState

class MainActivity : ComponentActivity() {
    
    // Using viewModels delegate requires fragment-ktx or activity-ktx which includes lifecycle-viewmodel-ktx
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalWellbeingGuardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionState by viewModel.permissionState.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Digital Wellbeing Guard",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // --- Permission Section ---
        PermissionStatusItem(
            label = "Usage Access",
            isGranted = permissionState.usageAccess
        )
        PermissionStatusItem(
            label = "Display Overlay",
            isGranted = permissionState.overlay
        )
        PermissionStatusItem(
            label = "Battery Optimization",
            isGranted = permissionState.batteryOptimization
        )
        PermissionStatusItem(
            label = "Notification",
            isGranted = permissionState.notification
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Permission Buttons ---
        if (!permissionState.usageAccess) {
            Button(onClick = { viewModel.openUsageAccessSettings(context) }) {
                Text(text = "Grant Usage Access")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!permissionState.overlay) {
            Button(onClick = { viewModel.openOverlaySettings(context) }) {
                Text(text = "Grant Overlay Permission")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!permissionState.batteryOptimization) {
            Button(onClick = { viewModel.openBatteryOptimizationSettings(context) }) {
                Text(text = "Disable Battery Optimization")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!permissionState.notification) {
            Button(onClick = { activity?.let { viewModel.requestNotificationPermission(it) } }) {
                Text(text = "Grant Notification Permission")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // --- Monitoring Section ---
        Text(
            text = "Monitoring Status:",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (isMonitoring) "🟢 Running" else "🔴 Stopped",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isMonitoring) Color.Green else Color.Red
        )

        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(24.dp))
        
        // Feature 2: Interval Selector
        val selectedInterval by viewModel.selectedInterval.collectAsState()
        var expanded by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text("Warning Interval: ${selectedInterval.label}")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MainViewModel.WarningInterval.entries.forEach { interval ->
                    DropdownMenuItem(
                        text = { Text(interval.label) },
                        onClick = {
                            viewModel.setWarningInterval(context, interval)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.toggleMonitoring(context) },
            enabled = viewModel.canStartMonitoring() || isMonitoring // Allow stop even if permissions revoked
        ) {
            Text(text = if (isMonitoring) "Stop Monitoring" else "Start Monitoring")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Feature 3: Usage Report
        Text(
            text = "Apps Used More Than 5 Minutes Today",
            style = MaterialTheme.typography.titleMedium
        )
        
        val usageList by viewModel.usageList.collectAsState()
        
        if (usageList.isEmpty()) {
            Text(
                text = "No apps exceed limit today.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                items(usageList) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon
                        if (app.appIcon != null) {
                            Image(
                                bitmap = app.appIcon.toBitmap().asImageBitmap(),
                                contentDescription = app.appName,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = app.appName, 
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            val minutes = (app.totalTime / 1000) / 60
                            val seconds = ((app.totalTime / 1000) % 60)
                            Text(
                                text = String.format("%02d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                    HorizontalDivider(color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun PermissionStatusItem(label: String, isGranted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = if (isGranted) "✅" else "❌",
            color = if (isGranted) Color.Green else Color.Red
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    DigitalWellbeingGuardTheme {
        Column(
             verticalArrangement = Arrangement.Center,
             horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Digital Wellbeing Guard")
            PermissionStatusItem("Usage Access", false)
            PermissionStatusItem("Overlay", true)
            PermissionStatusItem("Battery", false)
            Button(onClick = {}) { Text("Grant Usage Access") }
            Button(enabled = false, onClick = {}) { Text("Start Monitoring") }
        }
    }
}
