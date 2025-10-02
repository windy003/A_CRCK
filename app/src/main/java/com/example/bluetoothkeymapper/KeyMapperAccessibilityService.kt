package com.example.bluetoothkeymapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.io.IOException

class KeyMapperAccessibilityService : AccessibilityService() {
    
    private var audioManager: AudioManager? = null
    private var isDoubleClickMappingEnabled = true // 双击映射功能开关，默认开启
    private var isTvModeEnabled = false // 电视模式开关，默认关闭
    private var isBaiduModeEnabled = false // 百度网盘模式开关，默认关闭
    private var isTiktokModeEnabled = false // TikTok模式开关，默认关闭
    private var lastMediaPlayPauseTime = 0L // 上次播放/暂停按键时间戳
    private var screenWidth = 0 // 屏幕宽度
    private var screenHeight = 0 // 屏幕高度
    private var currentForegroundApp = "" // 当前前台应用包名
    private var isAutoModeEnabled = true // 自动模式切换开关
    private var lastTargetAppMode = "" // 上次使用的目标应用模式
    private var lastTargetAppTime = 0L // 上次使用目标应用的时间
    private var f5KeyPressTime = 0L // F5键按下时间戳
    private var f5KeyHandler: android.os.Handler? = null // F5键长按处理器
    private var isF5LongPressTriggered = false // F5长按是否已触发
    private var lastVolumeBeforeMute = -1 // 静音前的音量
    private var isMuted = false // 当前是否静音

    // 左方向键长按检测相关变量
    private var dpadLeftPressTime = 0L // 左方向键按下时间戳
    private var dpadLeftHandler: android.os.Handler? = null // 左方向键长按处理器
    private var isDpadLeftLongPressTriggered = false // 左方向键长按是否已触发

    // 右方向键长按检测相关变量
    private var dpadRightPressTime = 0L // 右方向键按下时间戳
    private var dpadRightHandler: android.os.Handler? = null // 右方向键长按处理器
    private var isDpadRightLongPressTriggered = false // 右方向键长按是否已触发
    
    companion object {
        private const val TAG = "KeyMapperAccessibility"
        var instance: KeyMapperAccessibilityService? = null
        private const val PREFS_NAME = "KeyMapperPrefs"
        private const val PREF_YOUTUBE_MODE_ENABLED = "youtube_mode_enabled"
        private const val PREF_TV_MODE_ENABLED = "tv_mode_enabled"
        private const val PREF_BAIDU_MODE_ENABLED = "baidu_mode_enabled"
        private const val PREF_TIKTOK_MODE_ENABLED = "tiktok_mode_enabled"

        // 应用包名映射
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
        private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
        private const val TIKTOK_LITE_PACKAGE = "com.zhiliaoapp.musically.go"
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        private const val DOUYIN_LITE_PACKAGE = "com.ss.android.ugc.aweme.lite"
        private const val BAIDU_DISK_PACKAGE = "com.baidu.netdisk"

        // 系统应用和桌面应用，不应该触发模式切换
        private val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.lge.launcher3",
            "com.android.launcher",
            "com.huawei.android.launcher",
            "com.xiaomi.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.samsung.android.launcher",
            "com.lge.displayfingerprint",
            "com.example.bluetoothkeymapper", // 自己的应用
            "android",
            "com.android.settings"
        )
    }
    
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        instance = this
        f5KeyHandler = android.os.Handler()
        dpadLeftHandler = android.os.Handler()
        dpadRightHandler = android.os.Handler()
        
        // 从SharedPreferences读取初始状态
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDoubleClickMappingEnabled = sharedPreferences.getBoolean(PREF_YOUTUBE_MODE_ENABLED, true)
        isTvModeEnabled = sharedPreferences.getBoolean(PREF_TV_MODE_ENABLED, false)
        isBaiduModeEnabled = sharedPreferences.getBoolean(PREF_BAIDU_MODE_ENABLED, false)
        isTiktokModeEnabled = sharedPreferences.getBoolean(PREF_TIKTOK_MODE_ENABLED, false)
        isAutoModeEnabled = sharedPreferences.getBoolean("auto_mode_enabled", true)

        // 初始化音量状态
        audioManager?.let { am ->
            val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            isMuted = currentVolume == 0
            Log.d(TAG, "初始化音量状态: 当前音量=$currentVolume, 静音状态=$isMuted")
        }

        Log.d(TAG, "无障碍服务已创建")
        Log.d(TAG, "双击映射初始状态: ${if (isDoubleClickMappingEnabled) "开启" else "关闭"}")
        Log.d(TAG, "电视模式初始状态: ${if (isTvModeEnabled) "开启" else "关闭"}")
        Log.d(TAG, "百度网盘模式初始状态: ${if (isBaiduModeEnabled) "开启" else "关闭"}")
        Log.d(TAG, "TikTok模式初始状态: ${if (isTiktokModeEnabled) "开启" else "关闭"}")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.e(TAG, "!!! 无障碍服务已连接 !!!")
        Log.e(TAG, "服务信息: ${serviceInfo}")
        Log.e(TAG, "可处理事件类型: ${serviceInfo?.eventTypes}")
        Log.e(TAG, "可过滤按键事件: ${serviceInfo?.flags?.and(android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0}")

        // 获取屏幕尺寸并自动检测比例
        getScreenDimensions()

        // 测试日志输出
        android.os.Handler().postDelayed({
            Log.e(TAG, "无障碍服务准备就绪，开始监听所有按键事件")
            Log.e(TAG, "映射模式: 媒体播放暂停键 + 双击屏幕映射")
            Log.e(TAG, "双击映射功能状态: ${if (isDoubleClickMappingEnabled) "开启" else "关闭"}")
            Log.e(TAG, "电视模式状态: ${if (isTvModeEnabled) "开启" else "关闭"}")
            Log.i(TAG, "dpad left: 双击屏幕坐标(133,439)")
            Log.i(TAG, "dpad right: 双击屏幕坐标 (竖屏810,265 / 横屏1940,384)")
            Log.i(TAG, "dpad down: 点击CC按钮 (竖屏876,154 / 横屏2273,88)")
            Log.i(TAG, "dpad up: 静音/恢复音量切换 - 显示音量控制UI")
            Log.i(TAG, "back key: 单击屏幕坐标(133,439)")
            Log.i(TAG, "move home key (122): 上一曲按键")
            Log.i(TAG, "menu key: 下一曲按键")
            Log.i(TAG, "请按下蓝牙遥控器按键进行测试")
            Log.i(TAG, "提示: 系统会根据屏幕比例自动切换模式")
            android.util.Log.wtf(TAG, "最高级别日志：等待按键事件...")
        }, 1000)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "配置发生变化，重新检测屏幕比例")

        // 延迟一下再获取屏幕尺寸，确保配置变化完成
        android.os.Handler().postDelayed({
            getScreenDimensions()
        }, 100)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 监听窗口状态变化事件，检测前台应用切换
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString()
                if (!packageName.isNullOrEmpty() && packageName != currentForegroundApp) {
                    currentForegroundApp = packageName
                    Log.e(TAG, "=== 前台应用切换 ===")
                    Log.e(TAG, "新应用: $packageName")
                    Log.e(TAG, "自动模式: ${if (isAutoModeEnabled) "开启" else "关闭"}")

                    if (isAutoModeEnabled) {
                        Log.e(TAG, "执行自动模式切换...")
                        // 根据应用包名自动切换模式
                        checkAndSwitchModeByApp(packageName)
                    } else {
                        Log.e(TAG, "自动模式已关闭，跳过模式切换")
                    }
                    Log.e(TAG, "==================")
                }
            }
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    private fun getScreenDimensions() {
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        Log.d(TAG, "屏幕尺寸: ${screenWidth}x${screenHeight}")

        // 检测屏幕比例并自动切换模式
        checkAndSwitchModeByAspectRatio()
    }

    private fun checkAndSwitchModeByAspectRatio() {
        val aspectRatio = if (screenWidth > screenHeight) {
            screenWidth.toFloat() / screenHeight.toFloat()
        } else {
            screenHeight.toFloat() / screenWidth.toFloat()
        }

        Log.e(TAG, "检测到屏幕比例: $aspectRatio")
        Log.e(TAG, "上次目标应用模式: $lastTargetAppMode")

        // 如果用户刚刚使用了特定应用模式，保持不变
        if (lastTargetAppMode.isNotEmpty()) {
            when (lastTargetAppMode) {
                "tiktok" -> {
                    if (isTiktokModeEnabled) {
                        Log.e(TAG, "保持TikTok模式，因为用户刚刚使用了TikTok")
                        return
                    }
                }
                "baidu" -> {
                    if (isBaiduModeEnabled) {
                        Log.e(TAG, "保持百度网盘模式，因为用户刚刚使用了百度网盘")
                        return
                    }
                }
                "youtube" -> {
                    if (isDoubleClickMappingEnabled && !isTvModeEnabled && !isBaiduModeEnabled && !isTiktokModeEnabled) {
                        Log.e(TAG, "保持YouTube模式，因为用户刚刚使用了YouTube")
                        return
                    }
                }
                "tv" -> {
                    if (!isDoubleClickMappingEnabled && isTvModeEnabled && !isBaiduModeEnabled && !isTiktokModeEnabled) {
                        Log.e(TAG, "保持电视模式，因为用户刚刚使用了YouTube(16:9)")
                        return
                    }
                }
            }
        }

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 如果有特定应用模式记录且时间较近（30秒内），不要根据屏幕比例切换
        val currentTime = System.currentTimeMillis()
        if (lastTargetAppMode.isNotEmpty() && (currentTime - lastTargetAppTime) < 30000) {
            Log.e(TAG, "有特定应用模式记录且时间较近(${(currentTime - lastTargetAppTime)/1000}秒前)，跳过屏幕比例切换")
            return
        } else if (lastTargetAppMode.isNotEmpty()) {
            Log.e(TAG, "特定应用模式记录已过期(${(currentTime - lastTargetAppTime)/1000}秒前)，清除记录")
            lastTargetAppMode = ""
            lastTargetAppTime = 0L
        }

        // 20:9 ≈ 2.22, 16:9 ≈ 1.78
        when {
            aspectRatio >= 2.1f -> {
                // 20:9屏幕 - 只有在没有任何模式时才切换到默认YouTube模式
                Log.e(TAG, "检测到20:9屏幕，考虑切换到默认YouTube模式")
                if (!isDoubleClickMappingEnabled && !isTvModeEnabled && !isBaiduModeEnabled && !isTiktokModeEnabled) {
                    Log.e(TAG, "切换到默认YouTube模式")
                    switchToMode("youtube")
                    // 不设置lastTargetAppMode，因为这是屏幕比例的默认选择
                } else {
                    Log.e(TAG, "已有激活模式，不切换")
                }
            }
            aspectRatio >= 1.6f && aspectRatio < 2.0f -> {
                // 16:9屏幕 - 只有在没有任何模式时才切换到默认电视模式
                Log.e(TAG, "检测到16:9屏幕，考虑切换到默认电视模式")
                if (!isDoubleClickMappingEnabled && !isTvModeEnabled && !isBaiduModeEnabled && !isTiktokModeEnabled) {
                    Log.e(TAG, "切换到默认电视模式")
                    switchToMode("tv")
                    // 不设置lastTargetAppMode，因为这是屏幕比例的默认选择
                } else {
                    Log.e(TAG, "已有激活模式，不切换")
                }
            }
            else -> {
                Log.e(TAG, "未知屏幕比例 ($aspectRatio)，保持当前模式")
            }
        }
    }

    private fun sendModeChangeBroadcast(action: String, enabled: Boolean) {
        try {
            val intent = Intent(action)
            intent.putExtra("enabled", enabled)
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Log.e(TAG, "📡 发送广播成功: ${action.substringAfterLast(".")} = $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送广播失败: ${e.message}")
        }
    }

    private fun checkAndSwitchModeByApp(packageName: String) {
        if (!isAutoModeEnabled) return

        Log.e(TAG, "=== 应用模式检查 ===")
        Log.e(TAG, "包名: $packageName")
        Log.e(TAG, "当前状态 - YouTube:$isDoubleClickMappingEnabled, TV:$isTvModeEnabled, Baidu:$isBaiduModeEnabled, TikTok:$isTiktokModeEnabled")

        when (packageName) {
            YOUTUBE_PACKAGE, YOUTUBE_MUSIC_PACKAGE -> {
                Log.e(TAG, "匹配到YouTube应用")

                // 根据屏幕比例决定YouTube使用哪种模式
                val aspectRatio = if (screenWidth > screenHeight) {
                    screenWidth.toFloat() / screenHeight.toFloat()
                } else {
                    screenHeight.toFloat() / screenWidth.toFloat()
                }

                if (aspectRatio >= 1.6f && aspectRatio < 2.0f) {
                    // 16:9屏幕 - YouTube使用电视模式
                    Log.e(TAG, "16:9屏幕上的YouTube，切换到电视模式")
                    lastTargetAppMode = "tv"
                    lastTargetAppTime = System.currentTimeMillis()
                    if (isDoubleClickMappingEnabled || !isTvModeEnabled || isBaiduModeEnabled || isTiktokModeEnabled) {
                        Log.e(TAG, "需要切换到电视模式")
                        switchToMode("tv")
                    } else {
                        Log.e(TAG, "已经是电视模式，无需切换")
                    }
                } else {
                    // 20:9或其他屏幕 - YouTube使用普通模式
                    Log.e(TAG, "20:9屏幕上的YouTube，切换到YouTube模式")
                    lastTargetAppMode = "youtube"
                    lastTargetAppTime = System.currentTimeMillis()
                    if (!isDoubleClickMappingEnabled || isTvModeEnabled || isBaiduModeEnabled || isTiktokModeEnabled) {
                        Log.e(TAG, "需要切换到YouTube模式")
                        switchToMode("youtube")
                    } else {
                        Log.e(TAG, "已经是YouTube模式，无需切换")
                    }
                }
            }
            TIKTOK_PACKAGE, TIKTOK_LITE_PACKAGE, DOUYIN_PACKAGE, DOUYIN_LITE_PACKAGE -> {
                Log.e(TAG, "匹配到TikTok/抖音应用")
                lastTargetAppMode = "tiktok"
                lastTargetAppTime = System.currentTimeMillis()
                if (isDoubleClickMappingEnabled || isTvModeEnabled || isBaiduModeEnabled || !isTiktokModeEnabled) {
                    Log.e(TAG, "需要切换到TikTok模式")
                    switchToMode("tiktok")
                } else {
                    Log.e(TAG, "已经是TikTok模式，无需切换")
                }
            }
            BAIDU_DISK_PACKAGE -> {
                Log.e(TAG, "匹配到百度网盘应用")
                lastTargetAppMode = "baidu"
                lastTargetAppTime = System.currentTimeMillis()
                if (isDoubleClickMappingEnabled || isTvModeEnabled || !isBaiduModeEnabled || isTiktokModeEnabled) {
                    Log.e(TAG, "需要切换到百度网盘模式")
                    switchToMode("baidu")
                } else {
                    Log.e(TAG, "已经是百度网盘模式，无需切换")
                }
            }
            else -> {
                // 检查是否为系统应用或桌面
                if (SYSTEM_PACKAGES.contains(packageName)) {
                    Log.e(TAG, "检测到系统应用或桌面: $packageName，保持当前模式")
                    return // 不切换模式
                }

                Log.e(TAG, "其他应用，根据屏幕比例自动切换")
                checkAndSwitchModeByAspectRatio()
            }
        }
        Log.e(TAG, "=================")
    }

    private fun switchToMode(mode: String) {
        Log.e(TAG, "=== 开始切换模式 ===")
        Log.e(TAG, "目标模式: $mode")

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        when (mode) {
            "youtube" -> {
                Log.e(TAG, "切换到YouTube模式...")
                isDoubleClickMappingEnabled = true
                isTvModeEnabled = false
                isBaiduModeEnabled = false
                isTiktokModeEnabled = false

                val editor = sharedPreferences.edit()
                    .putBoolean(PREF_YOUTUBE_MODE_ENABLED, true)
                    .putBoolean(PREF_TV_MODE_ENABLED, false)
                    .putBoolean(PREF_BAIDU_MODE_ENABLED, false)
                    .putBoolean(PREF_TIKTOK_MODE_ENABLED, false)

                val success = editor.commit() // 使用commit确保立即保存
                Log.e(TAG, "SharedPreferences保存结果: $success")

                Log.e(TAG, "发送广播...")
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.YOUTUBE_MODE_CHANGED", true)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.TV_MODE_CHANGED", false)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.BAIDU_MODE_CHANGED", false)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.TIKTOK_MODE_CHANGED", false)

                Log.e(TAG, "✅ 已成功切换到YouTube模式")
            }
            "tv" -> {
                isDoubleClickMappingEnabled = false
                isTvModeEnabled = true
                isBaiduModeEnabled = false
                isTiktokModeEnabled = false

                sharedPreferences.edit()
                    .putBoolean(PREF_YOUTUBE_MODE_ENABLED, false)
                    .putBoolean(PREF_TV_MODE_ENABLED, true)
                    .putBoolean(PREF_BAIDU_MODE_ENABLED, false)
                    .putBoolean(PREF_TIKTOK_MODE_ENABLED, false)
                    .apply()

                sendModeChangeBroadcast("com.example.bluetoothkeymapper.YOUTUBE_MODE_CHANGED", false)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.TV_MODE_CHANGED", true)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.BAIDU_MODE_CHANGED", false)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.TIKTOK_MODE_CHANGED", false)

                Log.d(TAG, "已自动切换到电视模式")
            }
            "baidu" -> {
                isDoubleClickMappingEnabled = false
                isTvModeEnabled = false
                isBaiduModeEnabled = true
                isTiktokModeEnabled = false

                sharedPreferences.edit()
                    .putBoolean(PREF_YOUTUBE_MODE_ENABLED, false)
                    .putBoolean(PREF_TV_MODE_ENABLED, false)
                    .putBoolean(PREF_BAIDU_MODE_ENABLED, true)
                    .putBoolean(PREF_TIKTOK_MODE_ENABLED, false)
                    .apply()

                sendModeChangeBroadcast("com.example.bluetoothkeymapper.YOUTUBE_MODE_CHANGED", false)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.TV_MODE_CHANGED", false)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.BAIDU_MODE_CHANGED", true)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.TIKTOK_MODE_CHANGED", false)

                Log.d(TAG, "已自动切换到百度网盘模式")
            }
            "tiktok" -> {
                Log.e(TAG, "切换到TikTok模式...")
                isDoubleClickMappingEnabled = false
                isTvModeEnabled = false
                isBaiduModeEnabled = false
                isTiktokModeEnabled = true

                val editor = sharedPreferences.edit()
                    .putBoolean(PREF_YOUTUBE_MODE_ENABLED, false)
                    .putBoolean(PREF_TV_MODE_ENABLED, false)
                    .putBoolean(PREF_BAIDU_MODE_ENABLED, false)
                    .putBoolean(PREF_TIKTOK_MODE_ENABLED, true)

                val success = editor.commit() // 使用commit确保立即保存
                Log.e(TAG, "SharedPreferences保存结果: $success")

                Log.e(TAG, "发送广播...")
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.YOUTUBE_MODE_CHANGED", false)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.TV_MODE_CHANGED", false)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.BAIDU_MODE_CHANGED", false)
                sendModeChangeBroadcast("com.example.bluetoothkeymapper.TIKTOK_MODE_CHANGED", true)

                Log.e(TAG, "✅ 已成功切换到TikTok模式")
            }
        }
        Log.e(TAG, "=== 模式切换完成 ===")
    }

    private fun getSwipePixels(): Int {
        val sharedPreferences = getSharedPreferences("TikTokRemoteControl", MODE_PRIVATE)
        return sharedPreferences.getInt("swipe_pixels", 100)
    }

    private fun isTiktokServiceEnabled(): Boolean {
        val sharedPreferences = getSharedPreferences("TikTokRemoteControl", MODE_PRIVATE)
        return sharedPreferences.getBoolean("service_master_switch", true)
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
        
        // 处理Enter键和其他OK键 - 根据模式进行不同映射
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
                    if (isTiktokModeEnabled) {
                        Log.e(TAG, "TikTok模式 - 执行屏幕中心点击操作")
                        performTiktokCenterClick()
                        Log.e(TAG, "TikTok模式中心点击操作完成")
                    } else {
                        Log.e(TAG, "执行按键映射为媒体播放暂停键")
                        sendMediaPlayPause()
                        Log.e(TAG, "按键映射完成")
                    }
                }
                return true // 拦截原始事件
            }
            
            // 处理dpad left键 - 支持长按检测
            KeyEvent.KEYCODE_DPAD_LEFT -> {  // 21 方向键左
                Log.e(TAG, "!!! 检测到dpad left按键: ${event.keyCode} !!!")

                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        // 记录按下时间
                        dpadLeftPressTime = System.currentTimeMillis()
                        isDpadLeftLongPressTriggered = false

                        // 设置1秒后触发长按事件
                        dpadLeftHandler?.postDelayed({
                            if (dpadLeftPressTime > 0 && !isDpadLeftLongPressTriggered) {
                                isDpadLeftLongPressTriggered = true
                                handleDpadLeftLongPress()
                            }
                        }, 1000) // 1秒长按

                        Log.e(TAG, "左方向键按下，开始计时...")
                    }

                    KeyEvent.ACTION_UP -> {
                        val pressDuration = System.currentTimeMillis() - dpadLeftPressTime
                        Log.e(TAG, "左方向键松开，按下时长: ${pressDuration}ms")

                        // 清除长按计时器
                        dpadLeftHandler?.removeCallbacksAndMessages(null)

                        if (!isDpadLeftLongPressTriggered && pressDuration < 1000) {
                            // 短按：执行原有的功能
                            handleDpadLeftShortPress()
                        }

                        // 重置状态
                        dpadLeftPressTime = 0L
                        isDpadLeftLongPressTriggered = false
                    }
                }
                return true // 拦截原始事件
            }
            
            // 处理dpad down键 - 根据模式进行不同映射
            KeyEvent.KEYCODE_DPAD_DOWN -> {  // 20 方向键下
                Log.e(TAG, "!!! 检测到dpad down按键: ${event.keyCode} !!!")

                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isTiktokModeEnabled) {
                        // TikTok模式：禁用下方向键功能
                        Log.e(TAG, "TikTok模式 - 下方向键功能已禁用")
                        return true // 拦截事件但不执行任何操作
                    } else if (isTvModeEnabled) {
                        // 电视模式：执行单击屏幕坐标(133,439) - 显示/隐藏控制器
                        Log.e(TAG, "电视模式 - 执行单击屏幕坐标(133,439)操作 - 显示/隐藏控制器")
                        performSingleClick(133f, 439f)
                        Log.e(TAG, "电视模式单击操作完成")
                    } else if (isDoubleClickMappingEnabled) {
                        // YouTube模式：执行单击屏幕坐标(133,439) - 显示/隐藏控制器
                        Log.e(TAG, "YouTube模式 - 执行单击屏幕坐标(133,439)操作 - 显示/隐藏控制器")
                        performSingleClick(133f, 439f)
                        Log.e(TAG, "YouTube模式下方向键操作完成")
                    } else {
                        Log.e(TAG, "执行点击CC按钮操作 - 打开/关闭YouTube CC字幕")
                        sendKeyC()
                        Log.e(TAG, "CC按钮点击操作完成")
                    }
                }
                return true // 拦截原始事件
            }

            // 处理dpad up键 - 静音和恢复音量切换
            KeyEvent.KEYCODE_DPAD_UP -> {  // 19 方向键上
                Log.e(TAG, "!!! 检测到dpad up按键: ${event.keyCode} !!!")

                if (event.action == KeyEvent.ACTION_DOWN) {
                    Log.e(TAG, "上方向键 - 执行静音/恢复音量切换")
                    toggleMute()
                }
                return true // 拦截原始事件
            }
            
            // 处理返回按键 - 根据模式进行不同映射
            KeyEvent.KEYCODE_BACK -> {  // 4 返回键
                Log.e(TAG, "!!! 检测到返回按键: ${event.keyCode} !!!")

                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isTvModeEnabled) {
                        // 电视模式：显示/隐藏控制器
                        Log.e(TAG, "电视模式 - 执行显示/隐藏控制器操作")
                        performSingleClick(133f, 439f)
                        Log.e(TAG, "电视模式返回键显示/隐藏控制器操作完成")
                    } else if (isDoubleClickMappingEnabled) {
                        // YouTube模式：显示/隐藏控制器
                        Log.e(TAG, "YouTube模式 - 执行显示/隐藏控制器操作")
                        performSingleClick(133f, 439f)
                        Log.e(TAG, "YouTube模式返回键显示/隐藏控制器操作完成")
                    } else {
                        Log.e(TAG, "执行单击屏幕坐标(133,439)操作 - 显示/隐藏控制器")
                        performSingleClick(133f, 439f)
                        Log.e(TAG, "单击操作完成")
                    }
                }
                return true // 拦截原始事件
            }
            
            // 处理home按键 - 根据模式进行不同映射
            122 -> {  // 122 Move Home键（蓝牙遥控器的Home键）
                Log.e(TAG, "!!! 检测到Move Home按键: ${event.keyCode} !!!")

                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isTiktokModeEnabled) {
                        // TikTok模式：返回进度条到起始位置
                        Log.e(TAG, "TikTok模式 - 执行进度条重置操作")
                        performTiktokSeekToStart()
                        Log.e(TAG, "TikTok模式进度条重置操作完成")
                        return true
                    } else if (isTvModeEnabled) {
                        // 电视模式：根据屏幕方向处理
                        val orientation = resources.configuration.orientation
                        val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

                        if (isPortrait) {
                            // 竖屏时：执行相应的点击
                            Log.e(TAG, "电视模式竖屏 - 执行单击屏幕坐标(995,634)操作")
                            performSingleClick(995f, 634f)
                            Log.e(TAG, "电视模式竖屏单击操作完成")
                        } else {
                            // 横屏时：映射为返回键
                            Log.e(TAG, "电视模式横屏 - Home键映射为返回键功能")
                            sendBackKey()
                            Log.e(TAG, "电视模式横屏返回键操作完成")
                        }
                    } else if (isDoubleClickMappingEnabled) {
                        // YouTube模式：根据屏幕方向处理
                        val orientation = resources.configuration.orientation
                        val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

                        if (isPortrait) {
                            // 竖屏时：执行相应的点击
                            Log.e(TAG, "YouTube模式竖屏 - 执行单击屏幕坐标(1050,620)操作")
                            performSingleClick(1050f, 620f)
                            Log.e(TAG, "YouTube模式竖屏单击操作完成")
                        } else {
                            // 横屏时：映射为返回键
                            Log.e(TAG, "YouTube模式横屏 - Home键映射为返回键功能")
                            sendBackKey()
                            Log.e(TAG, "YouTube模式横屏返回键操作完成")
                        }
                    } else {
                        // 非YouTube模式：执行上一曲操作
                        Log.e(TAG, "普通模式 - 执行上一曲操作")
                        sendMediaPrevious()
                        Log.e(TAG, "上一曲操作完成")
                    }
                }
                return true // 拦截原始事件
            }
            
            // 处理menu按键 - 根据模式进行不同映射
            KeyEvent.KEYCODE_MENU -> {  // 82 Menu键
                Log.e(TAG, "!!! 检测到Menu按键: ${event.keyCode} !!!")

                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isTvModeEnabled) {
                        // 电视模式：显示/隐藏字幕
                        Log.e(TAG, "电视模式 - 执行显示/隐藏字幕操作")
                        sendKeyC()
                        Log.e(TAG, "电视模式Menu键显示/隐藏字幕操作完成")
                    } else if (isDoubleClickMappingEnabled) {
                        // YouTube模式：显示/隐藏字幕
                        Log.e(TAG, "YouTube模式 - 执行显示/隐藏字幕操作")
                        sendKeyC()
                        Log.e(TAG, "YouTube模式Menu键显示/隐藏字幕操作完成")
                    } else {
                        // 非YouTube模式：执行下一曲操作
                        Log.e(TAG, "普通模式 - 执行下一曲操作")
                        sendMediaNext()
                        Log.e(TAG, "下一曲操作完成")
                    }
                }
                return true // 拦截原始事件
            }

            // 处理dpad right键 - 支持长按检测
            KeyEvent.KEYCODE_DPAD_RIGHT -> {  // 22 方向键右
                Log.e(TAG, "!!! 检测到dpad right按键: ${event.keyCode} !!!")

                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        // 记录按下时间
                        dpadRightPressTime = System.currentTimeMillis()
                        isDpadRightLongPressTriggered = false

                        // 设置1秒后触发长按事件
                        dpadRightHandler?.postDelayed({
                            if (dpadRightPressTime > 0 && !isDpadRightLongPressTriggered) {
                                isDpadRightLongPressTriggered = true
                                handleDpadRightLongPress()
                            }
                        }, 1000) // 1秒长按

                        Log.e(TAG, "右方向键按下，开始计时...")
                    }

                    KeyEvent.ACTION_UP -> {
                        val pressDuration = System.currentTimeMillis() - dpadRightPressTime
                        Log.e(TAG, "右方向键松开，按下时长: ${pressDuration}ms")

                        // 清除长按计时器
                        dpadRightHandler?.removeCallbacksAndMessages(null)

                        if (!isDpadRightLongPressTriggered && pressDuration < 1000) {
                            // 短按：执行原有的功能
                            handleDpadRightShortPress()
                        }

                        // 重置状态
                        dpadRightPressTime = 0L
                        isDpadRightLongPressTriggered = false
                    }
                }
                return true // 拦截原始事件
            }

            // 处理F5键(语音键) - 实现长按检测，将上方向键功能转移到长按
            135 -> {  // 135 F5键（蓝牙遥控器的语音键）
                Log.e(TAG, "!!! 检测到F5语音键: ${event.keyCode} !!!")

                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        // 记录按下时间
                        f5KeyPressTime = System.currentTimeMillis()
                        isF5LongPressTriggered = false

                        // 设置1秒后触发长按事件
                        f5KeyHandler?.postDelayed({
                            if (f5KeyPressTime > 0 && !isF5LongPressTriggered) {
                                isF5LongPressTriggered = true
                                handleF5LongPress()
                            }
                        }, 1000) // 1秒长按

                        Log.e(TAG, "F5键按下，开始计时...")
                    }

                    KeyEvent.ACTION_UP -> {
                        val pressDuration = System.currentTimeMillis() - f5KeyPressTime
                        Log.e(TAG, "F5键松开，按下时长: ${pressDuration}ms")

                        // 清除长按计时器
                        f5KeyHandler?.removeCallbacksAndMessages(null)

                        if (!isF5LongPressTriggered && pressDuration < 1000) {
                            // 短按：执行原有的点击功能
                            handleF5ShortPress()
                        }

                        // 重置状态
                        f5KeyPressTime = 0L
                        isF5LongPressTriggered = false
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
            val currentTime = System.currentTimeMillis()
            val timeDifference = currentTime - lastMediaPlayPauseTime
            
            // 如果距离上次点击不足2秒，则忽略此次点击
            if (timeDifference < 500) {
                Log.w(TAG, "播放/暂停按键被屏蔽 - 距离上次点击仅${timeDifference}ms (需要2000ms)")
                return
            }
            
            Log.e(TAG, "发送媒体播放暂停键...")
            
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            
            // 通过AudioManager发送媒体按键
            val result1 = audioManager?.dispatchMediaKeyEvent(downEvent)
            Thread.sleep(50)
            val result2 = audioManager?.dispatchMediaKeyEvent(upEvent)
            
            // 更新最后一次播放/暂停按键的时间
            lastMediaPlayPauseTime = currentTime
            
            Log.e(TAG, "媒体按键发送结果: down=$result1, up=$result2")
        } catch (e: Exception) {
            Log.e(TAG, "发送媒体按键失败: ${e.message}")
        }
    }
    
    private fun sendMediaPrevious() {
        try {
            Log.e(TAG, "发送媒体上一曲按键...")
            
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            
            // 通过AudioManager发送媒体按键
            val result1 = audioManager?.dispatchMediaKeyEvent(downEvent)
            Thread.sleep(50)
            val result2 = audioManager?.dispatchMediaKeyEvent(upEvent)
            
            Log.e(TAG, "媒体上一曲按键发送结果: down=$result1, up=$result2")
        } catch (e: Exception) {
            Log.e(TAG, "发送媒体上一曲按键失败: ${e.message}")
        }
    }
    
    private fun sendMediaNext() {
        try {
            Log.e(TAG, "发送媒体下一曲按键...")

            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)

            // 通过AudioManager发送媒体按键
            val result1 = audioManager?.dispatchMediaKeyEvent(downEvent)
            Thread.sleep(50)
            val result2 = audioManager?.dispatchMediaKeyEvent(upEvent)

            Log.e(TAG, "媒体下一曲按键发送结果: down=$result1, up=$result2")
        } catch (e: Exception) {
            Log.e(TAG, "发送媒体下一曲按键失败: ${e.message}")
        }
    }

    private fun sendBackKey() {
        try {
            Log.e(TAG, "发送返回按键...")

            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)

            // 发送返回按键事件
            val instrumentation = android.app.Instrumentation()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

            Log.e(TAG, "返回按键发送完成")
        } catch (e: Exception) {
            Log.e(TAG, "发送返回按键失败: ${e.message}")

            // 备用方法：直接使用performGlobalAction
            try {
                Log.e(TAG, "尝试使用performGlobalAction发送返回键...")
                performGlobalAction(GLOBAL_ACTION_BACK)
                Log.e(TAG, "performGlobalAction返回键发送完成")
            } catch (e2: Exception) {
                Log.e(TAG, "performGlobalAction发送返回键也失败: ${e2.message}")
            }
        }
    }
    
    private fun sendKeyC() {
        try {
            Log.e(TAG, "模拟点击CC按钮...")

            // 根据电视模式状态选择坐标
            val x: Float
            val y: Float

            if (isTvModeEnabled) {
                // 电视模式：根据屏幕方向选择不同坐标
                val orientation = resources.configuration.orientation
                val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

                if (isPortrait) {
                    // 电视模式竖屏坐标
                    x = 888f
                    y = 163f
                    Log.e(TAG, "电视模式竖屏 - 使用坐标: ($x, $y)")
                } else {
                    // 电视模式横屏坐标 (20.5:9 全屏模式)
                    x = 1740f
                    y = 95f
                    Log.e(TAG, "电视模式横屏 - 使用坐标: ($x, $y)")
                }
            } else {
                // 正常模式：检测屏幕方向选择对应的坐标
                val orientation = resources.configuration.orientation
                val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

                if (isPortrait) {
                    // 竖屏坐标
                    x = 876f
                    y = 154f
                    Log.e(TAG, "正常模式 - 检测到竖屏，使用坐标: ($x, $y)")
                } else {
                    // 横屏坐标
                    x = 2273f
                    y = 88f
                    Log.e(TAG, "正常模式 - 检测到横屏，使用坐标: ($x, $y)")
                }
            }

            // 执行单击CC按钮
            performSingleClick(x, y)
            Log.e(TAG, "CC按钮点击操作完成")

        } catch (e: Exception) {
            Log.e(TAG, "模拟点击CC按钮失败: ${e.message}")
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
    
    private fun performSingleClick(x: Float, y: Float) {
        try {
            Log.e(TAG, "开始执行单击操作，坐标: ($x, $y)")
            
            // 创建单击手势
            val clickPath = Path().apply {
                moveTo(x, y)
            }
            
            val clickStroke = GestureDescription.StrokeDescription(
                clickPath, 0, 100
            )
            
            val clickGesture = GestureDescription.Builder()
                .addStroke(clickStroke)
                .build()
            
            // 执行单击
            val result = dispatchGesture(clickGesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.e(TAG, "单击操作完成")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "单击操作被取消")
                }
            }, null)
            
            Log.d(TAG, "单击手势分发结果: $result")
            
        } catch (e: Exception) {
            Log.e(TAG, "执行单击操作失败: ${e.message}")
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
        
        // 通知所有监听器状态变化
        notifyYoutubeModeChanged(enabled)
    }
    
    private fun notifyYoutubeModeChanged(enabled: Boolean) {
        try {
            val intent = Intent("com.example.bluetoothkeymapper.YOUTUBE_MODE_CHANGED")
            intent.putExtra("enabled", enabled)
            sendBroadcast(intent)
            Log.d(TAG, "已发送YouTube模式状态变化广播: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "发送状态变化广播失败: ${e.message}")
        }
    }
    
    fun isDoubleClickMappingEnabled(): Boolean {
        return isDoubleClickMappingEnabled
    }
    
    fun setTvModeEnabled(enabled: Boolean) {
        isTvModeEnabled = enabled
        val status = if (isTvModeEnabled) "开启" else "关闭"
        
        Log.e(TAG, "=== 电视模式功能状态更新 ===")
        Log.e(TAG, "当前状态: $status")
        Log.e(TAG, "dpad down键映射: ${if (isTvModeEnabled) "点击CC按钮 电视坐标(1740,95)" else "点击CC按钮 正常坐标"}")
        Log.e(TAG, "===============================")
        
        // 使用Android系统通知样式的日志
        android.util.Log.wtf(TAG, "📺 电视模式功能已$status")
        
        // 通知所有监听器状态变化
        notifyTvModeChanged(enabled)
    }
    
    private fun notifyTvModeChanged(enabled: Boolean) {
        try {
            val intent = Intent("com.example.bluetoothkeymapper.TV_MODE_CHANGED")
            intent.putExtra("enabled", enabled)
            sendBroadcast(intent)
            Log.d(TAG, "已发送电视模式状态变化广播: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "发送电视模式状态变化广播失败: ${e.message}")
        }
    }
    
    fun isTvModeEnabled(): Boolean {
        return isTvModeEnabled
    }

    fun setBaiduModeEnabled(enabled: Boolean) {
        isBaiduModeEnabled = enabled
        val status = if (isBaiduModeEnabled) "开启" else "关闭"

        Log.e(TAG, "=== 百度网盘模式功能状态更新 ===")
        Log.e(TAG, "当前状态: $status")
        Log.e(TAG, "dpad left键映射: ${if (isBaiduModeEnabled) "上一曲" else "双击屏幕(133,439)"}")
        Log.e(TAG, "dpad right键映射: ${if (isBaiduModeEnabled) "下一曲" else "快进操作"}")
        Log.e(TAG, "===============================")

        // 使用Android系统通知样式的日志
        android.util.Log.wtf(TAG, "🎵 百度网盘模式功能已$status")

        // 通知所有监听器状态变化
        notifyBaiduModeChanged(enabled)
    }

    private fun notifyBaiduModeChanged(enabled: Boolean) {
        try {
            val intent = Intent("com.example.bluetoothkeymapper.BAIDU_MODE_CHANGED")
            intent.putExtra("enabled", enabled)
            sendBroadcast(intent)
            Log.d(TAG, "已发送百度网盘模式状态变化广播: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "发送百度网盘模式状态变化广播失败: ${e.message}")
        }
    }

    fun isBaiduModeEnabled(): Boolean {
        return isBaiduModeEnabled
    }

    fun setTiktokModeEnabled(enabled: Boolean) {
        isTiktokModeEnabled = enabled
        val status = if (isTiktokModeEnabled) "开启" else "关闭"

        Log.e(TAG, "=== TikTok模式功能状态更新 ===")
        Log.e(TAG, "当前状态: $status")
        Log.e(TAG, "dpad left键映射: ${if (isTiktokModeEnabled) "从(566,2213)左滑" else "原有功能"}")
        Log.e(TAG, "dpad right键映射: ${if (isTiktokModeEnabled) "从(566,2213)右滑" else "原有功能"}")
        Log.e(TAG, "dpad up键映射: ${if (isTiktokModeEnabled) "已禁用" else "原有功能"}")
        Log.e(TAG, "dpad down键映射: ${if (isTiktokModeEnabled) "已禁用" else "原有功能"}")
        Log.e(TAG, "OK键映射: ${if (isTiktokModeEnabled) "屏幕中心点击" else "原有功能"}")
        Log.e(TAG, "Home键映射: ${if (isTiktokModeEnabled) "进度条重置从(900,2213)到(0,2213)" else "原有功能"}")
        Log.e(TAG, "===============================")

        // 使用Android系统通知样式的日志
        android.util.Log.wtf(TAG, "🎵 TikTok模式功能已$status")

        // 通知所有监听器状态变化
        notifyTiktokModeChanged(enabled)
    }

    fun setAutoModeEnabled(enabled: Boolean) {
        isAutoModeEnabled = enabled
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("auto_mode_enabled", enabled).apply()
        Log.d(TAG, "自动模式切换已${if (enabled) "开启" else "关闭"}")
    }

    fun clearLastTargetAppMode() {
        lastTargetAppMode = ""
        lastTargetAppTime = 0L
        Log.e(TAG, "已清除上次目标应用模式记录")
    }

    private fun notifyTiktokModeChanged(enabled: Boolean) {
        try {
            val intent = Intent("com.example.bluetoothkeymapper.TIKTOK_MODE_CHANGED")
            intent.putExtra("enabled", enabled)
            sendBroadcast(intent)
            Log.d(TAG, "已发送TikTok模式状态变化广播: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "发送TikTok模式状态变化广播失败: ${e.message}")
        }
    }

    fun isTiktokModeEnabled(): Boolean {
        return isTiktokModeEnabled
    }

    private fun performTiktokLeftSwipe() {
        if (!isTiktokServiceEnabled()) {
            Log.d(TAG, "TikTok服务已关闭，忽略左滑手势")
            return
        }

        val swipePixels = getSwipePixels()
        val path = Path().apply {
            val startX = 566f
            val endX = startX - swipePixels.toFloat()  // 向左滑动客制化像素
            val y = 2270f
            moveTo(startX, y)
            lineTo(endX, y)
        }
        performTiktokSwipeGesture(path)
        Log.d(TAG, "执行TikTok左滑手势: 从(566,2270)向左滑动${swipePixels}px")
    }

    private fun performTiktokRightSwipe() {
        if (!isTiktokServiceEnabled()) {
            Log.d(TAG, "TikTok服务已关闭，忽略右滑手势")
            return
        }

        val swipePixels = getSwipePixels()
        val path = Path().apply {
            val startX = 566f
            val endX = startX + swipePixels.toFloat()  // 向右滑动客制化像素
            val y = 2270f
            moveTo(startX, y)
            lineTo(endX, y)
        }
        performTiktokSwipeGesture(path)
        Log.d(TAG, "执行TikTok右滑手势: 从(566,2270)向右滑动${swipePixels}px")
    }

    private fun performTiktokCenterClick() {
        if (!isTiktokServiceEnabled()) {
            Log.d(TAG, "TikTok服务已关闭，忽略中心点击手势")
            return
        }

        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        val path = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX, centerY)  // 点击手势，起始和结束位置相同
        }
        performTiktokClickGesture(path)
        Log.d(TAG, "执行TikTok屏幕中心点击: 位置(${centerX},${centerY})")
    }

    private fun performTiktokSeekToStart() {
        if (!isTiktokServiceEnabled()) {
            Log.d(TAG, "TikTok服务已关闭，忽略进度条重置手势")
            return
        }

        val path = Path().apply {
            val startX = 900f   // 从适中位置开始，避免被误认为返回手势
            val endX = 0f       // 滑动到最左边(0位置)，实现跳转到开头
            val y = 2270f       // 使用与左右滑动相同的Y坐标
            moveTo(startX, y)
            lineTo(endX, y)
        }
        performTiktokSwipeGesture(path)
        Log.d(TAG, "执行TikTok进度条重置: 从(900,2270)滑动到(0,2270)")
    }

    private fun performTiktokSwipeGesture(path: Path) {
        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 300L)
        gestureBuilder.addStroke(strokeDescription)

        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "TikTok手势执行完成")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d(TAG, "TikTok手势执行被取消")
            }
        }, null)
    }

    private fun performTiktokClickGesture(path: Path) {
        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 100L)
        gestureBuilder.addStroke(strokeDescription)

        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "TikTok点击手势执行完成")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d(TAG, "TikTok点击手势执行被取消")
            }
        }, null)
    }

    // 处理F5键短按（原上方向键功能）
    private fun handleF5ShortPress() {
        Log.e(TAG, "F5键短按 - 执行原上方向键功能")

        // 只在油管模式下才执行原上方向键功能
        if (isDoubleClickMappingEnabled || isTvModeEnabled) {
            if (isTiktokModeEnabled) {
                // TikTok模式：禁用上方向键功能
                Log.e(TAG, "TikTok模式 - 上方向键功能已禁用")
            } else {
                // 检查屏幕方向，只在横屏模式下执行
                val orientation = resources.configuration.orientation
                val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

                if (isLandscape) {
                    Log.e(TAG, "F5短按 - 横屏模式，执行单击屏幕坐标(520,107)操作")
                    performSingleClick(520f, 107f)
                    Log.e(TAG, "F5短按单击操作完成")
                } else {
                    Log.w(TAG, "F5短按 - 竖屏模式，上方向键功能已禁用，只在横屏模式下生效")
                }
            }
        } else {
            Log.w(TAG, "F5短按 - 非油管模式，不执行上方向键功能")
        }
    }

    // 处理F5键长按1秒（原有点击功能）
    private fun handleF5LongPress() {
        Log.e(TAG, "F5键长按1秒触发 - 执行原有点击功能")
        val orientation = resources.configuration.orientation
        val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

        if (isTvModeEnabled) {
            // 电视模式：根据屏幕方向选择不同坐标
            if (isPortrait) {
                Log.e(TAG, "电视模式竖屏状态，忽略F5键长按操作")
            } else {
                Log.e(TAG, "电视模式横屏状态，执行点击坐标(1885,60)操作")
                performSingleClick(1885f, 60f)
                Log.e(TAG, "电视模式F5键横屏点击操作完成")
            }
        } else {
            // 普通模式：原有逻辑
            if (isPortrait) {
                Log.e(TAG, "当前为竖屏状态，忽略F5键长按操作")
            } else {
                Log.e(TAG, "当前为横屏状态，执行点击坐标(2402,74)操作")
                performSingleClick(2402f, 74f)
                Log.e(TAG, "F5键横屏点击操作完成")
            }
        }
    }

    // 静音和恢复音量切换功能
    private fun toggleMute() {
        try {
            audioManager?.let { am ->
                if (isMuted) {
                    // 当前是静音状态，恢复音量
                    if (lastVolumeBeforeMute > 0) {
                        am.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            lastVolumeBeforeMute,
                            AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
                        )
                        Log.e(TAG, "恢复音量到: $lastVolumeBeforeMute")
                    } else {
                        // 如果没有保存的音量，设置为中等音量
                        val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val defaultVolume = maxVolume / 2
                        am.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            defaultVolume,
                            AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
                        )
                        Log.e(TAG, "恢复到默认音量: $defaultVolume")
                    }
                    isMuted = false
                    lastVolumeBeforeMute = -1
                } else {
                    // 当前不是静音状态，执行静音
                    val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (currentVolume > 0) {
                        lastVolumeBeforeMute = currentVolume
                        am.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            0,
                            AudioManager.FLAG_SHOW_UI
                        )
                        Log.e(TAG, "静音，保存音量: $currentVolume")
                        isMuted = true
                    } else {
                        Log.e(TAG, "当前已经是静音状态")
                    }
                }

                // 显示音量控制UI
                showVolumeUI()
            }
        } catch (e: Exception) {
            Log.e(TAG, "静音/恢复音量切换失败: ${e.message}")
        }
    }

    // 显示音量控制UI
    private fun showVolumeUI() {
        try {
            audioManager?.let { am ->
                val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                Log.e(TAG, "🔊 音量状态: ${if (isMuted) "静音" else "正常"} - $currentVolume/$maxVolume")

                // 通过设置相同音量来显示UI
                am.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    currentVolume,
                    AudioManager.FLAG_SHOW_UI
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示音量 UI 失败: ${e.message}")
        }
    }

    // 处理左方向键短按（原有功能）
    private fun handleDpadLeftShortPress() {
        Log.e(TAG, "左方向键短按 - 执行原有功能")

        if (isTiktokModeEnabled) {
            // TikTok模式：左滑操作
            Log.e(TAG, "TikTok模式 - 执行左滑操作")
            performTiktokLeftSwipe()
            Log.e(TAG, "TikTok模式左滑操作完成")
        } else if (isTvModeEnabled) {
            // 电视模式：根据屏幕方向选择不同坐标
            val orientation = resources.configuration.orientation
            val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

            if (isPortrait) {
                Log.e(TAG, "电视模式竖屏 - 执行双击屏幕坐标(201,253)操作")
                performDoubleClick(201f, 253f)
                Log.e(TAG, "电视模式竖屏双击操作完成")
            } else {
                Log.e(TAG, "电视模式横屏 - 执行双击屏幕坐标(133,439)操作")
                performDoubleClick(133f, 439f)
                Log.e(TAG, "电视模式横屏双击操作完成")
            }
        } else if (isBaiduModeEnabled) {
            Log.e(TAG, "百度网盘模式 - 执行上一曲操作")
            sendMediaPrevious()
            Log.e(TAG, "上一曲操作完成")
        } else if (isDoubleClickMappingEnabled) {
            Log.e(TAG, "普通模式 - 执行双击屏幕坐标(133,439)操作")
            performDoubleClick(133f, 439f)
            Log.e(TAG, "双击操作完成")
        } else {
            Log.w(TAG, "双击映射功能已关闭，恢复左方向键原有功能")
        }
    }

    // 处理左方向键长按1秒（播放上一个）
    private fun handleDpadLeftLongPress() {
        Log.e(TAG, "左方向键长按1秒触发 - 播放上一个")
        sendMediaPrevious()
        Log.e(TAG, "左方向键长按 - 上一个播放操作完成")
    }

    // 处理右方向键短按（原有功能）
    private fun handleDpadRightShortPress() {
        Log.e(TAG, "右方向键短按 - 执行原有功能")

        if (isTiktokModeEnabled) {
            // TikTok模式：右滑操作
            Log.e(TAG, "TikTok模式 - 执行右滑操作")
            performTiktokRightSwipe()
            Log.e(TAG, "TikTok模式右滑操作完成")
        } else if (isTvModeEnabled) {
            // 电视模式：根据屏幕方向选择不同坐标
            val orientation = resources.configuration.orientation
            val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

            if (isPortrait) {
                Log.e(TAG, "电视模式竖屏 - 执行双击屏幕坐标(810,265)操作")
                performDoubleClick(810f, 265f)
                Log.e(TAG, "电视模式竖屏双击操作完成")
            } else {
                Log.e(TAG, "电视模式横屏 - 执行双击屏幕坐标(1780,355)操作")
                performDoubleClick(1780f, 355f)
                Log.e(TAG, "电视模式横屏双击操作完成")
            }
        } else if (isBaiduModeEnabled) {
            Log.e(TAG, "百度网盘模式 - 执行下一曲操作")
            sendMediaNext()
            Log.e(TAG, "下一曲操作完成")
        } else {
            // 普通模式：根据屏幕方向双击不同坐标实现快进功能
            val orientation = resources.configuration.orientation
            val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

            if (isPortrait) {
                Log.e(TAG, "普通模式竖屏 - 执行双击屏幕坐标(810,265)操作")
                performDoubleClick(810f, 265f)
                Log.e(TAG, "竖屏模式双击操作完成")
            } else {
                Log.e(TAG, "普通模式横屏 - 执行双击屏幕坐标(1940,384)操作")
                performDoubleClick(1940f, 384f)
                Log.e(TAG, "横屏模式双击操作完成")
            }
        }
    }

    // 处理右方向键长按1秒（播放下一个）
    private fun handleDpadRightLongPress() {
        Log.e(TAG, "右方向键长按1秒触发 - 播放下一个")
        sendMediaNext()
        Log.e(TAG, "右方向键长按 - 下一个播放操作完成")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        f5KeyHandler?.removeCallbacksAndMessages(null)
        f5KeyHandler = null
        dpadLeftHandler?.removeCallbacksAndMessages(null)
        dpadLeftHandler = null
        dpadRightHandler?.removeCallbacksAndMessages(null)
        dpadRightHandler = null
        Log.d(TAG, "无障碍服务已销毁")
    }
}