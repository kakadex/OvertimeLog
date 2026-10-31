package com.overtimelog.domain.repository

import com.overtimelog.domain.model.RemoteRelease

/** 读取远端最新版本。实现放在 data 层（HTTP），这里只约定"拿最新发布"这一件事。 */
interface UpdateRepository {

    /**
     * 取最新发布版本。
     *
     * 约定：Gitee 优先，失败自动回落到 GitHub；两边都失败时返回失败原因。
     * 只读公开接口，不带 token，不上传任何本机数据。
     *
     * 返回的 [RemoteRelease.assets] 里带着该版本的全部附件直链，
     * 上层据此按手机 CPU 架构挑出对应 APK 直接下载安装。
     */
    suspend fun fetchLatestRelease(): Result<RemoteRelease>
}
