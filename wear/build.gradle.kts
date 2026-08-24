plugins {
    alias(libs.plugins.nursemate.android.application)
    alias(libs.plugins.nursemate.android.application.compose)
}

android {
    namespace = "app.nursemate.wear"

    defaultConfig {
        // 폰과 동일 applicationId — Wear 별도 APK 배포 요건 (동일 서명 키 필수)
        applicationId = "app.nursemate"
        // Ongoing Activity API 하한 = Wear OS 3 (API 30)
        minSdk = 30
        // 폼팩터 간 versionCode 충돌 방지 오프셋 (폰 versionCode + 1_000_000_000)
        versionCode = 1_000_000_001
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.datalayer)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
}
