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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内下载新版本并拉起系统安装。
 *
 * 两个关键设计（都是为了"切到别的 App 再回来还能继续"）：
 *
 * 1. **断点续传**：下载时边下边把数据写进目标文件，进度记在 `.part` 与已下载字节数上。
 *    连接断了不清文件，下次用 HTTP Range 从断点继续，不用从头下 2MB。
 * 2. **自动重试**：网络异常（切后台时系统会挂起进程、断连接）不直接判失败，
 *    而是退避重试若干次；只有真的连续失败才把状态改成失败。
 *
 * 下载仍然放在应用级协程里，用户切页面、切 Tab 都不影响；进程被系统回收属于极端情况，
 * 重进应用重新点一次即可，文件已下部分会复用。
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
        // 挂上前台服务：切到微信等应用时，系统不会冻结进程、掐断下载
        UpdateDownloadService.start(appContext)
        scope.launch {
            try {
                runDownload(release, supportedAbis, candidates, onReadyToInstall)
            } finally {
                // 无论成功、失败还是取消，都要收掉前台通知
                UpdateDownloadService.stop(appContext)
            }
        }
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
            // 完整文件已存在：直接进入安装，不用重下
            if (target.exists() && asset.sizeBytes > 0 && target.length() == asset.sizeBytes) {
                _state.value = InstallState.Installing
                installIntent(target)?.let { onReadyToInstall(it) }
                    ?: run { _state.value = InstallState.Failed("安装包已存在，但打不开安装界面") }
                return
            }
            val ok = try {
                fetchWithRetry(asset, release, target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = "下载失败：${error.message ?: "网络异常"}"
                false
            }
            if (ok) {
                _state.value = InstallState.Installing
                val intent = installIntent(target)
                if (intent == null) {
                    _state.value = InstallState.Failed("安装包已下载，但打不开安装界面，请在文件管理器里安装")
                } else {
                    onReadyToInstall(intent)
                }
                return
            }
        }
        _state.value = InstallState.Failed(lastError)
    }

    /**
     * 带重试的下载。
     *
     * 切到别的 App 时，系统会冻结进程并掐断连接，读操作会抛异常；
     * 这里退避重试（1s、2s、4s…）。支持续传的源（GitHub）每次用 Range 继续；
     * 不支持的源（Gitee CDN）检测到后自动改为"整包重下 + 进度归零"，保证进度条
     * 显示始终单调、不跳变。只要还有网络，切回来时通常已经下完了。
     */
    private suspend fun fetchWithRetry(asset: ReleaseAsset, release: RemoteRelease, target: File): Boolean {
        var attempt = 0
        var lastError: Throwable? = null
        var resumeUnsupported = false
        while (true) {
            if (!coroutineContextIsActive()) return false
            val finished = try {
                fetchOnce(asset, release, target, resumeUnsupported)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // fetchOnce 内部发现"发了 Range 却回 200"时，通过这个约定值告诉外层换策略；
                // 用异常附带标记比再加一路返回值更省事，这里解析后转成布尔。
                resumeUnsupported = resumeUnsupported || error.message?.contains(NO_RESUME_MARKER) == true
                lastError = error
                false
            }
            if (finished) return true
            // 下载中断后文件里可能残留半包；不支持续传的源重试时必须清掉，防止"假续传"
            if (resumeUnsupported && target.exists()) target.delete()
            attempt++
            if (attempt >= MAX_RETRIES) {
                throw lastError ?: IOException("下载失败")
            }
            // 退避等待；切后台时这段等待会被系统挂起，回到前台继续
            delay(RETRY_BASE_DELAY_MS * attempt)
        }
    }

    /**
     * 下载单个 APK（支持断点续传），边下边更新进度。
     *
     * 用 `RandomAccessFile` 追加写入，已存在的部分不重下；服务器不支持 Range（返回 200 而不是 206）时
     * 自动从头写。返回 true 表示文件已完整落盘。
     *
     * 跳转处理见 [RedirectAwareOpener]：手动跟 302，Range 头一路带过去，让支持续传的
     * 源（GitHub）真正 206 续传。
     *
     * **进度不跳的关键**：Gitee 的 CDN 实测忽略 Range（带 Range 也回 200 + 完整长度），
     * 也就是不支持续传。这种源上每次断线重试都会整包重发，如果把"已有字节数"当进度显示，
     * 用户看到的进度条就会从几十跳回十几反复横跳。这里用 [resumeUnsupported] 检测：
     * 一旦发现某源"发了 Range 却回 200"，重试时清空文件、进度归零重走，显示上永远是一个
     * 单调的 0→100 过程，不再跳动。
     *
     * 进度按 [PROGRESS_STEP_BYTES] 节流：每凑够一批再发一次状态，避免高频重组界面。
     */
    private suspend fun fetchOnce(
        asset: ReleaseAsset,
        release: RemoteRelease,
        target: File,
        resumeUnsupported: Boolean,
    ): Boolean {
        val version = AppVersion.parse(asset.name) ?: release.version
        // 这个源不支持续传时：无论文件里有什么，都从 0 开始，避免"进度起点乱跳"
        var existing = if (!resumeUnsupported && target.exists()) target.length() else 0L
        // 200 响应里发现"发了 Range 却被无视"时置位，通知调用方下一轮清零重下
        var detectedNoResume = false
        val connection = RedirectAwareOpener.open(
            urlStr = asset.url,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            userAgent = USER_AGENT,
            rangeFrom = existing,
        )
        try {
            val code = connection.responseCode
            val appending = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> true      // 206：服务器接受续传
                HttpURLConnection.HTTP_OK -> {              // 200：整包返回
                    // 发了 Range 却拿到 200 → 该源不支持续传，记下来让重试轮清零重下
                    if (existing > 0) detectedNoResume = true
                    existing = 0L
                    false
                }
                else -> throw IOException("服务器返回 $code")
            }

            val remaining = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            val total = when {
                appending && remaining > 0 -> existing + remaining
                remaining > 0 -> remaining
                else -> asset.sizeBytes
            }
            val startOffset = if (appending) existing else 0L

            var lastReported = -1L
            fun report(downloaded: Long) {
                // 节流：距上次上报不足 PROGRESS_STEP_BYTES 且百分比没跨档就不刷新，
                // 减少界面重组；downloaded 没变直接跳过。
                if (downloaded == lastReported) return
                val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else -1
                val lastPercent = if (lastReported >= 0 && total > 0) {
                    ((lastReported * 100) / total).toInt().coerceIn(0, 100)
                } else {
                    -1
                }
                if (lastReported >= 0 && percent == lastPercent &&
                    downloaded - lastReported < PROGRESS_STEP_BYTES
                ) {
                    return
                }
                lastReported = downloaded
                _state.value = InstallState.Downloading(
                    version = version,
                    percent = percent,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                )
            }
            report(startOffset)

            connection.inputStream.use { input ->
                RandomAccessFile(target, "rw").use { output ->
                    if (!appending) output.setLength(0)
                    output.seek(startOffset)
                    var downloaded = startOffset
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (!coroutineContextIsActive()) return false
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        report(downloaded)
                    }
                    output.fd.sync()
                }
            }

            // 拿得到期望大小时校验完整性；对不上说明又断了，下一轮用 Range 续传
            val expected = if (total > 0) total else asset.sizeBytes
            if (expected > 0 && target.length() != expected) {
                // 这轮没下完整：如果中途发现源不支持续传，把这个事实带回给重试循环
                if (detectedNoResume) throw IOException(NO_RESUME_MARKER)
                return false
            }
            return target.length() > 0
        } finally {
            connection.disconnect()
        }
    }

    /** 当前协程是否还活着（被取消就尽快退出，不写坏文件）。 */
    private suspend fun coroutineContextIsActive(): Boolean =
        kotlinx.coroutines.currentCoroutineContext().isActive

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
        const val CONNECT_TIMEOUT_MS = 20_000

        /**
         * 读超时给足：切到后台再切回来时，系统可能让连接僵住一段时间，
         * 设太短会误判失败（用户看到的就是"下载失败"）。
         */
        const val READ_TIMEOUT_MS = 60_000
        const val BUFFER_SIZE = 64 * 1024

        /** 最多重试次数；配合退避，总计能扛住几十秒的网络中断。 */
        const val MAX_RETRIES = 8
        const val RETRY_BASE_DELAY_MS = 1_000L

        /** 进度上报节流：累计至少 128KB 或百分比跨档才刷新一次界面。 */
        const val PROGRESS_STEP_BYTES = 128 * 1024L

        /** fetchOnce → fetchWithRetry 的内部标记：该源不支持断点续传（发了 Range 也回 200）。 */
        const val NO_RESUME_MARKER = "NO_RESUME"
    }
}
