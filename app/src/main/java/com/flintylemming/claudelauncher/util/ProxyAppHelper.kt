package com.flintylemming.claudelauncher.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast

data class ProxyAppInfo(
    val packageName: String,
    val appName: String,
    val isInstalled: Boolean
)

object ProxyAppHelper {

    val KNOWN_PROXY_APPS = listOf(
        "com.github.kr328.clash" to "Clash for Android",
        "com.github.meta.clash" to "Clash Meta",
        "io.github.clashverge" to "Clash Verge",
        "com.github.flclash" to "FlClash",
        "com.v2ray.ang" to "v2rayNG",
        "io.nekohasekai.sfa" to "Sing-box",
        "moe.nb4a" to "NekoBox",
        "com.getsurfboard" to "Surfboard",
        "moe.matsuri.lite" to "Matsuri"
    )

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getInstalledProxyApps(context: Context): List<ProxyAppInfo> {
        val result = mutableListOf<ProxyAppInfo>()
        val pm = context.packageManager
        for ((pkg, defaultName) in KNOWN_PROXY_APPS) {
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
                val label = pm.getApplicationLabel(appInfo).toString()
                result.add(ProxyAppInfo(pkg, label.ifEmpty { defaultName }, true))
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }
        return result
    }

    fun launchProxyApp(context: Context, preferredPackage: String? = null): Boolean {
        val pm = context.packageManager

        // 1. Try preferred package if set and valid
        if (!preferredPackage.isNullOrBlank()) {
            val intent = pm.getLaunchIntentForPackage(preferredPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }

        // 2. Scan known proxy apps
        for ((pkg, _) in KNOWN_PROXY_APPS) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }

        // 3. Fallback to system VPN settings
        return try {
            val vpnIntent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(vpnIntent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "未找到已安装的代理软件或无法打开 VPN 设置", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
