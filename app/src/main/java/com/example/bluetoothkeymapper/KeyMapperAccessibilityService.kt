package com.example.bluetoothkeymapper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

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
        Log.e(TAG, "!!! 无障碍服务已连接 !!!")
        Log.e(TAG, "服务信息: ${serviceInfo}")
        Log.e(TAG, "可处理事件类型: ${serviceInfo?.eventTypes}")
        Log.e(TAG, "可过滤按键事件: ${serviceInfo?.flags?.and(android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0}")
        
        // 测试日志输出
        android.os.Handler().postDelayed({
            Log.e(TAG, "无障碍服务准备就绪，开始监听所有按键事件")
            Log.e(TAG, "映射模式: 媒体播放暂停键")
            Log.i(TAG, "请按下蓝牙遥控器的OK键进行测试")
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
        
        // 处理Enter键和其他OK键 - 映射为媒体播放暂停键
        when (event.keyCode) {
            60,                              // 遥控器Enter键
            KeyEvent.KEYCODE_ENTER,          // 66 标准Enter键
            KeyEvent.KEYCODE_DPAD_CENTER,    // 23 方向键中心
            KeyEvent.KEYCODE_NUMPAD_ENTER,   // 160 数字键盘Enter
            KeyEvent.KEYCODE_BUTTON_A,       // 96 游戏手柄A键
            KeyEvent.KEYCODE_BUTTON_SELECT,  // 109 选择键
            KeyEvent.KEYCODE_BUTTON_START,   // 108 开始键
            13, 28, 158, 352,               // 其他可能的Enter键码
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, // 85 媒体播放暂停键
            KeyEvent.KEYCODE_MEDIA_PLAY,     // 126 媒体播放键
            KeyEvent.KEYCODE_SPACE -> {      // 62 空格键
                Log.e(TAG, "!!! 检测到目标按键: ${event.keyCode} !!!")
                
                if (event.action == KeyEvent.ACTION_DOWN) {
                    Log.e(TAG, "执行按键映射为媒体播放暂停键")
                    sendMediaPlayPause()
                    Log.e(TAG, "按键映射完成")
                }
                return true // 拦截原始事件
            }
        }
        
        // 记录所有未处理的按键
        Log.d(TAG, "未处理的按键: ${event.keyCode}")
        return super.onKeyEvent(event)
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
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "无障碍服务已销毁")
    }
}