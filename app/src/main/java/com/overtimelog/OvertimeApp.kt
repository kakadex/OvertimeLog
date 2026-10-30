package com.overtimelog

import android.app.Application
import com.overtimelog.di.AppContainer

/**
 * 应用入口。只做一件事：建好依赖容器。
 *
 * 全局单例都挂在 [container] 上，Activity / ViewModel 从这里取依赖，不自己 new 数据库。
 */
class OvertimeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
