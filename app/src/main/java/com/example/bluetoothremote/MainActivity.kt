package com.example.bluetoothremote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import com.example.bluetoothremote.ui.theme.BluetoothremoteTheme
import com.example.bluetoothremote.bluetooth.BluetoothLeManager
import com.example.bluetoothremote.protocol.RemoteProtocol
import com.example.bluetoothremote.viewmodel.RemoteViewModel
import com.example.bluetoothremote.password.PasswordManager
import com.example.bluetoothremote.ui.screens.PasswordChangeScreen
import com.example.bluetoothremote.ui.screens.DeviceManagementScreen
import com.example.bluetoothremote.ui.components.RemoteControllerView
import com.example.bluetoothremote.ui.components.RemoteLayoutMode
import com.example.bluetoothremote.ui.screens.DeviceScanScreen
import androidx.compose.runtime.collectAsState
import com.example.bluetoothremote.ui.components.StatusIndicator
import com.example.bluetoothremote.ui.components.ReconnectPasswordDialog
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    
    // 添加权限状态变量
    private var permissionsGranted by mutableStateOf(false)
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 权限结果处理
        val allGranted = permissions.values.all { it }
        permissionsGranted = allGranted
        
        if (allGranted) {
            // 所有权限都被授予，手动触发重组
            android.util.Log.d("MainActivity", "所有权限已授予")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化权限状态
        permissionsGranted = hasAllBluetoothPermissions()
        
        // 启动后2秒自动请求蓝牙权限
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000) // 等待2秒
            if (!hasAllBluetoothPermissions()) {
                requestBluetoothPermissions()
            }
        }
        
        setContent {
            BluetoothremoteTheme {
                val context = LocalContext.current
                var viewModel: RemoteViewModel? by remember { mutableStateOf(null) }
                
                // 安全初始化ViewModel
                LaunchedEffect(Unit) {
                    try {
                        val bleManager = BluetoothLeManager(context)
                        val protocol = RemoteProtocol(bleManager)
                        val learning = com.example.bluetoothremote.learning.LearningController(context)
                        viewModel = RemoteViewModel(bleManager, protocol, learning, context)
                    } catch (e: Exception) {
                        // 初始化失败，静默处理
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (viewModel != null) {
                        MainScreen(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = viewModel!!,
                            hasPermissions = permissionsGranted,
                            onRequestPermissions = { requestBluetoothPermissions() }
                        )
                    } else {
                        // 初始化加载界面
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("正在初始化蓝牙...")
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun hasAllBluetoothPermissions(): Boolean {
        // 位置权限在所有Android版本都需要
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要新权限 + 位置权限
            hasLocationPermission &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12以下使用旧权限 + 位置权限  
            hasLocationPermission &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: RemoteViewModel,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeviceManagement by remember { mutableStateOf(false) }
    var showPasswordChange by remember { mutableStateOf(false) }
    var layoutMode by remember { mutableStateOf(RemoteLayoutMode.SIX_KEYS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        when (uiState.connectionState) {
            BluetoothLeManager.ConnectionState.CONNECTED -> {
                // 已连接状态
                StatusIndicator(
                    connectionState = uiState.connectionState,
                    deviceName = uiState.connectedDeviceName,
                    signalStrength = uiState.signalStrength,
                    isLearningMode = uiState.isLearningMode,
                    batteryLevel = uiState.batteryLevel
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 修改密码按钮
                Button(
                    onClick = { showPasswordChange = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔑 修改密码")
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                // 遥控器界面
                RemoteControllerView(
                    isEnabled = true,
                    onKeyPressed = { key -> viewModel.onKeyPressed(key) },
                    onKeyReleased = { key -> viewModel.onKeyReleased(key) },
                    modifier = Modifier.weight(1f),
                    layoutMode = layoutMode
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.disconnect() }, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("断开连接")
                }
            }
            else -> {
                // 未连接状态 - 显示扫描界面
                Text(
                    text = "Bluetooth Remote",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 权限检查 - 简化UI，自动请求权限
                if (!hasPermissions) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在请求蓝牙权限...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "请在弹出对话框中允许权限以使用蓝牙功能",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                
                StatusIndicator(
                    connectionState = uiState.connectionState,
                    deviceName = uiState.connectedDeviceName,
                    signalStrength = uiState.signalStrength,
                    isLearningMode = uiState.isLearningMode,
                    batteryLevel = uiState.batteryLevel
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 布局切换：6/8/16 键
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = layoutMode == RemoteLayoutMode.SIX_KEYS,
                        onClick = { layoutMode = RemoteLayoutMode.SIX_KEYS },
                        label = { Text("6键") }
                    )
                    FilterChip(
                        selected = layoutMode == RemoteLayoutMode.EIGHT_KEYS,
                        onClick = { layoutMode = RemoteLayoutMode.EIGHT_KEYS },
                        label = { Text("8键") }
                    )
                    FilterChip(
                        selected = layoutMode == RemoteLayoutMode.SIXTEEN_KEYS,
                        onClick = { layoutMode = RemoteLayoutMode.SIXTEEN_KEYS },
                        label = { Text("16键") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showDeviceManagement = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📱 设备管理")
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                // 测试遥控器按钮
                // ???????
                                // ???????
                var showTestRemote by remember { mutableStateOf(false) }

                if (showTestRemote) {
                    // ??????????
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        // ???????
                        RemoteControllerView(
                            isEnabled = true,
                            onKeyPressed = { key ->
                                println("??: $key")
                            },
                            onKeyReleased = { key ->
                                println("??: $key")
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            layoutMode = layoutMode
                        )

                        // ????????
                        Button(
                            onClick = { showTestRemote = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                            )
                        ) {
                            Text("?", fontSize = 18.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = { showTestRemote = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("???????")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
DeviceScanScreen(
                    devices = uiState.scannedDevices,
                    isScanning = uiState.isScanning,
                    passwordManager = PasswordManager(LocalContext.current),
                    onStartScan = {
                        if (hasPermissions) {
                            viewModel.startScanning()
                        } else {
                            onRequestPermissions()
                        }
                    },
                    onStopScan = { viewModel.stopScanning() },
                    onDeviceConnect = { device, password, remember -> 
                        viewModel.connectToDevice(device, password)
                        if (remember) {
                            viewModel.saveDevicePassword(device.address, password)
                        }
                    },
                    connectionState = uiState.connectionState
                )
                
                // 权限授予后的初始化逻辑
                val context = LocalContext.current
                var hasInitialized by remember { mutableStateOf(false) }
                LaunchedEffect(hasPermissions) {
                    if (hasPermissions && !hasInitialized) {
                        hasInitialized = true
                        kotlinx.coroutines.delay(500) // 等待500ms让界面稳定
                        
                        // 检查是否有存储的设备
                        val passwordManager = com.example.bluetoothremote.password.PasswordManager(context)
                        val hasStoredDevices = passwordManager.getLastConnectedDevice() != null
                        
                        if (hasStoredDevices) {
                            // 有存储设备，尝试自动连接
                            viewModel.tryAutoConnect()
                        } else {
                            // 首次使用，没有存储设备，直接开始扫描
                            if (!uiState.isScanning) {
                                viewModel.startScanning()
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 设备管理界面
    if (showDeviceManagement) {
        DeviceManagementScreen(
            passwordManager = PasswordManager(LocalContext.current),
            onBack = { showDeviceManagement = false }
        )
    }
    
    // 密码修改界面
    if (showPasswordChange) {
        PasswordChangeScreen(
            viewModel = viewModel,
            onBack = { showPasswordChange = false }
        )
    }
    
    // 重新连接密码对话框
    if (uiState.showPasswordRetryDialog) {
        val retryDeviceInfo = uiState.retryDeviceInfo
        if (retryDeviceInfo != null) {
            val (device, oldPassword) = retryDeviceInfo
            val deviceName = try {
                device.name ?: "Unknown Device"
            } catch (e: SecurityException) {
                "Unknown Device"
            }
            
            ReconnectPasswordDialog(
                deviceName = deviceName,
                onPasswordEntered = { newPassword ->
                    viewModel.retryConnectWithNewPassword(newPassword)
                },
                onDeleteDevice = {
                    viewModel.deleteFailedDevice()
                },
                onDismiss = {
                    viewModel.dismissPasswordRetryDialog()
                },
                isReconnecting = uiState.isReconnecting,
                isRetryAfterFailure = uiState.isRetryAfterFailure
            )
        }
    }
}
