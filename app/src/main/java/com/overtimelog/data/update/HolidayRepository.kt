package com.overtimelog.data.update

import com.overtimelog.core.model.DayKind
import com.overtimelog.core.model.HolidayCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.LocalDate

/** 某一天的放假安排（从远程拿到的原始数据）。 */
data class RemoteDay(
    val date: LocalDate,
    val name: String,
    val holiday: Boolean,
)

/**
 * 拉取某一年的中国放假 / 调休安排。
 *
 * 国家没有公开的机器可读接口（中国政府网只有每年一份通告网页），所以这里用第三方整理好的
 * 公开接口 `timor.tech`，它把《国务院办公厅关于XX年部分节假日安排的通知》转成了 JSON。
 * 只在用户主动点"更新"时才联网，结果按年缓存到 DataStore，之后离线可用。
 *
 * 隐私：只按年份请求 `holiday/year/<年>`，不带任何本机数据、不做登录。
 */
class HolidayRepository(
    private val userAgent: String = "OvertimeLog-Calendar",
) {

    /** 取某年数据；失败时返回可读的中文原因。 */
    suspend fun fetchYear(year: Int): Result<Map<LocalDate, RemoteDay>> = withContext(Dispatchers.IO) {
        try {
            val body = getJson("https://timor.tech/api/holiday/year/$year")
            Result.success(parse(body, year))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(IOException(describe(error), error))
        }
    }

    /**
     * 解析接口返回。
     *
     * 结构：`{ "holiday": { "10-10": { "holiday": false, "name": "国庆节后补班", "date": "2026-10-10" } } }`
     * `holiday=true` 是放假，`false` 是调休补班。
     */
    private fun parse(body: String, year: Int): Map<LocalDate, RemoteDay> {
        val root = try {
            JSONObject(body)
        } catch (error: Exception) {
            throw IOException("返回内容不是有效 JSON", error)
        }
        if (root.optInt("code", -1) != 0) throw IOException("服务返回错误码 ${root.optInt("code")}")
        val holiday = root.optJSONObject("holiday") ?: return emptyMap()
        val result = LinkedHashMap<LocalDate, RemoteDay>()
        holiday.keys().forEach { key ->
            val item = holiday.optJSONObject(key) ?: return@forEach
            val dateText = item.optString("date").ifBlank { "$year-$key" }
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return@forEach
            val name = item.optString("name").trim()
            result[date] = RemoteDay(date = date, name = name, holiday = item.optBoolean("holiday", false))
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
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("服务器返回 $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun describe(error: Throwable?): String = when (error) {
        is UnknownHostException, is ConnectException -> "网络不可用，稍后再试"
        is SocketTimeoutException -> "连接超时，稍后再试"
        else -> error?.message?.takeIf { it.isNotBlank() } ?: "更新失败，请稍后再试"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
