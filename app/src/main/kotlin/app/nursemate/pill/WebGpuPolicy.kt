package app.nursemate.pill

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import app.nursemate.core.vision.PillDetector
import app.nursemate.core.vision.VisionBackend
import app.nursemate.core.vision.imprint.ImprintReader
import app.nursemate.core.vision.mark.MarkReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **이 기기에서 WebGPU 를 써도 되는가** — NM-574.
 *
 * 두 겹이다. 서로 다른 실패를 막으므로 둘 다 필요하다.
 *
 * | | 무엇을 막나 | 대가 |
 * |---|---|---|
 * | **허용목록** | 실기기에서 재서 **된다고 확인한 것만** 켠다 | 못 재 본 기기는 느리다 |
 * | **크래시 마커** | 허용했는데 나중에 죽는 경우 | 그 기기에서 **1회** 죽는다 |
 *
 * ## 왜 차단목록이 아니라 허용목록인가
 * 처음에는 「퀄컴이면 각인만 CPU」로 짰는데 **범위가 틀렸다.** 2026-10-13 실기기 전수 측정이
 * 보여 준 것은 이렇다.
 *
 * | 기기 | 검출 | 각인 | 마크 |
 * |---|---|---|---|
 * | Xclipse 940 (Exynos 2400) | **1.95배** | **2.03배** | **3.2배** |
 * | Xclipse 950 (Exynos 2500) | **2.1배** | **1.9배** | **2.7배** |
 * | Xclipse 960 (Exynos 2600) | 1.1배 | **1.8배** ⚠️ | **7.8배** |
 * | Adreno 740 (SM8550) | 1.53배 | **프로세스 사망** | 2.1배 |
 * | Adreno 750 (SM8650) | **프로세스 사망** | 예외 | 2.1배 |
 * | Mali (Tensor G4) | 예외 | 예외 | 예외 |
 *
 * **같은 Adreno 안에서도 세대마다 깨지는 모델이 다르다.** 740 은 검출이 되는데 750 은 죽고,
 * 둘 다 각인은 안 된다. 드라이버 빌드가 다르기 때문이다(`dc3d4da3a2` 대 `c81b28e1a5`).
 * 무엇이 깨질지 미리 알 수 없으니 **되는 것만 켜는** 쪽이 맞다 — 모르면 CPU 다.
 *
 * 2500·2600 줄은 2026-10-14 Samsung RTL 실측이다(NM-601). 2500 은 세 모델을 3회씩 돌려 값과 답이
 * 모두 같았고, 2600 은 검출 6회·마크 39회가 무결했다.
 *
 * ⚠️ **2600 각인은 「안전」이 아니라 「확률을 낮춘」 상태로 켠다.** 배치 144 에서 225회 중 30회
 *    예외 없이 빈 답(한 번은 `EE · 0.609` 처럼 계산이 망가진 값)이 나왔고, 배치를 72 로 내려
 *    1,393회 무결이 됐다(NM-602). 원인을 막은 것은 아니다 — 망가진 값의 점수가 우연히 0.949 를
 *    넘으면 틀린 각인을 채택한다. 운영에서 「각인 못 읽음」 비율이 2600 에서만 튀면 여기서
 *    각인을 빼는 것이 첫 조치다.
 *
 * ## 왜 Adreno 의 마크·검출까지 끄나
 * Adreno 740 은 검출·마크가 통과했고 2배 가까이 빠르다. 그런데 **한 번 통과한 것으로는
 * 모자란다.** 드라이버 크래시는 확률적이다 — 운영 크래시 ① 에서 각인이 알약 서너 개까지
 * 버티다 죽었다. 740 의 검출도 한 판 통과했을 뿐 다음 판에 죽지 않는다는 보장이 없다.
 *
 * 핫픽스가 할 일은 **죽는 것을 멈추는 것**이지 마지막 한 방울까지 짜내는 것이 아니다.
 * Adreno 를 CPU 로 돌리면 식별 한 번에 검출 +0.6초, 알약당 마크 +0.37초가 붙는다. 지금은
 * 그 기기들이 **앱째로 죽고 있다.**
 *
 * 더 촘촘한 매핑(740 은 검출·마크 허용 등)은 [ALLOWED] 에 줄을 더하면 된다. 다만 그 전에
 * **같은 기기를 여러 번 돌려** 확률적으로도 안전한지 봐야 한다.
 *
 * ## 왜 예외 폴백만으로는 부족한가
 * Adreno 740 의 각인과 750 의 검출은 **예외를 던지지 않고 프로세스가 죽는다**
 * (`vulkan.adreno.so` · `libllvm-qgl.so` SIGSEGV). 잡을 코드가 실행될 프로세스가 없다.
 */
@Singleton
class WebGpuPolicy @Inject constructor(@param:ApplicationContext private val context: Context) {

    /**
     * ⚠️ DataStore 가 아니라 SharedPreferences 다. [VisionBackend.allowWebGpu] 는 세션을 여는
     * 스레드에서 **동기로** 불리는데, DataStore 를 읽으려면 `runBlocking` 이 필요하다.
     */
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 이 프로세스가 WebGPU 를 썼다는 흔적. 죽으면 남고, 다음 실행이 읽는다. */
    private val sentinel = File(context.filesDir, SENTINEL)

    /** 한 번 막히면 이 설치본에서는 다시 안 켠다. 매번 디스크를 읽지 않도록 들고 있는다. */
    @Volatile
    private var blockedByCrash = false

    /**
     * [VisionBackend] 에 정책을 꽂는다. **온디바이스 추론이 처음 돌기 전에** 불려야 한다 —
     * `Application.onCreate` 가 그 자리다.
     */
    fun install() {
        blockedByCrash = prefs.getBoolean(KEY_BLOCKED, false) || trippedByLastExit()

        VisionBackend.allowWebGpu = ::allow
        VisionBackend.onWebGpuSessionOpened = { markAttempt() }

        Log.i(
            TAG,
            "WebGPU 정책 — SoC ${soc()} · 크래시차단=$blockedByCrash · " +
                "허용모델=${allowedModels().joinToString().ifEmpty { "없음(CPU 전용)" }}"
        )
    }

    /** 세션을 열 때 [OrtBackend][app.nursemate.core.vision.OrtBackend] 가 묻는다. */
    private fun allow(modelFileName: String): Boolean = !blockedByCrash && modelFileName in allowedModels()

    private fun allowedModels(): Set<String> = ALLOWED[soc()].orEmpty()

    /**
     * SoC 모델 이름. `SOC_MODEL` 이 API 31 부터라 그 아래는 `HARDWARE` 로 받는다 — 실기기에서
     * 둘이 같은 값(`s5e9945`)을 내는 것을 확인했다.
     *
     * GL 렌더러 문자열(`Xclipse 940`)로도 가릴 수 있지만 EGL 컨텍스트를 띄워야 해서, 앱이 뜨는
     * 길목에 두기엔 무겁다.
     */
    private fun soc(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE

    /** 이 프로세스가 WebGPU 세션을 열었다고 적어 둔다. 이미 있으면 건드리지 않는다. */
    private fun markAttempt() {
        runCatching { if (!sentinel.exists()) sentinel.writeText(MARK) }
            .onFailure { Log.w(TAG, "WebGPU 흔적을 남기지 못했다", it) }
    }

    /**
     * **직전 프로세스가 네이티브로 죽었고, 그때 WebGPU 를 쓰고 있었는가.**
     *
     * 흔적 파일만으로는 못 가린다 — 사용자가 작업목록에서 치워도 파일은 남는다. OS 에 왜
     * 죽었는지 직접 묻는다(API 30+). 그 아래 판은 판정하지 않는다. 오탐으로 멀쩡한 기기를
     * 영구히 느리게 만드는 쪽이, 1회 죽는 것보다 나쁘다.
     */
    private fun trippedByLastExit(): Boolean {
        if (!sentinel.exists()) return false
        // 판정했으면 지운다. 남겨 두면 다음 크래시가 아닌 종료까지 이 흔적에 걸린다.
        val crashed = lastExitWasNativeCrash()
        runCatching { sentinel.delete() }
        if (crashed) {
            prefs.edit().putBoolean(KEY_BLOCKED, true).apply()
            Log.w(TAG, "직전 실행이 네이티브로 죽었다 — 이 기기는 이제 CPU 로 돈다")
        }
        return crashed
    }

    private fun lastExitWasNativeCrash(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        val last = runCatching {
            context.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                ?.firstOrNull()
        }.getOrNull()

        if (last != null) {
            Log.i(TAG, "직전 종료 사유 ${last.reason} (status ${last.status}) — ${last.description}")
        }
        return when (last?.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> true

            // 신호로 죽은 것 중 **치명적 결함 신호만** 센다. SIGKILL(9) 은 OS 가 메모리를
            // 거두거나 사용자가 강제 종료한 것이라 우리 탓이 아니다.
            ApplicationExitInfo.REASON_SIGNALED -> last.status in FATAL_SIGNALS

            else -> false
        }
    }

    private companion object {
        const val TAG = "NM574"
        const val PREFS = "webgpu_policy"
        const val KEY_BLOCKED = "blocked_by_native_crash"
        const val SENTINEL = "webgpu_attempt"
        const val MARK = "1"

        /** SIGILL · SIGABRT · SIGBUS · SIGSEGV. 드라이버가 죽을 때 나오는 것들이다. */
        val FATAL_SIGNALS = setOf(4, 6, 7, 11)

        /**
         * **SoC 모델 → WebGPU 로 열어도 되는 모델 파일.**
         *
         * ⚠️ **실기기에서 재서 통과한 것만 적는다.** 추측으로 줄을 늘리면 그 기기 사용자가
         * 앱째로 죽는다. 늘릴 때는 같은 기기를 **여러 번** 돌려 확률적으로도 안전한지 본다.
         *
         * - `s5e9945` = Exynos 2400 · Xclipse 940 (Galaxy S24/S24+ 국내·유럽판).
         *   검출·각인·마크 셋 다 CPU 와 답이 같고(최대차 1e-7) 1.95~3.2배 빠르다.
         * - `s5e9955` = Exynos 2500 · Xclipse 950 (Galaxy Z Flip7). 셋 다 3회 재현, 1.9~2.7배.
         * - `s5e9965` = Exynos 2600 · Xclipse 960 (Galaxy S26/S26+ 국내판). 각인은 위 KDoc 의 ⚠️ 를 볼 것.
         */
        val ALLOWED: Map<String, Set<String>> = listOf("s5e9945", "s5e9955", "s5e9965").associateWith {
            setOf(
                PillDetector.MODEL_FILE_NAME,
                ImprintReader.MODEL_FILE_NAME,
                MarkReader.MODEL_FILE_NAME
            )
        }
    }
}
