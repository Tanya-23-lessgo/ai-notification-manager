package com.example.contextawarenotify

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.*

object ForegroundTracker {

    private const val TAG = "FOREGROUND_TRACKER"

    var currentApp: String? = null
        private set

    private var trackerScope: CoroutineScope? = null

    fun startTracking(context: Context) {

        if (trackerScope != null) {
            Log.d(TAG, "Tracker already running.")
            return
        }

        // Explicit Usage Access Permission Check before starting
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }

        if (mode != AppOpsManager.MODE_ALLOWED) {
            Log.e(TAG, "CRITICAL ERROR: Usage Access Permission is NOT granted! Foreground tracking will not start. Please grant it in Settings.")
            return 
        } else {
            Log.i(TAG, "Usage Access Permission is GRANTED. Starting foreground tracker.")
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        trackerScope = CoroutineScope(Dispatchers.Default)

        trackerScope!!.launch {

            var lastTimestamp = System.currentTimeMillis() - 1000 * 60 // Look back 1 minute initially

            while (isActive) {

                val now = System.currentTimeMillis()
                Log.d(TAG, "Querying events for ${context.packageName} from ${now - 20000} to $now") // Debugging query window

                // Query with a 20-second overlap to aggressively catch all events
                val events = usm.queryEvents(now - 20000, now)

                val eventsList = mutableListOf<UsageEvents.Event>()
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    eventsList.add(event)
                }
                Log.d(TAG, "Events received: ${eventsList.size}") // Crucial: how many events did we get?

                var appDetectedFromEvents: String? = null

                // Process events
                for (e in eventsList) {
                    // LOG EVERY RAW EVENT (Verbose level for deep debugging)
                    Log.v(TAG, "RAW EVENT: pkg=${e.packageName} | type=${e.eventType} | time=${e.timeStamp}")

                    @Suppress("DEPRECATION")
                    if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {

                        val pkg = e.packageName

                        // Filter out launcher, system UI, and our own app
                        if (!pkg.contains("launcher", ignoreCase = true) &&
                            !pkg.contains("systemui", ignoreCase = true) &&
                            pkg != context.packageName
                        ) {
                            appDetectedFromEvents = pkg // Found a valid app from events
                            break // Exit loop once a valid app is found
                        } else {
                            Log.d(TAG, "Ignoring System/Launcher/Self Event: $pkg")
                        }
                    }
                }

                if (appDetectedFromEvents != null) {
                    if (currentApp != appDetectedFromEvents) { // Only update and log if the foreground app actually changed
                        currentApp = appDetectedFromEvents
                        Log.i(TAG, "★★★ FOREGROUND APP UPDATED TO: $currentApp (from MOVE_TO_FOREGROUND events) ★★★")
                    }
                } else { // If no app found from events, try fallback
                    val appFromFallback = fallbackForeground(context)
                    if (appFromFallback != null) {
                        if (currentApp != appFromFallback) {
                            currentApp = appFromFallback
                            Log.d(TAG, "Fallback detected current app: $currentApp")
                        }
                    } else if (eventsList.isEmpty()) {
                        Log.e(TAG, "COULD NOT DETECT FOREGROUND APP - no MOVE_TO_FOREGROUND events, and fallback was also null.")
                    }
                }

                // Log current tracked app for continuous monitoring (1s heartbeat)
                Log.d(TAG, "Current tracked app (1s heartbeat): $currentApp")

                lastTimestamp = now
                delay(1000) // Check every second
            }
        }
    }

    fun stopTracking() {
        Log.d(TAG, "Stopping ForegroundTracker.")
        trackerScope?.cancel()
        trackerScope = null
        currentApp = null // Clear when stopped
    }

    private fun fallbackForeground(context: Context): String? {

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()

        // Query usage stats for the last 5 minutes as a more robust fallback
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 60 * 5,
            time
        )

        if (stats.isNullOrEmpty()) {
            Log.v(TAG, "No usage stats found for fallback. Check Usage Access Permission.")
            return null
        }

        val lastApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        
        // Filter out launcher and system UI from fallback too
        return if (lastApp?.contains("launcher", ignoreCase = true) == true || 
                    lastApp?.contains("systemui", ignoreCase = true) == true) {
            null
        } else {
            lastApp
        }
    }
}
