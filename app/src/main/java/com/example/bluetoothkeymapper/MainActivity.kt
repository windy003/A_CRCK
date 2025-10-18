package com.example.bluetoothkeymapper

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Switch
import android.widget.Toast
import android.app.ActivityManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bluetoothkeymapper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var bluetoothKeyService: BluetoothKeyService? = null
    private var serviceBound = false
    private lateinit var sharedPreferences: SharedPreferences
    private var isReceiverRegistered = false
    private var isServiceRunning = false
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "KeyMapperPrefs"
        private const val PREF_YOUTUBE_MODE_ENABLED = "youtube_mode_enabled"
        private const val PREF_TV_MODE_ENABLED = "tv_mode_enabled"
        private const val PREF_BAIDU_MODE_ENABLED = "baidu_mode_enabled"
        private const val PREF_TIKTOK_MODE_ENABLED = "tiktok_mode_enabled"
        private const val PREF_SERVICE_ENABLED = "service_enabled"
        private const val PREF_AUTO_MODE_ENABLED = "auto_mode_enabled"
    }
    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            updateUI()
        } else {
            Toast.makeText(this, "需要蓝牙权限才能使用此应用", Toast.LENGTH_LONG).show()
        }
    }
    
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        updateUI()
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as BluetoothKeyService.LocalBinder
            bluetoothKeyService = binder.getService()
            serviceBound = true
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName) {
            bluetoothKeyService = null
            serviceBound = false
            updateUI()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupUI()
        checkPermissions()
        checkServiceRunningState()
    }
    
    private fun setupUI() {
        Log.d(TAG, "设置UI")
        
        binding.btnToggleService.setOnClickListener {
            Log.d(TAG, "点击服务切换按钮, 当前状态: $isServiceRunning")
            if (isServiceRunning) {
                stopBluetoothService()
            } else {
                startBluetoothService()
            }
        }
        
        binding.btnAccessibilitySettings.setOnClickListener {
            Log.d(TAG, "点击无障碍设置按钮")
            openAccessibilitySettings()
        }
        
        binding.btnPermissions.setOnClickListener {
            Log.d(TAG, "点击权限按钮")
            requestPermissions.launch(bluetoothPermissions)
        }
        
        binding.btnEnableBluetooth.setOnClickListener {
            Log.d(TAG, "点击启用蓝牙按钮")
            enableBluetooth()
        }

        // 设置视频时长保存按钮点击事件
        binding.btnSavePixels.setOnClickListener {
            Log.d(TAG, "点击保存视频时长按钮")
            saveVideoDuration()
        }
        
        // 设置YouTube模式开关
        val isYoutubeModeEnabled = sharedPreferences.getBoolean(PREF_YOUTUBE_MODE_ENABLED, true)
        binding.switchYoutubeMode.isChecked = isYoutubeModeEnabled
        updateYoutubeModeStatus(isYoutubeModeEnabled)
        
        // 设置电视模式开关
        val isTvModeEnabled = sharedPreferences.getBoolean(PREF_TV_MODE_ENABLED, false)
        binding.switchTvMode.isChecked = isTvModeEnabled
        updateTvModeStatus(isTvModeEnabled)

        // 设置百度网盘模式开关
        val isBaiduModeEnabled = sharedPreferences.getBoolean(PREF_BAIDU_MODE_ENABLED, false)
        binding.switchBaiduMode.isChecked = isBaiduModeEnabled
        updateBaiduModeStatus(isBaiduModeEnabled)

        // 设置TikTok模式开关
        val isTiktokModeEnabled = sharedPreferences.getBoolean(PREF_TIKTOK_MODE_ENABLED, false)
        binding.switchTiktokMode.isChecked = isTiktokModeEnabled
        updateTiktokModeStatus(isTiktokModeEnabled)

        // 设置自动模式开关
        val isAutoModeEnabled = sharedPreferences.getBoolean(PREF_AUTO_MODE_ENABLED, true)
        binding.switchAutoMode.isChecked = isAutoModeEnabled
        updateAutoModeStatus(isAutoModeEnabled)

        // 初始化视频时长设置
        loadVideoDuration()

        // 设置开关监听器
        setAllModeListeners()
        
    }

    private fun setAutoModeSwitchListener() {
        binding.switchAutoMode.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "自动模式开关状态改变: $isChecked")

            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_AUTO_MODE_ENABLED, isChecked)
                .apply()

            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setAutoModeEnabled(isChecked)

            // 更新UI显示
            updateAutoModeStatus(isChecked)

            Toast.makeText(
                this,
                if (isChecked) "自动模式已开启 - 将根据当前应用自动切换模式" else "自动模式已关闭 - 需要手动切换模式",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateAutoModeStatus(enabled: Boolean) {
        binding.tvAutoModeStatus.text = if (enabled) {
            "自动模式已开启 - 根据当前应用自动切换模式"
        } else {
            "自动模式已关闭 - 需要手动切换模式"
        }

        binding.tvAutoModeStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }

    private fun setYoutubeModeSwitchListener() {
        binding.switchYoutubeMode.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "YouTube模式开关状态改变: $isChecked")

            if (isChecked) {
                // 关闭其他所有开关
                disableOtherModeSwitches(PREF_YOUTUBE_MODE_ENABLED)
            }

            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_YOUTUBE_MODE_ENABLED, isChecked)
                .apply()

            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setDoubleClickMappingEnabled(isChecked)
            // 清除自动模式记录，因为用户手动切换了
            KeyMapperAccessibilityService.instance?.clearLastTargetAppMode()

            // 更新UI显示
            updateYoutubeModeStatus(isChecked)

            Toast.makeText(
                this,
                if (isChecked) "YouTube模式已开启" else "YouTube模式已关闭",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun setTvModeSwitchListener() {
        binding.switchTvMode.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "电视模式开关状态改变: $isChecked")

            if (isChecked) {
                // 关闭其他所有开关
                disableOtherModeSwitches(PREF_TV_MODE_ENABLED)
            }

            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_TV_MODE_ENABLED, isChecked)
                .apply()

            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setTvModeEnabled(isChecked)
            // 清除自动模式记录，因为用户手动切换了
            KeyMapperAccessibilityService.instance?.clearLastTargetAppMode()

            // 更新UI显示
            updateTvModeStatus(isChecked)

            Toast.makeText(
                this,
                if (isChecked) "电视模式已开启 - 使用16:9坐标" else "电视模式已关闭 - 使用正常坐标",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    
    
    
    private fun checkServiceRunningState() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var serviceActuallyRunning = false

            for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (BluetoothKeyService::class.java.name == service.service.className) {
                    serviceActuallyRunning = true
                    break
                }
            }

            // 检查用户上次是否主动启动了服务
            val wasServiceEnabledByUser = sharedPreferences.getBoolean(PREF_SERVICE_ENABLED, false)

            if (serviceActuallyRunning && !wasServiceEnabledByUser) {
                // 服务在运行但用户上次关闭了它，停止服务
                Log.d(TAG, "检测到服务在运行但用户上次已关闭，自动停止服务")
                val intent = Intent(this, BluetoothKeyService::class.java)
                stopService(intent)
                isServiceRunning = false
            } else if (serviceActuallyRunning && wasServiceEnabledByUser) {
                // 服务在运行且用户上次启动了它
                Log.d(TAG, "检测到服务正在运行且用户上次已启动")
                isServiceRunning = true
            } else if (!serviceActuallyRunning && wasServiceEnabledByUser) {
                // 服务没运行但用户上次启动了它，可能需要重启（但这里不自动重启，让用户手动点击）
                Log.d(TAG, "用户上次启动了服务但服务未运行，等待用户手动启动")
                isServiceRunning = false
            } else {
                // 服务没运行且用户上次也没启动
                Log.d(TAG, "服务未运行，用户上次未启动")
                isServiceRunning = false
            }

            updateServiceButton()
        } catch (e: Exception) {
            Log.e(TAG, "检查服务运行状态失败: ${e.message}")
            isServiceRunning = false
            updateServiceButton()
        }
    }
    
    private fun checkPermissions() {
        val missingPermissions = bluetoothPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissions.launch(bluetoothPermissions)
        } else {
            bindToService()
        }
    }
    
    private fun bindToService() {
        val intent = Intent(this, BluetoothKeyService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun startBluetoothService() {
        Log.d(TAG, "startBluetoothService 被调用")
        
        val bluetoothEnabled = isBluetoothEnabled()
        val permissionsGranted = hasBluetoothPermissions()
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        
        Log.d(TAG, "蓝牙启用: $bluetoothEnabled, 权限: $permissionsGranted, 无障碍: $accessibilityEnabled")
        
        if (!bluetoothEnabled) {
            Toast.makeText(this, "请先启用蓝牙", Toast.LENGTH_LONG).show()
            return
        }
        
        if (!permissionsGranted) {
            Toast.makeText(this, "请先授予蓝牙权限", Toast.LENGTH_LONG).show()
            return
        }
        
        if (!accessibilityEnabled) {
            Toast.makeText(this, "请先启用无障碍服务", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        
        try {
            Log.d(TAG, "启动服务")
            val intent = Intent(this, BluetoothKeyService::class.java)
            startService(intent)
            bluetoothKeyService?.startBluetoothScanning()

            isServiceRunning = true

            // 保存服务启动状态
            sharedPreferences.edit()
                .putBoolean(PREF_SERVICE_ENABLED, true)
                .apply()
            Log.d(TAG, "已保存服务启动状态")


            Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show()
            updateServiceButton()
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "启动服务失败", e)
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopBluetoothService() {
        val intent = Intent(this, BluetoothKeyService::class.java)
        stopService(intent)

        isServiceRunning = false

        // 保存服务停止状态
        sharedPreferences.edit()
            .putBoolean(PREF_SERVICE_ENABLED, false)
            .apply()
        Log.d(TAG, "已保存服务停止状态")


        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
        updateServiceButton()
        updateUI()
    }
    
    private fun updateServiceButton() {
        if (isServiceRunning) {
            binding.btnToggleService.text = "停止服务"
            binding.btnToggleService.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
        } else {
            binding.btnToggleService.text = "启动服务"
            binding.btnToggleService.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_green_light)
            )
        }
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    private fun enableBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter?.isEnabled != true) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }
    
    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter?.isEnabled == true
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        return bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        return accessibilityServices?.contains(
            "${packageName}/${KeyMapperAccessibilityService::class.java.name}"
        ) == true
    }
    
    private fun updateUI() {
        val bluetoothEnabled = isBluetoothEnabled()
        val permissionsGranted = hasBluetoothPermissions()
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        
        Log.d(TAG, "updateUI - 蓝牙: $bluetoothEnabled, 权限: $permissionsGranted, 无障碍: $accessibilityEnabled")
        
        binding.tvBluetoothStatus.text = if (bluetoothEnabled) "蓝牙: 已启用" else "蓝牙: 未启用"
        binding.tvPermissionStatus.text = if (permissionsGranted) "权限: 已授予" else "权限: 未授予"
        binding.tvAccessibilityStatus.text = if (accessibilityEnabled) "无障碍服务: 已启用" else "无障碍服务: 未启用"
        
        val canStartService = bluetoothEnabled && permissionsGranted && accessibilityEnabled
        Log.d(TAG, "服务按钮可用状态: $canStartService")
        
        binding.btnToggleService.isEnabled = canStartService || isServiceRunning
        binding.btnEnableBluetooth.isEnabled = !bluetoothEnabled && permissionsGranted
        binding.btnPermissions.isEnabled = !permissionsGranted
        
        // 更新服务按钮的显示状态
        updateServiceButton()
        
        // 同步YouTube模式状态
        val isYoutubeModeEnabled = sharedPreferences.getBoolean(PREF_YOUTUBE_MODE_ENABLED, true)
        updateYoutubeModeStatus(isYoutubeModeEnabled)
        
        // 同步电视模式状态
        val isTvModeEnabled = sharedPreferences.getBoolean(PREF_TV_MODE_ENABLED, false)
        updateTvModeStatus(isTvModeEnabled)

        // 同步百度网盘模式状态
        val isBaiduModeEnabled = sharedPreferences.getBoolean(PREF_BAIDU_MODE_ENABLED, false)
        updateBaiduModeStatus(isBaiduModeEnabled)

        // 同步TikTok模式状态
        val isTiktokModeEnabled = sharedPreferences.getBoolean(PREF_TIKTOK_MODE_ENABLED, false)
        updateTiktokModeStatus(isTiktokModeEnabled)
    }
    
    private fun updateYoutubeModeStatus(enabled: Boolean) {
        binding.tvYoutubeModeStatus.text = if (enabled) {
            "YouTube模式已开启 - dpad left双击屏幕后退5秒"
        } else {
            "YouTube模式已关闭"
        }

        binding.tvYoutubeModeStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }
    
    private fun updateTvModeStatus(enabled: Boolean) {
        binding.tvTvModeStatus.text = if (enabled) {
            "电视模式已开启 - 使用16:9全屏坐标"
        } else {
            "电视模式已关闭 - 使用正常点击位置"
        }

        binding.tvTvModeStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }

    private fun setBaiduModeSwitchListener() {
        binding.switchBaiduMode.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "百度网盘模式开关状态改变: $isChecked")

            if (isChecked) {
                // 关闭其他所有开关
                disableOtherModeSwitches(PREF_BAIDU_MODE_ENABLED)
            }

            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_BAIDU_MODE_ENABLED, isChecked)
                .apply()

            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setBaiduModeEnabled(isChecked)
            // 清除自动模式记录，因为用户手动切换了
            KeyMapperAccessibilityService.instance?.clearLastTargetAppMode()

            // 更新UI显示
            updateBaiduModeStatus(isChecked)


            Toast.makeText(
                this,
                if (isChecked) "百度网盘模式已开启" else "百度网盘模式已关闭",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateBaiduModeStatus(enabled: Boolean) {
        binding.tvBaiduModeStatus.text = if (enabled) {
            "百度网盘模式已开启 - OK键播放/暂停，左右键上一曲/下一曲"
        } else {
            "百度网盘模式已关闭"
        }

        binding.tvBaiduModeStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }

    private fun setTiktokModeSwitchListener() {
        binding.switchTiktokMode.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "TikTok模式开关状态改变: $isChecked")

            if (isChecked) {
                // 关闭其他所有开关
                disableOtherModeSwitches(PREF_TIKTOK_MODE_ENABLED)
            }

            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_TIKTOK_MODE_ENABLED, isChecked)
                .apply()

            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setTiktokModeEnabled(isChecked)
            // 清除自动模式记录，因为用户手动切换了
            KeyMapperAccessibilityService.instance?.clearLastTargetAppMode()

            // 更新UI显示
            updateTiktokModeStatus(isChecked)

            Toast.makeText(
                this,
                if (isChecked) "TikTok/抖音/今日头条模式已开启" else "TikTok/抖音/今日头条模式已关闭",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateTiktokModeStatus(enabled: Boolean) {
        binding.tvTiktokModeStatus.text = if (enabled) {
            "TikTok/抖音/今日头条模式已开启 - 左右键滑动，上下键已禁用，Home键重置进度条，OK键播放/暂停"
        } else {
            "TikTok/抖音/今日头条模式已关闭"
        }

        binding.tvTiktokModeStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }


    private fun disableOtherModeSwitches(currentModeEnabled: String) {
        Log.d(TAG, "关闭其他模式开关，当前启用的模式: $currentModeEnabled")

        // 临时移除监听器防止递归调用
        binding.switchYoutubeMode.setOnCheckedChangeListener(null)
        binding.switchTvMode.setOnCheckedChangeListener(null)
        binding.switchBaiduMode.setOnCheckedChangeListener(null)
        binding.switchTiktokMode.setOnCheckedChangeListener(null)

        // 根据当前启用的模式，关闭其他所有模式
        when (currentModeEnabled) {
            PREF_YOUTUBE_MODE_ENABLED -> {
                // 关闭其他3个模式
                setModeState(PREF_TV_MODE_ENABLED, false, binding.switchTvMode)
                setModeState(PREF_BAIDU_MODE_ENABLED, false, binding.switchBaiduMode)
                setModeState(PREF_TIKTOK_MODE_ENABLED, false, binding.switchTiktokMode)
            }
            PREF_TV_MODE_ENABLED -> {
                // 关闭其他3个模式
                setModeState(PREF_YOUTUBE_MODE_ENABLED, false, binding.switchYoutubeMode)
                setModeState(PREF_BAIDU_MODE_ENABLED, false, binding.switchBaiduMode)
                setModeState(PREF_TIKTOK_MODE_ENABLED, false, binding.switchTiktokMode)
            }
            PREF_BAIDU_MODE_ENABLED -> {
                // 关闭其他3个模式
                setModeState(PREF_YOUTUBE_MODE_ENABLED, false, binding.switchYoutubeMode)
                setModeState(PREF_TV_MODE_ENABLED, false, binding.switchTvMode)
                setModeState(PREF_TIKTOK_MODE_ENABLED, false, binding.switchTiktokMode)
            }
            PREF_TIKTOK_MODE_ENABLED -> {
                // 关闭其他3个模式
                setModeState(PREF_YOUTUBE_MODE_ENABLED, false, binding.switchYoutubeMode)
                setModeState(PREF_TV_MODE_ENABLED, false, binding.switchTvMode)
                setModeState(PREF_BAIDU_MODE_ENABLED, false, binding.switchBaiduMode)
            }
        }

        // 重新设置监听器
        setYoutubeModeSwitchListener()
        setTvModeSwitchListener()
        setBaiduModeSwitchListener()
        setTiktokModeSwitchListener()

        Log.d(TAG, "其他模式开关已关闭")
    }

    private fun setModeState(prefKey: String, enabled: Boolean, switch: Switch) {
        // 保存状态到SharedPreferences
        sharedPreferences.edit().putBoolean(prefKey, enabled).apply()

        // 更新开关状态
        switch.isChecked = enabled

        // 更新相应的服务状态和UI
        when (prefKey) {
            PREF_YOUTUBE_MODE_ENABLED -> {
                KeyMapperAccessibilityService.instance?.setDoubleClickMappingEnabled(enabled)
                updateYoutubeModeStatus(enabled)
            }
            PREF_TV_MODE_ENABLED -> {
                KeyMapperAccessibilityService.instance?.setTvModeEnabled(enabled)
                updateTvModeStatus(enabled)
            }
            PREF_BAIDU_MODE_ENABLED -> {
                KeyMapperAccessibilityService.instance?.setBaiduModeEnabled(enabled)
                updateBaiduModeStatus(enabled)
            }
            PREF_TIKTOK_MODE_ENABLED -> {
                KeyMapperAccessibilityService.instance?.setTiktokModeEnabled(enabled)
                updateTiktokModeStatus(enabled)
            }
        }
    }

    private fun saveVideoDuration() {
        val minutesText = binding.etMinutes.text.toString()
        val secondsText = binding.etSeconds.text.toString()

        try {
            val minutes = if (minutesText.isNotEmpty()) minutesText.toInt() else 0
            val seconds = if (secondsText.isNotEmpty()) secondsText.toInt() else 0

            if (minutes == 0 && seconds == 0) {
                Toast.makeText(this, "请输入视频时长", Toast.LENGTH_SHORT).show()
                return
            }

            if (seconds >= 60) {
                Toast.makeText(this, "秒数不能超过59", Toast.LENGTH_SHORT).show()
                return
            }

            // 计算总秒数
            val totalSeconds = minutes * 60 + seconds

            // 计算分块数（总秒数除以5）
            val blockCount = totalSeconds / 5

            if (blockCount == 0) {
                Toast.makeText(this, "视频时长太短，至少需要5秒", Toast.LENGTH_SHORT).show()
                return
            }

            // 计算每次滑动的像素值（1080除以分块数）
            val swipePixels = 1080 / blockCount

            // 保存视频时长和计算出的像素值
            sharedPreferences.edit()
                .putInt("video_minutes", minutes)
                .putInt("video_seconds", seconds)
                .putInt("swipe_pixels", swipePixels)
                .apply()

            val tiktokPrefs = getSharedPreferences("TikTokRemoteControl", Context.MODE_PRIVATE)
            tiktokPrefs.edit().putInt("swipe_pixels", swipePixels).apply()

            // 更新说明文字显示计算结果
            binding.tvCalculatedPixels.text =
                "视频时长: ${minutes}分${seconds}秒 | 总秒数: ${totalSeconds}秒 | 分块数: ${blockCount} | 每次滑动: ${swipePixels}px"

            Toast.makeText(this, "已保存设置，每次滑动 ${swipePixels}px", Toast.LENGTH_LONG).show()
            Log.d(TAG, "视频时长已保存: ${minutes}分${seconds}秒，总秒数: ${totalSeconds}秒，分块数: ${blockCount}，滑动像素: ${swipePixels}px")
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadVideoDuration() {
        val savedMinutes = sharedPreferences.getInt("video_minutes", 0)
        val savedSeconds = sharedPreferences.getInt("video_seconds", 0)
        val savedPixels = sharedPreferences.getInt("swipe_pixels", 100)

        if (savedMinutes > 0 || savedSeconds > 0) {
            binding.etMinutes.setText(savedMinutes.toString())
            binding.etSeconds.setText(savedSeconds.toString())

            val totalSeconds = savedMinutes * 60 + savedSeconds
            val blockCount = totalSeconds / 5

            binding.tvCalculatedPixels.text =
                "视频时长: ${savedMinutes}分${savedSeconds}秒 | 总秒数: ${totalSeconds}秒 | 分块数: ${blockCount} | 每次滑动: ${savedPixels}px"
        } else {
            binding.tvCalculatedPixels.text = "说明：输入视频总时长后，系统会自动计算每次滑动的像素值"
        }

        Log.d(TAG, "加载视频时长: ${savedMinutes}分${savedSeconds}秒，滑动像素: ${savedPixels}px")
    }

    private fun removeAllModeListeners() {
        binding.switchAutoMode.setOnCheckedChangeListener(null)
        binding.switchYoutubeMode.setOnCheckedChangeListener(null)
        binding.switchTvMode.setOnCheckedChangeListener(null)
        binding.switchBaiduMode.setOnCheckedChangeListener(null)
        binding.switchTiktokMode.setOnCheckedChangeListener(null)
    }

    private fun setAllModeListeners() {
        setAutoModeSwitchListener()
        setYoutubeModeSwitchListener()
        setTvModeSwitchListener()
        setBaiduModeSwitchListener()
        setTiktokModeSwitchListener()
    }

    override fun onResume() {
        super.onResume()

        // 检查服务运行状态
        checkServiceRunningState()

        updateUI()

        // 延迟一下再同步状态，确保无障碍服务已经处理完应用切换
        android.os.Handler().postDelayed({
            syncAllModeStates()
        }, 200)
    }

    private fun syncAllModeStates() {
        Log.e(TAG, "🔄 开始同步所有模式状态")

        // 同步所有模式状态
        removeAllModeListeners()

        // 从无障碍服务获取实时状态
        val accessibilityService = KeyMapperAccessibilityService.instance
        if (accessibilityService != null) {
            Log.e(TAG, "📱 从无障碍服务获取实时状态")

            // 强制从SharedPreferences读取最新状态
            val currentYoutubeState = sharedPreferences.getBoolean(PREF_YOUTUBE_MODE_ENABLED, true)
            val currentTvModeState = sharedPreferences.getBoolean(PREF_TV_MODE_ENABLED, false)
            val currentBaiduModeState = sharedPreferences.getBoolean(PREF_BAIDU_MODE_ENABLED, false)
            val currentTiktokModeState = sharedPreferences.getBoolean(PREF_TIKTOK_MODE_ENABLED, false)
            val currentAutoModeState = sharedPreferences.getBoolean(PREF_AUTO_MODE_ENABLED, true)

            Log.e(TAG, "📊 当前状态 - YouTube:$currentYoutubeState, TV:$currentTvModeState, Baidu:$currentBaiduModeState, TikTok:$currentTiktokModeState, Auto:$currentAutoModeState")

            // 更新所有开关状态
            binding.switchYoutubeMode.isChecked = currentYoutubeState
            binding.switchTvMode.isChecked = currentTvModeState
            binding.switchBaiduMode.isChecked = currentBaiduModeState
            binding.switchTiktokMode.isChecked = currentTiktokModeState
            binding.switchAutoMode.isChecked = currentAutoModeState

            // 更新所有UI状态
            updateYoutubeModeStatus(currentYoutubeState)
            updateTvModeStatus(currentTvModeState)
            updateBaiduModeStatus(currentBaiduModeState)
            updateTiktokModeStatus(currentTiktokModeState)
            updateAutoModeStatus(currentAutoModeState)

            Log.e(TAG, "🎯 界面状态更新完成")
        } else {
            Log.w(TAG, "无障碍服务实例为空，使用SharedPreferences状态")

            // 直接从SharedPreferences读取状态
            val currentYoutubeState = sharedPreferences.getBoolean(PREF_YOUTUBE_MODE_ENABLED, true)
            val currentTvModeState = sharedPreferences.getBoolean(PREF_TV_MODE_ENABLED, false)
            val currentBaiduModeState = sharedPreferences.getBoolean(PREF_BAIDU_MODE_ENABLED, false)
            val currentTiktokModeState = sharedPreferences.getBoolean(PREF_TIKTOK_MODE_ENABLED, false)
            val currentAutoModeState = sharedPreferences.getBoolean(PREF_AUTO_MODE_ENABLED, true)

            // 更新所有开关状态
            binding.switchYoutubeMode.isChecked = currentYoutubeState
            binding.switchTvMode.isChecked = currentTvModeState
            binding.switchBaiduMode.isChecked = currentBaiduModeState
            binding.switchTiktokMode.isChecked = currentTiktokModeState
            binding.switchAutoMode.isChecked = currentAutoModeState

            // 更新所有UI状态
            updateYoutubeModeStatus(currentYoutubeState)
            updateTvModeStatus(currentTvModeState)
            updateBaiduModeStatus(currentBaiduModeState)
            updateTiktokModeStatus(currentTiktokModeState)
            updateAutoModeStatus(currentAutoModeState)
        }

        // 重新设置所有监听器
        setAllModeListeners()

        Log.e(TAG, "✅ 模式状态同步完成")
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
        }
        // 取消注册广播接收器
        
    }
}