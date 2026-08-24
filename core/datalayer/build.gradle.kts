plugins {
    alias(libs.plugins.nursemate.android.library)
}

android {
    namespace = "app.nursemate.core.datalayer"
}

dependencies {
    api(projects.core.model)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
}
