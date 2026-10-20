package com.example.service.sos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R

private const val TAG = "SmsBridgeReceiver"
private const val CHANNEL_ID = "vespera_sms_bridge_channel"

/**
 * BroadcastReceiver nhận tin nhắn SMS dự phòng từ thiết bị người thân khi mất mạng / ngoài 50m,
 * chuyển hóa SMS thành tin nhắn chat hoặc cảnh báo SOS hiển thị trực tiếp vào giao diện app.
 */
class SmsBridgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (sms in messages) {
                    val senderNumber = sms.originatingAddress ?: continue
                    val messageBody = sms.messageBody ?: continue

                    Log.d(TAG, "Nhận SMS từ $senderNumber: $messageBody")

                    if (messageBody.contains("[VESPERA_CHAT]") || messageBody.contains("[SOS]") || messageBody.contains("GIA ĐÌNH VIỆT")) {
                        showSmsBridgeNotification(context, senderNumber, messageBody)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi xử lý SMS đến: ${e.message}", e)
            }
        }
    }

    private fun showSmsBridgeNotification(context: Context, sender: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isSos = body.contains("[SOS]") || body.contains("SOS")

        // Kích hoạt rung khẩn cấp
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = if (isSos) longArrayOf(0, 500, 200, 500, 200, 800) else longArrayOf(0, 300, 200, 300)
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(1000)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Vibration trigger: ${e.message}")
        }

        val soundUri = if (isSos) {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        } else {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(if (isSos) android.media.AudioAttributes.USAGE_ALARM else android.media.AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cầu nối SMS & Khẩn cấp Vespera",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nhận thông điệp chat và SOS qua SMS dự phòng khi mất kết nối"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = if (isSos) "🚨 BÁO ĐỘNG SOS KHẨN CẤP QUA SMS!" else "💬 Tin nhắn cầu nối SMS từ gia đình"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 800))
            .setAutoCancel(true)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
