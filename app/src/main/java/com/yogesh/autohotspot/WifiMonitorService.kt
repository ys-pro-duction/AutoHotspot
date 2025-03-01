@file:Suppress("DEPRECATION")

package com.yogesh.autohotspot

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.android.dx.stock.ProxyBuilder
import java.io.File
import java.lang.reflect.Method


class WifiMonitorService : Service() {
    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("WifiMonitorService", "✅ WiFi Connected!")
            try {
                startTethering3(object : MyOnStartTetheringCallback {
                    override fun onTetheringStarted() {
                        Log.d(TAG, "onTetheringStarted: ")
                    }

                    override fun onTetheringFailed() {
                        Log.d(TAG, "onTetheringFailed: ")
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onLost(network: Network) {
            Log.d("WifiMonitorService", "❌ WiFi Disconnected!")
            stopTethering2(baseContext)
        }

    }

    @SuppressLint("PrivateApi")
    private fun OnStartTetheringCallbackClass(): Class<*>? {
        try {
            return Class.forName("android.net.ConnectivityManager\$OnStartTetheringCallback")
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "OnStartTetheringCallbackClass error: $e")
            e.printStackTrace()
        }
        return null
    }

    fun startTethering3(callback: MyOnStartTetheringCallback): Boolean {

//        if (isTetherActive()) {
//            Log.d(TAG, "Tether already active, returning")
//            return false
//        }

        val outputDir: File = baseContext.codeCacheDir
        val proxy: Any
        try {
            proxy = ProxyBuilder.forClass(OnStartTetheringCallbackClass())
                .dexCache(outputDir).handler { proxy, method, args ->
                    when (method.name) {
                        "onTetheringStarted" -> callback.onTetheringStarted()
                        "onTetheringFailed" -> callback.onTetheringFailed()
                        else -> ProxyBuilder.callSuper(proxy, method, *args)
                    }
                    null
                }.build()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error in enableTethering ProxyBuilder")
            e.printStackTrace()
            return false
        }

        try {
            val method: Method? = connectivityManager.javaClass.getDeclaredMethod(
                "startTethering",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, OnStartTetheringCallbackClass(),
                Handler::class.java
            )
            if (method == null) {
                Log.e(TAG, "startTetheringMethod is null")
            } else {
                method.invoke(
                    connectivityManager,
                    ConnectivityManager.TYPE_MOBILE,
                    false,
                    proxy,
                    null
                )
                Log.d(TAG, "startTethering invoked")
            }
            return true
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error in enableTethering")
            e.printStackTrace()
        }
        return false
    }

    interface MyOnStartTetheringCallback {
        fun onTetheringStarted()
        fun onTetheringFailed()
    }


    fun stopTethering2(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val method =
                cm.javaClass.getDeclaredMethod("stopTethering", Int::class.javaPrimitiveType)
            Log.d("HotspotControl", "Invoking stopTethering...")

            for (i in 0..10) {
                try {
                    method.invoke(cm, i)
                    Log.d("HotspotControl", "stopTethering($i) called successfully.")
                } catch (e: Exception) {
                    Log.e("HotspotControl", "Error stopping tethering for type $i: ${e.message}")
                }
            }
        } catch (e: NoSuchMethodException) {
            Log.e("HotspotControl", "stopTethering method not found!")
        } catch (e: Exception) {
            Log.e("HotspotControl", "Error invoking stopTethering: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = android.net.NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        Log.d("WifiMonitorService", "Service Stopped")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            isRunning = false
            listener?.onWifiMonitor(isRunning)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "wifi_monitor_channel"
        val channel = NotificationChannel(
            channelId,
            "WiFi Monitor Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        val stopIntent = Intent(this, WifiMonitorService::class.java)
        stopIntent.action = "STOP_SERVICE"
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("WiFi Monitor Running")
            .setContentText("Listening for WiFi connection changes...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        baseContext,
                        android.R.drawable.ic_menu_close_clear_cancel
                    ), "Stop", stopPendingIntent
                ).build()
            )
            .build()

        startForeground(1, notification)
        isRunning = true
        listener?.onWifiMonitor(isRunning)
    }
    interface WifiMonitorServiceListener {
        fun onWifiMonitor(status: Boolean)
    }

    companion object {
        var isRunning = false
        var listener: WifiMonitorServiceListener? = null
        private const val TAG = "WifiMonitorService"
    }
}
