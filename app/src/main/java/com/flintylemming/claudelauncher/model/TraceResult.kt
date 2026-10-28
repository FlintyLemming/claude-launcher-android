package com.flintylemming.claudelauncher.model

data class TraceResult(
    val isSuccess: Boolean,
    val ip: String? = null,
    val loc: String? = null,
    val isBlocked: Boolean = false,
    val errorMessage: String? = null,
    val rawTrace: String? = null
) {
    val displayLocation: String
        get() = when (loc?.uppercase()) {
            "CN" -> "中国大陆 (CN)"
            "HK" -> "香港 (HK)"
            "MO" -> "澳门 (MO)"
            "TW" -> "台湾 (TW)"
            "US" -> "美国 (US)"
            "JP" -> "日本 (JP)"
            "SG" -> "新加坡 (SG)"
            "GB", "UK" -> "英国 (GB)"
            "KR" -> "韩国 (KR)"
            "DE" -> "德国 (DE)"
            null -> "未知地区"
            else -> loc.uppercase()
        }
}
