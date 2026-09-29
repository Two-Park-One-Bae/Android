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

    // 각인 OCR 전처리(NM-485) — CLAHE(clip 3.0, 8×8) · morphologyEx(CLOSE) ·
    // connectedComponents · floodFill · warpAffine · resize(INTER_AREA).
    //
    // 손으로 옮기는 길도 재 봤다. 아홉 연산 중 여덟은 자명하고 CLAHE 하나만 까다로운데,
    // **기준 구현이 곧 OpenCV** 라 그쪽을 쓰면 「같은 답」이 구현의 부산물이고 손으로 옮기면
    // 증명해야 할 과제가 된다. 틀렸을 때도 조용하다 — CLAHE 가 미세하게 달라지면 확률이
    // 흔들리고 0.949 채택 임계가 뒤집혀 각인이, 그다음 후보가 달라진다. 크래시가 아니다.
    //
    // 실측 대가는 arm64-v8a 다운로드 **+8.9MB**(131.0 → 140.3MB, bundletool get-size).
    // 모델까지 들어가면 OpenCV 유무와 무관하게 200MB 근처라 이것이 분기점은 아니다.
    implementation(libs.opencv)

    // 촬영 사진의 EXIF 회전을 픽셀에 굽는 데 쓴다.
    implementation(libs.androidx.exifinterface)

    implementation(libs.kotlinx.coroutines.android)
}
