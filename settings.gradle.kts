pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "simbio-don"

// 순수 Kotlin 핵심 로직 (예산 엔진). Android 앱 모듈(:app)은 이후 추가.
include(":core")
