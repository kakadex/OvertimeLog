package com.overtimelog.domain.model

/** 更新信息来自哪个站点。Gitee 优先（国内直连快），连不上才用 GitHub。 */
enum class UpdateSource { GITEE, GITHUB }

/** 远端发布页上的一条版本信息。 */
data class RemoteRelease(
    val version: AppVersion,
    val tag: String,
    /** 对应 tag 的发布页地址，点"去下载"直接打开它。 */
    val pageUrl: String,
    val source: UpdateSource,
)

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
