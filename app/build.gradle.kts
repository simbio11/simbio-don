plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.simbio.don.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simbio.don"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "0.20.0"
    }

    // CI에서 빌드할 때마다 서명이 바뀌면 폰에 덮어 설치가 안 되고 데이터가 지워진다.
    // 개인용 디버그 빌드라 공개돼도 되는 디버그 키를 리포에 둔다. 배포용 키는 따로 관리.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    // AI SDK가 데려오는 라이브러리들의 겹치는 설명 파일
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.md",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
    sourceSets {
        // Room 마이그레이션 테스트(Robolectric)가 내보낸 스키마를 읽는다. Robolectric은 변형(debug)의 assets만 본다
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    // AI 도우미 (켤 때만, 사용자 키로): Anthropic 공식 SDK
    implementation("com.anthropic:anthropic-java:2.34.0")
    // SDK의 JSON 처리(jackson-module-kotlin)가 쓰는 reflect를 우리 Kotlin과 같은 판으로
    implementation(kotlin("reflect"))

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:$room")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ./gradlew :app:testDebugUnitTest -Pscreenshots → app/build/screenshots/*.png
tasks.withType<Test>().configureEach {
    if (project.hasProperty("screenshots")) {
        systemProperty("screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.path)
        outputs.upToDateWhen { false }
    }
    // ./gradlew :app:testDebugUnitTest --tests '*WidgetsTest*' -PwidgetSamples → 위젯 고르기 미리보기 그림을 다시 만든다
    if (project.hasProperty("widgetSamples")) {
        systemProperty("widgetSamples", file("src/main/res/drawable-nodpi").path)
        outputs.upToDateWhen { false }
    }
}
