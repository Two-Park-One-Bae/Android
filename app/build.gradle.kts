import java.util.Properties

plugins {
    alias(libs.plugins.nursemate.android.application)
    alias(libs.plugins.nursemate.android.application.compose)
    alias(libs.plugins.nursemate.hilt)
    // 빌드 타입별 google-services.json 을 읽어 FirebaseApp 설정을 만든다.
    //   debug   → app/src/debug/google-services.json    (Nursemate-dev  · app.nursemate.debug)
    //   release → app/src/release/google-services.json  (Nursemate-prod · app.nursemate)
    // ⚠️ 두 파일은 커밋하지 않는다(.gitignore). 새 환경에서는 Firebase 콘솔에서 받아 넣어야 빌드된다.
    alias(libs.plugins.google.services)
}

// 릴리스 서명 주입 — 우선순위: secrets.properties > 환경변수. keystore는 certificates 레포 보관 (docs/RELEASE.md)
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun secret(key: String): String? = secrets.getProperty(key) ?: System.getenv(key)

android {
    namespace = "app.nursemate"

    defaultConfig {
        applicationId = "app.nursemate"
        // 증가 정책은 docs/RELEASE.md — versionCode는 Play 업로드마다 +1, versionName은 SemVer
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        // 서명 정보가 없으면(PR CI 등) release는 미서명으로 빌드된다 — Play 업로드 산출물은 로컬/릴리스 CI에서만
        val storeFilePath = secret("RELEASE_STORE_FILE")
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Play 비공개 테스트 빌드와 **나란히** 설치되게 패키지를 분리한다.
            // 같은 applicationId 면 서명이 달라 INSTALL_FAILED_UPDATE_INCOMPATIBLE 이 나고,
            // 개발하려면 테스터가 쓰고 있는 앱을 지워야 한다.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // App Check provider 는 빌드 타입별로 하나씩만 넣는다.
    //   debug   → DebugAppCheckProvider  (Play 스토어 밖이라 Play Integrity 가 통하지 않는다)
    //   release → PlayIntegrityAppCheckProvider
    // 둘을 같이 넣으면 release 에서도 디버그 토큰으로 통과할 수 있어 App Check 가 무의미해진다.
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
