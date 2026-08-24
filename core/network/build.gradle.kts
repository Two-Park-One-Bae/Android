import java.util.Properties

plugins {
    alias(libs.plugins.nursemate.android.library)
    alias(libs.plugins.nursemate.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// BASE_URL 주입 — 우선순위: secrets.properties > 환경변수. 하드코딩 금지 (iOS Secrets.xcconfig 방식 미러)
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun secret(key: String): String = secrets.getProperty(key) ?: System.getenv(key) ?: ""

android {
    namespace = "app.nursemate.core.network"

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"${secret("BASE_URL_DEBUG")}\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"${secret("BASE_URL_RELEASE")}\"")
        }
    }
}

dependencies {
    api(projects.core.model)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
