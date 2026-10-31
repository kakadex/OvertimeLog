package com.overtimelog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Intent
import com.overtimelog.core.model.SalaryRule
import com.overtimelog.data.settings.AppSettings
import com.overtimelog.data.settings.ThemeMode
import com.overtimelog.di.AppContainer
import com.overtimelog.domain.model.InstallState
import com.overtimelog.domain.model.UpdateState
import com.overtimelog.ui.common.supportedAbis
import com.overtimelog.ui.common.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页状态：偏好设置 + 当前薪资规则 + 导出/清除动作。
 */
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), AppSettings())

    val rule: StateFlow<SalaryRule?> = container.salaryRuleRepository.observeActiveRule()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), null)

    /** 更新检查结果：启动时静默检查与这里手动检查共用同一份状态。 */
    val updateState: StateFlow<UpdateState> = container.updateChecker.state

    /** 应用内下载/安装进度，与首页共用同一份。 */
    val installState: StateFlow<InstallState> = container.apkInstaller.state

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * 下载并安装当前检查到的新版本。
     *
     * 直接下载这台手机对应的 APK（arm64-v8a 等），完成后把安装意图交给 [onReadyToInstall]
     * 去启动系统安装界面；只有拿不到直链时才退回打开发布页。
     */
    fun downloadUpdate(release: com.overtimelog.domain.model.RemoteRelease, onReadyToInstall: (Intent) -> Unit) {
        val candidates = container.apkInstaller.candidatesFor(release, supportedAbis())
        if (candidates.isEmpty()) {
            _events.trySend("没有找到可下载的安装包，请在下面打开发布页")
            return
        }
        container.apkInstaller.download(release, supportedAbis(), candidates, onReadyToInstall)
    }

    /** 从系统安装界面回来：复位下载状态，允许重新检查或再次下载。 */
    fun resetInstallState() {
        container.apkInstaller.resetIfFinished()
    }

    fun checkUpdate() {
        viewModelScope.launch { container.updateChecker.check(silent = false) }
    }

    fun setUpdateCheckOnStartup(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setUpdateCheckOnStartup(enabled) }
    }

    fun setDefaultBreakMinutes(minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setDefaultBreakMinutes(minutes) }
    }

    fun setBreakUnit(unit: com.overtimelog.data.settings.BreakUnit) {
        viewModelScope.launch { container.settingsRepository.setBreakUnit(unit) }
    }

    fun setDefaultTimes(start: java.time.LocalTime, end: java.time.LocalTime) {
        viewModelScope.launch { container.settingsRepository.setDefaultTimes(start, end) }
    }

    fun setDefaultType(type: com.overtimelog.core.model.OvertimeType) {
        viewModelScope.launch { container.settingsRepository.setDefaultOvertimeType(type) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun exportCsv(uri: String) {
        viewModelScope.launch {
            container.exportCsv(uri).fold(
                onSuccess = { _events.send("已导出 ${it.count} 条记录") },
                onFailure = { _events.send(it.toUiMessage()) },
            )
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            container.clearAllData().fold(
                onSuccess = { _events.send("已清除全部记录") },
                onFailure = { _events.send(it.toUiMessage()) },
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
