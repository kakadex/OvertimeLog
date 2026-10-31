package com.overtimelog.data.update

import com.overtimelog.domain.model.UpdateSource

/**
 * 发布渠道的固定信息。
 *
 * 只有公开的仓库地址和接口地址，不放 token；换仓库时只改这里。
 */
object ReleaseChannels {

    private const val GITEE_OWNER = "kakadex"
    private const val GITEE_REPO = "overtimelog"
    private const val GITHUB_OWNER = "kakadex"
    private const val GITHUB_REPO = "OvertimeLog"

    /** 检查更新用：Gitee 的"最新发布"接口（公开只读，不需要 token）。 */
    val GITEE_API_LATEST = "https://gitee.com/api/v5/repos/$GITEE_OWNER/$GITEE_REPO/releases/latest"

    /** 检查更新兜底：GitHub 的"最新发布"接口（未登录每小时 60 次，够用）。 */
    val GITHUB_API_LATEST = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /** 下载入口：发布页里能下最新 APK、历史版本和校验清单。 */
    val GITEE_RELEASES_PAGE = "https://gitee.com/$GITEE_OWNER/$GITEE_REPO/releases"
    val GITHUB_RELEASES_PAGE = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    /** 某个 tag 对应的发布页地址。 */
    fun tagPage(tag: String, source: UpdateSource): String = when (source) {
        UpdateSource.GITEE -> "https://gitee.com/$GITEE_OWNER/$GITEE_REPO/releases/tag/$tag"
        UpdateSource.GITHUB -> "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/$tag"
    }
}
