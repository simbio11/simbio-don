pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "simbio-don"

// 순수 Kotlin 핵심 로직 (예산 엔진·알림 파서)
include(":core")
// Android 앱
include(":app")
