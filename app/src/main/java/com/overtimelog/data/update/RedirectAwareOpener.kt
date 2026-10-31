package com.overtimelog.data.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 下载专用的 HTTP 打开器：手动跟随 302 跳转，保证 Range 头一路带过去。
 *
 * 为什么不用 [HttpURLConnection.instanceFollowRedirects]：Gitee 的下载直链要经过
 * 两次 302（releases/download → attach_files → foruda CDN），跨主机跳转时 JVM 会
 * **丢掉自定义的 Range 头**（实测），服务器于是每次都返回完整文件（200 + 全部长度）。
 * 断点续传就变成了"断一次重头下一次"，界面上的进度条自然反复跳动。
 *
 * 手动跟跳转后，每个新连接都重新带上 Range，服务器就能返回 206 部分内容，
 * 进度单调递增。GitHub 的跳转链同理受益。
 */
internal object RedirectAwareOpener {

    private const val MAX_REDIRECTS = 5

    /** 打开 [urlStr] 的下载连接：自动跟 3xx，Range 等请求头在每一跳都保留。 */
    fun open(
        urlStr: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        userAgent: String,
        rangeFrom: Long,
    ): HttpURLConnection {
        var url = urlStr
        var redirects = 0
        while (true) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false   // 自己跟，防止 JVM 丢 Range
                useCaches = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/octet-stream")
                if (rangeFrom > 0) setRequestProperty("Range", "bytes=$rangeFrom-")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("服务器返回 $code 但没有跳转地址")
                redirects++
                if (redirects > MAX_REDIRECTS) throw IOException("下载地址跳转次数过多")
                url = absolutize(url, location)
                continue
            }
            return connection
        }
    }

    /** 把相对 Location 拼成绝对地址（跳转一般给绝对地址，这里兜底相对的情况）。 */
    private fun absolutize(base: String, location: String): String = try {
        URL(URL(base), location).toString()
    } catch (error: Throwable) {
        throw IOException("跳转地址无效：$location", error)
    }
}
