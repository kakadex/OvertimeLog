// 仓库配置：国内镜像优先，官方源兜底。
// 依赖解析全部走阿里云镜像；只有镜像缺某个版本时才会回落到 google()/mavenCentral()。
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "OvertimeLog"

// :app 负责组装与 UI；:core:model 与 :core:calculation 是纯 Kotlin/JVM 模块，
// 不依赖 Android SDK，因此计算规则可以用普通 JVM 单测覆盖（架构文档第 3 节）。
include(":app")
include(":core:model")
include(":core:calculation")
