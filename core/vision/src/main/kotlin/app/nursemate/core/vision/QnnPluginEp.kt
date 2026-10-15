package app.nursemate.core.vision

import java.io.File

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

/**
 * 이 설치본에 QNN 이 **어디까지** 내려왔는가 — NM-603 관측용.
 *
 * QNN 은 모듈 둘이 짝이다. `qnn_core`(플러그인 클래스 + 토대 `.so`)는 퀄컴 그룹 전부에, 세대
 * 모듈(`qnn_v##`, 그 세대 Skel)은 자기 그룹에만 간다. 둘 중 하나만 있으면 QNN 은 못 뜬다.
 *
 * | 값 | 뜻 |
 * |---|---|
 * | `core+skel` | 정상 — 기기그룹에 맞았다 |
 * | `core_only` | 토대만 왔다 — 세대 모듈 그룹 목록이 `qnn_core` 와 어긋났다 |
 * | `skel_only` | 세대만 왔다 — 같은 어긋남의 반대쪽 |
 * | `none` | 아무 그룹에도 안 맞았다 — **이 칩을 목록에 안 적었다** |
 *
 * 「토대가 있다」는 **플러그인 클래스가 로드되고 `libQnnHtp.so` 가 실파일로 있다**는 뜻이다. 둘 중
 * 하나만 보면 패키징이 `.so` 를 떨궜을 때 QNN 이 못 뜨는데도 정상으로 센다.
 *
 * 퀄컴 기기에서 `none` 이 나오면 그 `SOC_MODEL` 을 `device_targeting_configuration.xml` 에
 * 더할 후보다.
 */
enum class QnnDeliveryState(val label: String) {
    CORE_AND_SKEL("core+skel"),
    CORE_ONLY("core_only"),
    SKEL_ONLY("skel_only"),
    NONE("none");

    companion object {
        /**
         * @param nativeLibDir `applicationInfo.nativeLibraryDir`. `useLegacyPackaging` 이라 split 의
         *                     `.so` 도 여기에 실파일로 풀린다(NM-598).
         */
        fun of(nativeLibDir: File): QnnDeliveryState = of(nativeLibDir, QNN_BUNDLED)

        /** [QNN_BUNDLED] 를 받는 판정 본체 — JVM 테스트가 클래스 로드 없이 두 경우를 다 본다. */
        internal fun of(nativeLibDir: File, pluginLoaded: Boolean): QnnDeliveryState {
            val names = nativeLibDir.list()?.toSet().orEmpty()
            val core = pluginLoaded && CORE_LIB in names
            val skel = names.any { SKEL_NAME.matches(it) }
            return when {
                core && skel -> CORE_AND_SKEL
                core -> CORE_ONLY
                skel -> SKEL_ONLY
                else -> NONE
            }
        }

        private const val CORE_LIB = "libQnnHtp.so"

        /** 세대 Skel — `libQnnHtpV73Skel.so` 꼴. `QnnFeatureConventionPlugin.wantedLibs` 와 같은 이름이다. */
        private val SKEL_NAME = Regex("libQnnHtpV\\d+Skel\\.so")
    }
}
