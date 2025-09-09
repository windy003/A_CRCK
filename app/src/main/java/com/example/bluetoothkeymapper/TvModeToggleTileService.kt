package com.example.bluetoothkeymapper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class TvModeToggleTileService : TileService() {
    
    companion object {
        private const val TAG = "TvModeToggleTile"
        private const val PREFS_NAME = "KeyMapperPrefs"
        private const val PREF_TV_MODE_ENABLED = "tv_mode_enabled"
        private const val TV_MODE_CHANGED_ACTION = "com.example.bluetoothkeymapper.TV_MODE_CHANGED"
    }
    
    override fun onTileAdded() {
        super.onTileAdded()
        Log.e(TAG, "20.5:9模式磁贴已添加到快速设置")
        updateTileState()
    }
    
    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.e(TAG, "20.5:9模式磁贴已从快速设置移除")
    }
    
    private var isReceiverRegistered = false
    private val tvModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TV_MODE_CHANGED_ACTION) {
                Log.d(TAG, "收到20.5:9模式状态变化广播")
                updateTileState()
            }
        }
    }
    
    override fun onStartListening() {
        super.onStartListening()
        Log.e(TAG, "20.5:9模式磁贴开始监听")
        
        // 注册广播接收器
        try {
            if (!isReceiverRegistered) {
                val filter = IntentFilter(TV_MODE_CHANGED_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(tvModeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(tvModeReceiver, filter)
                }
                isReceiverRegistered = true
                Log.d(TAG, "广播接收器注册成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "注册广播接收器失败: ${e.message}")
        }
        
        updateTileState()
    }
    
    override fun onStopListening() {
        super.onStopListening()
        Log.e(TAG, "20.5:9模式磁贴停止监听")
        
        try {
            if (isReceiverRegistered) {
                unregisterReceiver(tvModeReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "广播接收器取消注册成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "取消注册广播接收器失败: ${e.message}")
        }
    }
    
    override fun onClick() {
        super.onClick()
        Log.e(TAG, "!!! 20.5:9模式磁贴被点击 !!!")
        
        try {
            // 获取当前状态
            val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentState = sharedPreferences.getBoolean(PREF_TV_MODE_ENABLED, false)
            val newState = !currentState
            
            Log.e(TAG, "当前状态: $currentState, 新状态: $newState")
            
            // 保存新状态
            val editor = sharedPreferences.edit()
            editor.putBoolean(PREF_TV_MODE_ENABLED, newState)
            val saveResult = editor.commit() // 使用commit()确保立即保存
            
            Log.e(TAG, "状态保存结果: $saveResult")
            
            // 通知无障碍服务状态变更
            val accessibilityService = KeyMapperAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.setTvModeEnabled(newState)
                Log.e(TAG, "已通知无障碍服务20.5:9模式状态变更")
            } else {
                Log.w(TAG, "无障碍服务实例为空，无法通知状态变更")
            }
            
            // 立即更新磁贴显示
            updateTileState()
            
            Log.e(TAG, "20.5:9模式已${if (newState) "开启" else "关闭"}")
            
            // 发送广播确保状态同步到MainActivity
            val intent = Intent(TV_MODE_CHANGED_ACTION)
            intent.putExtra("enabled", newState)
            intent.setPackage(packageName) // 确保广播只发送给自己的应用
            sendBroadcast(intent)
            Log.e(TAG, "已发送状态变化广播到MainActivity: $newState")
            
        } catch (e: Exception) {
            Log.e(TAG, "处理点击事件失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun updateTileState() {
        try {
            val tile = qsTile
            if (tile == null) {
                Log.e(TAG, "磁贴对象为空，无法更新状态")
                return
            }
            
            // 获取当前状态
            val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = sharedPreferences.getBoolean(PREF_TV_MODE_ENABLED, false)
            
            Log.e(TAG, "更新磁贴状态 - 当前20.5:9模式: ${if (isEnabled) "开启" else "关闭"}")
            
            // 更新磁贴状态
            tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "20.5:9模式"
            
            // 根据API级别设置副标题
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isEnabled) "电视模式已开启" else "电视模式已关闭"
            }
            
            // 设置图标 - 使用数字2
            tile.icon = createNumberTwoIcon()
            
            // 更新磁贴显示
            tile.updateTile()
            
            Log.e(TAG, "磁贴状态更新完成: 状态=${tile.state}, 标签=${tile.label}")
            
        } catch (e: Exception) {
            Log.e(TAG, "更新磁贴状态失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun createNumberTwoIcon(): Icon {
        val size = 64 // 图标大小
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 设置画笔
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        // 在画布中央绘制数字2
        val centerX = size / 2f
        val centerY = size / 2f + paint.textSize / 3f // 稍微向下偏移以居中
        canvas.drawText("2", centerX, centerY, paint)
        
        return Icon.createWithBitmap(bitmap)
    }
}