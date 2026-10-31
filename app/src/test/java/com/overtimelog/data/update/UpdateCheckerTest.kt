package com.overtimelog.data.update

import com.overtimelog.domain.model.AppVersion
import com.overtimelog.domain.model.RemoteRelease
import com.overtimelog.domain.model.UpdateSource
import com.overtimelog.domain.model.UpdateState
import com.overtimelog.domain.repository.UpdateRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 检查更新的判定逻辑。
 *
 * 关键点：远端版本比本机新才提示；相同或更旧一律"已是最新"（不能因为发布页还挂着旧版本
 * 就提示升级）；启动时的静默失败要打上 silent 标记，让设置页不显示报错。
 */
class UpdateCheckerTest {

    private val local = AppVersion(1, 0, 2)

    @Test
    fun `newer release is reported as available`() = runTest {
        val release = remote("v1.0.3", UpdateSource.GITEE)
        val checker = UpdateChecker(FakeUpdateRepository(Result.success(release)), local)

        val state = checker.check()

        assertEquals(UpdateState.Available(release), state)
        assertEquals(UpdateState.Available(release), checker.state.value)
    }

    @Test
    fun `same version is up to date`() = runTest {
        val checker = UpdateChecker(
            FakeUpdateRepository(Result.success(remote("v1.0.2", UpdateSource.GITHUB))),
            local,
        )

        assertEquals(UpdateState.UpToDate, checker.check())
    }

    @Test
    fun `older release is up to date`() = runTest {
        val checker = UpdateChecker(
            FakeUpdateRepository(Result.success(remote("v1.0.0", UpdateSource.GITEE))),
            local,
        )

        assertEquals(UpdateState.UpToDate, checker.check())
    }

    @Test
    fun `manual failure keeps the reason and is not silent`() = runTest {
        val checker = UpdateChecker(
            FakeUpdateRepository(Result.failure(IOException("连不上更新服务器"))),
            local,
        )

        val state = checker.check(silent = false)

        assertTrue(state is UpdateState.Failed)
        state as UpdateState.Failed
        assertEquals("连不上更新服务器", state.message)
        assertEquals(false, state.silent)
    }

    @Test
    fun `startup failure is marked silent`() = runTest {
        val checker = UpdateChecker(
            FakeUpdateRepository(Result.failure(IOException("网络不可用"))),
            local,
        )

        val state = checker.check(silent = true)

        assertEquals(UpdateState.Failed("网络不可用", silent = true), state)
    }

    private fun remote(tag: String, source: UpdateSource) = RemoteRelease(
        version = AppVersion.parse(tag)!!,
        tag = tag,
        pageUrl = ReleaseChannels.tagPage(tag, source),
        source = source,
    )

    private class FakeUpdateRepository(
        private val result: Result<RemoteRelease>,
    ) : UpdateRepository {
        override suspend fun fetchLatestRelease(): Result<RemoteRelease> = result
    }
}
