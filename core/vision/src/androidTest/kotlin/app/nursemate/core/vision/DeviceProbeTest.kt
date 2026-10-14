package app.nursemate.core.vision

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.qnnpluginep.getEpName as qnnEpName
import ai.onnxruntime.qnnpluginep.getLibraryPath as qnnLibraryPath
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 이 기기가 **무엇인지** 한 줄로 남긴다 — GPU · 실행 공급자 · Vulkan 판.
 *
 * ## 왜 있나
 * 성능·정확도 근거가 전부 Galaxy S24(SM-S921N · Exynos 2400 · Xclipse 940) 한 대에서 나왔다.
 * 다른 기기에서 돌릴 때마다 **무슨 GPU 위에서 잰 값인지**가 기록에 남아야, 숫자가 갈릴 때
 * 기기 탓인지 코드 탓인지 가린다. [EpOverride] 가 고른 경로도 함께 찍는다.
 *
 * 아무것도 단정하지 않는다 — 통과 여부가 아니라 **로그가 산출물**이다. logcat 에서
 * `NM-PROBE` 로 찾는다.
 */
@RunWith(AndroidJUnit4::class)
class DeviceProbeTest {

    @Test
    fun `기기와_실행공급자를_기록한다`() {
        val path = EpOverride.apply()
        val context = InstrumentationRegistry.getInstrumentation().context

        val lines = buildList {
            add("기기       ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE} · ${Build.HARDWARE})")
            add("안드로이드 ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add("SoC        ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
            }
            add("ABI        ${Build.SUPPORTED_ABIS.joinToString()}")
            add("요청 경로  $path")
            val providers = runCatching { OrtEnvironment.getAvailableProviders().joinToString() }
            add("ORT EP     ${providers.getOrElse { "조회 실패: $it" }}")
            add("Vulkan     ${vulkanVersion(context.packageManager)}")
            addAll(glStrings())
            addAll(qnnLines())
            addAll(modelLines(context))
        }

        // 한 줄씩 찍는다 — logcat 이 긴 줄을 잘라서 묶어 찍으면 GPU 이름이 날아간다.
        lines.forEach { Log.i(TAG, it) }
    }

    /**
     * **모델이 실제로 어디에 있는지** 찍는다.
     *
     * 모델은 저장소에 없어서(119MB) 기기로 밀어 넣는데, 경로가 한 칸만 어긋나도 테스트가
     * `assumeTrue` 에서 **조용히 skipped** 된다 — 통과처럼 보인다. Test Lab 은 실기기 할당량이
     * 하루 5회뿐이라 그렇게 한 번을 날리면 비싸다. 그래서 디렉터리를 그대로 남긴다.
     */
    private fun modelLines(context: android.content.Context): List<String> = buildList {
        val dir = context.getExternalFilesDir(null)
        add("모델 경로  $dir")
        val files = dir?.listFiles()
        when {
            files == null -> add("모델       디렉터리를 못 읽었다")
            files.isEmpty() -> add("모델       **비어 있다** — 밀어 넣는 경로가 위와 같은지 보라")
            else -> files.sortedBy { it.name }
                .forEach { add("모델       ${it.name} ${it.length() / 1024 / 1024}MB") }
        }
    }

    /**
     * **QNN 플러그인이 꽂히는가** — 그리고 꽂은 뒤에도 WebGPU 가 남아 있는가(NM-577).
     *
     * ## 왜 이 두 줄이 같이 필요한가
     * `com.microsoft.onnxruntime:onnxruntime-android-qnn` 은 ORT 를 **갈아 끼우는** 배포본이라
     * 기기에서 재 보니 EP 가 `CPU, QNN` 둘뿐이었다 — WebGPU 를 버려야 QNN 을 얻는 거래였다.
     * 지금 붙인 `com.qualcomm.qti:onnxruntime-android-qnn` 은 표준 배포본에 **덧붙는 플러그인**
     * 이라고 되어 있다. 그 말이 맞으면 위 `ORT EP` 줄에 WebGPU 가 그대로 있고, 아래 줄에
     * QNN 디바이스가 함께 나온다. **둘이 같이 나오는 것이 증거다.**
     *
     * ## 플러그인 EP 는 `getAvailableProviders()` 에 안 나온다
     * 그쪽은 ORT 안에 박힌 EP 목록이다. 플러그인은 `registerExecutionProviderLibrary` 로
     * 환경에 올려야 비로소 `getEpDevices()` 에 나타난다.
     *
     * 단정하지 않는다 — 엑시노스에는 HTP 가 없어 **없는 것이 정상**이다. 기록만 남긴다.
     */
    private fun qnnLines(): List<String> = buildList {
        val env = runCatching { OrtEnvironment.getEnvironment() }.getOrElse {
            add("QNN       환경을 못 얻었다: $it")
            return@buildList
        }

        add("QNN 라이브러리 ${qnnLibraryPath()} · EP 이름 ${qnnEpName()}")

        runCatching { env.registerExecutionProviderLibrary(qnnEpName(), qnnLibraryPath()) }
            .onFailure {
                // 등록 실패가 곧 기록이다. `.so` 가 APK 에 안 들어갔거나, 기기의 vendor
                // 라이브러리(`libcdsprpc.so` 등)를 못 찾은 경우가 여기로 온다.
                add("QNN 등록   실패 — ${it.message}")
                return@buildList
            }

        val devices = runCatching { env.epDevices }.getOrElse {
            add("QNN 디바이스 조회 실패 — ${it.message}")
            return@buildList
        }
        add("EP 디바이스 ${devices.joinToString { "${it.epName}/${it.device.type}" }}")
        for (device in devices.filter { it.epName == qnnEpName() }) {
            add("QNN 디바이스 ${device.device.type} · 벤더 ${device.epVendor} · 메타 ${device.epMetadata}")
        }
    }

    /**
     * `GL_RENDERER` 가 GPU 이름을 그대로 준다 — `Adreno (TM) 740` · `Mali-G715` · `Xclipse 940`.
     *
     * WebGPU 는 Vulkan 으로 내려가지만 Vulkan 에는 이렇게 손쉽게 이름을 묻는 길이 없다. GPU 를
     * 가리는 데는 GL 쪽 문자열로 충분하고, 같은 칩이면 두 드라이버가 같은 하드웨어를 가리킨다.
     */
    private fun glStrings(): List<String> {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return listOf("$GL EGL 디스플레이를 못 얻었다")

        // 어디서 걸렸는지 남긴다. 조회가 통째로 비면 「GPU 를 못 읽었다」와 「GL 이 없다」가 섞인다.
        var failure = "eglInitialize 실패"
        var strings = emptyList<String>()
        val configs = arrayOfNulls<EGLConfig>(1)
        val chosen = IntArray(1)

        if (EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)) {
            failure = "eglChooseConfig 실패"
            val ok = EGL14.eglChooseConfig(display, CONFIG_SPEC, 0, configs, 0, 1, chosen, 0)
            if (ok && chosen[0] > 0) {
                failure = "eglMakeCurrent 실패"
                strings = readStrings(display, configs[0]!!)
            }
            EGL14.eglTerminate(display)
        }

        return strings.ifEmpty { listOf("$GL $failure") }
    }

    /** 1×1 오프스크린이면 된다 — 문자열을 읽으려면 컨텍스트가 current 여야 할 뿐이다. */
    private fun readStrings(display: EGLDisplay, config: EGLConfig): List<String> {
        val surface = EGL14.eglCreatePbufferSurface(display, config, PBUFFER_SPEC, 0)
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, CONTEXT_SPEC, 0)
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return emptyList()

        val strings = listOf(
            "GL 벤더    ${GLES20.glGetString(GLES20.GL_VENDOR)}",
            "GL 렌더러  ${GLES20.glGetString(GLES20.GL_RENDERER)}",
            "GL 판      ${GLES20.glGetString(GLES20.GL_VERSION)}"
        )

        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        return strings
    }

    /** 기기가 내거는 Vulkan 판. `0x400003` 형태의 정수라 사람이 읽게 풀어 준다. */
    private fun vulkanVersion(packages: PackageManager): String {
        val feature = packages.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            ?: return "없음"
        val v = feature.version
        return "${v shr 22}.${(v shr 12) and 0x3FF}.${v and 0xFFF}"
    }

    private companion object {
        const val TAG = "NM-PROBE"

        /** 로그 줄의 머리. 실패 줄도 같은 폭으로 맞춰 둬야 눈으로 훑어진다. */
        const val GL = "GL        "

        val CONFIG_SPEC = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE,
            EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,
            EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )

        val PBUFFER_SPEC = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)

        val CONTEXT_SPEC = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
    }
}
