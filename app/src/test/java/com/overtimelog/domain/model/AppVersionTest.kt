package com.overtimelog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号解析与比较。
 *
 * 这里最容易出错的是两件事：tag 带 `v` 前缀能不能认、以及 1.0.10 和 1.0.9 谁大。
 * 判断错的直接后果是"明明有新版本却不提示"或者"一直提示同一个版本"。
 */
class AppVersionTest {

    @Test
    fun `parse accepts plain and v prefixed tags`() {
        assertEquals(AppVersion(1, 0, 2), AppVersion.parse("1.0.2"))
        assertEquals(AppVersion(1, 0, 2), AppVersion.parse("v1.0.2"))
        assertEquals(AppVersion(1, 0, 2), AppVersion.parse("  V1.0.2  "))
    }

    @Test
    fun `parse accepts prerelease suffix and short forms`() {
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("v1.2.0-beta1"))
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("1.2"))
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse("2"))
    }

    @Test
    fun `parse rejects text that is not a version`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("v1.0.0.zip"))
    }

    @Test
    fun `compare treats 1_0_10 as newer than 1_0_9`() {
        assertTrue(AppVersion(1, 0, 10).isNewerThan(AppVersion(1, 0, 9)))
        assertTrue(AppVersion(1, 10, 0).isNewerThan(AppVersion(1, 9, 9)))
        assertTrue(AppVersion(2, 0, 0).isNewerThan(AppVersion(1, 99, 99)))
        assertFalse(AppVersion(1, 0, 9).isNewerThan(AppVersion(1, 0, 10)))
        assertFalse(AppVersion(1, 0, 1).isNewerThan(AppVersion(1, 0, 1)))
    }

    @Test
    fun `toString is the version people see`() {
        assertEquals("1.0.2", AppVersion(1, 0, 2).toString())
    }
}
