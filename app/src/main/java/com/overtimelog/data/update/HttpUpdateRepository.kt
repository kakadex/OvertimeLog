package com.overtimelog.data.update

import com.overtimelog.BuildConfig
import com.overtimelog.domain.model.AppVersion
import com.overtimelog.domain.model.ReleaseAsset
import com.overtimelog.domain.model.RemoteRelease
import com.overtimelog.domain.model.UpdateSource
import com.overtimelog.domain.repository.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * 用 HTTP 读 Release 的最新版本号。
 *
 * 顺序固定：先 Gitee（国内直连快），失败或返回内容不对再 GitHub。
 * 只读公开接口、不带 token、不上传任何本机数据；两个超时都控制在几秒内，
 * 所以断网时启动检查也不会拖住界面。
 */
class HttpUpdateRepository(
    private val userAgent: String = "OvertimeLog/${BuildConfig.VERSION_NAME} (Android)",
) : UpdateRepository {

    override suspend fun fetchLatestRelease(): Result<RemoteRelease> = withContext(Dispatchers.IO) {
        val giteeError = try {
            return@withContext Result.success(fetchFromGitee())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error
        }

        try {
            Result.success(fetchFromGithub())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(
                UpdateCheckException(
                    "连不上更新服务器（Gitee：${describe(giteeError)}；GitHub：${describe(error)}）",
                    error,
                ),
            )
        }
    }

    private fun fetchFromGitee(): RemoteRelease {
        val body = getJson(ReleaseChannels.GITEE_API_LATEST)
        return parseRelease(body, UpdateSource.GITEE)
    }

    private fun fetchFromGithub(): RemoteRelease {
        val body = getJson(ReleaseChannels.GITHUB_API_LATEST)
        return parseRelease(body, UpdateSource.GITHUB)
    }

    private fun parseRelease(body: String, source: UpdateSource): RemoteRelease {
        val json = try {
            JSONObject(body)
        } catch (error: Exception) {
            throw UpdateCheckException("返回内容不是有效 JSON", error)
        }
        val tag = json.optString("tag_name").trim()
        if (tag.isEmpty() || tag == "null") throw UpdateCheckException("没有读到版本号")
        val version = AppVersion.parse(tag) ?: throw UpdateCheckException("版本号无法识别：$tag")
        // GitHub 会带 html_url；Gitee 不一定带，按 tag 拼发布页地址
        val pageUrl = json.optString("html_url").trim()
            .ifBlank { ReleaseChannels.tagPage(tag, source) }
        return RemoteRelease(
            version = version,
            tag = tag,
            pageUrl = pageUrl,
            source = source,
            assets = parseAssets(json.optJSONArray("assets")),
        )
    }

    /**
     * 解析附件列表。
     *
     * 两个站的字段名一致（`name` / `browser_download_url`），只有大小在 GitHub 才有（`size`）；
     * Gitee 不给大小时记 0，下载时按"总大小未知"处理，界面显示"下载中…"。
     * Gitee 还会附带源码包（v1.0.3.zip 之类），这些不是 APK，后面挑包时会自然被过滤掉。
     */
    private fun parseAssets(array: JSONArray?): List<ReleaseAsset> {
        if (array == null) return emptyList()
        val result = ArrayList<ReleaseAsset>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val url = item.optString("browser_download_url").trim()
            if (name.isEmpty() || url.isEmpty()) continue
            result += ReleaseAsset(name = name, url = url, sizeBytes = item.optLong("size", 0L))
        }
        return result
    }

    private fun getJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            // GitHub 的公开接口强制要求 User-Agent；Gitee 也接受，统一带上应用标识
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            // Gitee 的响应头不带 charset，这里固定按 UTF-8 读，避免中文说明乱码
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** 把异常翻译成用户能看懂的一小段中文，附在"连不上更新服务器"后面。 */
    private fun describe(error: Throwable?): String = when (error) {
        null -> "未知原因"
        is UnknownHostException, is ConnectException -> "网络不可用"
        is SocketTimeoutException -> "连接超时"
        is HttpStatusException -> "服务器返回 ${error.code}"
        is UpdateCheckException -> error.message ?: "响应异常"
        else -> "响应异常"
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private class UpdateCheckException(message: String, cause: Throwable? = null) : IOException(message, cause)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 6_000
    }
}
