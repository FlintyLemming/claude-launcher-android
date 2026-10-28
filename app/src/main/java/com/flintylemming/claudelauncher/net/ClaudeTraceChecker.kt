package com.flintylemming.claudelauncher.net

import com.flintylemming.claudelauncher.model.TraceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class ClaudeTraceChecker(
    private val clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
) {

    suspend fun check(
        traceUrl: String = "https://claude.ai/cdn-cgi/trace",
        timeoutSeconds: Int = 3,
        blockedRegions: Set<String> = setOf("CN", "HK")
    ): TraceResult = withContext(Dispatchers.IO) {
        val client = clientBuilder
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout((timeoutSeconds + 2).toLong(), TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val request = Request.Builder()
            .url(traceUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0)")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TraceResult(
                        isSuccess = false,
                        isBlocked = true,
                        errorMessage = "服务器返回异常状态码: ${response.code}"
                    )
                }

                val body = response.body?.string() ?: ""
                parseTraceResponse(body, blockedRegions)
            }
        } catch (e: SocketTimeoutException) {
            TraceResult(
                isSuccess = false,
                isBlocked = true,
                errorMessage = "连接超时 (>${timeoutSeconds}s)，可能无法直连 Claude"
            )
        } catch (e: UnknownHostException) {
            TraceResult(
                isSuccess = false,
                isBlocked = true,
                errorMessage = "域名解析失败，请检查网络或开启代理"
            )
        } catch (e: SSLException) {
            TraceResult(
                isSuccess = false,
                isBlocked = true,
                errorMessage = "SSL 连接握手失败，可能受到网络阻断"
            )
        } catch (e: IOException) {
            TraceResult(
                isSuccess = false,
                isBlocked = true,
                errorMessage = "网络连接失败: ${e.localizedMessage ?: "未知错误"}"
            )
        } catch (e: Exception) {
            TraceResult(
                isSuccess = false,
                isBlocked = true,
                errorMessage = "检测异常: ${e.localizedMessage ?: "未知异常"}"
            )
        }
    }

    companion object {
        fun parseTraceResponse(raw: String, blockedRegions: Set<String>): TraceResult {
            val lines = raw.lines()
            var ip: String? = null
            var loc: String? = null

            for (line in lines) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    when (key) {
                        "ip" -> ip = value
                        "loc" -> loc = value.uppercase()
                    }
                }
            }

            if (ip == null && loc == null) {
                return TraceResult(
                    isSuccess = false,
                    isBlocked = true,
                    errorMessage = "未能解析到有效的 Cloudflare Trace 信息",
                    rawTrace = raw
                )
            }

            val upperBlocked = blockedRegions.map { it.uppercase() }.toSet()
            val isBlocked = loc != null && upperBlocked.contains(loc)

            return TraceResult(
                isSuccess = true,
                ip = ip,
                loc = loc,
                isBlocked = isBlocked,
                rawTrace = raw
            )
        }
    }
}
