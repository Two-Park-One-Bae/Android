plugins {
    alias(libs.plugins.nursemate.android.library)
    alias(libs.plugins.nursemate.hilt)
}

android {
    namespace = "app.nursemate.core.vision"

    /*
     * 계측 테스트 APK 의 ABI 를 하나로 줄이는 탈출구 — **Firebase Test Lab 에 올릴 때만** 쓴다.
     *
     * OpenCV 와 ONNX Runtime 이 ABI 4개분 네이티브를 싣고 와서 테스트 APK 가 267MB 다.
     * 올리는 데만 한참 걸리는데 Test Lab 실기기는 전부 arm64 다.
     *
     * ```
     * ./gradlew :core:vision:assembleDebugAndroidTest -Pnm.abi=arm64-v8a
     * ```
     *
     * ⚠️ 속성을 주지 않으면 평소대로 ABI 전부다 — CI·로컬 빌드는 영향받지 않는다.
     */
    providers.gradleProperty("nm.abi").orNull?.let { abi ->
        defaultConfig.ndk.abiFilters.add(abi)
    }

    /*
     * 테스트 메서드마다 프로세스를 새로 띄운다 (NM-577).
     *
     * 두 가지를 막는다.
     *  - **세그폴트가 나머지를 날리는 것.** Adreno 에서 각인이 죽으면 그 뒤 EP 를 하나도 못 쟀다.
     *  - **테스트끼리 메모리를 물려받는 것.** 한 프로세스에서 119MB 검출 모델을 EP 마다 열면
     *    뒤쪽 테스트가 압박을 받아 메시지도 없이 죽는다(2026-10-13 실측).
     *
     * Test Lab 은 `--use-orchestrator`, 로컬은 이 설정이 같은 일을 한다.
     */
    testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"
}

/*
 * 에뮬레이터에서만 OpenCV 를 내려 쓰는 탈출구.
 *
 * ## 왜 필요한가 — 에뮬레이터가 CPU 를 잘못 광고한다
 * Apple Silicon 의 Android 에뮬레이터는 `/proc/cpuinfo` 에 **`sve2` 를 내걸면서 커널에는 SVE 를
 * 켜 주지 않는다**(`/proc/sys/abi/sve_default_vector_length` 가 없다). OpenCV 4.12 가 물고 있는
 * KleidiCV 0.5.0 이 그 광고를 믿고 SVE 명령(`addvl`)을 실행해 **`SIGILL` 로 죽는다.**
 * 에뮬레이터 결함이지 우리 문제도 OpenCV 문제도 아니다 — 실기기에서는 멀쩡히 돈다.
 *
 * 에뮬레이터 이미지를 바꿔도(API 35·36 동일), 에뮬레이터를 올려도(37.1.11 이 최신),
 * `-qemu -cpu max,sve=off` 로도(`Property '.sve' not found`) 풀리지 않는다.
 *
 * ## 왜 빌드 타입이 아니라 속성인가
 * `debug` 는 4.11, `release` 는 4.12 로 가르면 **평소 테스트하는 것과 출시하는 것이 달라진다.**
 * 이 저장소는 그 부류의 사고를 이미 한 번 겪었다(R8 이 카카오 SDK 필드명을 바꿔 릴리스만
 * 전 사용자 크래시 — `docs/RELEASE.md`). 그래서 **기본값은 언제나 4.12** 고, 에뮬에서 각인을
 * 돌려 볼 때만 개발자가 손으로 내린다.
 *
 * ```
 * ./gradlew installDebug -Pnm.opencv=4.11.0     # 에뮬에서 각인을 볼 때만
 * ```
 *
 * CI·릴리스·실기기는 이 속성을 주지 않으므로 항상 카탈로그 값(4.12.0)이다.
 *
 * ⚠️ 내려 써도 **각인 판독 결과는 같다** — 4.11 과 4.12 가 전처리 픽스처 144장에 대해
 * 바이트 단위로 같은 값을 낸다(`ImprintPreprocessParityTest`, 실기기 확인). 두 판이 갈리는
 * 것은 속도와 SVE 사용 여부뿐이다.
 */
configurations.configureEach {
    resolutionStrategy {
        providers.gradleProperty("nm.opencv").orNull?.let { force("org.opencv:opencv:$it") }
    }
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
    //
    // ⚠️ **4.11.0 으로 내리지 말 것.** 그 판이 딸려 보내는 `libc++_shared.so` 가 4KB 정렬이라
    // 16KB 페이지 기기에서 호환성 경고가 뜨고, **2027-02-01 부터 Play 업데이트가 막힌다**
    // (Android 15+ 타깃 대상). 4.12.0 부터 그 파일이 16KB 다. 에뮬레이터에서만 예외적으로
    // 내려 쓰는 길은 위 `resolutionStrategy` 참고.
    implementation(libs.opencv)

    // 촬영 사진의 EXIF 회전을 픽셀에 굽는 데 쓴다.
    implementation(libs.androidx.exifinterface)

    implementation(libs.kotlinx.coroutines.android)

    // 각인 전처리 대조(NM-485). OpenCV 네이티브가 필요해 JVM 테스트로는 못 돌린다.
    // ⚠️ **debug 를 상대로 돈다** — R8 을 거치지 않으므로 하네스 클래스가 사라지는 문제가 없다.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)

    // 메서드마다 프로세스를 새로 띄운다 — Adreno 에서 세그폴트가 나도 나머지 EP 를 끝까지 잰다.
    // Test Lab 은 --use-orchestrator, 로컬은 android.testInstrumentationRunnerArguments.clearPackageData 와 함께.
    androidTestUtil(libs.androidx.test.orchestrator)
}
