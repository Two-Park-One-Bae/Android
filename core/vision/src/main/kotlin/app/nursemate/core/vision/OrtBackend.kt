package app.nursemate.core.vision

// `ai.onnxruntime.qnnpluginep.*` 는 플러그인 artifact 가 들고 있는 이름이다. 손으로 적지
// 않는다 — artifact 를 올렸을 때 이름이 바뀌면 컴파일이 깨져 알게 된다.
//
// ⚠️ `compileOnly` 로 문다 — 이 클래스들은 feature module `qnn_core` 에 있고, 설치본에 따라
//    런타임에 없을 수 있다([QNN_BUNDLED]).

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import ai.onnxruntime.qnnpluginep.getEpName as qnnEpName
import ai.onnxruntime.qnnpluginep.getLibraryPath as qnnLibraryPath
import android.util.Log
import java.io.File
import java.util.EnumSet

/**
 * 세션을 **WebGPU 로 열고, 안 되면 CPU 로 되돌아간다**.
 *
 * ## 어느 EP 를 쓰는가 — **기기가 정한다**
 * 2026-10-13 실기기 전수 측정(NM-577). 사진 3장(알약 3·7·4정) · 예열 후 · 답 대조 포함.
 *
 * | | 검출 | 각인 | 마크(1장) |
 * |---|---|---|---|
 * | **Xclipse 940** (Exynos 2400) | 1575 → **810 ms** | 4376 → **2157 ms** | 650 → **206 ms** |
 * | Adreno 740 (SM8550) | 1700 → 1110 ms | **프로세스 사망** | 704 → 337 ms |
 * | Adreno 750 (SM8650) | **프로세스 사망** | 예외 | 560 → 262 ms |
 * | Mali (Tensor G4) | 예외 | 예외 | 예외 |
 *
 * WebGPU 가 되는 곳에서는 **답이 CPU 와 같다**(검출 박스·점수 최대차 1e-7). 다만 **같은
 * Adreno 안에서도 세대마다 깨지는 모델이 다르다** — 무엇이 깨질지 미리 알 수 없으므로
 * **되는 것만 켠다**(`WebGpuPolicy` 허용목록). 모르면 CPU 다.
 *
 * ## 다른 EP 는 재 보고 접었다
 * 옛 주석은 「Galaxy S24 에서 전부 재 봤다」였는데 그 한 대의 결과였다. 네 기기로 다시 쟀다.
 *
 * | 경로 | 결과 |
 * |---|---|
 * | NNAPI | **검출이 전 기기에서 `ANEURALNETWORKS_OP_FAILED`**. 각인·마크는 돌지만 CPU 보다 느리다. Android 15 에서 deprecated |
 * | XNNPACK | 전부 CPU 보다 1.5~2.5배 느리고, **마크는 네 기기 전부 오답**(종 7 → 65) |
 * | Exynos NNC | 컴파일 전체 실패 (Xclipse) |
 * | int8 | 세그는 트랜스포머 활성 이상치에 무너진다 |
 * | QNN | 접은 것이 **배포본 선택**이었다 — 아래 참조. 플러그인으로 다시 열렸다(NM-577) |
 *
 * 즉 **지금 운영의** 선택지는 CPU 와 WebGPU 둘뿐이다.
 *
 * ## QNN 은 「거래」가 아니었다 — 배포본을 잘못 봤다 (2026-10-13)
 * 옛 주석은 「프리빌트 `onnxruntime-android-qnn` 에 Dawn 이 없으니 QNN 을 쓰려면 WebGPU 를
 * 버려야 한다」였다. 그 **artifact** 에 대해서는 맞다(`com.microsoft.onnxruntime` 쪽은 ORT 를
 * 통째로 갈아 끼워 기기에서 EP 가 `CPU, QNN` 둘만 보인다). 틀린 것은 **그것이 유일한 길이라고
 * 본 것**이다.
 *
 * `com.qualcomm.qti:onnxruntime-android-qnn` 은 **플러그인 EP** 다. `libonnxruntime.so` 를 품지
 * 않고 ORT API 를 팩토리 ABI 로 넘겨받아서, 표준 배포본 옆에 그냥 얹힌다. 같은 프로세스에서
 * `registerExecutionProviderLibrary` 로 올리고 나서도 `getAvailableProviders()` 가
 * **`CPU, NNAPI, XNNPACK, WEBGPU` 를 그대로 내놓는 것**을 기기에서 확인했다(SM-S921N).
 *
 * 즉 **WebGPU 와 QNN 은 양립한다.** 기기에 따라 둘 중 되는 쪽을 고르는 선택이 가능하다 —
 * 엑시노스는 WebGPU, 스냅드래곤은 QNN. [Ep.QNN] 계열이 그 측정 경로다.
 *
 * ## QNN 실측 — Adreno 740 (SM8550 · HTP V73), 2026-10-13
 * 같은 기기에서 같은 입력으로 CPU 와 맞댄 값이다. 정답 대조 포함.
 *
 * | | 세션 생성 | 추론 | 답 |
 * |---|---|---|---|
 * | 각인 CPU | 297ms | 4206ms | YS · 점수 **0.9699** |
 * | 각인 QNN | 84.0초 | **418ms** (10.1배) | `null` · pair YS · 점수 **0.9436** |
 * | 각인 QNN fp32 강제 | 88.4초 | 435ms | 같음 (0.9436) |
 * | 각인 QNN + `cpu(/rnn/)` | 91.1초 | 447ms | 같음 (0.9436) |
 * | 마크 CPU | 222ms | 3743ms | 정답 |
 * | 마크 QNN | 8.4초 | **253ms** (14.8배) | **정답** |
 *
 * ### 각인은 「틀린」 것이 아니다 — **임계에서 0.0054 모자란다**
 * QNN 도 best_pair 를 **YS 로 맞게** 고른다. 점수가 0.9436 이고
 * [ImprintDecoding.ADOPT_THRESHOLD] 가 **0.949** 라서 채택이 안 될 뿐이다.
 * `ImprintPreprocess` KDoc 이 이 실패를 그대로 적어 뒀다 — 「미세하게 달라지면 글자 확률이
 * 흔들려 0.949 채택 임계가 뒤집힌다」. 그때는 CLAHE 얘기였고 이번엔 HTP 수치다.
 *
 ### 왜 다르게 나오는지 — **QNN 이 글자 확신도를 떨어뜨린다** (off-device 재현)
 * AI Hub 실기기 S23(SM8550, Test Lab `dm1q` 와 같은 칩)과 로컬 ORT CPU 에 **같은 입력**을 넣고
 * `ImprintDecoding` 을 그대로 다시 계산해 맞댔다. 기기에서 본 일이 그대로 재현된다.
 *
 * ```
 * CPU  점수 0.9774 · pair 'YS' · 확신글자 'YS' · 채택 'YS'
 * QNN  점수 0.9202 · pair 'YS' · 확신글자 'S'  · 채택 null
 * ```
 *
 * | 지표 | 값 |
 * |---|---|
 * | 로짓 | 최대차 1.75 · 평균차 0.28 · **PSNR 37.9 dB** |
 * | **argmax 불일치** | **0.000%** — 글자를 다르게 읽는 일은 없다 |
 * | argmax 자리 확률차 | 평균 0.0050 · 95분위 0.0203 · **최대 0.1506** |
 *
 * **평균은 0.005 인데 꼬리가 0.15 다.** [ImprintDecoding.bestPair] 가 인접 두 글자 확률의
 * **낮은 쪽**(`min`)을 쓰므로, 한 글자만 꼬리에 맞아도 점수가 주저앉는다. 위 예가 정확히
 * 그렇다 — CPU 는 `Y`·`S` 둘 다 [ImprintDecoding.SURE_THRESHOLD] 를 넘겨 `YS` 인데, QNN 은
 * `Y` 가 그 아래로 내려가 `S` 만 남았다.
 *
 * ### 「같은 변환인데 왜 각인만」 — 지표가 아니라 **모델**이다
 * 마크를 **같은 방식으로** 재서 가렸다(`scratchpad/qnn_metric.py`).
 *
 * | | 로짓 PSNR | 최대차 | argmax 일치 | 최대확률 차 |
 * |---|---|---|---|---|
 * | 마크 | **67.50 dB** | 0.0234 | 100% | 0.0007 |
 * | 각인 | **37.92 dB** | 1.7542 | 100% | **0.1506** |
 *
 * **30dB 차 = 오차 에너지 약 1000배.** 「오차는 비슷한데 지표가 민감해서」가 아니다 —
 * 마크의 0.0234 는 fp16 반올림 수준이고 각인은 자릿수가 다르다. 각인 구조가 오차를 키운다.
 *
 * 각인에만 있는 증폭기가 둘이다.
 *  1. **STN(TPS 격자)** — 기하 재샘플링이다. 격자 좌표가 틀리면 **샘플링 위치가 옮겨져** 뒤쪽이
 *     「살짝 어긋난 활성」이 아니라 **다른 그림**을 받는다. 오차가 더해지지 않고 곱해진다
 *  2. **양방향 LSTM** — 32 시점을 순환하며 누적된다
 *
 * **둘 중 어느 쪽인지도 이미 가려졌다.** [Ep.QNN_RNN_CPU] 로 LSTM 을 CPU 에 고정했을 때 점수가
 * **0.9436 그대로**였다. LSTM 에서 오차가 난다면 고쳐졌어야 한다 — 즉 오차는 **RNN 에 들어가기
 * 전에 이미 있다(STN/CNN 앞단).** 다음에 잴 것은 `cpu(/stn/)` 다.
 *
 * ⚠️ 절대값은 기기값과 다르다(CPU 0.9774 대 기기 0.9699). 고정물에 회전 90° CLAHE 패치가
 * 없어서 r0 을 90° 돌려 만들고, 144장 대신 5변형으로 채웠다. **같은 입력에서의 CPU↔QNN 차이**는
 * 그래도 유효하다 — 그것이 재는 대상이다.
 *
 * ### 이 재현이 Test Lab 을 대신한다
 * 각인 수치 문제는 이제 **실기기 할당량 없이** 돌릴 수 있다(`scratchpad/qnn_score.py`).
 * 하루 5회 제약에 묶이지 않는다.
 *
 * ### 가른 것과 못 가른 것 — 기기 측정
 * 네 설정의 점수가 **0.9436 으로 소수점 넷째 자리까지 같다.** 그중 의미가 있는 것만 적는다.
 *  - **LSTM 은 아니다** — WebGPU 를 살린 `cpu(/rnn/)` 를 걸어도 같다. 마크가 QNN 에서 정답인 것도
 *    LSTM 쪽을 가리켰는데 아니었다
 *  - **TPS 격자만 fp32 로 남긴 변형**(AI Hub 일치율 99.6%)을 대도 같다. 그 변형은 노드 360개
 *    (기준 350)에 가중치가 fp16 47 + fp32 2인 **다른 모델**이고 CPU 에서는 0.9750 으로
 *    기준(0.9699)보다 **높다**. 그런데 QNN 에서는 기준과 같다
 *
 * ⚠️ **fp16 은 아직 안 갈랐다.** 「기본」과 「`enable_htp_fp16_precision=0`」을 비교했는데,
 * 이 플러그인 저장소(`onnxruntime-qnn`) 문서가 **기본값을 0** 이라고 적는다(ORT 본가 문서는 1로
 * 적어 서로 다르다). 그러면 두 번 다 같은 설정을 잰 것이고, 시간이 418 대 435ms 로 안 움직인
 * 것도 그래서다. [Ep.QNN_FP16] 이 그 축이다 — **그걸 재기 전에는 정밀도를 배제할 수 없다.**
 *
 * ### 런타임을 나란히 — **감사셋 331면 전수** (`scratchpad/eval_production.py`)
 * ML 쪽 감사셋(사람이 면·정답을 확정한 실사진 331면)을 **생산 경로 그대로** 돌렸다 —
 * `infer.py` 의 전처리·TTA(24방향 × 6배율 = 144장)·디코딩을 그대로 쓰고 **런타임만** 갈아 끼웠다.
 * 모델은 출시본 단일 시드 `reader_ep60_s1.onnx` 다.
 *
 * | 런타임 | 채택 면 | 정답 | 정밀도 | 평균점수 | 331면 소요 |
 * |---|---|---|---|---|---|
 * | ONNX · CPU EP (기준) | 229 | 212 | 92.6% | 0.9021 | 255s |
 * | **Core ML · ANE+CPU** | **229** | **212** | **92.6%** | 0.9021 | **43s** |
 * | Core ML · CPU only | 229 | 212 | 92.6% | 0.9021 | 152s |
 *
 * 하네스가 기준과 맞는다 — `INFO.md` 의 단일 시드 값이 「정밀도 90%에서 240면/150알약」인데
 * 고정 임계 0.949 로 **229면 · 정밀도 92.6%** 가 나왔다(정밀도가 높은 지점이라 면수가 적다).
 * 같은 문서가 적은 **시드 노이즈 ±15면**이 유의성 기준이다 — EP 를 바꿔 그 안이면 차이 없음이다.
 *
 * **「NPU 는 원래 수치가 다르다」가 아니다.** 애플 뉴럴엔진은 331면에서 채택·정답·정밀도가
 * CPU 와 **완전히 같고 6배 빠르다.** 각인 한 장만 봐도 상대오차 0.001%(115dB)인데 QNN 은
 * 9.931%(37.9dB) 다 — 78dB 차이다. 9.9% 는 **HTP 고유 문제**이고 단계별 측정이 `GridSample` 을
 * 지목한 것과 맞물린다. 비교 기준을 CPU 하나로 보면 이걸 NPU 일반의 한계로 잘못 읽는다.
 *
 * ### QNN 도 같은 331면으로 쟀다 — **각인은 쓸 수 있다** (Adreno 740 · SM8550)
 * [app.nursemate.core.vision.imprint.ImprintEvalSetTest] 로 실기기에서 전수로 돌렸다.
 * 기기 쪽은 앱의 전처리·TTA·디코딩을 **그대로** 쓰고, 정답 대조만 호스트에서 한다.
 *
 * | | 면 | 채택 | 정답 | 정밀도 | 평균점수 | 면당 |
 * |---|---|---|---|---|---|---|
 * | **QNN(HTP)** | 331 | **235** | **215** | **91.5%** | 0.8964 | **427ms** |
 * | ONNX CPU(맥 기준선) | 331 | 229 | 212 | 92.6% | 0.9021 | — |
 *
 * **채택이 6면 늘고 정답이 3면 늘었다. 정밀도만 1.1%p 낮다**(오답 17 → 20건). 시드 노이즈가
 * ±15면이므로 **차이 없음 구간**이다.
 *
 * ### ⚠️ 한 장으로 본 결론은 틀렸다
 * 고정물 한 장(`sample_crop.png`)에서 QNN 이 0.9436 으로 채택에 떨어진 것을 보고 「각인이
 * QNN 에서 깨진다」고 적었다. **전수에서는 그렇지 않다.** 그 한 장이 하필 임계 바로 위
 * (CPU 0.9774)라 뒤집힌 것이고, 331면에서는 내려가는 면과 올라가는 면이 상쇄된다.
 * 임계 하나를 넘나드는 지표는 **표본 한 개로 방향조차 정할 수 없다.**
 *
 * ⚠️ CPU 기준선은 **맥에서 낸 값**이다(ORT CPU EP·`infer.py` 전처리). 전처리 동등성은
 * `ImprintPreprocessParityTest` 가 받치지만, **같은 기기 CPU 로 재지는 않았다** — 정밀도
 * 1.1%p 는 그만큼 느슨하게 읽어야 한다.
 *
 * ### 다른 EP 와의 관계 — 비교한 것과 못 한 것
 * | 상대 | 결과 |
 * |---|---|
 * | **CPU** (같은 기기·같은 입력) | 0.9699 대 **0.9436 — 다르다** |
 * | **ML 기준 구현** | 0.969883 (`ImprintReaderParityTest` · `INFO.md` 「YS 0.970」) = CPU 와 같다 |
 * | **WebGPU** | **이 기기에서는 비교 못 한다** — Adreno 740 에서 각인 모델로 프로세스가 죽는다(NM-574). |
 * | | 엑시노스에서는 돌고, 그때 `각인_WEBGPU` 가 **0.969883 ± 1e-4 단정을 통과**했다 |
 * | **Core ML(mlpackage)** | **쟀다 — CPU 와 같다**(상대오차 0.001%). 바로 위 표 |
 *
 * 즉 **잰 범위에서는 QNN 만 다르다.** WebGPU 는 도는 기기에서 CPU·기준과 같았다.
 *
 * ⚠️ **ONNX(fp32)→QNN 의 출력 충실도는 아직 잰 적이 없다.** 돌아다니는 「각인 87.3%」는
 * **fp16 각인 모델의 최종 답 일치율**이고 ML 쪽이 **CPU 에서** 잰 값이다(같은 측정에서 마크
 * 코사인 1.0000 · 검출 IoU 0.995/0.999). QNN 수치와 무관한 숫자라 근거로 쓰면 안 된다.
 *
 * ### 임계 쪽도 열려 있다
 * 읽기는 **네 번 다 맞았다**(pair YS). 점수만 [ImprintDecoding.ADOPT_THRESHOLD] 0.949 에
 * 0.0054 모자란다. 그 값은 **CPU 수치의 감사셋에서 정밀도 90% 지점**으로 잡은 것이라,
 * QNN 경로를 쓰려면 그 경로로 다시 잡아야 한다. ⚠️ 이 저장소 각인 고정물은 **한 장**
 * (`imprint/input_gray.bin`)이어서 분포가 어떻게 움직이는지는 여기서 증명할 수 없다 —
 * 재보정은 감사셋을 가진 쪽의 일이다.
 *
 * ### 변형은 CPU 로는 못 쓴다
 * 각인변형 CPU 추론이 **22735ms** 였다(기준 4206ms). fp16 가중치가 CPU 에서 Cast 를 불러
 * **5.4배 느리다.** 변형을 쓰려면 QNN 전용으로 따로 실어야 한다 — 기기별로 다른 모델을
 * 주는 길(Play 기기 타깃팅)이 필요해지는 지점이다.
 *
 * ### 세션 생성 84초는 **캐시가 없앤다** — 126배
 * QNN 은 ONNX 그래프를 기기에서 HTP 명령으로 컴파일한다(`libQnnHtpPrepare.so`, 84MB).
 * [Ep.QNN_CACHED] 가 그 결과를 EPContext 모델로 써 두고 **다음부터 건너뛴다.**
 *
 * | | 컴파일(첫 세션) | 캐시 적중 | 컴파일 결과 크기 |
 * |---|---|---|---|
 * | 각인 | 84.1초 | **0.67초** | 27.9MB |
 * | 마크 | 10.1초 | **0.46초** | 58.6MB |
 *
 * **이것이 운영 경로를 정한다.** 기기에서 한 번 치르고 캐시하는 길도 있지만(각인 84초를 첫
 * 실행에 한 번), 컴파일 결과는 **HTP 세대마다 다르다** — 그래서 세대별로 **미리 컴파일해
 * 실으면** 컴파일이 아예 없고 84MB `libQnnHtpPrepare.so` 도 빠진다. Play 기기 타깃팅의
 * `system-on-chip` 선택자가 `Build.SOC_MODEL` 로 거르므로 세대와 1:1로 맞는다.
 *
 * ⚠️ 쓰는 법에 함정이 둘 있다. 둘 다 **조용히** 틀린다.
 *  1. `ep.context_*` 는 **세션 설정**이다(`addConfigEntry`). EP 제공자 옵션으로 넘기면 무시되고
 *     캐시가 아예 안 생긴다 — 둘째 세션도 88초였다
 *  2. **생성을 켜는 것은 캐시가 없을 때만**이다. 이미 컨텍스트 모델을 열면서 또 켜면 ORT 가
 *     거절한다(`Failed to generate EP context model since the file … exists already`)
 *
 * ### 쓰려면 셋이 필요하다 — 하나만 빠져도 조용히 CPU 로 돈다
 * 전부 실측으로 걸렀다. 공통점은 **세션이 열리고 답도 맞아서 겉보기엔 성공**이라는 것이다.
 *  1. **배치 차원 고정** — `setSymbolicDimensionValue("batch", N)`. 없으면 HTP 가 모양을 못
 *     구해(`Cannot get shape`) 노드를 거의 안 받는다
 *  2. **`backend_path`** — `backend_type` 만으로는 `ADSP_LIBRARY_PATH` 가 비어 DSP 가 skel 을
 *     못 찾는다([qnnNativeLibDir])
 *  3. **`.so` 를 APK 밖으로** — `useLegacyPackaging = true`. cDSP 는 `apk!/lib/...` 를 못 연다
 *
 * 셋 중 하나라도 빠진 상태의 측정값이 **각인 4349ms · 마크 4611ms** 였다 — CPU 와 구별되지 않는다.
 *
 * ## 프리빌트에 들어 있다 — 소스 빌드가 아니다
 * 2026-09 에는 「프리빌트 Maven 배포본에 EP 가 없어 소스 빌드가 필요하다」가 맞았고, 그래서
 * 11MB 커스텀 AAR 과 minSdk 28 을 받아들여야 했다. **공식 `onnxruntime-android` 1.30.0 부터는
 * 들어 있다**(ABI 4개 전부 · minSdk 24). 저장소가 1.22.0 에 머물러 있었을 뿐이다.
 *
 * ## 되돌아갈 길이 필요한 이유
 * WebGPU 는 Dawn 을 통해 Vulkan 으로 내려간다. 드라이버가 못 받는 기기가 있어서, 세션 생성이
 * 실패하면 잡아서 CPU 로 다시 연다. 답은 같다(감사셋 대조 완료, NM-528).
 *
 * ⚠️ **아래 `runCatching` 은 세션 생성 실패만 잡는다.** 예전 주석은 「드라이버가 못 받으면
 * 세션 생성 실패로 나온다」를 전제했는데 **그 전제가 틀렸다**(NM-574, 2026-10-13 Test Lab 실측).
 * 실제로 터지는 자리는 [OrtSession.run][ai.onnxruntime.OrtSession.run] 안이고, 양상이 둘이다:
 *
 * | 기기 | 무엇이 일어나나 | 잡히나 |
 * |---|---|---|
 * | SM-S916N · Adreno 740 | `vulkan.adreno.so` `vkCmdPipelineBarrier` 에서 **SIGSEGV** | **못 잡는다** — OS 가 프로세스를 끝낸다 |
 * | SM-S928N · Adreno 750 | Conv 노드 `OrtException`, 이어서 WebGPU 디바이스 사망 | 잡히지만 그 프로세스의 WebGPU 는 죽은 뒤다 |
 *
 * 그래서 **들어가기 전에 막는 길**을 [VisionBackend] 로 따로 냈다. 이 폴백은 그대로 두되,
 * 이것만으로 충분하다고 읽히면 안 된다.
 */
internal object OrtBackend {

    /**
     * WebGPU 를 쓸지 — **계측 테스트만 끈다**(`-e nm_webgpu false`).
     *
     * 같은 기기에서 CPU 와 WebGPU 의 답을 맞대 보려면 한쪽을 강제로 끌 수 있어야 한다.
     * 운영 코드에는 이 값을 바꾸는 자리가 없고, 기본값은 늘 켜짐이다.
     *
     * 왜 필요했나: 2026-10-13 Adreno(SM-S916N)에서 `vulkan.adreno.so` 세그폴트가 올라왔는데,
     * WebGPU 성능·정확도 근거가 전부 Xclipse(S24 · Exynos 2400) 한 대에서 나온 것이었다.
     * 다른 GPU 에서 **답이 같은지**를 재려면 같은 기기에서 두 경로를 다 돌려야 한다.
     */
    internal var webGpuEnabled: Boolean = true

    /**
     * 실행 공급자를 하나로 **못박는다** — 계측 테스트 전용(`-e nm_ep nnapi`).
     *
     * ## 왜 필요한가
     * 지금의 EP 비교표([OrtBackend] KDoc)가 **Galaxy S24(Exynos 2400 · Xclipse 940) 한 대**
     * 에서 나왔다. 「NNAPI 실패」는 엑시노스 드라이버 얘기지 퀄컴 NNAPI(Hexagon NPU 로
     * 내려간다)에 대한 것이 아니고, 「XNNPACK 더 느림」도 그 CPU 에서의 값이다.
     * 2026-10-13 Adreno 에서 각인이 깨지는 것이 드러나면서 **그 표를 다시 깔아야** 한다.
     *
     * `null` 이면 운영과 같다 — WebGPU 가 되면 WebGPU, 안 되면 CPU.
     */
    internal var forcedEp: Ep? = null

    /**
     * 노드별 트레이스를 남길 파일 경로의 **접두어** — 계측 테스트 전용.
     *
     * ORT 가 Chrome 트레이싱 JSON 을 쓴다. 항목마다 `provider` 와 `dur` 가 붙어 **어느 노드가
     * 어느 EP 로 갔고 누가 시간을 먹는지**가 그대로 나온다. `null` 이면 끄는 것이고 운영은 늘 꺼져 있다.
     */
    internal var profilePrefix: String? = null

    /** 이 기기가 WebGPU EP 를 갖고 있는가. 런타임 한 번만 묻는다. */
    private val hasWebGpu: Boolean by lazy {
        OrtEnvironment.getAvailableProviders().contains(OrtProvider.WEBGPU).also {
            Log.i(TAG, "사용 가능 EP: ${OrtEnvironment.getAvailableProviders()} · WebGPU=$it")
        }
    }

    /**
     * @param cpuNodeNames WebGPU 로 열 때 **CPU 에 남겨 둘 노드**의 이름 조각.
     *                     각인처럼 일부 연산이 GPU 에서 오히려 느린 모델이 쓴다 — [ImprintReader] KDoc 참조.
     *                     CPU 로 되돌아갈 때는 의미가 없으므로 무시된다.
     * @param webGpuOptions WebGPU EP 제공자 옵션(`preferredLayout` 등). 기본은 비어 있다 —
     *                       **무엇이 Adreno 를 살리는지 재는 데 쓴다**(NM-577).
     * @param configure CPU·WebGPU 양쪽에 공통으로 줄 설정
     */
    fun openSession(
        env: OrtEnvironment,
        modelFile: File,
        cpuNodeNames: String? = null,
        webGpuOptions: Map<String, String> = emptyMap(),
        configure: OrtSession.SessionOptions.() -> Unit = {}
    ): OrtSession {
        forcedEp?.let { ep ->
            // [Ep.QNN_CACHED] 는 **만들어진 컨텍스트 모델을 연다**. 없으면 원본을 열고, 그때
            // `ep.context_enable` 이 이 경로에 써 둔다 — 다음 열기가 적중이다.
            val source = contextModelOrNull(ep, modelFile) ?: modelFile
            Log.i(TAG, "${modelFile.name} EP 고정 → $ep${if (source !== modelFile) " · 캐시 적중 ${source.name}" else ""}")
            return env.createSession(
                source.absolutePath,
                OrtSession.SessionOptions().apply {
                    configure()
                    applyEp(env, ep, modelFile, cpuNodeNames, webGpuOptions)
                    profilePrefix?.let {
                        val trace = "$it-$ep-${modelFile.name.substringBeforeLast('.')}"
                        Log.i(TAG, "프로파일 → $trace")
                        enableProfiling(trace)
                    }
                }
            )
        }

        // 바깥이 막는 모델은 아예 시도하지 않는다 — 이 기기에서 터지는 것이 이미 알려진 조합이거나,
        // 전에 네이티브로 죽은 적이 있는 기기다(NM-574).
        val allowed = VisionBackend.allowWebGpu(modelFile.name)
        if (!allowed) Log.i(TAG, "${modelFile.name} WebGPU 차단됨 — CPU 로 연다")

        val onWebGpu = if (allowed && webGpuEnabled && hasWebGpu) {
            runCatching {
                env.createSession(
                    modelFile.absolutePath,
                    OrtSession.SessionOptions().apply {
                        configure()
                        addWebGPU(webGpuOptions)
                        cpuNodeNames?.let { addConfigEntry(LAYER_ASSIGNMENT, "cpu($it)") }
                    }
                )
            }.onSuccess {
                // 열렸다는 것만 알린다. **추론은 아직 안 돌았다** — 터지는 것은 그다음이라,
                // 기록하는 쪽이 「이 프로세스는 WebGPU 를 썼다」를 미리 남겨 둬야 죽고 나서 안다.
                VisionBackend.onWebGpuSessionOpened(modelFile.name)
            }.onFailure {
                // 기기에 따라 드라이버가 못 받을 수 있다. 판독을 통째로 포기하는 것보다 느려도 도는 쪽이 낫다.
                Log.w(TAG, "${modelFile.name} WebGPU 세션 실패 — CPU 로 연다: ${it.message}")
            }.getOrNull()
        } else {
            null
        }

        return onWebGpu ?: env.createSession(
            modelFile.absolutePath,
            OrtSession.SessionOptions().apply(configure)
        )
    }

    /** [forcedEp] 가 정한 EP 하나만 건다 — 계측 테스트 경로다. 폴백이 없어야 그 EP 를 잰 값이 된다. */
    private fun OrtSession.SessionOptions.applyEp(
        env: OrtEnvironment,
        ep: Ep,
        modelFile: File,
        cpuNodeNames: String?,
        webGpuOptions: Map<String, String>
    ) = when (ep) {
        Ep.CPU -> Unit

        Ep.WEBGPU -> {
            addWebGPU(webGpuOptions)
            cpuNodeNames?.let { addConfigEntry(LAYER_ASSIGNMENT, "cpu($it)") }
        }

        // 기본 플래그. CPU 폴백이 **허용**돼 있어, 느리게 나오면 NNAPI 의 자체 CPU 구현
        // (`nnapi-reference`)으로 떨어진 것일 수 있다 — 그걸 가리는 것이 [Ep.NNAPI_NO_CPU] 다.
        Ep.NNAPI -> addNnapi()

        // CPU 폴백을 막는다. 그래도 돌면 진짜 NPU/GPU 에서 돈 것이고, 실패하면 위가 CPU 였다는 뜻이다.
        Ep.NNAPI_NO_CPU -> addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED))

            /*
             * **NNAPI 가 잘하는 것만 준다** — 가속기가 못 받는 덩어리를 손으로 CPU 에 고정한다.
             *
             * ORT 는 자동으로도 쪼갠다(`NnapiExecutionProvider_Nnapi_..._156_76` 같은 노드 이름이
             * 그 흔적이다). 그런데 자동 분할이 잘게 쪼개지면 **경계마다 복사**가 붙어 CPU 단독보다
             * 느려진다 — 각인이 NNAPI 4817ms 대 CPU 4376ms 로 그 꼴이다.
             *
             * 각인은 CNN(Conv·ReLU·MaxPool) → RNN(LSTM) → out 구조다. LSTM 을 통째로 CPU 에
             * 고정하면 남는 그래프가 **conv 뿐**이라 NNAPI 가 한 덩이로 가져갈 수 있다.
             * WebGPU 에서 같은 수법이 9943 → 1796ms 를 만들었다([ImprintReader] KDoc).
             */
        Ep.NNAPI_SPLIT -> {
            addNnapi()
            cpuNodeNames?.let { addConfigEntry(LAYER_ASSIGNMENT, "cpu($it)") }
        }

        // ⚠️ 권장 설정이 **아니다**. 바깥 `configure()` 가 ORT 풀에 4스레드를 거는데 XNNPACK 은
        // 자기 풀이 따로라 경합한다. 비교 기준으로만 둔다 — 제대로 건 것은 [Ep.XNNPACK_TUNED].
        Ep.XNNPACK -> addXnnpack(emptyMap())

        // ORT 문서 권장: ORT 풀은 1, XNNPACK 풀에 코어 수, intra-op 스핀 끄기.
        // 「XNNPACK has a separate internal threadpool which can lead to contention with
        //   the ORT intra-op threadpool」
        Ep.XNNPACK_TUNED -> {
            setIntraOpNumThreads(1)
            addConfigEntry(INTRA_OP_SPINNING, "0")
            addXnnpack(mapOf("intra_op_num_threads" to XNNPACK_THREADS.toString()))
        }

        // 퀄컴 HTP 계열. 분기가 여섯이고 KDoc 이 길어 따로 뺐다 — 여기서 복잡도가 샜다.
        else -> applyQnnEp(env, ep, modelFile, cpuNodeNames)
    }

    /**
     * 퀄컴 HTP 계열 분기. [applyEp] 에서 떼어냈다 — 여섯 갈래에 각각 긴 사정이 붙어
     * 한 함수에 두면 읽기도 어렵고 복잡도 한계도 넘는다.
     */
    private fun OrtSession.SessionOptions.applyQnnEp(
        env: OrtEnvironment,
        ep: Ep,
        modelFile: File,
        cpuNodeNames: String?
    ) = when (ep) {
        // 퀄컴 HTP. 기본 설정만 — 옵션은 [qnnBaseOptions] 가 공통으로 깐다.
        Ep.QNN -> addQnn(env, emptyMap())

            /*
             * **HTP 를 fp32 로 묶는다** — 각인이 QNN 에서 틀리는 이유를 가르는 대조군이다.
             *
             * ⚠️ **지금은 [Ep.QNN] 과 같은 설정이다.** 기본값에 기대지 않으려고 [qnnBaseOptions] 가
             *    `0` 을 명시하게 바꿨기 때문이다. fp16 대조는 [Ep.QNN_FP16] 과 해야 한다.
             *
             * 아래 표는 바꾸기 전, [Ep.QNN] 이 기본값(문서상 `1`)으로 돌던 2026-10-13 Adreno 740 측정이다.
             *
             * | | CPU | QNN |
             * |---|---|---|
             * | 각인 | 4206ms · **YS** | 418ms · **null** |
             * | 마크 | 3743ms · 정답 | 253ms · 정답 |
             *
             * 즉 **빠르지만 각인만 답을 잃는다.** AI Hub 에서 같은 모델이 87.3% 일치였고, TPS
             * 격자만 fp32 로 남긴 변형이 99.6% 로 올라갔다 — 격자가 fp16 에 민감한 것이다.
             * 여기서 fp32 로 묶어 **그 설명이 맞는지**, 그리고 묶고도 CPU 보다 빠른지를 본다.
             */
        Ep.QNN_FP32 -> addQnn(env, mapOf("enable_htp_fp16_precision" to "0"))

            /*
             * **fp16 을 명시로 켠다.** [Ep.QNN_FP32] 의 반대쪽이고, 둘 중 하나는 기본값과 같다.
             *
             * ⚠️ 기본값이 문서에 따라 갈린다 — ORT 본가 문서는 `1`, 이 플러그인의 저장소
             * (`onnxruntime-qnn`) 문서는 `0` 이라고 적는다. 그래서 **양쪽을 다 명시로** 재야
             * 기본값이 무엇인지와 fp16 이 점수에 영향을 주는지를 같이 가린다.
             *
             * 2026-10-13 에 이 축을 빼고 「기본」과 「=0」만 재서 **같은 설정을 두 번 재고
             * 「fp16 은 원인이 아니다」로 잘못 결론**했다.
             */
        Ep.QNN_FP16 -> addQnn(env, mapOf("enable_htp_fp16_precision" to "1"))

            /*
             * **STN 만 CPU 에 남기고 나머지를 HTP 에 준다** — 각인 오차의 **발원지**를 피한다.
             *
             * 단계별 측정이 자리를 찍어 줬다(`scratchpad/qnn_stage.py`, Adreno 740 계열 SM8550).
             *
             * | 단계 | CPU 대비 상대오차 |
             * |---|---|
             * | ① STN 격자(GridSample 좌표) | 2.5% |
             * | ② **STN 출력(재샘플링 이미지)** | **18.2%** ← 한 연산에서 7배 |
             * | ③ CNN 특징 | 21.4% |
             * | ④ 어텐션 출력(LSTM 입력) | 21.2% |
             *
             * 좌표는 2.5% 만 틀리는데 **재샘플링 결과가 18.2%** 다. CLAHE 를 거친 각인 패치는
             * 글자 경계가 날카로워서 샘플링 좌표가 조금 밀리면 픽셀값이 크게 바뀐다 — 오차가
             * 더해지지 않고 **곱해지는** 자리다. 그 뒤로는 안 자란다(18.2 → 21.4 → 21.2%).
             *
             * 그래서 [Ep.QNN_RNN_CPU] 가 아무 효과도 없었다 — LSTM 은 증폭기가 아니었다.
             * `/stn/` 은 350 노드 중 74개다.
             *
             * 기기 없이 검증했다(앞 74노드는 로컬 CPU, 뒤 276노드는 AI Hub 실기기 QNN).
             *
             * | | 점수 | 채택 | 로짓 PSNR | 상대오차 |
             * |---|---|---|---|---|
             * | 전부 CPU(기준) | 0.9774 | `YS` | — | — |
             * | **STN 만 CPU** | 0.9769 | **`YS`** | **64.34dB** | **0.474%** |
             * | 전부 QNN | 0.9202 | `null` | 37.92dB | 9.931% |
             *
             * ## ⚠️ 이 표로 「안전하다」고 말할 수 없다 — **표본이 한 장이다**
             * 두 열의 근거가 다르다. 섞어 읽으면 안 된다.
             *  - **PSNR·상대오차는 n=1 이 아니다.** 한 번의 순전파에서 나온 로짓
             *    144×32×37 = **170,496 개**를 원소별로 맞댄 값이다. 「오차가 어디서 나고
             *    `cpu(/stn/)` 이 그걸 21배 줄인다」는 이 열이 받친다
             *  - **점수·채택은 n=1 이다.** 알약 한 개, 각인 하나(`YS`), 임계 한 번 넘기다.
             *    「QNN 으로 바꿔도 채택률이 안 떨어진다」는 **전혀 재지 않았다**
             *
             * 하필 이 표본은 **경계에서 멀다** — CPU 점수 0.9774 로 임계 0.949 보다 0.028 위다.
             * 정작 위험한 것은 CPU 가 0.9495 쯤인 알약이고, 거기서는 0.474% 잔차로도 뒤집힌다.
             * 그 비율은 모른다.
             *
             * 이 저장소 각인 고정물은 `sample_crop.png` **한 장뿐**이라(나머지 `.bin` 은 같은
             * 알약의 중간 단계다) 여기서 더 할 수 있는 것이 없다. **0.949 를 「감사셋 정밀도 90%
             * 지점」으로 잡은 그 감사셋**을 CPU · QNN · QNN+`cpu(/stn/)` 세 경로로 돌려
             * 채택률과 정밀도를 비교해야 한다 — ML 쪽 일이고, 돌릴 하네스는
             * `scratchpad/qnn_stn_cpu.py` 에 있다.
             */
        // ⚠️ **지금 이 분기는 [Ep.QNN] 과 같다.** 331면 전수에서 점수가 **331/331 동일**하게
        //    나왔다 — `session.name_based_layer_assignment` 가 플러그인 EP(`addExecutionProvider`)
        //    경로에는 적용되지 않는 것으로 보인다. WebGPU·NNAPI 는 ORT 안에 박힌 EP 라 먹는다.
        //    각인이 전수에서 쓸 만하게 나왔으므로(위 표) 급한 축은 아니다. 고치려면 QNN
        //    제공자 옵션 쪽(`qnn_subgraphs` 등)을 봐야 한다.
        Ep.QNN_STN_CPU -> {
            addQnn(env, emptyMap())
            addConfigEntry(LAYER_ASSIGNMENT, "cpu($STN_PREFIX)")
        }

            /*
             * **LSTM 을 CPU 에 남기고 conv 만 HTP 에 준다** — 각인이 QNN 에서 답을 잃는 이유의
             * 두 번째 가설이다.
             *
             * 첫 가설은 fp16 이었고 **틀렸다** — `enable_htp_fp16_precision=0` 으로 묶어도 답이
             * 그대로 `null` 이고 시간도 418 → 435ms 로 거의 안 변했다(2026-10-13 Adreno 740).
             *
             * 남은 용의자가 양방향 LSTM 이다. 근거가 둘 있다.
             *  - **LiteRT 는 이 모델을 아예 못 열었다** — CPU 포함 전 백엔드 `Failed to load
             *    model from file`. 마크·검출은 열렸다. LSTM 이 변환기를 막는다는 뜻이다
             *  - WebGPU 도 통째로는 9943ms 였고 **LSTM 을 CPU 에 고정해서 1796ms** 가 됐다
             *    ([ImprintReader] KDoc). 같은 경계가 QNN 에도 있을 수 있다
             *
             * 마크가 QNN 에서 **정답**인 것이 이 가설을 받친다 — 마크엔 LSTM 이 없다.
             *
             * ⚠️ **기각됐다.** 단계별 측정에서 오차의 발원지는 STN 재샘플링이었고 이 분기는 효과가
             *    없었다 — 위 [Ep.QNN_STN_CPU] 표 참고. 비교용으로만 남긴다.
             */
        Ep.QNN_RNN_CPU -> {
            addQnn(env, emptyMap())
            cpuNodeNames?.let { addConfigEntry(LAYER_ASSIGNMENT, "cpu($it)") }
        }

            /*
             * **컴파일 결과를 캐시한다** — 84MB `libQnnHtpPrepare.so` 를 운영에서 뺄 수 있는지가
             * 여기에 달려 있다.
             *
             * QNN 은 ONNX 그래프를 HTP 명령으로 **기기에서 컴파일**한다(그 일을 하는 것이
             * `HtpPrepare` 다). `ep.context_enable` 을 켜면 그 결과를 `ep.context_file_path` 에
             * EPContext 모델로 써 두고 다음부터 **컴파일을 건너뛴다.**
             *
             * ⚠️ **생성과 사용이 다른 호출이다.** `ep.context_enable` 은 **쓰기만** 한다. 적중을
             * 받으려면 그렇게 만들어진 컨텍스트 모델을 **파일로 직접 열어야** 한다 —
             * [contextCacheFile] 이 그 경로고, 파일이 이미 있으면 [openSession] 이 그쪽을 연다.
             * 2026-10-13 첫 측정에서 원본 ONNX 를 다시 열어 둘째 세션도 86초였다.
             */
        Ep.QNN_CACHED -> {
            // ⚠️ **생성을 켜는 것은 캐시가 없을 때만이다.** 이미 컨텍스트 모델을 열면서
            //    또 켜면 ORT 가 거절한다 — `Failed to generate EP context model since the
            //    file … exists already`(2026-10-13 실측).
            if (contextModelOrNull(ep, modelFile) == null) {
                // ⚠️ **세션 설정이다, EP 제공자 옵션이 아니다.** `addExecutionProvider` 에
                //    넘기면 조용히 무시된다 — 그래서 한 번은 캐시가 아예 안 생겼다.
                addConfigEntry(EP_CONTEXT_ENABLE, "1")
                // 컴파일 바이너리를 ONNX 안에 품지 않고 옆 파일로 뺀다. 각인 것이 27.9MB 다.
                addConfigEntry(EP_CONTEXT_EMBED_MODE, "0")
                // ⚠️ **모델마다 다른 파일**이어야 한다. 하나로 두면 먼저 돈 모델의 컴파일
                //    결과를 다음 모델이 읽는다 — orchestrator 가 프로세스는 새로 띄우지만
                //    `cacheDir` 은 그대로라 파일이 남는다.
                addConfigEntry(EP_CONTEXT_FILE_PATH, contextCacheFile(modelFile))
            }
            addQnn(env, emptyMap())
        }

        // [applyEp] 가 QNN 계열만 넘긴다. 나머지는 거기서 이미 갈렸다.
        else -> error("QNN 계열이 아닌 EP 가 넘어왔다: $ep")
    }

    /**
     * QNN 플러그인 EP 를 세션에 건다.
     *
     * 다른 EP 와 거는 방식이 다르다. `addNnapi()` 처럼 ORT 안에 박힌 EP 가 아니라 **따로 배포되는
     * `.so`** 라서, 먼저 환경에 라이브러리를 등록하고([ensureQnnRegistered]) 그렇게 생긴
     * **EP 디바이스 목록**을 세션에 넘긴다.
     *
     * 디바이스가 비면 **던진다**. 이 경로는 측정용이고, 조용히 CPU 로 돌면 「QNN 234ms」 같은
     * 값을 CPU 숫자로 적게 된다 — 엑시노스에서 NNAPI 가 실제로 그렇게 속였다(최대차 0.0 · 배율 1.00).
     */
    private fun OrtSession.SessionOptions.addQnn(env: OrtEnvironment, options: Map<String, String>) {
        ensureQnnRegistered(env)
        val devices = env.epDevices.filter { it.epName == qnnEpName() }
        require(devices.isNotEmpty()) {
            "QNN EP 디바이스가 없다 — 이 기기에 HTP 가 없거나 백엔드 .so 를 못 찾았다. " +
                "보이는 EP 디바이스: ${env.epDevices.map { "${it.epName}/${it.device.type}" }}"
        }
        Log.i(TAG, "QNN 디바이스 ${devices.map { it.device.type }} · 옵션 ${options.keys}")
        addExecutionProvider(devices, qnnBaseOptions() + options)
    }

    /**
     * 환경에 플러그인 `.so` 를 등록한다 — **환경당 한 번만** 된다(두 번째는 던진다).
     *
     * [OrtEnvironment] 는 프로세스에 하나라, orchestrator 가 메서드마다 프로세스를 새로 띄우는
     * 지금 구조에서는 사실상 테스트당 한 번이다.
     */
    @Synchronized
    private fun ensureQnnRegistered(env: OrtEnvironment) {
        // 이 설치본에 `:qnn` feature module 이 없으면 여기서 끊어 **무엇이 없는지** 말한다 —
        // 그냥 두면 NoClassDefFoundError 가 한 칸 더 안쪽에서 터져 읽기 어렵다.
        check(QNN_BUNDLED) {
            "QNN feature module 이 이 설치본에 없다 — Play 기기그룹에 안 맞는 기기이거나 base 만 설치됐다"
        }
        if (qnnRegistered) return
        env.registerExecutionProviderLibrary(qnnEpName(), qnnLibraryPath())
        qnnRegistered = true
        Log.i(TAG, "QNN 플러그인 등록 — ${qnnLibraryPath()} · EP 디바이스 ${env.epDevices.size}개")
    }

    /** 두 번 등록하면 ORT 가 던진다. 검출·각인·마크가 동시에 열 수 있어 [ensureQnnRegistered] 를 잠근다. */
    @Volatile
    private var qnnRegistered = false

    /**
     * [Ep.QNN_CACHED] 가 EPContext 모델을 둘 **디렉터리**. 테스트가 쓰기 가능한 곳으로 채운다 —
     * `null` 이면 ORT 가 모델 옆에 쓰려 하는데, adb 로 밀어 넣은 자리는 앱에 쓰기 권한이 없을 수 있다.
     */
    internal var contextCacheDir: File? = null

    /**
     * [Ep.QNN_CACHED] 이고 컨텍스트 모델이 **이미 있으면** 그 파일. 그 밖에는 `null`.
     *
     * 컨텍스트 모델은 원본과 같은 입출력을 갖는 ONNX 인데 그래프 자리에 **컴파일된 HTP
     * 바이너리를 참조하는 노드 하나**가 들어 있다. 그래서 원본 대신 이것을 열면 컴파일을 건너뛴다.
     */
    private fun contextModelOrNull(ep: Ep, modelFile: File): File? {
        if (ep != Ep.QNN_CACHED) return null
        return File(contextCacheFile(modelFile)).takeIf { it.isFile && it.length() > 0 }
    }

    /** EPContext 생성을 켜는 **세션 설정** 키. ORT 의 `kOrtSessionOptionEpContextEnable`. */
    private const val EP_CONTEXT_ENABLE = "ep.context_enable"

    /** `0` 이면 컴파일 바이너리를 ONNX 안에 품지 않고 옆 파일로 뺀다. */
    private const val EP_CONTEXT_EMBED_MODE = "ep.context_embed_mode"

    /** 생성될 컨텍스트 모델의 경로. 이 경로를 **다음에 직접 열어야** 적중이다. */
    private const val EP_CONTEXT_FILE_PATH = "ep.context_file_path"

    /**
     * 모델 하나당 캐시 파일 하나. 디렉터리를 안 주면 모델 옆이다(ORT 기본과 같다).
     *
     * 이름에 **원본의 크기·수정 시각**을 넣는다 — 같은 파일명으로 모델을 바꿔 끼우면 옛 HTP
     * 그래프를 그대로 집어 들어 이전 모델을 잰다. 생성이 중간에 끊겨 남은 반쪽 파일은 여전히
     * 거르지 못한다(크기 > 0 만 본다) — 그때는 캐시 디렉터리를 비운다.
     */
    private fun contextCacheFile(modelFile: File): String {
        val stamp = "${modelFile.length()}-${modelFile.lastModified()}"
        val name = "${modelFile.name.substringBeforeLast('.')}.$stamp.ctx.onnx"
        return File(contextCacheDir ?: modelFile.parentFile, name).absolutePath
    }

    /**
     * `libQnnHtp.so` 가 **실파일로** 놓인 디렉터리 — 보통 `applicationInfo.nativeLibraryDir`.
     *
     * ## 왜 경로를 받아야 하나 — `backend_type` 만으로는 안 된다
     * 2026-10-13 Adreno 740·750 에서 첫 측정이 **CPU 와 같은 값**을 냈다(각인 4349ms, AI Hub 는
     * 234ms). logcat 이 이유를 그대로 말했다.
     *
     * ```
     * W qnn_backend_manager.cc:332 Setting ADSP_LIBRARY_PATH =      ← 비어 있다
     * E cannot open libQnnHtpV73Skel.so, errno 2 (no such file or dir)
     * E QNN SetupBackend failed … QNN_DEVICE_ERROR_INVALID_CONFIG
     * ```
     *
     * HTP 는 **DSP 쪽에서 도는 skel** 을 FastRPC 로 올린다. 그 skel 을 찾는 환경변수가
     * `ADSP_LIBRARY_PATH` 인데, ORT 는 그 값을 **`backend_path` 의 디렉터리에서 만든다.**
     * `backend_type=htp` 만 주면 경로가 없어 빈 문자열이 되고, DSP 는 아무것도 못 찾는다.
     * 그러면 `GetCapability` 가 실패해 EP 가 **노드를 하나도 안 가져가고** 전부 CPU 로 돈다 —
     * 세션은 열리고 답도 맞아서 **겉보기엔 성공**이다. 느린 것만이 증거였다.
     */
    internal var qnnNativeLibDir: File? = null

    /**
     * 모든 QNN 세션에 공통으로 거는 것.
     *
     * `burst` 는 추론 동안 DVFS 를 최대로 묶는다 — 우리 추론은 짧고 띄엄띄엄 들어와서, 묶지
     * 않으면 클럭이 올라오기 전에 끝난다.
     */
    private fun qnnBaseOptions(): Map<String, String> {
        val dir = requireNotNull(qnnNativeLibDir) { "qnnNativeLibDir 을 안 줬다 — applicationInfo.nativeLibraryDir" }
        val backend = File(dir, "libQnnHtp.so")
        // ⚠️ **실파일이어야 한다.** `useLegacyPackaging=false` 면 `.so` 가 APK 안에만 있어서
        //    dlopen 은 되지만 cDSP 는 `apk!/lib/...` 를 열 수 없다. 그 경우 여기서 걸린다.
        require(backend.isFile) { "libQnnHtp.so 가 실파일로 없다: $backend — useLegacyPackaging 을 보라" }
        return mapOf(
            "backend_path" to backend.absolutePath,
            "htp_performance_mode" to "burst",
            // ⚠️ **기본값에 기대지 않는다.** 이 키의 기본값을 ORT 본가 문서는 `1`, 이 플러그인
            //    저장소(`onnxruntime-qnn`)는 `0` 으로 적어 **서로 다르다.** 비워 두면 어느 정밀도로
            //    잰 값인지 기록이 못 된다 — 2026-10-13 에 「기본」과 「=0」을 비교해 놓고
            //    같은 설정을 두 번 잰 적이 있다. [Ep.QNN_FP16] 이 이 값을 덮는다.
            "enable_htp_fp16_precision" to "0"
        )
    }

    /**
     * 노드 이름 조각으로 배정을 강제하는 **공식 설정 키**.
     *
     * WebGPU EP 전용 옵션 `ep.webgpuexecutionprovider.forceCpuNodeNames` 도 있는데 **안 먹는다**
     * (각인에서 11715 ms — 고정이 아예 안 됐다). 그쪽은 정확한 노드 이름 목록을 받는 것으로 보이고,
     * 이 키가 부분 문자열로 맞춘다.
     */
    private const val LAYER_ASSIGNMENT = "session.name_based_layer_assignment"

    /**
     * 모델 입력의 **배치 차원 심볼 이름**. 각인·마크가 같은 내보내기 스크립트에서 나와 둘 다 이 이름이다
     * (`image: [batch, …]`). 검출은 `[1,3,576,576]` 로 고정이라 해당 없다.
     *
     * 세션을 열 때 `setSymbolicDimensionValue(BATCH_DIM, N)` 으로 못박아야 QNN 이 그래프를 받는다 —
     * 이유는 [ImprintReader] 의 그 호출 자리에 적어 뒀다.
     */
    internal const val BATCH_DIM = "batch"

    /** 공간 변환기(STN) 노드 이름의 접두어. [Ep.QNN_STN_CPU] 가 이걸로 CPU 에 고정한다. */
    private const val STN_PREFIX = "/stn/"

    private const val TAG = "NM534"

    /** ORT intra-op 풀의 스핀 대기를 끄는 공식 설정 키. XNNPACK 권장 설정의 한 축이다. */
    private const val INTRA_OP_SPINNING = "session.intra_op.allow_spinning"

    /**
     * XNNPACK 자기 풀의 스레드 수. 문서는 「물리 코어 수」를 권하는데, 이 저장소는 이미 4가
     * 최적임을 쟀다(`ImprintReaderBenchmark`: 1스레드 13345 · 2스레드 6765 · **4스레드 4124** ·
     * 6스레드 6939 · 8스레드 8059 ms). CPU 기준선과 같은 값이라 비교도 공정하다.
     */
    private const val XNNPACK_THREADS = 4
}

/**
 * 계측 테스트가 못박을 수 있는 실행 공급자.
 *
 * 앞 일곱은 프리빌트 `onnxruntime-android` 에 들어 있고, `QNN*` 셋은 **따로 배포되는 플러그인**
 * (`com.qualcomm.qti:onnxruntime-android-qnn`)이 더해 주는 것이다 — 스냅드래곤에서만 성립한다.
 */
internal enum class Ep {
    CPU,
    WEBGPU,
    NNAPI,
    NNAPI_NO_CPU,
    NNAPI_SPLIT,
    XNNPACK,
    XNNPACK_TUNED,
    QNN,
    QNN_FP32,
    QNN_FP16,
    QNN_STN_CPU,
    QNN_RNN_CPU,
    QNN_CACHED
}
