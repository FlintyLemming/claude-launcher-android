package com.flintylemming.claudelauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.flintylemming.claudelauncher.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class HeroPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

    var onQuickTestClick: ((tvResult: TextView, progressBar: LinearProgressIndicator, statusDot: ImageView, statusLabel: TextView, btnTest: MaterialButton) -> Unit)? = null
    var onBind: ((tvResult: TextView, progressBar: LinearProgressIndicator, statusDot: ImageView, statusLabel: TextView, btnTest: MaterialButton) -> Unit)? = null

    init {
        layoutResource = R.layout.preference_hero_card
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val btnQuickTest = holder.findViewById(R.id.heroBtnQuickTest) as? MaterialButton ?: return
        val tvResult = holder.findViewById(R.id.heroTvTestResult) as? TextView ?: return
        val progressBar = holder.findViewById(R.id.heroProgressBar) as? LinearProgressIndicator ?: return
        val statusDot = holder.findViewById(R.id.heroStatusDot) as? ImageView ?: return
        val statusLabel = holder.findViewById(R.id.heroStatusLabel) as? TextView ?: return

        onBind?.invoke(tvResult, progressBar, statusDot, statusLabel, btnQuickTest)

        btnQuickTest.setOnClickListener {
            onQuickTestClick?.invoke(tvResult, progressBar, statusDot, statusLabel, btnQuickTest)
        }
    }

    fun refresh() {
        notifyChanged()
    }
}
