package com.example.bluetoothremote.protocol

import com.example.bluetoothremote.bluetooth.BluetoothLeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel

/**
 * 新协议（2025-9-4）：所有按键与命令均为 3 字节帧：
 * 0xFF + dataHigh + dataLow。
 */
class RemoteProtocol(private val bluetoothManager: BluetoothLeManager) {

    private val _receivedKeyData = MutableStateFlow<Set<String>>(emptySet())
    val receivedKeyData: StateFlow<Set<String>> = _receivedKeyData

    private val _isLearningMode = MutableStateFlow(false)
    val isLearningMode: StateFlow<Boolean> = _isLearningMode

    private var sendJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        // 帧头
        const val FRAME_HEAD: Byte = 0xFF.toByte()

        // 主机 -> 模块 命令 (0xFF FF XX)
        const val CMD_REQUEST_KEY: Byte = 0x01 // 查询键值
        const val CMD_ENTER_LEARNING: Byte = 0x02
        const val CMD_EXIT_LEARNING: Byte = 0x03
        const val CMD_DISABLE_BOARD_KEYS: Byte = 0x04
        const val CMD_ENABLE_BOARD_KEYS: Byte = 0x05
        const val CMD_RESET_PASSWORD: Byte = 0x06
        const val CMD_MODULE_REBOOT: Byte = 0x07

        // 模块 -> APP 状态 (0xFF XX XX), 第二字节已确认为 0xFF
        const val STATUS_CONNECTED: Byte = 0x0A
        const val STATUS_DISCONNECTED: Byte = 0x0B
        const val STATUS_PASSWORD_OK: Byte = 0x0C
        const val STATUS_PASSWORD_ERROR: Byte = 0x0D

        /**
         * 16 键映射：高字节在前。
         * K16 仅单键有效，组合时需移除。
         */
        private val KEY_MAP: Map<String, Pair<Byte, Byte>> = mapOf(
            "K1" to (0x00.toByte() to 0x01.toByte()),
            "K2" to (0x00.toByte() to 0x02.toByte()),
            "K3" to (0x00.toByte() to 0x04.toByte()),
            "K4" to (0x00.toByte() to 0x08.toByte()),
            "K5" to (0x00.toByte() to 0x10.toByte()),
            "K6" to (0x00.toByte() to 0x20.toByte()),
            "K7" to (0x00.toByte() to 0x40.toByte()),
            "K8" to (0x00.toByte() to 0x80.toByte()),
            "K9" to (0x01.toByte() to 0x00.toByte()),
            "K10" to (0x02.toByte() to 0x00.toByte()),
            "K11" to (0x04.toByte() to 0x00.toByte()),
            "K12" to (0x08.toByte() to 0x00.toByte()),
            "K13" to (0x10.toByte() to 0x00.toByte()),
            "K14" to (0x20.toByte() to 0x00.toByte()),
            "K15" to (0x40.toByte() to 0x00.toByte()),
            "K16" to (0x80.toByte() to 0x00.toByte())
        )

        private const val SEND_INTERVAL_MS = 50L // 按下期间 50–100ms 连发，取 50ms
    }

    init {
        // 监听蓝牙接收数据，解析 3 字节帧
        coroutineScope.launch {
            bluetoothManager.receivedData.collect { data ->
                data?.let { processReceivedData(it) }
            }
        }
    }

    /**
     * 进入学习模式（0xFF FF 02）
     */
    fun enterLearningMode(): Boolean {
        if (bluetoothManager.connectionState.value != BluetoothLeManager.ConnectionState.CONNECTED) {
            return false
        }
        val success = bluetoothManager.writeData(buildCommandPacket(CMD_ENTER_LEARNING))
        if (success) {
            _isLearningMode.value = true
        }
        return success
    }

    /**
     * 退出学习模式（0xFF FF 03）
     */
    fun exitLearningMode(): Boolean {
        if (bluetoothManager.connectionState.value != BluetoothLeManager.ConnectionState.CONNECTED) {
            return false
        }
        val success = bluetoothManager.writeData(buildCommandPacket(CMD_EXIT_LEARNING))
        if (success) {
            _isLearningMode.value = false
        }
        return success
    }

    /**
     * 连续发送按键：3 字节帧；K16 只能单键，组合时自动移除。
     */
    fun beginContinuousSend(keys: Set<String>): Boolean {
        sendJob?.cancel()

        val filteredKeys = enforceK16Rule(keys)
        val (high, low) = calculateCompositeKey(filteredKeys)
        val frame = byteArrayOf(FRAME_HEAD, high, low)

        sendJob = coroutineScope.launch {
            while (isActive) {
                bluetoothManager.writeData(frame)
                delay(SEND_INTERVAL_MS)
            }
        }
        return true
    }

    /**
     * 松开发送 0xFF 00 00；需要更高可靠性时可考虑双发。
     */
    fun sendKeyRelease(): Boolean {
        sendJob?.cancel()
        sendJob = null
        val release = byteArrayOf(FRAME_HEAD, 0x00, 0x00)
        val first = bluetoothManager.writeData(release)
        // 释放要求可发多组 00，这里双发以提高可靠性
        val second = bluetoothManager.writeData(release)
        return first && second
    }

    /**
     * K16 只能单键。若含 K16 且有其他键，则移除 K16。
     */
    private fun enforceK16Rule(keys: Set<String>): Set<String> {
        return if (keys.contains("K16") && keys.size > 1) {
            keys.filterNot { it == "K16" }.toSet()
        } else {
            keys
        }
    }

    /**
     * 计算复合键的高低字节（位或），K16 为 0x8000。
     */
    fun calculateCompositeKey(keys: Set<String>): Pair<Byte, Byte> {
        var high = 0
        var low = 0
        keys.forEach { key ->
            KEY_MAP[key]?.let { (h, l) ->
                high = high or (h.toInt() and 0xFF)
                low = low or (l.toInt() and 0xFF)
            }
        }
        return high.toByte() to low.toByte()
    }

    /**
     * 构建命令帧 (0xFF FF cmd)
     */
    fun buildCommandPacket(cmd: Byte): ByteArray {
        return byteArrayOf(FRAME_HEAD, 0xFF.toByte(), cmd)
    }

    /**
     * 处理 3 字节接收数据，当前仅更新按键集合；状态帧交由上层决定是否消费。
     */
    private fun processReceivedData(data: ByteArray) {
        if (data.size < 3) return
        if (data[0] != FRAME_HEAD) return

        val high = data[1].toInt() and 0xFF
        val low = data[2].toInt() and 0xFF
        // 跳过状态帧（连接/断开/密码正确/密码错误），避免误报按键
        if (isStatusFrame(high, low)) return

        val pressed = parseKeyData(high, low)
        _receivedKeyData.value = pressed
    }

    /**
     * 解析按键集合（从高低字节 bitmask）
     */
    fun parseKeyData(high: Int, low: Int): Set<String> {
        val pressed = mutableSetOf<String>()
        KEY_MAP.forEach { (key, pair) ->
            val (h, l) = pair
            val hInt = h.toInt() and 0xFF
            val lInt = l.toInt() and 0xFF
            val hitHigh = hInt != 0 && (high and hInt) != 0
            val hitLow = lInt != 0 && (low and lInt) != 0
            if (hitHigh || hitLow) {
                pressed.add(key)
            }
        }
        return pressed
    }

    private fun isStatusFrame(high: Int, low: Int): Boolean {
        return (high == 0x00 && (low == 0x0A || low == 0x0B)) ||
            (high == 0xFF && (low == 0x0C || low == 0x0D))
    }

    fun getKeyDisplayName(key: String): String {
        return when (key) {
            "K1" -> "上"
            "K2" -> "下"
            "K3" -> "左"
            "K4" -> "右"
            "K5" -> "功能1"
            "K6" -> "功能2"
            "K7" -> "功能3"
            "K8" -> "功能4"
            "K9" -> "K9"
            "K10" -> "K10"
            "K11" -> "K11"
            "K12" -> "K12"
            "K13" -> "K13"
            "K14" -> "K14"
            "K15" -> "K15"
            "K16" -> "K16"
            else -> key
        }
    }

    fun cleanup() {
        sendJob?.cancel()
        coroutineScope.cancel()
    }
}
