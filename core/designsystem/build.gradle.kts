plugins {
    alias(libs.plugins.nursemate.android.library)
    alias(libs.plugins.nursemate.android.library.compose)
}

android {
    namespace = "app.nursemate.core.designsystem"
}

dependencies {
    implementation(libs.androidx.compose.material3)
}
