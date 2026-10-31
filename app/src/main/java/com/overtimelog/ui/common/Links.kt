package com.overtimelog.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 用系统浏览器打开网页。
 *
 * 不内置 WebView：更新说明和下载页交给浏览器，应用不用申请更多权限，也不用维护网页容器。
 * 返回 false 表示这台设备上没有能处理链接的应用，调用方给一句提示即可。
 */
fun Context.openWebPage(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
}
