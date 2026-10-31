package com.overtimelog.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * 用系统浏览器打开网页。
 *
 * 发布页、校验清单这类内容交给浏览器，应用不用维护网页容器。
 * 返回 false 表示这台设备上没有能处理链接的应用，调用方给一句提示即可。
 */
fun Context.openWebPage(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * 这台设备支持的 CPU 架构，按优先级从高到低。
 *
 * Android 保证 [Build.SUPPORTED_ABIS] 第一个就是最优 ABI（现代手机基本都是 arm64-v8a），
 * 所以顺序直接用它的顺序，挑 APK 时按这个顺序去匹配。
 */
fun supportedAbis(): List<String> =
    Build.SUPPORTED_ABIS?.map { it.lowercase() }?.filter { it.isNotBlank() } ?: emptyList()
