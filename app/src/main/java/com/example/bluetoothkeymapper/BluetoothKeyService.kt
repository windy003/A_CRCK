package com.example.bluetoothkeymapper

import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class BluetoothKeyService : Service() {
    
    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var audioManager: AudioManager? = null
    private var isServiceStarted = false
    
    companion object {
        private const val TAG = "BluetoothKeyService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "BluetoothKeyService"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothKeyService = this@BluetoothKeyService
    }
    
    override fun onCreate() {
        super.onCreate()
        initializeService()
        createNotificationChannel()
    }
    
    private fun initializeService() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isServiceStarted) {
            startForegroundService()
            isServiceStarted = true
        }
        return START_STICKY
    }
    
    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("蓝牙按键映射器")
            .setContentText("服务正在运行")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "蓝牙按键映射服务",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }
    
    fun startBluetoothScanning() {
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "蓝牙未启用")
            return
        }
        
        // 检查蓝牙权限
        val hasBluetoothPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasBluetoothPermission) {
            Log.e(TAG, "缺少蓝牙权限")
            return
        }
        
        Log.d(TAG, "开始扫描蓝牙设备")
        val pairedDevices = bluetoothAdapter?.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            Log.d(TAG, "没有配对的蓝牙设备")
            return
        }
        
        pairedDevices.forEach { device ->
            Log.d(TAG, "已配对设备: ${device.name} - ${device.address}")
            // 对于按键映射，我们不需要GATT连接，只需要监听系统按键事件
        }
        
        Log.d(TAG, "蓝牙服务启动成功，等待按键事件")
    }
    
    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        bluetoothGatt = device.connectGatt(this, true, gattCallback)
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "已连接到GATT服务器")
                    if (ActivityCompat.checkSelfPermission(
                            this@BluetoothKeyService,
                            android.Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "从GATT服务器断开连接")
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "发现服务")
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.value?.let { data ->
                handleKeyEvent(data)
            }
        }
    }
    
    private fun handleKeyEvent(data: ByteArray) {
        if (data.isNotEmpty()) {
            val keyCode = data[0].toInt()
            Log.d(TAG, "接收到按键代码: $keyCode")
            
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                sendMediaPlayPauseCommand()
            }
        }
    }
    
    private fun sendMediaPlayPauseCommand() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager?.dispatchMediaKeyEvent(keyEvent)
        
        val keyEventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager?.dispatchMediaKeyEvent(keyEventUp)
        
        Log.d(TAG, "发送媒体播放/暂停命令")
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        isServiceStarted = false
    }
}