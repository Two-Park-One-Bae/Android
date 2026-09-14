plugins {
    alias(libs.plugins.nursemate.android.library)
    alias(libs.plugins.nursemate.hilt)
}

android {
    namespace = "app.nursemate.core.data"
}

dependencies {
    api(projects.core.model)
    // 토큰 공급자 인터페이스만 가져다 구현한다. 반대 방향(network → data)이면 순환이 된다.
    implementation(projects.core.network)

    // 기기 식별자를 DataStore 에 둔다(DeviceIdRepository).
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // 인증 세션. google-services 플러그인은 :app 에만 적용한다 —
    // FirebaseApp 은 앱 프로세스에서 한 번 초기화되고 여기서는 그 인스턴스를 쓰기만 한다.
    api(platform(libs.firebase.bom))
    api(libs.firebase.auth)
    // App Check 토큰 조회 API. provider 설치(빌드 타입별)는 :app 이 한다.
    api(libs.firebase.appcheck)
    implementation(libs.kotlinx.coroutines.play.services)
}
