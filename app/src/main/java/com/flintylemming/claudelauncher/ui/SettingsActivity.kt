package com.flintylemming.claudelauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.flintylemming.claudelauncher.R
import com.flintylemming.claudelauncher.data.AppPreferences
import com.flintylemming.claudelauncher.databinding.ActivitySettingsBinding
import com.flintylemming.claudelauncher.net.ClaudeTraceChecker
import com.flintylemming.claudelauncher.util.ProxyAppHelper
import com.flintylemming.claudelauncher.util.VpnChecker
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    internal lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle Material 3 Edge-to-Edge status bar insets on AppBarLayout
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBar.top)
            insets
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val checker = ClaudeTraceChecker()

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            listView.clipToPadding = false
            ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
                val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                v.updatePadding(bottom = navBar.bottom)
                insets
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            tintPreferenceIcons(preferenceScreen)

            setupHeroHeader()
            setupDetectionModePreference()
            setupProxyPreference()
            setupTestConnectionPreference()
            setupAboutPreference()
        }

        private fun tintPreferenceIcons(group: PreferenceGroup) {
            val tintColor = MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(requireContext(), R.color.m3_light_onSurfaceVariant)
            )
            for (i in 0 until group.preferenceCount) {
                val pref = group.getPreference(i)
                if (pref is PreferenceGroup) {
                    tintPreferenceIcons(pref)
                } else {
                    pref.icon?.let { icon ->
                        val wrapped = DrawableCompat.wrap(icon.mutate())
                        DrawableCompat.setTint(wrapped, tintColor)
                        pref.icon = wrapped
                    }
                }
            }
        }

        private fun setupHeroHeader() {
            val heroPref = findPreference<HeroPreference>("pref_hero_header") ?: return
            val context = requireContext()
            val prefs = AppPreferences(context)

            fun renderHero(
                tvResult: TextView,
                progressBar: LinearProgressIndicator,
                statusDot: ImageView,
                statusLabel: TextView,
                btnTest: MaterialButton
            ) {
                progressBar.visibility = View.GONE
                btnTest.isEnabled = true
                if (prefs.isVpnOnlyMode) {
                    val isVpn = VpnChecker.isVpnActive(context)
                    btnTest.text = "测试"
                    if (isVpn) {
                        statusDot.setColorFilter(ContextCompat.getColor(context, R.color.status_green))
                        statusLabel.text = "VPN 已连接 (极速直连)"
                        tvResult.text = "模式: 仅检查 VPN · ✅ VPN 连接已激活，无感闪启"
                    } else {
                        statusDot.setColorFilter(ContextCompat.getColor(context, R.color.status_amber))
                        statusLabel.text = "VPN 未开启 (将拦截启动)"
                        tvResult.text = "模式: 仅检查 VPN · ⚠️ 未检测到 VPN 连接"
                    }
                } else {
                    btnTest.text = "测速"
                    statusDot.setColorFilter(ContextCompat.getColor(context, R.color.status_green))
                    statusLabel.text = "启动防护已就绪 (${prefs.blockedRegionsString} 拦截)"
                    tvResult.text = "模式: 请求 Claude 检测 · 点击「测速」检测出口 IP"
                }
            }

            heroPref.onBind = { tvResult, progressBar, statusDot, statusLabel, btnTest ->
                renderHero(tvResult, progressBar, statusDot, statusLabel, btnTest)
            }

            heroPref.onQuickTestClick = { tvResult, progressBar, statusDot, statusLabel, btnTest ->
                progressBar.visibility = View.VISIBLE
                btnTest.isEnabled = false

                if (prefs.isVpnOnlyMode) {
                    tvResult.text = "正在检测本地 VPN 状态…"
                    lifecycleScope.launch {
                        delay(150)
                        progressBar.visibility = View.GONE
                        btnTest.isEnabled = true
                        val isVpn = VpnChecker.isVpnActive(context)
                        if (isVpn) {
                            statusDot.setColorFilter(ContextCompat.getColor(context, R.color.status_green))
                            statusLabel.text = "VPN 已连接 (极速直连)"
                            tvResult.text = "检测结果: ✅ VPN 已开启，启动 Claude 零等待"
                        } else {
                            statusDot.setColorFilter(ContextCompat.getColor(context, R.color.status_amber))
                            statusLabel.text = "VPN 未开启"
                            tvResult.text = "检测结果: ⚠️ 未检测到 VPN 连接，启动时将被拦截"
                        }
                    }
                } else {
                    tvResult.text = "正在检测当前网络出口…"
                    lifecycleScope.launch {
                        val result = checker.check(
                            traceUrl = prefs.traceUrl,
                            timeoutSeconds = prefs.timeoutSeconds,
                            blockedRegions = prefs.blockedRegions
                        )

                        progressBar.visibility = View.GONE
                        btnTest.isEnabled = true

                        if (result.isSuccess) {
                            val ip = result.ip ?: "未知"
                            val loc = result.displayLocation
                            if (result.isBlocked) {
                                statusDot.setColorFilter(ContextCompat.getColor(context, R.color.status_amber))
                                statusLabel.text = "当前出口处于受限地区 (${result.loc})"
                                tvResult.text = "出口 IP: $ip · $loc · ⚠️ 拦截保护"
                            } else {
                                statusDot.setColorFilter(ContextCompat.getColor(context, R.color.status_green))
                                statusLabel.text = "网络出口正常，可直接启动"
                                tvResult.text = "出口 IP: $ip · $loc · ✅ 验证通过"
                            }
                        } else {
                            statusDot.setColorFilter(ContextCompat.getColor(context, R.color.status_red))
                            statusLabel.text = "网络连接受阻"
                            tvResult.text = "连接异常: ${result.errorMessage ?: "请求超时"}"
                        }
                    }
                }
            }
        }

        private fun setupDetectionModePreference() {
            val modePref = findPreference<ListPreference>("pref_detection_mode") ?: return
            val traceCategory = findPreference<PreferenceCategory>("category_trace_rules")
            val heroPref = findPreference<HeroPreference>("pref_hero_header")

            fun updateModeUi(mode: String) {
                val isTraceMode = mode == AppPreferences.MODE_CLAUDE_TRACE
                traceCategory?.isEnabled = isTraceMode
                traceCategory?.summary = if (isTraceMode) {
                    "向 Claude 端点发送 Trace 请求，精准解析出口地区"
                } else {
                    "当前为「仅检查 VPN」模式，以下探测规则已暂缓生效"
                }
            }

            updateModeUi(modePref.value ?: AppPreferences.DEFAULT_DETECTION_MODE)

            modePref.setOnPreferenceChangeListener { _, newValue ->
                val newMode = newValue.toString()
                updateModeUi(newMode)
                heroPref?.refresh()
                true
            }
        }

        private fun setupProxyPreference() {
            val proxyPref = findPreference<ListPreference>("pref_preferred_proxy") ?: return
            val context = requireContext()
            val installedProxies = ProxyAppHelper.getInstalledProxyApps(context)

            val entries = mutableListOf("自动扫描调起")
            val entryValues = mutableListOf("")

            for (proxy in installedProxies) {
                entries.add(proxy.appName)
                entryValues.add(proxy.packageName)
            }

            proxyPref.entries = entries.toTypedArray()
            proxyPref.entryValues = entryValues.toTypedArray()

            if (proxyPref.value == null) {
                proxyPref.value = ""
            }
        }

        private fun setupTestConnectionPreference() {
            val testPref = findPreference<Preference>("pref_test_connection") ?: return
            testPref.setOnPreferenceClickListener {
                val context = requireContext()
                val prefs = AppPreferences(context)

                if (prefs.isVpnOnlyMode) {
                    val isVpn = VpnChecker.isVpnActive(context)
                    if (isVpn) {
                        MaterialAlertDialogBuilder(context)
                            .setIcon(R.drawable.ic_check_circle)
                            .setTitle("VPN 连接正常")
                            .setMessage("✅ 检测到设备当前已建立活动的 VPN / 代理连接。\n\n在「仅检查 VPN」模式下，启动器将跳过网络请求，以毫秒级速度直接秒开 Claude。")
                            .setPositiveButton("我知道了", null)
                            .setNeutralButton("探测 Claude 出口 IP") { _, _ ->
                                runClaudeTraceTest(context, prefs)
                            }
                            .show()
                    } else {
                        MaterialAlertDialogBuilder(context)
                            .setIcon(R.drawable.ic_warning_amber)
                            .setTitle("未检测到 VPN")
                            .setMessage("⚠️ 未检测到任何活动的 VPN 或代理网络。\n\n在「仅检查 VPN」模式下，启动 Claude 时将被拦截。\n\n请开启代理客户端连接后再试。")
                            .setPositiveButton("我知道了", null)
                            .setNeutralButton("探测 Claude 出口 IP") { _, _ ->
                                runClaudeTraceTest(context, prefs)
                            }
                            .show()
                    }
                } else {
                    runClaudeTraceTest(context, prefs)
                }
                true
            }
        }

        private fun runClaudeTraceTest(context: Context, prefs: AppPreferences) {
            val dialog = MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_speed)
                .setTitle("正在测试连接…")
                .setMessage("正在向 Claude Trace 端点发送分流探测请求…")
                .setCancelable(false)
                .show()

            lifecycleScope.launch {
                val result = checker.check(
                    traceUrl = prefs.traceUrl,
                    timeoutSeconds = prefs.timeoutSeconds,
                    blockedRegions = prefs.blockedRegions
                )

                dialog.dismiss()

                val (iconRes, title, statusMsg) = if (result.isSuccess) {
                    if (result.isBlocked) {
                        Triple(
                            R.drawable.ic_shield_alert,
                            "检测未通过 (受限地区)",
                            "当前出口 IP: ${result.ip ?: "未知"}\n所在地区: ${result.displayLocation}\n\n⚠️ 该地区属于受限地区规则，启动器将在启动时实施拦截。"
                        )
                    } else {
                        Triple(
                            R.drawable.ic_check_circle,
                            "检测通过 (允许直连)",
                            "当前出口 IP: ${result.ip ?: "未知"}\n所在地区: ${result.displayLocation}\n\n✅ 地区合规，日常启动将无感秒开 Claude。"
                        )
                    }
                } else {
                    Triple(
                        R.drawable.ic_warning_amber,
                        "连接失败",
                        "未能连通 Claude 服务器。\n\n详情: ${result.errorMessage ?: "未知网络错误"}\n\n请确认设备网络正常并已开启代理分流规则。"
                    )
                }

                MaterialAlertDialogBuilder(context)
                    .setIcon(iconRes)
                    .setTitle(title)
                    .setMessage(statusMsg)
                    .setPositiveButton("我知道了", null)
                    .show()
            }
        }

        private fun setupAboutPreference() {
            val aboutPref = findPreference<Preference>("pref_about") ?: return
            aboutPref.setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setIcon(R.drawable.ic_claude_sparkle)
                    .setTitle("Claude 启动器")
                    .setMessage(
                        "版本: 1.0.0 (Material Design 3)\n\n" +
                        "核心设计理念:\n" +
                        "• ⚡ 极速双模：支持「仅检查 VPN」毫秒级秒开，与「请求 Claude 检测」精准分流验证自由切换\n" +
                        "• 🔍 精准探测：直连 Claude 域名分流端点，规避常规 IP 误判\n" +
                        "• 🛡️ 防封拦截：阻止受限地区直连与无代理启动，告别账号风控封禁\n" +
                        "• 🚀 代理联动：一键调起主流代理客户端并支持原位复检\n" +
                        "• 🎨 Material 3：全套 M3 控件、动态配色与精细排版\n\n" +
                        "纯本地运行 · 零后台耗电 · 无任何隐私数据收集"
                    )
                    .setPositiveButton("确定", null)
                    .show()
                true
            }
        }
    }
}
