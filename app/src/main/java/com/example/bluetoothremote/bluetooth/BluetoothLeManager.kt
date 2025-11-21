package com.example.bluetoothremote.bluetooth

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import com.example.bluetoothremote.password.PasswordManager

class BluetoothLeManager(private val context: Context) {
    
    private val bluetoothAdapter: BluetoothAdapter? = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (e: Exception) {
        null
    }
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private val passwordManager = PasswordManager(context)
    
    // 当前连接使用的密码
    private var currentPassword: String? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices
    
    private val _receivedData = MutableStateFlow<ByteArray?>(null)
    val receivedData: StateFlow<ByteArray?> = _receivedData
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _reconnectAttemptsFlow = MutableStateFlow(0)
    val reconnectAttemptsFlow: StateFlow<Int> = _reconnectAttemptsFlow
    
    private val _signalStrength = MutableStateFlow<Int?>(null)
    val signalStrength: StateFlow<Int?> = _signalStrength

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var authenticationJob: Job? = null
    private var reconnectJob: Job? = null
    
    // 认证状态跟踪
    private var authenticationStartTime: Long = 0
    private var isWaitingForAuthResponse: Boolean = false
    
    // BLE服务和特征值UUID
    companion object {
        private val SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805f9b34fb")
        private val WRITE_CHARACTERISTIC_UUID = UUID.fromString("0000FFE9-0000-1000-8000-00805f9b34fb")
        private val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("0000FFE4-0000-1000-8000-00805f9b34fb")
        
        // 密码转换为字节数组的工具方法
        private fun passwordToBytes(password: String): ByteArray {
            return password.toByteArray(Charsets.UTF_8)
        }
        
        private const val CONNECTION_TIMEOUT = 10000L // 10秒
        private const val AUTHENTICATION_TIMEOUT = 5000L // 5秒认证超时
        private const val AUTH_VERIFICATION_TIMEOUT = 3000L // 3秒验证超时
        private const val RECONNECT_DELAY = 10000L // 10秒
        private const val MAX_RECONNECT_ATTEMPTS = 5
        
        // 密码修改指令头
        private val PASSWORD_CHANGE_HEADER = byteArrayOf(0xAA.toByte(), 0x2D.toByte(), 0xD4.toByte())
    }
    
    enum class ConnectionState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        AUTHENTICATING,
        CONNECTED,
        RECONNECTING
    }
    
    private var reconnectAttempts = 0
    private var targetDevice: BluetoothDevice? = null
    
    /**
     * 检查蓝牙是否可用
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    
    /**
     * 检查是否具有必要的蓝牙权限
     */
    private fun hasBluetoothPermissions(): Boolean {
        // 位置权限在所有Android版本都需要
        val hasLocationPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要新权限 + 位置权限
            hasLocationPermission &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12以下使用旧权限 + 位置权限
            hasLocationPermission &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 开始扫描BLE设备
     */
    fun startScanning() {
        // 最简单的扫描，但要显示设备
        try {
            bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
            _isScanning.value = true
        } catch (e: Exception) {
            // 完全忽略所有异常
        }
    }
    
    /**
     * 停止扫描BLE设备
     */
    fun stopScanning() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.value = false
        } catch (e: Exception) {
            // 完全忽略异常，但确保状态正确
            _isScanning.value = false
        }
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            try {
                val device = result?.device
                if (device != null) {
                    // 获取设备名称，允许多次尝试
                    val deviceName = try {
                        device.name
                    } catch (e: SecurityException) {
                        null
                    }
                    
                    // 检查是否是目标设备 - 只添加ALLREMOTE开头的设备或已知的设备地址
                    val shouldAdd = if (!deviceName.isNullOrEmpty()) {
                        deviceName.startsWith("ALLREMOTE", ignoreCase = true)
                    } else {
                        // 如果名称暂时获取不到，检查是否是已知设备
                        false
                    }
                    
                    if (shouldAdd) {
                        val currentDevices = _scannedDevices.value.toMutableList()
                        // 避免重复添加同一设备
                        if (!currentDevices.any { it.address == device.address }) {
                            android.util.Log.d("BluetoothLeManager", "发现设备: $deviceName (${device.address})")
                            currentDevices.add(device)
                            _scannedDevices.value = currentDevices
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d("BluetoothLeManager", "扫描回调异常: ${e.message}")
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            try {
                _isScanning.value = false
            } catch (e: Exception) {
                // 完全忽略
            }
        }
    }
    
    /**
     * 连接到指定设备
     */
    fun connectToDevice(device: BluetoothDevice, password: String) {
        // 检查权限
        if (!hasBluetoothPermissions()) {
            _errorMessage.value = "缺少蓝牙权限，请检查应用权限设置"
            return
        }
        
        stopScanning()
        disconnect()
        
        targetDevice = device
        currentPassword = password
        _connectionState.value = ConnectionState.CONNECTING
        
        coroutineScope.launch {
            try {
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
                
                // 设置连接超时检测
                delay(CONNECTION_TIMEOUT)
                // 如果超时后仍未连接成功，强制断开
                if (_connectionState.value == ConnectionState.CONNECTING) {
                    android.util.Log.d("BluetoothLeManager", "连接超时，强制断开")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _errorMessage.value = "连接超时，请检查设备是否在范围内"
                    bluetoothGatt?.disconnect()
                }
            } catch (e: SecurityException) {
                android.util.Log.e("BluetoothLeManager", "连接权限被拒绝", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                _errorMessage.value = "蓝牙连接权限被拒绝"
            }
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            android.util.Log.d("BluetoothLeManager", "连接状态变化: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // 检查连接状态码，只有成功才继续
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        android.util.Log.d("BluetoothLeManager", "BLE连接成功，开始服务发现")
                        coroutineScope.launch {
                            _connectionState.value = ConnectionState.CONNECTING
                            delay(1000) // 等待连接稳定
                            // 优先请求使用 BLE Coded PHY S=8（125 kbps），提高远距离稳定性
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    bluetoothGatt?.setPreferredPhy(
                                        BluetoothDevice.PHY_LE_CODED,
                                        BluetoothDevice.PHY_LE_CODED,
                                        BluetoothDevice.PHY_OPTION_S8
                                    )
                                }
                            } catch (_: Exception) { }
                            android.util.Log.d("BluetoothLeManager", "开始发现服务")
                            bluetoothGatt?.discoverServices()
                        }
                    } else {
                        android.util.Log.d("BluetoothLeManager", "BLE连接失败: status=$status")
                        _connectionState.value = ConnectionState.DISCONNECTED
                        _errorMessage.value = "连接失败 (错误代码: $status)"
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    android.util.Log.d("BluetoothLeManager", "BLE已断开连接: status=$status")
                    authenticationJob?.cancel() // 取消认证超时任务
                    
                    // 如果是在认证期间断开，很可能是密码错误
                    if (isWaitingForAuthResponse) {
                        android.util.Log.d("BluetoothLeManager", "认证期间断开连接，可能是密码错误")
                        isWaitingForAuthResponse = false
                        _errorMessage.value = "认证失败，密码可能不正确"
                    }
                    
                    _connectionState.value = ConnectionState.DISCONNECTED
                    // 移除自动重连逻辑 - 只有APP刚启动时才自动连接，其他时候都需要手动操作
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            android.util.Log.d("BluetoothLeManager", "服务发现回调: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                android.util.Log.d("BluetoothLeManager", "查找服务 FFE0: ${if(service != null) "找到" else "未找到"}")
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
                    notifyCharacteristic = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)
                    
                    android.util.Log.d("BluetoothLeManager", "写特征值 FFE9: ${if(writeCharacteristic != null) "找到" else "未找到"}")
                    android.util.Log.d("BluetoothLeManager", "通知特征值 FFE4: ${if(notifyCharacteristic != null) "找到" else "未找到"}")
                    
                    // 启用通知
                    notifyCharacteristic?.let { characteristic ->
                        android.util.Log.d("BluetoothLeManager", "启用通知特征值")
                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            val writeResult = gatt.writeDescriptor(descriptor)
                            android.util.Log.d("BluetoothLeManager", "写入通知描述符: ${if(writeResult) "成功" else "失败"}")
                        } else {
                            android.util.Log.d("BluetoothLeManager", "未找到通知描述符")
                        }
                    }
                    
                    // 延迟开始认证，确保通知已设置
                    coroutineScope.launch {
                        delay(500)
                        // 获取信号强度
                        try {
                            gatt.readRemoteRssi()
                        } catch (e: Exception) {
                            // 静默处理失败
                        }
                        startAuthentication()
                    }
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _errorMessage.value = "未发现服务 FFE0"
                }
            } else {
                _connectionState.value = ConnectionState.DISCONNECTED
                _errorMessage.value = "服务发现失败(status=$status)"
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.value?.let { data ->
                android.util.Log.d("BluetoothLeManager", "接收到数据: ${data.joinToString(" ") { "0x%02X".format(it) }}")
                _receivedData.value = data
                // 新协议 3 字节帧 0xFF + high + low，避免将 0xFF 误判为断开
                if (handleNewProtocolFrame(data)) {
                    return
                }
                // 旧逻辑保留为兼容，不再对 0xFF 做断开处理
                if (data.isNotEmpty()) {
                    val responseCode = data[0].toInt() and 0xFF
                    android.util.Log.d("BluetoothLeManager", "收到数据: 0x${"%02X".format(responseCode)}")
                }
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            // 写入完成回调
        }
        
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            // 特征值读取完成回调
        }
        
        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _signalStrength.value = rssi
            }
        }
    }
    
    /**
     * 开始认证流程
     */
    private fun startAuthentication() {
        android.util.Log.d("BluetoothLeManager", "开始认证流程")
        val password = currentPassword ?: passwordManager.getDefaultPassword()
        val passwordBytes = passwordToBytes(password)
        
        android.util.Log.d("BluetoothLeManager", "发送认证密码: ${passwordBytes.joinToString(" ") { "0x%02X".format(it) }}")
        
        // 发送认证密码
        val success = writeData(passwordBytes)
        android.util.Log.d("BluetoothLeManager", "认证密码发送${if(success) "成功" else "失败"}")
        
        if (success) {
            android.util.Log.d("BluetoothLeManager", "认证密码发送成功 - 等待连接状态确认")
            _connectionState.value = ConnectionState.AUTHENTICATING
            isWaitingForAuthResponse = true // 标记正在等待认证响应
            reconnectAttempts = 0
            
            // 设置认证超时 - 如果超时时间内蓝牙还连着，就认为认证成功
            authenticationJob = coroutineScope.launch {
                delay(AUTHENTICATION_TIMEOUT)
                if (_connectionState.value == ConnectionState.AUTHENTICATING) {
                    // 超时了但蓝牙还连着，说明认证成功（模块没有断开连接）
                    android.util.Log.d("BluetoothLeManager", "认证超时但连接保持，认证成功")
                    isWaitingForAuthResponse = false
                    authenticationStartTime = 0
                    _connectionState.value = ConnectionState.CONNECTED
                }
            }
        } else {
            android.util.Log.d("BluetoothLeManager", "认证密码发送失败 - 断开连接")
            _connectionState.value = ConnectionState.DISCONNECTED
            _errorMessage.value = "认证密码发送失败"
        }
    }
    
    /**
     * 发送数据到设备
     */
    fun writeData(data: ByteArray): Boolean {
        val characteristic = writeCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false
        
        return try {
            characteristic.value = data
            // 写入无响应可降低延迟（若设备支持）。此处保留标准写以兼容。
            gatt.writeCharacteristic(characteristic)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 开始重连流程
     */
    private fun startReconnection() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS || targetDevice == null) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _reconnectAttemptsFlow.value = 0
            return
        }
        
        _connectionState.value = ConnectionState.RECONNECTING
        reconnectAttempts++
        _reconnectAttemptsFlow.value = reconnectAttempts
        
        reconnectJob = coroutineScope.launch {
            delay(RECONNECT_DELAY)
            targetDevice?.let { device ->
                val reconnectPassword = currentPassword ?: passwordManager.getDefaultPassword()
                connectToDevice(device, reconnectPassword)
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        authenticationJob?.cancel()
        reconnectJob?.cancel()
        
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        
        // 重置认证状态
        isWaitingForAuthResponse = false
        authenticationStartTime = 0
        
        _connectionState.value = ConnectionState.DISCONNECTED
        reconnectAttempts = 0
        _reconnectAttemptsFlow.value = 0
        targetDevice = null
    }
    
    /**
     * 修改设备密码
     * @param newPassword 新密码字符串（6位）
     * @return 是否发送成功
     */
    fun changePassword(newPassword: String): Boolean {
        if (newPassword.length != 6) {
            android.util.Log.e("BluetoothLeManager", "密码长度必须为6位")
            return false
        }
        
        if (_connectionState.value != ConnectionState.CONNECTED) {
            android.util.Log.e("BluetoothLeManager", "设备未连接，无法修改密码")
            return false
        }
        
        // 构建密码修改数据包：0xAA 0x2D 0xD4 + 6字节密码
        val passwordBytes = newPassword.toByteArray(Charsets.UTF_8)
        val changePasswordPacket = PASSWORD_CHANGE_HEADER + passwordBytes
        
        android.util.Log.d("BluetoothLeManager", "发送密码修改指令: ${changePasswordPacket.joinToString(" ") { "0x%02X".format(it) }}")
        android.util.Log.d("BluetoothLeManager", "新密码: $newPassword")
        
        return writeData(changePasswordPacket)
    }
    
    /**
     * 修改设备密码（带当前密码验证）
     */
    fun changePasswordWithVerification(
        currentPassword: String, 
        newPassword: String
    ): Boolean {
        // 验证输入格式
        if (currentPassword.length != 6 || newPassword.length != 6) {
            android.util.Log.e("BluetoothLeManager", "密码长度必须为6位")
            _errorMessage.value = "密码长度必须为6位"
            return false
        }
        
        // 验证设备连接状态
        if (_connectionState.value != ConnectionState.CONNECTED) {
            android.util.Log.e("BluetoothLeManager", "设备未连接，无法修改密码")
            _errorMessage.value = "设备未连接，无法修改密码"
            return false
        }
        
        // 验证当前密码
        val targetAddress = targetDevice?.address
        if (targetAddress != null) {
            val savedPassword = passwordManager.getDevicePassword(targetAddress)
            if (currentPassword != savedPassword) {
                android.util.Log.e("BluetoothLeManager", "当前密码错误")
                _errorMessage.value = "当前密码错误，请重新输入"
                return false
            }
        } else {
            android.util.Log.e("BluetoothLeManager", "目标设备信息丢失")
            _errorMessage.value = "连接信息错误"
            return false
        }
        
        // 执行密码修改
        val success = changePassword(newPassword)
        if (success) {
            // 安全措施：修改密码成功后，彻底清除设备信息
            // 强制下次连接时重新扫描和输入新密码
            passwordManager.resetDevicePassword(targetAddress)
            android.util.Log.d("BluetoothLeManager", "密码修改成功，已彻底清除设备信息，下次需重新扫描连接")
            _errorMessage.value = "密码修改成功，设备已移除，请重新扫描连接"
        } else {
            _errorMessage.value = "密码修改失败"
        }
        
        return success
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopScanning()
        disconnect()
        coroutineScope.cancel()
    }

    /**
     * 处理新协议 3 字节帧：0xFF + high + low。
     * 返回 true 表示已处理，避免走旧逻辑。
     */
    private fun handleNewProtocolFrame(data: ByteArray): Boolean {
        if (data.size < 3) return false
        if ((data[0].toInt() and 0xFF) != 0xFF) return false

        val high = data[1].toInt() and 0xFF
        val low = data[2].toInt() and 0xFF

        // 状态帧处理（不触发误断开）
        when {
            high == 0x00 && low == 0x0A -> {
                // 模块通知连接成功
                _connectionState.value = ConnectionState.CONNECTED
            }
            high == 0x00 && low == 0x0B -> {
                // 模块通知断开
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            high == 0xFF && low == 0x0C -> {
                // 密码正确
                _errorMessage.value = "密码正确"
            }
            high == 0xFF && low == 0x0D -> {
                // 密码错误
                _errorMessage.value = "密码错误"
            }
            else -> {
                // 其他帧（包括键值 0xFF xx xx、释放 0xFF 00 00）仅记录即可
            }
        }
        return true
    }
}
