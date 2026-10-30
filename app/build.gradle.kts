plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.overtimelog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.overtimelog"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 默认开启 R8 代码压缩 + 资源裁剪。实测：debug 13.3 MB -> release 1.8 MB。
            //
            // 出问题时的应急开关（不改代码）：
            //   ./gradlew :app:assembleRelease -PdisableR8=true
            // 关掉后体积回到 9 MB 左右，功能与 debug 一致，用来快速判断"是不是 R8 裁错了"。
            val r8Enabled = (findProperty("disableR8") as String?)?.toBoolean() != true
            isMinifyEnabled = r8Enabled
            isShrinkResources = r8Enabled
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
