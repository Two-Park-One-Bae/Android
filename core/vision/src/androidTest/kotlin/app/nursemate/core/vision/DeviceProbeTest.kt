package app.nursemate.core.vision

import ai.onnxruntime.OrtEnvironment
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
        }

        // 한 줄씩 찍는다 — logcat 이 긴 줄을 잘라서 묶어 찍으면 GPU 이름이 날아간다.
        lines.forEach { Log.i(TAG, it) }
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
