package com.flintylemming.claudelauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        const val KEY_DETECTION_MODE = "pref_detection_mode"
        const val KEY_BLOCKED_REGIONS = "pref_blocked_regions"
        const val KEY_TIMEOUT_SECONDS = "pref_timeout_seconds"
        const val KEY_TIMEOUT_SECONDS_LIST = "pref_timeout_seconds_list"
        const val KEY_PREFERRED_PROXY = "pref_preferred_proxy"
        const val KEY_TRACE_URL = "pref_trace_url"

        const val MODE_VPN = "vpn"
        const val MODE_CLAUDE_TRACE = "claude_trace"
        const val DEFAULT_DETECTION_MODE = MODE_CLAUDE_TRACE

        const val DEFAULT_BLOCKED_REGIONS = "CN, HK"
        const val DEFAULT_TIMEOUT_SECONDS = 3
        const val DEFAULT_TRACE_URL = "https://claude.ai/cdn-cgi/trace"
    }

    var detectionMode: String
        get() = prefs.getString(KEY_DETECTION_MODE, DEFAULT_DETECTION_MODE) ?: DEFAULT_DETECTION_MODE
        set(value) = prefs.edit().putString(KEY_DETECTION_MODE, value).apply()

    val isVpnOnlyMode: Boolean
        get() = detectionMode == MODE_VPN

    var blockedRegionsString: String
        get() = prefs.getString(KEY_BLOCKED_REGIONS, DEFAULT_BLOCKED_REGIONS) ?: DEFAULT_BLOCKED_REGIONS
        set(value) = prefs.edit().putString(KEY_BLOCKED_REGIONS, value).apply()

    val blockedRegions: Set<String>
        get() = blockedRegionsString
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    var timeoutSeconds: Int
        get() {
            val listVal = prefs.getString(KEY_TIMEOUT_SECONDS_LIST, null)?.toIntOrNull()
            if (listVal != null && listVal in 1..15) return listVal
            val v = prefs.getInt(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
            return if (v in 1..15) v else DEFAULT_TIMEOUT_SECONDS
        }
        set(value) {
            prefs.edit()
                .putInt(KEY_TIMEOUT_SECONDS, value)
                .putString(KEY_TIMEOUT_SECONDS_LIST, value.toString())
                .apply()
        }

    var preferredProxyPackage: String?
        get() = prefs.getString(KEY_PREFERRED_PROXY, null)
        set(value) = prefs.edit().putString(KEY_PREFERRED_PROXY, value).apply()

    var traceUrl: String
        get() = prefs.getString(KEY_TRACE_URL, DEFAULT_TRACE_URL) ?: DEFAULT_TRACE_URL
        set(value) = prefs.edit().putString(KEY_TRACE_URL, value).apply()
}
