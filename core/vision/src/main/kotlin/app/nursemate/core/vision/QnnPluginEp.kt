package app.nursemate.core.vision

/**
 * QNN 플러그인 EP 가 **지금 이 설치본에 들어 있는지.**
 *
 * ## 왜 런타임에 묻나
 * QNN 런타임은 base 가 아니라 **조건부 전달 feature module(`:qnn`)** 에 있다. arm64 비압축
 * 180MB 라 전 기기에 내려보낼 수 없고, Hexagon NPU 가 없는 기기에는 한 바이트도 쓸모가 없다.
 * Play 가 `system-on-chip` 기기그룹을 보고 **맞는 기기에만** 설치한다.
 *
 * 그래서 base 코드에서는 클래스가 있을 수도 없을 수도 있다. `core:vision` 은 플러그인을
 * `compileOnly` 로 물어 컴파일만 하고, 실제 존재 여부는 여기서 한 번 확인한다.
 *
 * ## 왜 `SplitInstallManager` 가 아닌가
 * 설치 시점 조건부 전달이라 앱이 뜬 뒤에 받아 올 일이 없다 — 있으면 처음부터 있고, 없으면
 * 영영 없다. 클래스가 로드되는지 보는 쪽이 Play Core 의존성 없이 같은 답을 준다.
 *
 * ⚠️ **없을 때 조용히 CPU 로 되돌리지 않는다.** 그러면 「QNN 418ms」 같은 값을 CPU 숫자로
 * 적게 된다 — 엑시노스에서 NNAPI 가 실제로 그렇게 속였다(최대차 0.0 · 배율 1.00).
 * 쓸지 말지는 부르는 쪽이 이 값을 보고 **미리** 정한다.
 */
internal val QNN_BUNDLED: Boolean by lazy {
    runCatching { Class.forName(QNN_PLUGIN_MARKER_CLASS) }.isSuccess
}

/**
 * 플러그인 artifact 가 들고 있는 유일한 클래스. `getEpName()`·`getLibraryPath()` 가 여기 있다.
 *
 * ⚠️ 문자열로 적는 자리라 artifact 를 올려 이름이 바뀌어도 컴파일이 안 깨진다. 올릴 때는
 * `DeviceProbeTest` 의 QNN 줄이 「등록 실패」로 바뀌는지 본다.
 */
private const val QNN_PLUGIN_MARKER_CLASS = "ai.onnxruntime.qnnpluginep.QnnPluginEpLibraryKt"
