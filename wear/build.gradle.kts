plugins {
    alias(libs.plugins.nursemate.android.application)
    alias(libs.plugins.nursemate.android.application.compose)
    // 폰과 같은 방식으로 의존성을 잇는다 — 스냅샷 리스너(서비스)와 화면이 **같은 상태**를
    // 봐야 해서, 전역 객체 대신 주입으로 묶는다.
    alias(libs.plugins.nursemate.hilt)
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
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.datalayer)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
}
