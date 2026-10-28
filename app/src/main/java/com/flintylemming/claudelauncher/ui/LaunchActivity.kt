package com.flintylemming.claudelauncher.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.flintylemming.claudelauncher.R
import com.flintylemming.claudelauncher.data.AppPreferences
import com.flintylemming.claudelauncher.databinding.DialogBlockedBinding
import com.flintylemming.claudelauncher.model.TraceResult
import com.flintylemming.claudelauncher.net.ClaudeTraceChecker
import com.flintylemming.claudelauncher.util.ProxyAppHelper
import com.flintylemming.claudelauncher.util.VpnChecker
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LaunchActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private val checker = ClaudeTraceChecker()
    private var activeDialog: Dialog? = null
    private var dialogBinding: DialogBlockedBinding? = null
    private var lastResult: TraceResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)

        startDetection()
    }

    override fun onResume() {
        super.onResume()
        // If the blocked dialog is active and user returns from a proxy app in VPN mode,
        // automatically proceed if VPN is now turned on!
        if (activeDialog?.isShowing == true && prefs.isVpnOnlyMode) {
            if (VpnChecker.isVpnActive(this)) {
                launchClaude()
            }
        }
    }

    private fun startDetection() {
        if (prefs.isVpnOnlyMode) {
            val isVpn = VpnChecker.isVpnActive(this)
            if (isVpn) {
                // VPN active, zero-delay instant launch
                launchClaude()
            } else {
                // VPN missing, show intercept dialog
                showVpnBlockedDialog()
            }
        } else {
            lifecycleScope.launch {
                val result = checker.check(
                    traceUrl = prefs.traceUrl,
                    timeoutSeconds = prefs.timeoutSeconds,
                    blockedRegions = prefs.blockedRegions
                )

                if (!result.isBlocked) {
                    // Non-blocked region detected, directly launch Claude!
                    launchClaude()
                } else {
                    // Blocked or network failure, show Material 3 intercept dialog
                    showBlockedDialog(result)
                }
            }
        }
    }

    private fun launchClaude() {
        activeDialog?.dismiss()
        val claudePackage = "com.anthropic.claude"
        val launchIntent = packageManager.getLaunchIntentForPackage(claudePackage)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, R.string.claude_not_installed, Toast.LENGTH_SHORT).show()
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开浏览器: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun getOrCreateDialog(): Pair<Dialog, DialogBlockedBinding> {
        val existingDialog = activeDialog
        val existingBinding = dialogBinding
        if (existingDialog != null && existingBinding != null) {
            return Pair(existingDialog, existingBinding)
        }

        val dialog = Dialog(this)
        activeDialog = dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val binding = DialogBlockedBinding.inflate(layoutInflater)
        dialogBinding = binding
        dialog.setContentView(binding.root)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        // Constrain dialog width to standard Material 3 mobile dimensions (max 336dp with ample side margins)
        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels
        val maxDialogWidth = (336 * density).toInt()
        val minHorizontalMargin = (48 * density).toInt()
        val widthPx = (screenWidthPx - minHorizontalMargin).coerceAtMost(maxDialogWidth)
        dialog.window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)

        setupDialogListeners(binding)

        dialog.setOnDismissListener {
            if (!isChangingConfigurations && !isFinishing) {
                finish()
            }
        }

        return Pair(dialog, binding)
    }

    private fun setupDialogListeners(binding: DialogBlockedBinding) {
        binding.btnCopyIp.setOnClickListener {
            val ip = lastResult?.ip
            if (!ip.isNullOrBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Claude Exit IP", ip)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@LaunchActivity, "出口 IP 已复制到剪贴板", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@LaunchActivity, "暂无可复制的 IP 地址", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenProxy.setOnClickListener {
            ProxyAppHelper.launchProxyApp(this, prefs.preferredProxyPackage)
        }

        binding.btnRetry.setOnClickListener {
            performRetry(binding)
        }

        binding.btnLaunchAnyway.setOnClickListener {
            launchClaude()
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performRetry(binding: DialogBlockedBinding) {
        binding.layoutProgress.visibility = View.VISIBLE
        binding.btnRetry.isEnabled = false
        binding.btnOpenProxy.isEnabled = false

        lifecycleScope.launch {
            if (prefs.isVpnOnlyMode) {
                binding.tvProgressHint.text = getString(R.string.checking_vpn)
                delay(100)
                val isVpn = VpnChecker.isVpnActive(this@LaunchActivity)
                binding.layoutProgress.visibility = View.GONE
                binding.btnRetry.isEnabled = true
                binding.btnOpenProxy.isEnabled = true

                if (isVpn) {
                    launchClaude()
                } else {
                    updateVpnBlockedUI(binding)
                    Toast.makeText(this@LaunchActivity, "检测完成：仍未检测到 VPN 连接", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.tvProgressHint.text = getString(R.string.checking_network)
                val retryResult = checker.check(
                    traceUrl = prefs.traceUrl,
                    timeoutSeconds = prefs.timeoutSeconds,
                    blockedRegions = prefs.blockedRegions
                )
                binding.layoutProgress.visibility = View.GONE
                binding.btnRetry.isEnabled = true
                binding.btnOpenProxy.isEnabled = true

                if (!retryResult.isBlocked) {
                    launchClaude()
                } else {
                    updateDialogUI(binding, retryResult)
                    val toastMsg = if (retryResult.isSuccess) {
                        "检测完成：当前出口地区 (${retryResult.loc}) 仍受限"
                    } else {
                        "检测失败：${retryResult.errorMessage ?: "网络无法连接"}"
                    }
                    Toast.makeText(this@LaunchActivity, toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showVpnBlockedDialog() {
        if (isFinishing || isDestroyed) return
        val (dialog, binding) = getOrCreateDialog()
        updateVpnBlockedUI(binding)
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    private fun showBlockedDialog(result: TraceResult) {
        if (isFinishing || isDestroyed) return
        val (dialog, binding) = getOrCreateDialog()
        updateDialogUI(binding, result)
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    private fun updateVpnBlockedUI(binding: DialogBlockedBinding) {
        lastResult = null

        binding.tvTitle.text = getString(R.string.dialog_title_vpn_missing)
        binding.tvSubtitle.text = "VPN 状态检测"
        binding.tvMessage.text = getString(R.string.dialog_msg_vpn_missing)
        binding.ivStatusIcon.setImageResource(R.drawable.ic_shield_alert)

        val errorContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorErrorContainer)
        val onErrorContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnErrorContainer)

        binding.cardIconContainer.setCardBackgroundColor(errorContainer)
        binding.ivStatusIcon.setColorFilter(onErrorContainer)
        binding.cardStatusPill.setCardBackgroundColor(errorContainer)
        binding.tvStatusPillText.setTextColor(onErrorContainer)
        binding.tvStatusPillText.text = "VPN 未开启"
        binding.tvStatusSubtext.text = "请先开启代理软件连接"

        binding.tvCurrentIp.text = "未开启 VPN"
        binding.tvCurrentLoc.text = "直连网络 (未检测出口)"
        binding.btnCopyIp.visibility = View.GONE
    }

    private fun updateDialogUI(binding: DialogBlockedBinding, result: TraceResult) {
        lastResult = result

        if (result.isSuccess) {
            binding.tvTitle.text = getString(R.string.dialog_title_blocked)
            binding.tvSubtitle.text = "Claude 防封分流保护"
            binding.tvMessage.text = "检测到当前访问 Claude 的网络出口 IP 属于受限地区，直接连接可能会导致封号或无法加载。"
            binding.ivStatusIcon.setImageResource(R.drawable.ic_shield_alert)

            val errorContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorErrorContainer)
            val onErrorContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnErrorContainer)

            binding.cardIconContainer.setCardBackgroundColor(errorContainer)
            binding.ivStatusIcon.setColorFilter(onErrorContainer)
            binding.cardStatusPill.setCardBackgroundColor(errorContainer)
            binding.tvStatusPillText.setTextColor(onErrorContainer)
            binding.tvStatusPillText.text = "受限地区 (${result.loc ?: "未知"})"
            binding.tvStatusSubtext.text = "建议切换至已支持国家节点"

            binding.tvCurrentIp.text = result.ip ?: "未知"
            binding.tvCurrentLoc.text = result.displayLocation
            binding.btnCopyIp.visibility = if (result.ip.isNullOrBlank()) View.GONE else View.VISIBLE
        } else {
            binding.tvTitle.text = getString(R.string.dialog_title_error)
            binding.tvSubtitle.text = "网络连接受阻"
            binding.tvMessage.text = result.errorMessage ?: "无法直接连接至 Claude 服务器，可能已被网络防火墙阻断或连接超时。"
            binding.ivStatusIcon.setImageResource(R.drawable.ic_warning_amber)

            val tertiaryContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorTertiaryContainer)
            val onTertiaryContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnTertiaryContainer)

            binding.cardIconContainer.setCardBackgroundColor(tertiaryContainer)
            binding.ivStatusIcon.setColorFilter(onTertiaryContainer)
            binding.cardStatusPill.setCardBackgroundColor(tertiaryContainer)
            binding.tvStatusPillText.setTextColor(onTertiaryContainer)
            binding.tvStatusPillText.text = "连接超时 / 失败"
            binding.tvStatusSubtext.text = "请开启代理以接入网络"

            binding.tvCurrentIp.text = "未连通"
            binding.tvCurrentLoc.text = "未解析"
            binding.btnCopyIp.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeDialog?.dismiss()
        activeDialog = null
        dialogBinding = null
    }
}
