package com.example.bluetoothkeymapper

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
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
        private const val YOUTUBE_MODE_CHANGED_ACTION = "com.example.bluetoothkeymapper.YOUTUBE_MODE_CHANGED"
        private const val TV_MODE_CHANGED_ACTION = "com.example.bluetoothkeymapper.TV_MODE_CHANGED"
        private const val BAIDU_MODE_CHANGED_ACTION = "com.example.bluetoothkeymapper.BAIDU_MODE_CHANGED"
        private const val TIKTOK_MODE_CHANGED_ACTION = "com.example.bluetoothkeymapper.TIKTOK_MODE_CHANGED"
        private const val MAIN_TOGGLE_CHANGED_ACTION = "com.example.bluetoothkeymapper.MAIN_TOGGLE_CHANGED"
    }
    
    // 广播接收器监听磁贴状态变化
    private val tileStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                YOUTUBE_MODE_CHANGED_ACTION -> {
                    val enabled = intent.getBooleanExtra("enabled", true)
                    Log.d(TAG, "收到YouTube模式状态变化广播: $enabled")

                    // 更新开关状态，但不触发监听器
                    binding.switchYoutubeMode.setOnCheckedChangeListener(null)
                    binding.switchYoutubeMode.isChecked = enabled

                    // 重新设置监听器
                    setYoutubeModeSwitchListener()

                    // 更新UI显示
                    updateYoutubeModeStatus(enabled)

                    Log.d(TAG, "已同步YouTube模式状态到MainActivity开关: $enabled")
                }
                TV_MODE_CHANGED_ACTION -> {
                    val enabled = intent.getBooleanExtra("enabled", false)
                    Log.d(TAG, "收到电视模式状态变化广播: $enabled")

                    // 更新开关状态，但不触发监听器
                    binding.switchTvMode.setOnCheckedChangeListener(null)
                    binding.switchTvMode.isChecked = enabled

                    // 重新设置监听器
                    setTvModeSwitchListener()

                    // 更新UI显示
                    updateTvModeStatus(enabled)

                    Log.d(TAG, "已同步电视模式状态到MainActivity开关: $enabled")
                }
                MAIN_TOGGLE_CHANGED_ACTION -> {
                    val enabled = intent.getBooleanExtra("enabled", false)
                    Log.d(TAG, "收到总开关状态变化广播: $enabled")

                    // 同步服务状态
                    isServiceRunning = enabled
                    updateServiceButton()
                    updateUI()

                    Log.d(TAG, "已同步总开关状态到MainActivity: $enabled")
                }
                BAIDU_MODE_CHANGED_ACTION -> {
                    val enabled = intent.getBooleanExtra("enabled", false)
                    Log.d(TAG, "收到百度网盘模式状态变化广播: $enabled")

                    // 更新开关状态，但不触发监听器
                    binding.switchBaiduMode.setOnCheckedChangeListener(null)
                    binding.switchBaiduMode.isChecked = enabled

                    // 重新设置监听器
                    setBaiduModeSwitchListener()

                    // 更新UI显示
                    updateBaiduModeStatus(enabled)

                    Log.d(TAG, "已同步百度网盘模式状态到MainActivity开关: $enabled")
                }
                TIKTOK_MODE_CHANGED_ACTION -> {
                    val enabled = intent.getBooleanExtra("enabled", false)
                    Log.d(TAG, "收到TikTok模式状态变化广播: $enabled")

                    // 更新开关状态，但不触发监听器
                    binding.switchTiktokMode.setOnCheckedChangeListener(null)
                    binding.switchTiktokMode.isChecked = enabled

                    // 重新设置监听器
                    setTiktokModeSwitchListener()

                    // 更新UI显示
                    updateTiktokModeStatus(enabled)

                    Log.d(TAG, "已同步TikTok模式状态到MainActivity开关: $enabled")
                }
            }
        }
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

        // 设置客制化像素保存按钮点击事件
        binding.btnSavePixels.setOnClickListener {
            Log.d(TAG, "点击保存像素值按钮")
            saveSwipePixels()
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

        // 初始化客制化像素值
        loadSwipePixels()

        // 设置开关监听器
        setYoutubeModeSwitchListener()
        setTvModeSwitchListener()
        setBaiduModeSwitchListener()
        setTiktokModeSwitchListener()
        
        // 注册广播接收器监听磁贴状态变化
        registerTileReceiver()
    }
    
    private fun setYoutubeModeSwitchListener() {
        binding.switchYoutubeMode.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "YouTube模式开关状态改变: $isChecked")

            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_YOUTUBE_MODE_ENABLED, isChecked)
                .apply()

            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setDoubleClickMappingEnabled(isChecked)

            // 更新UI显示
            updateYoutubeModeStatus(isChecked)

            // 发送广播通知磁贴更新状态
            val intent = Intent(YOUTUBE_MODE_CHANGED_ACTION)
            intent.putExtra("enabled", isChecked)
            sendBroadcast(intent)
            Log.d(TAG, "已发送YouTube模式状态变化广播给磁贴")

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
            
            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_TV_MODE_ENABLED, isChecked)
                .apply()
            
            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setTvModeEnabled(isChecked)
            
            // 更新UI显示
            updateTvModeStatus(isChecked)
            
            // 发送广播通知磁贴更新状态
            val intent = Intent(TV_MODE_CHANGED_ACTION)
            intent.putExtra("enabled", isChecked)
            sendBroadcast(intent)
            Log.d(TAG, "已发送电视模式状态变化广播")
            
            Toast.makeText(
                this, 
                if (isChecked) "电视模式已开启 - 使用16:9坐标" else "电视模式已关闭 - 使用正常坐标",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun registerTileReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(YOUTUBE_MODE_CHANGED_ACTION)
                    addAction(TV_MODE_CHANGED_ACTION)
                    addAction(BAIDU_MODE_CHANGED_ACTION)
                    addAction(TIKTOK_MODE_CHANGED_ACTION)
                    addAction(MAIN_TOGGLE_CHANGED_ACTION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(tileStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(tileStateReceiver, filter)
                }
                isReceiverRegistered = true
                Log.d(TAG, "状态广播接收器注册成功")
            } catch (e: Exception) {
                Log.e(TAG, "注册状态广播接收器失败: ${e.message}")
            }
        }
    }
    
    private fun unregisterTileReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(tileStateReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "磁贴状态广播接收器取消注册成功")
            } catch (e: Exception) {
                Log.e(TAG, "取消注册磁贴状态广播接收器失败: ${e.message}")
            }
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

            // 发送广播通知总开关磁贴更新状态
            val broadcastIntent = Intent(MAIN_TOGGLE_CHANGED_ACTION)
            broadcastIntent.putExtra("enabled", true)
            sendBroadcast(broadcastIntent)
            Log.d(TAG, "已发送总开关状态变化广播给磁贴")

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

        // 发送广播通知总开关磁贴更新状态
        val broadcastIntent = Intent(MAIN_TOGGLE_CHANGED_ACTION)
        broadcastIntent.putExtra("enabled", false)
        sendBroadcast(broadcastIntent)
        Log.d(TAG, "已发送总开关状态变化广播给磁贴")

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

            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_BAIDU_MODE_ENABLED, isChecked)
                .apply()

            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setBaiduModeEnabled(isChecked)

            // 更新UI显示
            updateBaiduModeStatus(isChecked)

            // 发送广播通知磁贴更新状态
            val intent = Intent(BAIDU_MODE_CHANGED_ACTION)
            intent.putExtra("enabled", isChecked)
            sendBroadcast(intent)
            Log.d(TAG, "已发送百度网盘模式状态变化广播")

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

            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_TIKTOK_MODE_ENABLED, isChecked)
                .apply()

            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setTiktokModeEnabled(isChecked)

            // 更新UI显示
            updateTiktokModeStatus(isChecked)

            // 发送广播通知磁贴更新状态
            val intent = Intent(TIKTOK_MODE_CHANGED_ACTION)
            intent.putExtra("enabled", isChecked)
            sendBroadcast(intent)
            Log.d(TAG, "已发送TikTok模式状态变化广播")

            // 当启用TikTok模式时，集成A_DTC功能
            if (isChecked) {
                integrateDTCFunctionality()
            }

            Toast.makeText(
                this,
                if (isChecked) "TikTok/抖音模式已开启" else "TikTok/抖音模式已关闭",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateTiktokModeStatus(enabled: Boolean) {
        binding.tvTiktokModeStatus.text = if (enabled) {
            "TikTok/抖音模式已开启 - 左右键上下视频，OK键播放/暂停，支持客制化像素滑动"
        } else {
            "TikTok/抖音模式已关闭"
        }

        binding.tvTiktokModeStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }

    private fun integrateDTCFunctionality() {
        // A_DTC项目功能已集成
        Log.d(TAG, "A_DTC项目功能已集成到TikTok模式中")
    }

    private fun saveSwipePixels() {
        val pixelsText = binding.etSwipePixels.text.toString()
        if (pixelsText.isNotEmpty()) {
            try {
                val pixels = pixelsText.toInt()
                if (pixels > 0) {
                    // 保存到两个地方：主应用的SharedPreferences和TikTok专用的SharedPreferences
                    sharedPreferences.edit().putInt("swipe_pixels", pixels).apply()

                    val tiktokPrefs = getSharedPreferences("TikTokRemoteControl", Context.MODE_PRIVATE)
                    tiktokPrefs.edit().putInt("swipe_pixels", pixels).apply()

                    Toast.makeText(this, "滑动像素值已保存: ${pixels}px", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "滑动像素值已保存: ${pixels}px")
                } else {
                    Toast.makeText(this, "请输入大于0的数值", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "请输入像素值", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSwipePixels() {
        val savedPixels = sharedPreferences.getInt("swipe_pixels", 100)
        binding.etSwipePixels.setText(savedPixels.toString())
        Log.d(TAG, "加载滑动像素值: ${savedPixels}px")
    }
    
    override fun onResume() {
        super.onResume()
        
        // 检查服务运行状态
        checkServiceRunningState()
        
        updateUI()
        
        // 同步SharedPreferences中的状态到开关，防止磁贴修改后不同步
        val currentState = sharedPreferences.getBoolean(PREF_YOUTUBE_MODE_ENABLED, true)
        if (binding.switchYoutubeMode.isChecked != currentState) {
            Log.d(TAG, "onResume检测到YouTube模式状态不同步，更新开关状态: $currentState")
            binding.switchYoutubeMode.setOnCheckedChangeListener(null)
            binding.switchYoutubeMode.isChecked = currentState
            setYoutubeModeSwitchListener()
            updateYoutubeModeStatus(currentState)
        }
        
        // 同步电视模式状态
        val currentTvModeState = sharedPreferences.getBoolean(PREF_TV_MODE_ENABLED, false)
        if (binding.switchTvMode.isChecked != currentTvModeState) {
            Log.d(TAG, "onResume检测到电视模式状态不同步，更新开关状态: $currentTvModeState")
            binding.switchTvMode.setOnCheckedChangeListener(null)
            binding.switchTvMode.isChecked = currentTvModeState
            setTvModeSwitchListener()
            updateTvModeStatus(currentTvModeState)
        }

        // 同步百度网盘模式状态
        val currentBaiduModeState = sharedPreferences.getBoolean(PREF_BAIDU_MODE_ENABLED, false)
        if (binding.switchBaiduMode.isChecked != currentBaiduModeState) {
            Log.d(TAG, "onResume检测到百度网盘模式状态不同步，更新开关状态: $currentBaiduModeState")
            binding.switchBaiduMode.setOnCheckedChangeListener(null)
            binding.switchBaiduMode.isChecked = currentBaiduModeState
            setBaiduModeSwitchListener()
            updateBaiduModeStatus(currentBaiduModeState)
        }

        // 同步TikTok模式状态
        val currentTiktokModeState = sharedPreferences.getBoolean(PREF_TIKTOK_MODE_ENABLED, false)
        if (binding.switchTiktokMode.isChecked != currentTiktokModeState) {
            Log.d(TAG, "onResume检测到TikTok模式状态不同步，更新开关状态: $currentTiktokModeState")
            binding.switchTiktokMode.setOnCheckedChangeListener(null)
            binding.switchTiktokMode.isChecked = currentTiktokModeState
            setTiktokModeSwitchListener()
            updateTiktokModeStatus(currentTiktokModeState)
        }
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
        }
        // 取消注册广播接收器
        unregisterTileReceiver()
    }
}