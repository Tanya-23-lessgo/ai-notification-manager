package com.example.contextawarenotify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MyNotificationListener : NotificationListenerService() {

    private val TAG = "AI_NOTIFY"
    private lateinit var modelHandler: ModelHandler
    private lateinit var prefs: android.content.SharedPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        modelHandler = ModelHandler(this)
        prefs = getSharedPreferences("PriorityPrefs", Context.MODE_PRIVATE)
        createUrgentChannel()

        // Start foreground app tracking
        ForegroundTracker.startTracking(this)

        Log.i(TAG, "Notification Listener Started and Tracking initialized.")
    }

    private fun createUrgentChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Priority Notifications"
            val channel = NotificationChannel("URGENT_CHANNEL", name, NotificationManager.IMPORTANCE_HIGH).apply {
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isOtp(text: String): Boolean {
        val otpRegex = Regex("\\b\\d{4,8}\\b") // Detects 4 to 8 digit numbers
        val keywords = listOf("otp", "verification", "code", "login", "password", "secret")
        val lowerText = text.lowercase()
        return otpRegex.containsMatchIn(text) && keywords.any { lowerText.contains(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Prevent loops
        if (sbn.packageName == packageName || sbn.notification.channelId == "URGENT_CHANNEL") return

        val notification = sbn.notification
        
        // Safety: Always allow Phone Calls
        if (notification.category == Notification.CATEGORY_CALL || sbn.packageName.contains("telecom") || sbn.packageName.contains("dialer")) {
            return
        }

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "No Title"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        val fullContent = "$title $text".trim()
        if (fullContent.isEmpty()) return

        serviceScope.launch {
            val prediction = modelHandler.predict(fullContent)
            val foregroundApp = ForegroundTracker.currentApp
            val priorityApps = prefs.getStringSet("apps", setOf()) ?: setOf()

            // START HARDCODED DEMO OVERRIDE
            var isPriorityMode = foregroundApp != null && priorityApps.contains(foregroundApp)
            val hardcodedPriorityApps = listOf("com.android.settings", "com.google.android.gm")
            if (foregroundApp != null && hardcodedPriorityApps.contains(foregroundApp) && priorityApps.contains(foregroundApp)) {
                isPriorityMode = true
                Log.d(TAG, "│ DEBUG: DEMO OVERRIDE ACTIVE: Forcing PriorityMode=ON for $foregroundApp")
            }
            // END HARDCODED DEMO OVERRIDE

            val isUrgent = prediction.contains("URGENT", ignoreCase = true) || isOtp(fullContent)
            val isSpamOrPromo = prediction.contains("Spam", ignoreCase = true) || prediction.contains("Promotion", ignoreCase = true)

            Log.i(TAG, " ")
            Log.i(TAG, "┌────────── INCOMING: ${sbn.packageName} ──────────")
            Log.i(TAG, "│ AI CLASSIFICATION: ${if(isOtp(fullContent)) "FORCE URGENT (OTP)" else prediction}")
            Log.i(TAG, "│ TRACKED FOREGROUND: $foregroundApp")
            Log.i(TAG, "│ CONTEXT: PriorityMode=${if(isPriorityMode) "ON (STRICT)" else "OFF (Normal)"}")
            Log.i(TAG, "├──────────────────────────────────────────")

            when {
                // 1. URGENT / OTP: Always allowed and always pops up
                isUrgent -> {
                    Log.i(TAG, "│ [DECISION] -> ALLOWED (Urgent/OTP Alert)")
                    showUrgentPopup(title, text)
                }
                
                // 2. STRICT MODE: If in Priority App, suppress everything else (Personal, System, Spam)
                isPriorityMode -> {
                    Log.w(TAG, "│ [DECISION] -> SUPPRESSED (Strict Mode enforced)")
                    cancelNotification(sbn.key)
                }
                
                // 3. NORMAL MODE: Suppress only Spam and Promotion
                isSpamOrPromo -> {
                    Log.w(TAG, "│ [DECISION] -> SUPPRESSED (Filter: Spam/Promo)")
                    cancelNotification(sbn.key)
                }
                
                // 4. NORMAL MODE: Personal (2) and System (4) are allowed to stay in the bar
                else -> {
                    Log.i(TAG, "│ [DECISION] -> PASSED (Normal notification allowed)")
                }
            }
            Log.i(TAG, "└──────────────────────────────────────────")
        }
    }

    private fun showUrgentPopup(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, "URGENT_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 URGENT: $title")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        ForegroundTracker.stopTracking()
        modelHandler.close()
    }
}
