// 루트 빌드 파일 — 플러그인 버전만 클래스패스에 올린다. 로직은 build-logic/convention에.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// 포맷 — 파일 패턴 기반이라 AGP built-in Kotlin과 무관하게 동작 (규칙은 .editorconfig)
spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "spec/**")
        ktlint(libs.versions.ktlint.get())
    }
}

// 정적 분석 — 타입 해석 없는 소스 모드 (루트에서 전 모듈 일괄)
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "app/src",
            "wear/src",
            "core/model/src",
            "core/data/src",
            "core/network/src",
            "core/vision/src",
            "core/datalayer/src",
            "core/designsystem/src",
            "build-logic/convention/src"
        )
    )
}
