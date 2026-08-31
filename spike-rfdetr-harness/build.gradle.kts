/*
 * NM-396 스파이크 PoC — 프로덕션 모듈이 아니다.
 *
 * 목적: RF-DETR-Seg를 Android에서 **실제로 써보면서** 체감 성능을 재는 것.
 * 합성 벤치마크는 출력 마샬링 오버헤드·발열·실행 순서에 오염되기 쉬워서,
 * 촬영 → 추론 → BBOX 표시까지 손으로 돌려보는 쪽이 판단에 더 쓸모 있다.
 *
 * 모델은 APK에 넣지 않는다(125 MB). 앱 전용 외부 저장소에 adb push 해서 읽는다:
 *   adb push rfdetr_seg_small.onnx /sdcard/Android/data/app.nursemate.spike.rfdetr/files/
 *
 * 스파이크 종료 후 이 모듈은 제거하거나 core:vision 으로 승격한다.
 */
plugins {
    alias(libs.plugins.nursemate.android.application)
    alias(libs.plugins.nursemate.android.application.compose)
}

android {
    namespace = "app.nursemate.spike.rfdetr"

    defaultConfig {
        applicationId = "app.nursemate.spike.rfdetr"
        versionCode = 1
        versionName = "0.1.0-spike"

        // 직접 빌드한 ORT AAR이 --android_api 28 로 만들어져 minSdk 28을 요구한다.
        // 스파이크 전용이라 여기서만 올린다(프로덕션 :app 은 26 유지).
        minSdk = 28
    }

    buildTypes {
        // 스파이크라 릴리스로 나갈 일이 없다. 최적화 끄고 빠르게 빌드한다.
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)

    // 온디바이스 추론. 현규님이 준 rfdetr_seg_small.onnx (CoreML을 만든 원본 그래프)를 쓴다.
    //
    // ⚠️ 프리빌트(Maven) 대신 **직접 빌드한 AAR**을 쓴다.
    // 프리빌트 onnxruntime-android에는 WebGPU EP가 컴파일돼 있지 않다
    // (ORT 문서상 WebGPU 프리빌트는 Python/NuGet만 제공, Android는 소스 빌드 필요).
    // 이 AAR은 ORT v1.29.0을 --use_webgpu 로 arm64-v8a 빌드한 것이다.
    // 재현: onnxruntime 소스에서 JDK 17 + NDK 29 로
    //   ./build.sh --android --android_abi arm64-v8a --android_api 28 \
    //     --use_webgpu --build_shared_lib --build_java --config Release
    implementation(files("libs/onnxruntime-webgpu.aar"))

    // 병렬 전처리가 순차 실행과 동일한 값을 내는지 검증하는 단위 테스트용
    testImplementation(libs.junit)
}
