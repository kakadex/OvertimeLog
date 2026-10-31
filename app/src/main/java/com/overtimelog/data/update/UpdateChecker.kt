package com.overtimelog.data.update

import com.overtimelog.domain.model.AppVersion
import com.overtimelog.domain.model.UpdateState
import com.overtimelog.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 检查更新：拿到远端版本，和本机比大小，存成界面可直接用的状态。
 *
 * 设置页手动检查、启动时静默检查都走这里，所以"检查结果"只有一份，
 * 切页面、退出设置再进来都能看到刚才的结果；同时用 [Mutex] 防止连点触发两次请求。
 */
class UpdateChecker(
    private val repository: UpdateRepository,
    private val currentVersion: AppVersion,
) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 本机版本，界面显示用。 */
    val currentVersionText: String get() = currentVersion.toString()

    /**
     * 检查一次。
     *
     * @param silent 启动时自动检查传 true：失败只记录、不在设置页显示，
     *               避免用户没点"检查更新"却看到一句报错。
     */
    suspend fun check(silent: Boolean = false): UpdateState = mutex.withLock {
        _state.value = UpdateState.Checking
        val result = repository.fetchLatestRelease().fold(
            onSuccess = { release ->
                if (release.version.isNewerThan(currentVersion)) {
                    UpdateState.Available(release)
                } else {
                    UpdateState.UpToDate
                }
            },
            onFailure = { error ->
                UpdateState.Failed(
                    message = error.message?.takeIf { it.isNotBlank() } ?: "检查更新失败，请稍后再试",
                    silent = silent,
                )
            },
        )
        _state.value = result
        result
    }
}
