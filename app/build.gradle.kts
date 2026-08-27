import java.util.Properties

plugins {
    alias(libs.plugins.nursemate.android.application)
    alias(libs.plugins.nursemate.android.application.compose)
    alias(libs.plugins.nursemate.hilt)
}

// 릴리스 서명 주입 — 우선순위: secrets.properties > 환경변수. keystore는 certificates 레포 보관 (docs/RELEASE.md)
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun secret(key: String): String? = secrets.getProperty(key) ?: System.getenv(key)

android {
    namespace = "app.nursemate"

    // 설정 화면에서 versionName을 표시한다
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "app.nursemate"
        // 증가 정책은 docs/RELEASE.md — versionCode는 Play 업로드마다 +1, versionName은 SemVer
        versionCode = 1
        versionName = "0.1.0"
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
