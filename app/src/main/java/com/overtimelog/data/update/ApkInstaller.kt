package com.overtimelog.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.overtimelog.domain.model.AppVersion
import com.overtimelog.domain.model.InstallState
import com.overtimelog.domain.model.ReleaseAsset
import com.overtimelog.domain.model.RemoteRelease
import com.overtimelog.domain.repository.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内下载新版本并拉起系统安装。
 *
 * 为什么自己下载、不用系统 [android.app.DownloadManager]：DownloadManager 不保证能写进
 * 应用的外部私有目录，实测在部分设备上任务一直停在排队状态、文件根本没创建。这里直接用
 * HttpURLConnection 下到 `files/updates/`，进度可控，也不需要任何存储权限。
 *
 * 装包时用 [FileProvider] 把下载好的 APK 临时授权给系统安装器（Android 8+ 的强制要求）。
 * 这个类只做状态机；真正弹出安装界面由界面层用 ActivityResultLauncher 启动这个意图。
 */
class ApkInstaller(
    private val context: Context,
    @Suppress("unused") private val repository: UpdateRepository,
) {

    private val appContext = context.applicationContext

    /** 下载放在应用级作用域，用户切页面时不会中断。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    /** 挑出这台手机能用的 APK 直链；界面层也用它判断"有没有可下载的文件"。 */
    fun candidatesFor(release: RemoteRelease, supportedAbis: List<String>): List<ReleaseAsset> =
        listOfNotNull(release.pickApk(supportedAbis))

    /**
     * 下载并安装 [release] 对应的 APK。
     *
     * [candidates] 是这台手机能用的下载直链（按优先级排好）。一个地址失败自动换下一个。
     */
    fun download(
        release: RemoteRelease,
        supportedAbis: List<String>,
        candidates: List<ReleaseAsset>,
        onReadyToInstall: (Intent) -> Unit,
    ) {
        if (_state.value is InstallState.Downloading) return
        scope.launch { runDownload(release, supportedAbis, candidates, onReadyToInstall) }
    }

    private suspend fun runDownload(
        release: RemoteRelease,
        supportedAbis: List<String>,
        candidates: List<ReleaseAsset>,
        onReadyToInstall: (Intent) -> Unit,
    ) {
        val usable = candidates.filter { it.url.isNotBlank() }
        if (usable.isEmpty()) {
            _state.value = InstallState.Failed("发布页里没有可用的 APK，请到发布页手动下载")
            return
        }
        val targetDir = File(appContext.filesDir, "updates")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            _state.value = InstallState.Failed("无法创建下载目录，请到发布页手动下载")
            return
        }

        var lastError = "下载失败，请检查网络后重试"
        for (asset in usable) {
            val target = File(targetDir, asset.name)
            target.delete()
            val result = try {
                fetch(asset, release, target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = "下载失败：${error.message ?: "网络异常"}"
                false
            }
            if (result) {
                _state.value = InstallState.Installing
                val intent = installIntent(target)
                if (intent == null) {
                    _state.value = InstallState.Failed("安装包已下载，但打不开安装界面，请在文件管理器里安装 $target")
                } else {
                    onReadyToInstall(intent)
                }
                return
            }
        }
        _state.value = InstallState.Failed(lastError)
    }

    /**
     * 下载单个 APK，边下边更新进度。
     *
     * 返回 true 表示文件已完整落盘。只接受 2xx；Gitee / GitHub 的 302 由 HttpURLConnection
     * 自动跟随（followRedirects 默认 true）。
     */
    private suspend fun fetch(asset: ReleaseAsset, release: RemoteRelease, target: File): Boolean {
        val version = AppVersion.parse(asset.name) ?: release.version
        val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("服务器返回 $code")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.sizeBytes
            var downloaded = 0L
            _state.value = InstallState.Downloading(
                version = version,
                percent = if (total > 0) 0 else -1,
                downloadedBytes = 0,
                totalBytes = total,
            )
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        _state.value = InstallState.Downloading(
                            version = version,
                            percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else -1,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                        )
                    }
                    output.flush()
                }
            }
            // 大小对不上说明连接中途断了，删掉残缺文件，交给下一个地址
            if (total > 0 && target.length() != total) {
                target.delete()
                return false
            }
            return target.length() > 0
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 生成"安装 APK"意图。
     *
     * Android 8+ 必须用 FileProvider 授权，不能直接给 file:// 路径，否则安装器会拒绝打开。
     */
    private fun installIntent(apk: File): Intent? {
        if (!apk.exists()) return null
        val uri = try {
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", apk)
        } catch (error: Throwable) {
            return null
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 从系统安装界面回来之后复位状态，允许再次检查或重新下载。 */
    fun resetIfFinished() {
        if (_state.value is InstallState.Installing) _state.value = InstallState.Idle
    }

    fun clearState() {
        _state.value = InstallState.Idle
    }

    private companion object {
        const val MIME_APK = "application/vnd.android.package-archive"
        const val USER_AGENT = "OvertimeLog-Update"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
    }
}
