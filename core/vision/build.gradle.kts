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

    /*
     * QNN 런타임에서 **쓰지 않는 것**을 덜어낸다 — NM-577.
     *
     * `qnn-runtime` 이 arm64 비압축 214MB 다. 그중 실제로 필요한 것은 넷뿐이다.
     *  - `libQnnHtp.so` · `libQnnSystem.so` — 백엔드와 그래프 직렬화
     *  - `libQnnHtpPrepare.so`(84MB) — **기기에서 ONNX 그래프를 컴파일**할 때만 쓴다.
     *    `ep.context_file_path` 로 컴파일 결과를 캐시하면 둘째 실행부터는 안 들어가고,
     *    미리 컴파일한 EPContext 모델을 싣는다면 아예 빼도 된다.
     *  - `libQnnHtpV##Skel/Stub.so` — HTP 세대별. Skel 은 DSP 쪽에서 돌고 앱이 실어 줘야 한다.
     *
     * 덜어내는 것:
     *  - `libQnnGpu.so`(11.8MB) — QNN 의 OpenCL 백엔드. 우리 GPU 길은 ORT WebGPU 다.
     *  - `libQnnDsp*.so`(4.3MB) — Hexagon **DSP(v66)**, 스냅드래곤 855 세대. 대상에 없다.
     *  - `V68`·`V69` skel(25.9MB) — 865 · 888 세대. 대상에 없다.
     *
     * 남기는 세대: **V73**(8 Gen 2 · S23) · **V75**(8 Gen 3 · S24) · **V79**(8 Elite · S25) ·
     * **V81**(그다음). 기기의 `htp_arch` 와 안 맞으면 세션 생성부터 실패하므로 넉넉히 둔다.
     */
    /*
     * `.so` 를 APK **밖으로 풀어** 설치한다 — QNN 이 그걸 요구한다(NM-577).
     *
     * AGP 기본은 `useLegacyPackaging=false` 다. 그래도 `dlopen` 은 되는데(ORT 가 그렇게 돈다)
     * HTP 는 다르다. DSP 쪽 skel(`libQnnHtpV73Skel.so`)은 **cDSP 데몬이 파일 경로로** 연다 —
     * `base.apk!/lib/arm64-v8a/...` 를 열 수 없어 `errno 2` 로 실패하고, 그러면 QNN EP 가
     * 노드를 하나도 안 가져가 **전부 CPU 로 돌면서 테스트는 통과한다.**
     *
     * ⚠️ 설치 용량이 늘어난다. 운영에 QNN 을 넣을 때는 필요한 skel 하나만 런타임에 꺼내
     * 쓰는 쪽이 낫다 — 지금은 측정이라 통째로 푼다.
     */
    packaging.jniLibs.useLegacyPackaging = true

    packaging.jniLibs.excludes += setOf(
        "**/libQnnGpu.so",
        "**/libQnnDsp.so",
        "**/libQnnDspV66Skel.so",
        "**/libQnnDspV66Stub.so",
        "**/libQnnHtpV68Skel.so",
        "**/libQnnHtpV68Stub.so",
        "**/libQnnHtpV69Skel.so",
        "**/libQnnHtpV69Stub.so"
    )
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
    // 온디바이스 추론 — 검출·각인·마크 셋 다 이 런타임으로 돈다.
    //
    // 프리빌트 Maven 배포본에 **CPU · WebGPU · NNAPI · XNNPACK** 이 들어 있다. NM-396 스파이크
    // 때는 WebGPU 를 쓰려면 소스 빌드가 필요했는데 그 뒤 프리빌트에 들어왔다 — 지금은 WebGPU 가
    // 운영 경로다(`OrtBackend`). EP 별 실측은 같은 KDoc 의 비교표.
    implementation(libs.onnxruntime.android)

    // 퀄컴 Hexagon NPU(HTP) — **컴파일만 한다.** 실제 `.so` 와 클래스는 조건부 전달 모듈
    // `:qnn` 이 들고 있고, Play 가 퀄컴 기기에만 설치한다.
    //
    // 위 표준 배포본을 그대로 두고 **덧붙는 플러그인 EP** 라서(카탈로그 주석에 근거) base 의
    // ORT 를 건드리지 않는다 — 그래서 모듈 하나를 더하고 빼는 것으로 갈라진다.
    //
    // ⚠️ `compileOnly` 다. 런타임에 없을 수 있으므로 **부르기 전에 [QNN_BUNDLED] 를 본다.**
    //    그냥 부르면 NoClassDefFoundError 다.
    compileOnly(libs.onnxruntime.qnn.plugin)

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

    // 측정 하네스는 QNN 을 **실제로** 돌려야 한다. 운영 경로는 `:qnn` 모듈이 받지만 이
    // 테스트 APK 는 그 모듈을 못 쓰므로(계측 테스트는 base 만 본다) 여기서 직접 문다.
    // 운영 APK 에는 들어가지 않는다 — androidTest 구성이다.
    androidTestImplementation(libs.onnxruntime.qnn.plugin)
    androidTestImplementation(libs.qnn.runtime)

    // 메서드마다 프로세스를 새로 띄운다 — Adreno 에서 세그폴트가 나도 나머지 EP 를 끝까지 잰다.
    // Test Lab 은 --use-orchestrator, 로컬은 android.testInstrumentationRunnerArguments.clearPackageData 와 함께.
    androidTestUtil(libs.androidx.test.orchestrator)

    // LiteRT — **계측 테스트에만** 넣는다. 운영 APK 에는 안 들어간다.
    // ONNX Runtime 을 대체하려는 것이 아니라, WebGPU 가 못 가는 기기(Mali 전멸,
    // Adreno 각인·검출 사망)에서 OpenCL GPU 델리게이트가 대안이 되는지 재는 용도다.
    androidTestImplementation(libs.litert)
    androidTestImplementation(libs.litert.gpu)
}
