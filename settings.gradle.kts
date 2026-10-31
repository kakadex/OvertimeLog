// 仓库配置：默认"本地优先国内镜像，官方源兜底"。
// 但在 CI（GitHub Actions 跑在海外）上，阿里云镜像经常连不通或超时，
// 会导致 "Plugin [id: 'com.google.devtools.ksp' ...] was not found" 这类假报错。
// 所以 CI 环境直接把官方源排在最前面；本地仍优先走镜像（下载快）。
//
// 注意：pluginManagement 块优先于脚本其余部分求值，块内不能引用外部变量，
// 所以两个块里各自判断一次 CI 环境变量。

pluginManagement {
    val onCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true
    repositories {
        if (onCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        if (!onCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val onCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (onCi) {
            google()
            mavenCentral()
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        if (!onCi) {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "OvertimeLog"

// :app 负责组装与 UI；:core:model 与 :core:calculation 是纯 Kotlin/JVM 模块，
// 不依赖 Android SDK，因此计算规则可以用普通 JVM 单测覆盖（架构文档第 3 节）。
include(":app")
include(":core:model")
include(":core:calculation")
