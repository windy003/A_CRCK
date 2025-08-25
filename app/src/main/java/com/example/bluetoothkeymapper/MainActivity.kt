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
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "KeyMapperPrefs"
        private const val PREF_DOUBLE_CLICK_ENABLED = "double_click_mapping_enabled"
        private const val YOUTUBE_MODE_CHANGED_ACTION = "com.example.bluetoothkeymapper.YOUTUBE_MODE_CHANGED"
    }
    
    // 广播接收器监听磁贴状态变化
    private val tileStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == YOUTUBE_MODE_CHANGED_ACTION) {
                val enabled = intent.getBooleanExtra("enabled", true)
                Log.d(TAG, "收到磁贴状态变化广播: $enabled")
                
                // 更新开关状态，但不触发监听器
                binding.switchDoubleClickMapping.setOnCheckedChangeListener(null)
                binding.switchDoubleClickMapping.isChecked = enabled
                
                // 重新设置监听器
                setSwitchListener()
                
                // 更新UI显示
                updateMappingStatus(enabled)
                
                Log.d(TAG, "已同步磁贴状态到MainActivity开关: $enabled")
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
    }
    
    private fun setupUI() {
        Log.d(TAG, "设置UI")
        
        binding.btnStartService.setOnClickListener {
            Log.d(TAG, "点击启动服务按钮, serviceBound: $serviceBound")
            startBluetoothService()
        }
        
        binding.btnStopService.setOnClickListener {
            Log.d(TAG, "点击停止服务按钮")
            stopBluetoothService()
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
        
        // 设置双击映射开关
        val isDoubleClickEnabled = sharedPreferences.getBoolean(PREF_DOUBLE_CLICK_ENABLED, true)
        binding.switchDoubleClickMapping.isChecked = isDoubleClickEnabled
        updateMappingStatus(isDoubleClickEnabled)
        
        // 设置开关监听器
        setSwitchListener()
        
        // 注册广播接收器监听磁贴状态变化
        registerTileReceiver()
    }
    
    private fun setSwitchListener() {
        binding.switchDoubleClickMapping.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "双击映射开关状态改变: $isChecked")
            
            // 保存状态
            sharedPreferences.edit()
                .putBoolean(PREF_DOUBLE_CLICK_ENABLED, isChecked)
                .apply()
            
            // 更新无障碍服务状态
            KeyMapperAccessibilityService.instance?.setDoubleClickMappingEnabled(isChecked)
            
            // 更新UI显示
            updateMappingStatus(isChecked)
            
            // 发送广播通知磁贴更新状态
            val intent = Intent(YOUTUBE_MODE_CHANGED_ACTION)
            intent.putExtra("enabled", isChecked)
            sendBroadcast(intent)
            Log.d(TAG, "已发送双击映射状态变化广播给磁贴")
            
            Toast.makeText(
                this, 
                if (isChecked) "双击映射功能已开启" else "双击映射功能已关闭",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun registerTileReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(YOUTUBE_MODE_CHANGED_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(tileStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(tileStateReceiver, filter)
                }
                isReceiverRegistered = true
                Log.d(TAG, "磁贴状态广播接收器注册成功")
            } catch (e: Exception) {
                Log.e(TAG, "注册磁贴状态广播接收器失败: ${e.message}")
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
            
            Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show()
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "启动服务失败", e)
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopBluetoothService() {
        val intent = Intent(this, BluetoothKeyService::class.java)
        stopService(intent)
        
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
        updateUI()
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
        Log.d(TAG, "启动服务按钮状态: $canStartService")
        
        binding.btnStartService.isEnabled = canStartService
        binding.btnEnableBluetooth.isEnabled = !bluetoothEnabled && permissionsGranted
        binding.btnPermissions.isEnabled = !permissionsGranted
        
        // 同步双击映射状态
        val isDoubleClickEnabled = sharedPreferences.getBoolean(PREF_DOUBLE_CLICK_ENABLED, true)
        updateMappingStatus(isDoubleClickEnabled)
    }
    
    private fun updateMappingStatus(enabled: Boolean) {
        binding.tvMappingStatus.text = if (enabled) {
            "状态: 双击映射已开启"
        } else {
            "状态: 双击映射已关闭"
        }
        
        binding.tvMappingStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
        
        // 同步SharedPreferences中的状态到开关，防止磁贴修改后不同步
        val currentState = sharedPreferences.getBoolean(PREF_DOUBLE_CLICK_ENABLED, true)
        if (binding.switchDoubleClickMapping.isChecked != currentState) {
            Log.d(TAG, "onResume检测到状态不同步，更新开关状态: $currentState")
            binding.switchDoubleClickMapping.setOnCheckedChangeListener(null)
            binding.switchDoubleClickMapping.isChecked = currentState
            setSwitchListener()
            updateMappingStatus(currentState)
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