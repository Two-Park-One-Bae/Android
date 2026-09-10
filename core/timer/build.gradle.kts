plugins {
    alias(libs.plugins.nursemate.android.library)
    alias(libs.plugins.nursemate.hilt)
}

android {
    namespace = "app.nursemate.core.timer"
}

dependencies {
    api(projects.core.model)
    // 복제본을 주고받는 통로. 타이머 계층이 폰·워치 어느 쪽에 있든 같은 방식으로 맞춘다.
    implementation(projects.core.datalayer)

    implementation(libs.androidx.datastore.preferences)
    // 타이머·프리셋을 DataStore 에 JSON 블롭으로 넣는다(TimerStore). 직렬화 코드 생성은
    // core:model 이 하고, 여기서는 Json·ListSerializer 만 쓴다.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
