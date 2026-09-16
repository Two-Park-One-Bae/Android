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
        // ⚠️ **versionName 은 폰과 같아야 한다** — docs/RELEASE.md. 한쪽만 올리면 어느 코드가
        // 어느 기기에 깔렸는지 기기에서 구분할 수 없다(실제로 폰 0.2.0 / 워치 0.1.0 이 되어
        // 새 빌드를 깔고도 옛 버전으로 보였다).
        versionCode = 1_000_000_007
        versionName = "0.2.4"
    }

    buildTypes {
        debug {
            // ⚠️ **폰과 접미사를 맞춘다.** Data Layer 는 패키지명이 같은 앱끼리만 주고받는다.
            // 폰 디버그가 `app.nursemate.debug` 인데 워치가 `app.nursemate` 면, 붙어 있어도
            // 스냅샷도 명령도 도착하지 않는다 — 실기기에서 확인했다.
            applicationIdSuffix = ".debug"
        }
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
    // 워치도 폰과 같은 저장·상태 전이·복제 코드를 쓴다. 자기가 누구인지만 다르게 준다.
    implementation(projects.core.timer)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.ongoing)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
}
