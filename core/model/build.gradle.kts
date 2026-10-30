plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 纯 Kotlin/JVM：不引 Android 插件，保证这里的模型能被普通 JVM 单测直接引用。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit4)
}
