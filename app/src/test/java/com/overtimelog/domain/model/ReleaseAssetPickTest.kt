package com.overtimelog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 按手机 CPU 架构挑 APK。
 *
 * 这段逻辑错了的后果很直接：给 32 位手机推 64 位包会装不上，或者在支持 arm64 的手机上
 * 多下 100KB 的 universal 包。所以顺序必须严格按设备 ABI 优先级来。
 */
class ReleaseAssetPickTest {

    private fun release(vararg names: String) = RemoteRelease(
        version = AppVersion(1, 0, 4),
        tag = "v1.0.4",
        pageUrl = "https://example.com/tag/v1.0.4",
        source = UpdateSource.GITEE,
        assets = names.map { ReleaseAsset(name = it, url = "https://example.com/$it") },
    )

    @Test
    fun `picks matching abi for arm64 phone`() {
        val r = release(
            "OvertimeLog-1.0.4-android-armeabi-v7a.apk",
            "OvertimeLog-1.0.4-android-arm64-v8a.apk",
            "OvertimeLog-1.0.4-android-universal.apk",
        )
        val picked = r.pickApk(listOf("arm64-v8a", "armeabi-v7a", "armeabi"))
        assertEquals("OvertimeLog-1.0.4-android-arm64-v8a.apk", picked?.name)
    }

    @Test
    fun `picks 32 bit package for 32 bit phone`() {
        val r = release(
            "OvertimeLog-1.0.4-android-arm64-v8a.apk",
            "OvertimeLog-1.0.4-android-armeabi-v7a.apk",
        )
        val picked = r.pickApk(listOf("armeabi-v7a", "armeabi"))
        assertEquals("OvertimeLog-1.0.4-android-armeabi-v7a.apk", picked?.name)
    }

    @Test
    fun `falls back to universal when no abi matches`() {
        val r = release(
            "OvertimeLog-1.0.4-android-arm64-v8a.apk",
            "OvertimeLog-1.0.4-android-universal.apk",
        )
        val picked = r.pickApk(listOf("mips64"))
        assertEquals("OvertimeLog-1.0.4-android-universal.apk", picked?.name)
    }

    @Test
    fun `ignores non apk assets`() {
        val r = release(
            "OvertimeLog-1.0.4.aab",
            "SHA256SUMS.txt",
            "latest.txt",
            "v1.0.4.zip",
        )
        assertNull(r.pickApk(listOf("arm64-v8a")))
    }

    @Test
    fun `uses the best supported abi first`() {
        val r = release(
            "OvertimeLog-1.0.4-android-x86_64.apk",
            "OvertimeLog-1.0.4-android-arm64-v8a.apk",
        )
        // 模拟器上 SUPPORTED_ABIS 可能是 x86_64 在前，但安卓真机 arm64 在前；按传入顺序优先
        assertEquals("OvertimeLog-1.0.4-android-x86_64.apk", r.pickApk(listOf("x86_64", "arm64-v8a"))?.name)
        assertEquals("OvertimeLog-1.0.4-android-arm64-v8a.apk", r.pickApk(listOf("arm64-v8a", "x86_64"))?.name)
    }
}
