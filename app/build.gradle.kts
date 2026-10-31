import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------- 版本号：VERSION 与 BUILD_NUMBER 两个文件共同决定 ----------
// VERSION 是版本名（X.Y.Z，第二、三段 0-99，满 100 进位），
// BUILD_NUMBER 是单调递增的 versionCode；两者都由构建脚本统一维护。
val versionFile = rootProject.file("VERSION")
val buildNumberFile = rootProject.file("BUILD_NUMBER")
val appVersionName = if (versionFile.exists()) versionFile.readText().trim() else "0.0.0"
val appVersionCode = if (buildNumberFile.exists()) buildNumberFile.readText().trim().toInt() else 1

// ---------- 正式签名：从本地签名配置读取，缺省时不签名 ----------
val signingPropertiesFile = rootProject.file("signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = signingProperties.getProperty("storeFile").isNullOrBlank().not()

// 本地临时测试用：显式允许时退回 debug 证书，签名与正式包不同，不能对外分发。
val allowDebugSign = (findProperty("allowDebugSign") as String?)?.toBoolean() == true

// 发布脚本用：打开 ABI 拆分，产出每个 CPU 架构一个 APK + 一个 universal APK。
val abiSplits = (findProperty("abiSplits") as String?)?.toBoolean() == true

android {
    namespace = "com.overtimelog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.overtimelog"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = false
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    splits {
        abi {
            isEnable = abiSplits
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            // R8 代码压缩 + 资源裁剪。实测：debug 13.3 MB -> release 1.8 MB。
            // 应急开关：./gradlew :app:assembleRelease -PdisableR8=true
            val r8Enabled = (findProperty("disableR8") as String?)?.toBoolean() != true
            isMinifyEnabled = r8Enabled
            isShrinkResources = r8Enabled
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // 签名优先级：正式 keystore > 显式允许的 debug 证书 > 不签名。
            // 没有正式签名时产出的是 unsigned 包，装不上；发布流程会在打包前直接拦住。
            signingConfig = when {
                // 显式允许时才用调试证书（只给本地测试脚本），否则永远优先正式签名
                allowDebugSign -> signingConfigs.getByName("debug")
                hasReleaseSigning -> signingConfigs.getByName("release")
                else -> null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 设置页要显示真实版本号（versionName / versionCode）
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// Room 用 KSP 生成 DAO 实现；schema 暂不导出（V1 只有 version = 1，迁移测试后续补）。
ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:calculation"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
