plugins {
    alias(libs.plugins.nursemate.android.library)
    alias(libs.plugins.nursemate.hilt)
}

android {
    namespace = "app.nursemate.core.vision"
}

dependencies {
    // 온디바이스 검출 (RF-DETR-Seg-Small).
    //
    // 프리빌트 Maven 배포본을 쓴다. WebGPU EP가 포함된 커스텀 빌드가 CPU 대비 1.75배
    // 빠르지만(1550 → 845 ms), 11 MB 바이너리를 저장소에 넣어야 하고 minSdk를 28로
    // 올려야 해서 채택하지 않았다. 근거는 NM-396 스파이크 참조.
    implementation(libs.onnxruntime.android)

    // 촬영 사진의 EXIF 회전을 픽셀에 굽는 데 쓴다.
    implementation(libs.androidx.exifinterface)

    implementation(libs.kotlinx.coroutines.android)
}
