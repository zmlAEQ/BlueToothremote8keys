package com.example.bluetoothremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

@Composable
fun RemoteControllerView(
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit,
    modifier: Modifier = Modifier,
    layoutMode: RemoteLayoutMode = RemoteLayoutMode.SIX_KEYS
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isLightOn by remember { mutableStateOf(false) }
    var pressedKeys by remember { mutableStateOf(setOf<String>()) }
    
    Box(modifier = modifier.fillMaxSize()) {
        // 红色指示灯 - 在左上角
        IndicatorLight(
            isOn = isLightOn || pressedKeys.isNotEmpty(),
            onClick = {
                isLightOn = !isLightOn
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        
        when (layoutMode) {
            RemoteLayoutMode.SIX_KEYS -> {
                SixKeyLayout(
                    isEnabled = isEnabled,
                    onKeyPressed = { key ->
                        pressedKeys = pressedKeys + key
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onKeyPressed(key)
                    },
                    onKeyReleased = { key ->
                        pressedKeys = pressedKeys - key
                        onKeyReleased(key)
                    }
                )
            }
            RemoteLayoutMode.EIGHT_KEYS -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DirectionKeysLayout(
                        isEnabled = isEnabled,
                        onKeyPressed = { key ->
                            pressedKeys = pressedKeys + key
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onKeyPressed(key)
                        },
                        onKeyReleased = { key ->
                            pressedKeys = pressedKeys - key
                            onKeyReleased(key)
                        }
                    )

                    Spacer(modifier = Modifier.height(80.dp))

                    FunctionKeysLayout(
                        isEnabled = isEnabled,
                        onKeyPressed = { key ->
                            pressedKeys = pressedKeys + key
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onKeyPressed(key)
                        },
                        onKeyReleased = { key ->
                            pressedKeys = pressedKeys - key
                            onKeyReleased(key)
                        }
                    )
                }
            }
            RemoteLayoutMode.SIXTEEN_KEYS -> {
                SixteenKeyLayout(
                    isEnabled = isEnabled,
                    onKeyPressed = { key ->
                        pressedKeys = pressedKeys + key
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onKeyPressed(key)
                    },
                    onKeyReleased = { key ->
                        pressedKeys = pressedKeys - key
                        onKeyReleased(key)
                    }
                )
            }
        }
    }
}

enum class RemoteLayoutMode { SIX_KEYS, EIGHT_KEYS, SIXTEEN_KEYS }

@Composable
private fun DirectionKeysLayout(
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上键 - 三角形
        TriangleKeyButton(
            key = "K1",
            direction = TriangleDirection.UP,
            isEnabled = isEnabled,
            onKeyPressed = onKeyPressed,
            onKeyReleased = onKeyReleased
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 中间行：左键、中间空白、右键
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左键 - 三角形
            TriangleKeyButton(
                key = "K3",
                direction = TriangleDirection.LEFT,
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased
            )
            
            Spacer(modifier = Modifier.width(88.dp)) // 80dp + 4dp + 4dp
            
            // 右键 - 三角形
            TriangleKeyButton(
                key = "K4",
                direction = TriangleDirection.RIGHT,
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 下键 - 三角形
        TriangleKeyButton(
            key = "K2",
            direction = TriangleDirection.DOWN,
            isEnabled = isEnabled,
            onKeyPressed = onKeyPressed,
            onKeyReleased = onKeyReleased
        )
    }
}

@Composable
private fun FunctionKeysLayout(
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // K5 太阳图标
            CustomIconButton(
                key = "K5",
                iconType = CustomIcon.SUN,
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased
            )
            
            // K6 双三角形
            CustomIconButton(
                key = "K6", 
                iconType = CustomIcon.DOUBLE_TRIANGLE,
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // K7 房子图标
            CustomIconButton(
                key = "K7",
                iconType = CustomIcon.HOUSE,
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased
            )
            
            // K8 双圆圈
            CustomIconButton(
                key = "K8",
                iconType = CustomIcon.DOUBLE_CIRCLE,
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased
            )
        }
    }
}

@Composable
private fun RemoteKeyButton(
    key: String,
    icon: ImageVector,
    label: String,
    isEnabled: Boolean,
    keyColor: Color,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    // 监听按压交互
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (!isPressed) {
                        isPressed = true
                        onKeyPressed(key)
                    }
                }
                is PressInteraction.Release -> {
                    if (isPressed) {
                        isPressed = false
                        onKeyReleased(key)
                    }
                }
                is PressInteraction.Cancel -> {
                    if (isPressed) {
                        isPressed = false
                        onKeyReleased(key)
                    }
                }
            }
        }
    }
    
    val buttonColor = when {
        !isEnabled -> Color.Gray
        isPressed -> keyColor.copy(alpha = 0.8f)
        else -> keyColor
    }
    
    val contentColor = if (isEnabled) Color.White else Color.Gray
    
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FunctionKeyButton12(
    key: String,
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    // 监听按压交互
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (!isPressed) {
                        isPressed = true
                        onKeyPressed(key)
                    }
                }
                is PressInteraction.Release -> {
                    if (isPressed) {
                        isPressed = false
                        onKeyReleased(key)
                    }
                }
                is PressInteraction.Cancel -> {
                    if (isPressed) {
                        isPressed = false
                        onKeyReleased(key)
                    }
                }
            }
        }
    }
    
    val buttonColor = when {
        !isEnabled -> Color.Gray
        isPressed -> Color(0xFF6B7280).copy(alpha = 0.8f)
        else -> Color(0xFF6B7280)
    }
    
    val contentColor = if (isEnabled) Color.White else Color.Gray
    
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 显示 "1" 和 "2" 的组合
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1",
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "2", 
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.offset(y = (-4).dp)
                )
            }
            Text(
                text = "菜单",
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

enum class TriangleDirection {
    UP, DOWN, LEFT, RIGHT
}

@Composable
private fun TriangleKeyButton(
    key: String,
    direction: TriangleDirection,
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    // 监听按压交互
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (!isPressed) {
                        isPressed = true
                        onKeyPressed(key)
                    }
                }
                is PressInteraction.Release -> {
                    if (isPressed) {
                        isPressed = false
                        onKeyReleased(key)
                    }
                }
                is PressInteraction.Cancel -> {
                    if (isPressed) {
                        isPressed = false
                        onKeyReleased(key)
                    }
                }
            }
        }
    }
    
    val buttonColor = when {
        !isEnabled -> Color.Gray
        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.primary
    }
    
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(buttonColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val trianglePath = Path()
            val size = this.size.minDimension
            val center = Offset(size / 2, size / 2)
            val radius = size * 0.35f // 更大更饱满的三角形
            
            when (direction) {
                TriangleDirection.UP -> {
                    trianglePath.moveTo(center.x, center.y - radius)
                    trianglePath.lineTo(center.x - radius * 0.866f, center.y + radius * 0.5f)
                    trianglePath.lineTo(center.x + radius * 0.866f, center.y + radius * 0.5f)
                    trianglePath.close()
                }
                TriangleDirection.DOWN -> {
                    trianglePath.moveTo(center.x, center.y + radius)
                    trianglePath.lineTo(center.x - radius * 0.866f, center.y - radius * 0.5f)
                    trianglePath.lineTo(center.x + radius * 0.866f, center.y - radius * 0.5f)
                    trianglePath.close()
                }
                TriangleDirection.LEFT -> {
                    trianglePath.moveTo(center.x - radius, center.y)
                    trianglePath.lineTo(center.x + radius * 0.5f, center.y - radius * 0.866f)
                    trianglePath.lineTo(center.x + radius * 0.5f, center.y + radius * 0.866f)
                    trianglePath.close()
                }
                TriangleDirection.RIGHT -> {
                    trianglePath.moveTo(center.x + radius, center.y)
                    trianglePath.lineTo(center.x - radius * 0.5f, center.y - radius * 0.866f)
                    trianglePath.lineTo(center.x - radius * 0.5f, center.y + radius * 0.866f)
                    trianglePath.close()
                }
            }
            
            drawPath(
                path = trianglePath,
                color = Color.White // 白色三角形
            )
        }
    }
}

@Composable
private fun IndicatorLight(
    isOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lightColor = if (isOn) {
        Color(0xFF44FF44) // 亮绿色
    } else {
        Color(0xFF333333) // 暗灰色（无颜色状态）
    }
    
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(lightColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 添加一个小的内部亮点来模拟LED效果
        if (isOn) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFAAFFAA)) // 更亮的绿色中心点
            )
        }
    }
}

enum class CustomIcon {
    SUN, DOUBLE_TRIANGLE, HOUSE, DOUBLE_CIRCLE
}

@Composable
private fun CustomIconButton(
    key: String,
    iconType: CustomIcon,
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    // 监听按压交互
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (!isPressed) {
                        isPressed = true
                        onKeyPressed(key)
                    }
                }
                is PressInteraction.Release -> {
                    if (isPressed) {
                        isPressed = false
                        onKeyReleased(key)
                    }
                }
                is PressInteraction.Cancel -> {
                    if (isPressed) {
                        isPressed = false
                        onKeyReleased(key)
                    }
                }
            }
        }
    }
    
    val buttonColor = when {
        !isEnabled -> Color.Gray
        isPressed -> Color(0xFF6B7280).copy(alpha = 0.8f)
        else -> Color(0xFF6B7280)
    }
    
    val iconColor = if (isEnabled) Color.White else Color.Gray
    
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(buttonColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            when (iconType) {
                CustomIcon.SUN -> {
                    // 太阳图标 - 保持原始大小
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension * 0.15f
                    
                    // 中心圆圈
                    drawCircle(
                        color = iconColor,
                        radius = radius,
                        center = center
                    )
                    
                    // 太阳光线
                    val rayLength = size.minDimension * 0.12f
                    val rayStart = radius + size.minDimension * 0.05f
                    for (i in 0..7) {
                        val angle = i * 45f * (kotlin.math.PI / 180f)
                        val startX = center.x + kotlin.math.cos(angle).toFloat() * rayStart
                        val startY = center.y + kotlin.math.sin(angle).toFloat() * rayStart
                        val endX = center.x + kotlin.math.cos(angle).toFloat() * (rayStart + rayLength)
                        val endY = center.y + kotlin.math.sin(angle).toFloat() * (rayStart + rayLength)
                        
                        drawLine(
                            color = iconColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }
                
                CustomIcon.DOUBLE_TRIANGLE -> {
                    // 双重叠三角形 - 更大尺寸，左小空心，右大实心
                    val center = Offset(size.width / 2, size.height / 2)
                    val smallTriangleSize = size.minDimension * 0.28f
                    val largeTriangleSize = size.minDimension * 0.35f
                    
                    // 第一个三角形（左边小的空心）
                    val triangle1 = Path().apply {
                        moveTo(center.x - smallTriangleSize * 0.4f, center.y - smallTriangleSize * 0.5f)
                        lineTo(center.x + smallTriangleSize * 0.6f, center.y)
                        lineTo(center.x - smallTriangleSize * 0.4f, center.y + smallTriangleSize * 0.5f)
                        close()
                    }
                    
                    // 第二个三角形（右边大的实心）
                    val triangle2 = Path().apply {
                        moveTo(center.x + largeTriangleSize * 0.05f, center.y - largeTriangleSize * 0.6f)
                        lineTo(center.x + largeTriangleSize * 1.2f, center.y)
                        lineTo(center.x + largeTriangleSize * 0.05f, center.y + largeTriangleSize * 0.6f)
                        close()
                    }
                    
                    // 绘制小三角形（空心）
                    drawPath(
                        triangle1, 
                        iconColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                    // 绘制大三角形（实心）
                    drawPath(triangle2, iconColor)
                }
                
                CustomIcon.HOUSE -> {
                    // 房屋图标 - 更大尺寸
                    val center = Offset(size.width / 2, size.height / 2)
                    val houseSize = size.minDimension * 0.4f  // 从0.3f增加到0.4f
                    
                    val housePath = Path().apply {
                        // 屋顶
                        moveTo(center.x, center.y - houseSize * 0.6f)  // 屋顶更高
                        lineTo(center.x - houseSize * 0.6f, center.y)  // 屋顶更宽
                        lineTo(center.x + houseSize * 0.6f, center.y)
                        close()
                        
                        // 房屋主体
                        addRect(
                            androidx.compose.ui.geometry.Rect(
                                left = center.x - houseSize * 0.5f,    // 房屋更宽
                                top = center.y,
                                right = center.x + houseSize * 0.5f,
                                bottom = center.y + houseSize * 0.6f   // 房屋更高
                            )
                        )
                    }
                    
                    drawPath(housePath, iconColor)
                }
                
                CustomIcon.DOUBLE_CIRCLE -> {
                    // 双重叠圆圈 - 更大尺寸，左小空心，右大实心
                    val center = Offset(size.width / 2, size.height / 2)
                    val smallRadius = size.minDimension * 0.18f    // 小圆半径
                    val largeRadius = size.minDimension * 0.22f    // 大圆半径
                    val smallOffset = size.minDimension * 0.12f    // 小圆偏移
                    val largeOffset = size.minDimension * 0.10f    // 大圆偏移
                    
                    // 第一个圆圈（左边小的空心）
                    drawCircle(
                        color = iconColor,
                        radius = smallRadius,
                        center = Offset(center.x - smallOffset, center.y),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                    
                    // 第二个圆圈（右边大的实心）
                    drawCircle(
                        color = iconColor,
                        radius = largeRadius,
                        center = Offset(center.x + largeOffset, center.y)
                    )
                }
            }
        }
    }
}

@Composable
private fun SixKeyLayout(
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 上
        TriangleKeyButton(
            key = "K1",
            direction = TriangleDirection.UP,
            isEnabled = isEnabled,
            onKeyPressed = onKeyPressed,
            onKeyReleased = onKeyReleased,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 左 中 右
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            TriangleKeyButton(
                key = "K3",
                direction = TriangleDirection.LEFT,
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.width(40.dp))

            TriangleKeyButton(
                key = "K4",
                direction = TriangleDirection.RIGHT,
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased,
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 下
        TriangleKeyButton(
            key = "K2",
            direction = TriangleDirection.DOWN,
            isEnabled = isEnabled,
            onKeyPressed = onKeyPressed,
            onKeyReleased = onKeyReleased,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 两个功能键
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundFunctionKey(
                key = "K5",
                label = "OK",
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased
            )
            RoundFunctionKey(
                key = "K6",
                label = "Back",
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased
            )
        }
    }
}

@Composable
private fun SixteenKeyLayout(
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DirectionKeysLayout(
            isEnabled = isEnabled,
            onKeyPressed = onKeyPressed,
            onKeyReleased = onKeyReleased
        )

        Spacer(modifier = Modifier.height(48.dp))

        FunctionKeysLayout(
            isEnabled = isEnabled,
            onKeyPressed = onKeyPressed,
            onKeyReleased = onKeyReleased
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 额外的 8 个键 K9-K16
        AdditionalKeyGrid(
            keys = listOf("K9", "K10", "K11", "K12"),
            isEnabled = isEnabled,
            onKeyPressed = onKeyPressed,
            onKeyReleased = onKeyReleased
        )

        Spacer(modifier = Modifier.height(16.dp))

        AdditionalKeyGrid(
            keys = listOf("K13", "K14", "K15", "K16"),
            isEnabled = isEnabled,
            onKeyPressed = onKeyPressed,
            onKeyReleased = onKeyReleased
        )
    }
}

@Composable
private fun AdditionalKeyGrid(
    keys: List<String>,
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            RoundFunctionKey(
                key = key,
                label = key,
                isEnabled = isEnabled,
                onKeyPressed = onKeyPressed,
                onKeyReleased = onKeyReleased,
                modifier = Modifier
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun RoundFunctionKey(
    key: String,
    label: String,
    isEnabled: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyReleased: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (!isPressed) {
                        isPressed = true
                        onKeyPressed(key)
                    }
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    if (isPressed) {
                        isPressed = false
                        onKeyReleased(key)
                    }
                }
            }
        }
    }

    val bg = when {
        !isEnabled -> Color.Gray
        isPressed -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
