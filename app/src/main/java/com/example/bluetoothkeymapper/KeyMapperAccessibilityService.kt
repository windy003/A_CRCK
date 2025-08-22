package com.example.bluetoothkeymapper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.io.IOException

class KeyMapperAccessibilityService : AccessibilityService() {
    
    private var audioManager: AudioManager? = null
    
    companion object {
        private const val TAG = "KeyMapperAccessibility"
        
        // 映射模式选择
        const val MODE_MEDIA_KEY = 1    // 媒体播放暂停键
        const val MODE_SPACE_KEY = 2    // 空格键
        const val MODE_SCREEN_TAP = 3   // 屏幕点击
        
        // 当前使用的模式 - 可以修改这个值来切换模式
        private var currentMode = MODE_MEDIA_KEY
        
        // 获取当前模式
        fun getCurrentMode(): Int = currentMode
        
        // 设置模式
        fun setCurrentMode(mode: Int) {
            currentMode = mode
        }
        
        // 获取模式名称
        fun getModeName(mode: Int): String {
            return when (mode) {
                MODE_MEDIA_KEY -> "媒体播放暂停键"
                MODE_SPACE_KEY -> "空格键" 
                MODE_SCREEN_TAP -> "屏幕点击"
                else -> "未知"
            }
        }
        
        // 切换到下一个模式
        fun switchToNextMode(): Int {
            currentMode = when (currentMode) {
                MODE_MEDIA_KEY -> MODE_SPACE_KEY
                MODE_SPACE_KEY -> MODE_SCREEN_TAP
                MODE_SCREEN_TAP -> MODE_MEDIA_KEY
                else -> MODE_MEDIA_KEY
            }
            return currentMode
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        Log.d(TAG, "无障碍服务已创建")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.e(TAG, "!!! 无障碍服务已连接 !!!")
        Log.e(TAG, "服务信息: ${serviceInfo}")
        Log.e(TAG, "可处理事件类型: ${serviceInfo?.eventTypes}")
        Log.e(TAG, "可过滤按键事件: ${serviceInfo?.flags?.and(android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0}")
        
        // 显示当前模式
        val modeName = getModeName(getCurrentMode())
        
        // 测试日志输出
        android.os.Handler().postDelayed({
            Log.e(TAG, "无障碍服务准备就绪，开始监听所有按键事件")
            Log.e(TAG, "当前映射模式: $modeName")
            Log.i(TAG, "请按下蓝牙遥控器的OK键进行测试")
            Log.i(TAG, "长按音量上键可切换映射模式")
            android.util.Log.wtf(TAG, "最高级别日志：等待按键事件...")
        }, 1000)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }
    
    override fun onKeyEvent(event: KeyEvent): Boolean {
        // 强制记录所有按键事件，确保日志可见
        android.util.Log.i(TAG, "!!! 捕获按键事件 !!!")
        android.util.Log.i(TAG, "键码: ${event.keyCode}")
        android.util.Log.i(TAG, "动作: ${event.action} (${if (event.action == KeyEvent.ACTION_DOWN) "按下" else if (event.action == KeyEvent.ACTION_UP) "松开" else "其他"})")
        android.util.Log.i(TAG, "设备: ${event.device?.name ?: "未知"}")
        android.util.Log.i(TAG, "外部设备: ${event.device?.isExternal ?: false}")
        android.util.Log.i(TAG, "扫描码: ${event.scanCode}")
        
        // 详细的调试日志
        val deviceInfo = event.device?.let { device ->
            "name='${device.name}', id=${device.id}, productId=${device.productId}, vendorId=${device.vendorId}, isExternal=${device.isExternal}"
        } ?: "null"
        
        Log.w(TAG, "=== 完整按键事件详情 ===")
        Log.w(TAG, "键码: ${event.keyCode}")
        Log.w(TAG, "动作: ${if (event.action == KeyEvent.ACTION_DOWN) "按下" else if (event.action == KeyEvent.ACTION_UP) "松开" else "其他(${event.action})"}")
        Log.w(TAG, "设备: $deviceInfo")
        Log.w(TAG, "扫描码: ${event.scanCode}")
        Log.w(TAG, "重复次数: ${event.repeatCount}")
        Log.w(TAG, "==========================")
        
        // 特别处理Enter键 - 根据模式选择映射方式
        when (event.keyCode) {
            60,                              // 遥控器Enter键
            KeyEvent.KEYCODE_ENTER -> {      // 66 标准Enter键
                Log.e(TAG, "!!! 检测到Enter键: ${event.keyCode} !!!")
                
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val mode = getCurrentMode()
                    Log.e(TAG, "执行Enter键映射，当前模式: $mode")
                    
                    // 根据模式选择不同的映射方式，避免冲突
                    when (mode) {
                        MODE_MEDIA_KEY -> {
                            Log.e(TAG, "使用媒体播放暂停键模式")
                            sendMediaPlayPause()
                        }
                        MODE_SPACE_KEY -> {
                            Log.e(TAG, "使用空格键模式")
                            sendSpaceKeyDirect()
                        }
                        MODE_SCREEN_TAP -> {
                            Log.e(TAG, "使用屏幕点击模式")
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                performScreenTap()
                            }
                        }
                    }
                    
                    Log.e(TAG, "Enter键映射完成")
                }
                return true // 拦截原始事件
            }
            
            // 其他可能的OK/确认键
            KeyEvent.KEYCODE_DPAD_CENTER,    // 23 方向键中心
            KeyEvent.KEYCODE_NUMPAD_ENTER,   // 160 数字键盘Enter
            KeyEvent.KEYCODE_BUTTON_A,       // 96 游戏手柄A键
            KeyEvent.KEYCODE_BUTTON_SELECT,  // 109 选择键
            KeyEvent.KEYCODE_BUTTON_START,   // 108 开始键
            13, 28, 158, 352,               // 其他可能的Enter键码
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, // 85 媒体播放暂停键
            KeyEvent.KEYCODE_MEDIA_PLAY,     // 126 媒体播放键
            KeyEvent.KEYCODE_SPACE -> {      // 62 空格键
                Log.e(TAG, "!!! 检测到其他目标按键: ${event.keyCode} !!!")
                
                if (event.action == KeyEvent.ACTION_DOWN) {
                    sendPlayPauseCommand()
                }
                return true
            }
            
            // 模式切换：长按音量上键
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) {
                    // 长按检测（重复次数大于0）
                    switchMode()
                    return true // 拦截，避免真的调整音量
                }
            }
        }
        
        // 记录所有未处理的按键
        Log.d(TAG, "未处理的按键: ${event.keyCode}")
        return super.onKeyEvent(event)
    }
    
    private fun isBluetoothDevice(event: KeyEvent): Boolean {
        val device = event.device
        if (device == null) {
            Log.d(TAG, "设备为null")
            return false
        }
        
        val deviceName = device.name?.lowercase() ?: ""
        val isExternal = device.isExternal
        val deviceId = event.deviceId
        
        Log.d(TAG, "设备检查: name='$deviceName', isExternal=$isExternal, deviceId=$deviceId")
        
        // 检查是否为蓝牙或外部设备
        val isBluetoothDevice = deviceName.contains("bluetooth") || 
                               deviceName.contains("remote") || 
                               deviceName.contains("gamepad") ||
                               deviceName.contains("controller") ||
                               isExternal
        
        Log.d(TAG, "判断结果: $isBluetoothDevice")
        return isBluetoothDevice
    }
    
    private fun sendPlayPauseCommand() {
        try {
            Log.d(TAG, "发送播放/暂停命令")
            
            // 方法1: 发送媒体播放暂停键 - 最有效的方法
            handleMediaPlayPause()
            
            // 方法2: 发送空格键 - 对YouTube等网页视频有效
            sendSpaceKey()
            
            // 方法3: 点击屏幕中央 - 对某些视频应用有效
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                performScreenTap()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "发送播放/暂停命令失败", e)
        }
    }
    
    private fun sendMediaPlayPause() {
        try {
            Log.e(TAG, "发送媒体播放暂停键...")
            
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            
            // 通过AudioManager发送媒体按键
            val result1 = audioManager?.dispatchMediaKeyEvent(downEvent)
            Thread.sleep(50)
            val result2 = audioManager?.dispatchMediaKeyEvent(upEvent)
            
            Log.e(TAG, "媒体按键发送结果: down=$result1, up=$result2")
        } catch (e: Exception) {
            Log.e(TAG, "发送媒体按键失败: ${e.message}")
        }
    }
    
    private fun sendSpaceKeyDirect() {
        try {
            Log.e(TAG, "发送直接空格键...")
            
            // 方法1: 通过系统输入事件
            val spaceDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
            val spaceUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)
            
            // 通过AudioManager尝试发送空格键
            audioManager?.dispatchMediaKeyEvent(spaceDown)
            Thread.sleep(30)
            audioManager?.dispatchMediaKeyEvent(spaceUp)
            
            Log.e(TAG, "直接空格键发送完成")
        } catch (e: Exception) {
            Log.e(TAG, "发送直接空格键失败: ${e.message}")
        }
    }
    
    private fun sendSpaceKey() {
        try {
            Log.d(TAG, "发送空格键事件")
            
            // 通过InputConnection发送空格键（如果可能）
            val spaceDownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
            val spaceUpEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)
            
            // 尝试通过AudioManager分发空格键
            audioManager?.dispatchMediaKeyEvent(spaceDownEvent)
            Thread.sleep(20)
            audioManager?.dispatchMediaKeyEvent(spaceUpEvent)
            
            Log.d(TAG, "空格键发送完成")
        } catch (e: Exception) {
            Log.e(TAG, "发送空格键失败: ${e.message}")
        }
    }
    
    private fun performScreenTap() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                // 获取屏幕尺寸
                val metrics = resources.displayMetrics
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels
                
                // 点击屏幕中央，这通常会触发视频播放/暂停
                val centerX = screenWidth / 2f
                val centerY = screenHeight / 2f
                
                val path = android.graphics.Path()
                path.moveTo(centerX, centerY)
                
                val gestureDescription = android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100))
                    .build()
                
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {
                        Log.d(TAG, "手势点击完成")
                    }
                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {
                        Log.d(TAG, "手势点击取消")
                    }
                }
                
                val result = dispatchGesture(gestureDescription, callback, null)
                
                Log.d(TAG, "执行屏幕中央点击手势: $result")
            } catch (e: Exception) {
                Log.e(TAG, "执行手势失败", e)
            }
        }
    }
    
    private fun switchMode() {
        val newMode = switchToNextMode()
        val modeName = getModeName(newMode)
        
        Log.e(TAG, "!!! 切换映射模式为: $modeName !!!")
        android.util.Log.wtf(TAG, "新的映射模式: $modeName")
    }
    
    private fun handleMediaPlayPause() {
        try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
            
            Log.d(TAG, "发送媒体播放/暂停命令")
        } catch (e: Exception) {
            Log.e(TAG, "发送媒体按键失败", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "无障碍服务已销毁")
    }
}