plugins {
    alias(libs.plugins.nursemate.android.library)
    alias(libs.plugins.nursemate.android.library.compose)
}

android {
    namespace = "app.nursemate.core.designsystem"
}

dependencies {
    implementation(libs.androidx.compose.material3)

    // V2.1.0 컴포넌트 캡처(V2GalleryCaptureTest) — 정본과 나란히 보는 용도다.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
