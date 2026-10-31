package com.overtimelog.domain.model

/** 更新信息来自哪个站点。Gitee 优先（国内直连快），连不上才用 GitHub。 */
enum class UpdateSource { GITEE, GITHUB }

/** 发布页上的一个附件（APK、AAB、校验清单等）。 */
data class ReleaseAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long = 0L,
)

/** 远端发布页上的一条版本信息。 */
data class RemoteRelease(
    val version: AppVersion,
    val tag: String,
    /** 对应 tag 的发布页地址，作为"打不开直链"时的兜底入口。 */
    val pageUrl: String,
    val source: UpdateSource,
    /** 发布页上的全部附件，用来按手机 CPU 架构挑出对应的 APK 直链。 */
    val assets: List<ReleaseAsset> = emptyList(),
) {

    /**
     * 按手机支持的 ABI 顺序挑一个 APK。
     *
     * 顺序：先按 [supportedAbis] 的顺序找 `-android-<abi>.apk`（arm64-v8a 在绝大多数手机上排第一），
     * 找不到再退到通用包 `-android-universal.apk`，最后才用任意一个 APK。
     * 返回 null 表示这个发布里没有任何 APK，界面要退回"打开发布页"。
     */
    fun pickApk(supportedAbis: List<String>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        supportedAbis.forEach { abi ->
            val exact = apks.firstOrNull { it.name.endsWith("-android-$abi.apk", ignoreCase = true) }
            if (exact != null) return exact
        }
        apks.firstOrNull { it.name.endsWith("-android-universal.apk", ignoreCase = true) }?.let { return it }
        return apks.first()
    }
}

/**
 * 检查更新的结果，同时也是设置页要显示的状态。
 *
 * 状态放在一处（[com.overtimelog.data.update.UpdateChecker]），
 * 这样手动检查完切走再回来、或者启动时静默检查完再进设置页，看到的是同一份结果。
 */
sealed interface UpdateState {

    /** 还没检查过。 */
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(val release: RemoteRelease) : UpdateState

    /**
     * 检查失败。
     *
     * [silent] 为 true 表示这次是启动时自动检查的，设置页不显示这条失败，
     * 免得用户没主动点也看到一句报错。
     */
    data class Failed(
        val message: String,
        val silent: Boolean = false,
    ) : UpdateState
}

/** 应用内下载并安装新版本的过程。设置页和首页共用同一份，避免两个入口各下载一遍。 */
sealed interface InstallState {

    data object Idle : InstallState

    /**
     * 正在下载。[percent] 为 -1 表示服务器没给文件大小，界面显示"下载中…"而不是百分比。
     */
    data class Downloading(
        val version: AppVersion,
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : InstallState {
        val percentText: String get() = if (percent in 0..100) "$percent%" else "下载中…"
    }

    /** 已经拉起系统安装界面。 */
    data object Installing : InstallState

    data class Failed(val message: String) : InstallState
}
