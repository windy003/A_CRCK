package com.example.bluetoothkeymapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class KeyMapperAccessibilityService : AccessibilityService() {
    
    private var audioManager: AudioManager? = null
    private var isDoubleClickMappingEnabled = true // 双击映射功能开关，默认开启
    
    companion object {
        private const val TAG = "KeyMapperAccessibility"
        var instance: KeyMapperAccessibilityService? = null
        private const val PREFS_NAME = "KeyMapperPrefs"
        private const val PREF_DOUBLE_CLICK_ENABLED = "double_click_mapping_enabled"
    }
    
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        instance = this
        
        // 从SharedPreferences读取初始状态
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDoubleClickMappingEnabled = sharedPreferences.getBoolean(PREF_DOUBLE_CLICK_ENABLED, true)
        
        Log.d(TAG, "无障碍服务已创建")
        Log.d(TAG, "双击映射初始状态: ${if (isDoubleClickMappingEnabled) "开启" else "关闭"}")
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
            Log.e(TAG, "映射模式: 媒体播放暂停键 + 双击屏幕映射")
            Log.e(TAG, "双击映射功能状态: ${if (isDoubleClickMappingEnabled) "开启" else "关闭"}")
            Log.i(TAG, "dpad left: 双击屏幕坐标(133,439)")
            Log.i(TAG, "请按下蓝牙遥控器按键进行测试")
            Log.i(TAG, "提示: 可在APP界面切换双击映射功能开关")
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
            
            // 处理dpad left键 - 映射为双击屏幕坐标(133,439)实现YouTube后退5秒
            20,                              // dpad left键码
            KeyEvent.KEYCODE_DPAD_LEFT -> {  // 21 方向键左
                Log.e(TAG, "!!! 检测到dpad left按键: ${event.keyCode} !!!")
                
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isDoubleClickMappingEnabled) {
                        Log.e(TAG, "执行双击屏幕坐标(133,439)操作")
                        performDoubleClick(133f, 439f)
                        Log.e(TAG, "双击操作完成")
                    } else {
                        Log.w(TAG, "双击映射功能已关闭，忽略dpad left按键")
                    }
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
    
    private fun performDoubleClick(x: Float, y: Float) {
        try {
            Log.e(TAG, "开始执行双击操作，坐标: ($x, $y)")
            
            // 创建第一次点击的手势
            val firstClickPath = Path().apply {
                moveTo(x, y)
            }
            
            val firstClickStroke = GestureDescription.StrokeDescription(
                firstClickPath, 0, 100
            )
            
            val firstClickGesture = GestureDescription.Builder()
                .addStroke(firstClickStroke)
                .build()
            
            // 执行第一次点击
            val result1 = dispatchGesture(firstClickGesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "第一次点击完成")
                    
                    // 延迟后执行第二次点击
                    android.os.Handler().postDelayed({
                        executeSecondClick(x, y)
                    }, 100) // 100ms延迟
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "第一次点击被取消")
                }
            }, null)
            
            Log.d(TAG, "第一次点击手势分发结果: $result1")
            
        } catch (e: Exception) {
            Log.e(TAG, "执行双击操作失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun executeSecondClick(x: Float, y: Float) {
        try {
            Log.d(TAG, "执行第二次点击")
            
            // 创建第二次点击的手势
            val secondClickPath = Path().apply {
                moveTo(x, y)
            }
            
            val secondClickStroke = GestureDescription.StrokeDescription(
                secondClickPath, 0, 100
            )
            
            val secondClickGesture = GestureDescription.Builder()
                .addStroke(secondClickStroke)
                .build()
            
            // 执行第二次点击
            val result2 = dispatchGesture(secondClickGesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.e(TAG, "双击操作完全完成")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "第二次点击被取消")
                }
            }, null)
            
            Log.d(TAG, "第二次点击手势分发结果: $result2")
            
        } catch (e: Exception) {
            Log.e(TAG, "执行第二次点击失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun setDoubleClickMappingEnabled(enabled: Boolean) {
        isDoubleClickMappingEnabled = enabled
        val status = if (isDoubleClickMappingEnabled) "开启" else "关闭"
        
        Log.e(TAG, "=== 双击映射功能状态更新 ===")
        Log.e(TAG, "当前状态: $status")
        Log.e(TAG, "dpad left键映射: ${if (isDoubleClickMappingEnabled) "双击屏幕(133,439)" else "已禁用"}")
        Log.e(TAG, "===============================")
        
        // 使用Android系统通知样式的日志
        android.util.Log.wtf(TAG, "🔧 双击映射功能已$status")
    }
    
    fun isDoubleClickMappingEnabled(): Boolean {
        return isDoubleClickMappingEnabled
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "无障碍服务已销毁")
    }
}