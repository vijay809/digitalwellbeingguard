package com.digitalwellbeingguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.digitalwellbeingguard.ui.EnterPinDialog
import com.digitalwellbeingguard.ui.SetPinDialog
import com.digitalwellbeingguard.ui.theme.*
import com.digitalwellbeingguard.viewmodel.MainViewModel
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalWellbeingGuardTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showEnterPinDialog by remember { mutableStateOf(false) }
    var pinErrorMessage by remember { mutableStateOf("") }
    var lockIsRed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Main Canvas
    Box(modifier = modifier) {
        // Gradient background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF86d2c1).copy(alpha = 0.1f),
                radius = size.minDimension,
                center = androidx.compose.ui.geometry.Offset(0f, 0f)
            )
            drawCircle(
                color = Color(0xFFcfc1ff).copy(alpha = 0.1f),
                radius = size.minDimension,
                center = androidx.compose.ui.geometry.Offset(size.width, size.height)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            DashboardTopAppBar()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    SetupProgressCard(viewModel, isAppUnlocked, onShowPinDialog = { showSetPinDialog = true })
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        MonitoringStatusCard(viewModel, isAppUnlocked)
                        if (hasPin && !isAppUnlocked) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.7f))
                                    .zIndex(1f)
                                    .clickable { showEnterPinDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { showEnterPinDialog = true },
                                    enabled = !lockIsRed,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(if (lockIsRed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, CircleShape)
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = "Unlock", tint = Color.White)
                                }
                            }
                        }
                    }
                }
                item {
                    MonitoredAppsSection(viewModel, isAppUnlocked)
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
}

@Composable
fun DashboardTopAppBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.app_icon),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Text(
                text = "Mindful   Scrolling",
                style = MaterialTheme.typography.displayLarge,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Removed user profile icon per request
    }
}

@Composable
fun SetupProgressCard(viewModel: MainViewModel, isAppUnlocked: Boolean, onShowPinDialog: () -> Unit) {
    val context = LocalContext.current
    val permissionState by viewModel.permissionState.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()
    
    val isSetupComplete = permissionState.usageAccess && 
                          permissionState.overlay && 
                          permissionState.batteryOptimization && 
                          permissionState.notification && 
                          hasPin

    var expandedByUser by remember { mutableStateOf(false) }
    val expanded = if (!isSetupComplete) true else expandedByUser

    LaunchedEffect(expandedByUser, isSetupComplete) {
        if (isSetupComplete && expandedByUser) {
            kotlinx.coroutines.delay(5000L)
            expandedByUser = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isSetupComplete) { expandedByUser = !expandedByUser }
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isSetupComplete) "Setup Complete" else "Setup Progress", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isSetupComplete) "All set to protect your focus" else "Complete steps for full protection", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = if (isSetupComplete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    PermissionItem(
                        label = "Usage Access",
                        isGranted = permissionState.usageAccess,
                        whyText = "Required to monitor which apps you are using and for how long.",
                        buttonContent = {
                            if (!permissionState.usageAccess) {
                                GradientButton("Grant Usage Access", premium_gradient_primary_start, premium_gradient_primary_end, MaterialTheme.colorScheme.onPrimaryContainer) {
                                    viewModel.openUsageAccessSettings(context)
                                }
                            }
                        }
                    )
                    
                    PermissionItem(
                        label = "Display Overlay",
                        isGranted = permissionState.overlay,
                        whyText = "Required to show the block screen over restricted apps.",
                        buttonContent = {
                            if (!permissionState.overlay) {
                                GradientButton("Grant Overlay Permission", premium_gradient_primary_start, premium_gradient_primary_end, MaterialTheme.colorScheme.onPrimaryContainer) {
                                    viewModel.openOverlaySettings(context)
                                }
                            }
                        }
                    )
                    
                    PermissionItem(
                        label = "Battery Optimization",
                        isGranted = permissionState.batteryOptimization,
                        whyText = "Ensures the app isn't killed by the system so monitoring continues reliably.",
                        buttonContent = {
                            if (!permissionState.batteryOptimization) {
                                GradientButton("Disable Battery Optimization", premium_gradient_primary_start, premium_gradient_primary_end, MaterialTheme.colorScheme.onPrimaryContainer) {
                                    viewModel.openBatteryOptimizationSettings(context)
                                }
                            }
                        }
                    )

                    PermissionItem(
                        label = "Notification",
                        isGranted = permissionState.notification,
                        whyText = "Used to show a persistent notification that keeps the monitoring service alive.",
                        buttonContent = {
                            if (!permissionState.notification) {
                                val activity = context as? android.app.Activity
                                GradientButton("Grant Notification Permission", premium_gradient_primary_start, premium_gradient_primary_end, MaterialTheme.colorScheme.onPrimaryContainer) {
                                    activity?.let { viewModel.requestNotificationPermission(it) }
                                }
                            }
                        }
                    )
                    
                    PermissionItem(
                        label = "App PIN Security",
                        isGranted = hasPin,
                        whyText = "Prevents unauthorized changes to your settings or disabling of the monitor.",
                        buttonContent = {
                            GradientButton(
                                text = if (hasPin) "Change PIN" else "Set PIN", 
                                startColor = premium_gradient_secondary_start, 
                                endColor = premium_gradient_secondary_end, 
                                textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                enabled = !hasPin || isAppUnlocked
                            ) {
                                onShowPinDialog()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    label: String,
    isGranted: Boolean,
    whyText: String,
    buttonContent: @Composable () -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    Text(
                        text = "Why?", 
                        style = MaterialTheme.typography.labelSmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), 
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showTooltip = true }
                    )
                    if (showTooltip) {
                        androidx.compose.ui.window.Dialog(onDismissRequest = { showTooltip = false }) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = whyText, 
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        LaunchedEffect(showTooltip) {
                            kotlinx.coroutines.delay(5000L)
                            showTooltip = false
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Clear,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        buttonContent()
    }
}

@Composable
fun GradientButton(text: String, startColor: Color, endColor: Color, textColor: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.5f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(99.dp))
            .background(Brush.verticalGradient(listOf(startColor.copy(alpha = alpha), endColor.copy(alpha = alpha))))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(99.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, fontSize = 16.sp, color = textColor.copy(alpha = alpha), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MonitoringStatusCard(viewModel: MainViewModel, isAppUnlocked: Boolean) {
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val context = LocalContext.current
    val selectedInterval by viewModel.selectedInterval.collectAsState()
    val selectedRefreshInterval by viewModel.selectedRefreshInterval.collectAsState()
    var expandedWarning by remember { mutableStateOf(false) }
    var expandedRefresh by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val opacity by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "opacity"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "MONITORING STATUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), letterSpacing = 1.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isMonitoring) {
                        Box(modifier = Modifier.size(16.dp).scale(scale).clip(CircleShape).background(Color(0xFF86d2c1).copy(alpha = opacity)))
                    }
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).background(if (isMonitoring) Color(0xFF146a5c) else MaterialTheme.colorScheme.error))
                }
                Text(
                    text = if (isMonitoring) "Running" else "Stopped",
                    style = MaterialTheme.typography.displayLarge,
                    color = if (isMonitoring) Color(0xFF146a5c) else MaterialTheme.colorScheme.error,
                    fontSize = 32.sp
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(99.dp))
                        .clickable(enabled = isAppUnlocked) { expandedWarning = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "Warning Interval: ${selectedInterval.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (expandedWarning) {
                    Dialog(onDismissRequest = { expandedWarning = false }) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                                Text("Select Warning Interval", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                                MainViewModel.WarningInterval.entries.forEach { interval ->
                                    TextButton(
                                        onClick = {
                                            viewModel.setWarningInterval(context, interval)
                                            expandedWarning = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(interval.label)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(99.dp))
                        .clickable(enabled = isAppUnlocked) { expandedRefresh = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "Refresh Session After: ${selectedRefreshInterval.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (expandedRefresh) {
                    Dialog(onDismissRequest = { expandedRefresh = false }) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                                Text("Refresh Session After", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                                MainViewModel.RefreshInterval.entries.forEach { interval ->
                                    TextButton(
                                        onClick = {
                                            viewModel.setRefreshInterval(context, interval)
                                            expandedRefresh = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(interval.label)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (isAppUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable(enabled = isAppUnlocked) { viewModel.toggleMonitoring(context) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isMonitoring) "Stop Monitoring" else "Start Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isAppUnlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MonitoredAppsSection(viewModel: MainViewModel, isAppUnlocked: Boolean) {
    val usageList by viewModel.usageList.collectAsState()
    val configuredApps by viewModel.configuredApps.collectAsState()
    val context = LocalContext.current

    val sortedUsageList = usageList.sortedByDescending { configuredApps[it.packageName] == true }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Monitored Apps", style = MaterialTheme.typography.headlineLarge, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        ) {
            if (sortedUsageList.isEmpty()) {
                Text("No apps monitored today.", modifier = Modifier.padding(24.dp))
            } else {
                sortedUsageList.forEachIndexed { index, app ->
                    val isChecked = configuredApps[app.packageName] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isChecked) Color.White.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable(enabled = isAppUnlocked) { viewModel.toggleAppStatus(context, app.packageName, !isChecked) }
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (app.appIcon != null) {
                                    Image(bitmap = app.appIcon.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(Icons.Filled.Build, contentDescription = null)
                                }
                            }
                            Column {
                                Text(text = app.appName, style = MaterialTheme.typography.titleMedium, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                val minutes = (app.totalTime / 1000) / 60
                                val seconds = ((app.totalTime / 1000) % 60)
                                Text(text = "Active Today: ${String.format("%02d:%02d", minutes, seconds)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), letterSpacing = 1.sp)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isAppUnlocked) 0.1f else 0.05f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isAppUnlocked) 0.2f else 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecked) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isAppUnlocked) 1f else 0.5f), modifier = Modifier.size(20.dp))
                            } else {
                                Box(modifier = Modifier.size(18.dp).border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isAppUnlocked) 0.6f else 0.3f), CircleShape))
                            }
                        }
                    }
                    if (index < sortedUsageList.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }

}

