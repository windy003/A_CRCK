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
    }
    
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        Log.d(TAG, "无障碍服务已创建")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")
        
        // 测试发送媒体按键
        android.os.Handler().postDelayed({
            Log.d(TAG, "无障碍服务准备就绪，等待按键事件")
        }, 1000)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }
    
    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "接收到按键事件: keyCode=${event.keyCode}, action=${event.action}, device=${event.device?.name}")
        
        // 拦截蓝牙遥控器的Enter键(60)或其他相关按键
        when (event.keyCode) {
            60,                              // 遥控器Enter键
            KeyEvent.KEYCODE_ENTER,          // 66 标准Enter键
            KeyEvent.KEYCODE_DPAD_CENTER -> { // 23 方向键中心
                Log.d(TAG, "检测到目标按键: ${event.keyCode}")
                // 暂时简化：对所有设备的这些按键都进行转换，方便调试
                Log.d(TAG, "转换为空格键和媒体按键")
                
                // 发送空格键事件来控制视频播放/暂停
                if (event.action == KeyEvent.ACTION_DOWN) {
                    sendSpaceKey()
                }
                return true // 拦截原始事件，不让它继续传递
                
                // 如果需要只对蓝牙设备生效，取消注释下面的代码
                /*
                if (isBluetoothDevice(event)) {
                    Log.d(TAG, "来自蓝牙设备，转换为空格键")
                    
                    // 发送空格键事件来控制视频播放/暂停
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        sendSpaceKey()
                    }
                    return true // 拦截原始事件，不让它继续传递
                } else {
                    Log.d(TAG, "来自非蓝牙设备: ${event.device?.name}")
                }
                */
            }
        }
        
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
    
    private fun sendSpaceKey() {
        try {
            Log.d(TAG, "模拟空格键事件")
            
            // 方法1: 尝试通过无障碍服务的手势模拟点击屏幕中央
            // 这可以触发视频播放器的播放/暂停
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                performScreenTap()
            }
            
            // 方法2: 发送媒体按键作为主要方法
            handleMediaPlayPause()
            
            // 方法3: 尝试发送KEYCODE_SPACE作为系统事件
            try {
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)
                
                // 创建一个新的KeyEvent并尝试通过AudioManager分发
                audioManager?.dispatchMediaKeyEvent(downEvent)
                Thread.sleep(50)
                audioManager?.dispatchMediaKeyEvent(upEvent)
                
                Log.d(TAG, "通过AudioManager发送空格键")
            } catch (e: Exception) {
                Log.e(TAG, "通过AudioManager发送失败: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "发送空格键失败", e)
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