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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.clickable
import kotlinx.coroutines.withContext
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import com.digitalwellbeingguard.ui.SetPinDialog
import com.digitalwellbeingguard.ui.EnterPinDialog
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
    val hasPin by viewModel.hasPin.collectAsState()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()
    val configuredApps by viewModel.configuredApps.collectAsState()

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showEnterPinDialog by remember { mutableStateOf(false) }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var pinErrorMessage by remember { mutableStateOf("") }
    var lockIsRed by remember { mutableStateOf(false) }

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
            text = "Digital Guard",
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "App PIN Security", style = MaterialTheme.typography.bodyLarge)
            Text(text = if (hasPin) "✅" else "🟡")
        }

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

        Button(onClick = { showSetPinDialog = true }) {
            Text(text = if (hasPin) "Change PIN" else "Set PIN")
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
        
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                // Feature 2: Interval Selector
                val selectedInterval by viewModel.selectedInterval.collectAsState()
                var expanded by remember { mutableStateOf(false) }

                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        enabled = isAppUnlocked || !hasPin
                    ) {
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
                    enabled = (viewModel.canStartMonitoring() || isMonitoring) && (isAppUnlocked || !hasPin)
                ) {
                    Text(text = if (isMonitoring) "Stop Monitoring" else "Start Monitoring")
                }
            }

            if (hasPin && !isAppUnlocked) {
                // Semi-transparent overlay with Lock Icon
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.7f))
                        .zIndex(1f),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { showEnterPinDialog = true },
                        enabled = !lockIsRed,
                        modifier = Modifier
                            .size(64.dp)
                            .background(if (lockIsRed) Color.Red else MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Unlock", tint = Color.White)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Feature 3: Usage Report
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Monitored Apps",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { showAddAppDialog = true }) {
                Text("+ Add App")
            }
        }
        
        val usageList by viewModel.usageList.collectAsState()
        val sortedUsageList = usageList.sortedByDescending { configuredApps[it.packageName] == true }
        
        if (sortedUsageList.isEmpty()) {
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
                items(sortedUsageList) { app ->
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
                        
                        Column(modifier = Modifier.weight(1f)) {
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
                        
                        Checkbox(
                            checked = configuredApps[app.packageName] == true,
                            onCheckedChange = { isChecked ->
                                viewModel.toggleAppStatus(context, app.packageName, isChecked)
                            }
                        )
                    }
                    HorizontalDivider(color = Color.LightGray)
                }
            }
        }
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onPinSet = { pin ->
                viewModel.setPin(context, pin)
                showSetPinDialog = false
            }
        )
    }

    if (showEnterPinDialog) {
        EnterPinDialog(
            onDismiss = {
                showEnterPinDialog = false
                pinErrorMessage = ""
            },
            onPinEntered = { pin ->
                if (viewModel.verifyAppPin(context, pin)) {
                    showEnterPinDialog = false
                    pinErrorMessage = ""
                    lockIsRed = false
                } else {
                    pinErrorMessage = "Incorrect PIN"
                    showEnterPinDialog = false
                    lockIsRed = true
                }
            },
            errorMessage = pinErrorMessage
        )
    }

    if (showAddAppDialog) {
        val installedApps = remember { mutableStateOf<List<com.digitalwellbeingguard.data.AppUsage>>(emptyList()) }
        
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val apps = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.digitalwellbeingguard.data.UsageRepository().getInstalledApps(context)
            }
            installedApps.value = apps
        }
        
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAddAppDialog = false }) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth().height(400.dp)) {
                    Text(text = "Select App to Monitor", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (installedApps.value.isEmpty()) {
                        Text("Loading...")
                    } else {
                        LazyColumn {
                            items(installedApps.value) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.manuallyAddApp(context, app)
                                            showAddAppDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (app.appIcon != null) {
                                        Image(
                                            bitmap = app.appIcon.toBitmap().asImageBitmap(),
                                            contentDescription = app.appName,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = app.appName)
                                }
                            }
                        }
                    }
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
            Text(text = "Digital Guard")
            PermissionStatusItem("Usage Access", false)
            PermissionStatusItem("Overlay", true)
            PermissionStatusItem("Battery", false)
            Button(onClick = {}) { Text("Grant Usage Access") }
            Button(enabled = false, onClick = {}) { Text("Start Monitoring") }
        }
    }
}
