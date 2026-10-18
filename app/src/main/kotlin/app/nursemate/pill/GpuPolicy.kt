package app.nursemate.pill

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import app.nursemate.BuildConfig
import app.nursemate.core.vision.VisionBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **이 기기에서 모델을 GPU 로 열어도 되는가** — NM-628(LiteRT). NM-574 의 WebGPU 정책을 이어받았다.
 *
 * ## 허용목록이 없어졌다
 * ORT WebGPU 시절에는 SoC 마다 깨지는 모델이 달라 **재서 된 기기만** 켰다(Exynos 2400 · 2500 · 2600).
 * LiteRT OpenCL 은 ML 이 8개 SoC(Exynos 3 · Snapdragon 5)에서 공식 세트로 재 **전부 운영 fp32 와 같은 답**을
 * 냈다(`_handoff/litert-ondevice-migration.md`). 기기별 분기 없이 다 GPU 로 열고, 안 되는 경우만 막는다.
 *
 * | 겹 | 무엇을 막나 | 대가 |
 * |---|---|---|
 * | GPU 로 못 열면 CPU | 드라이버가 모델을 거절 | 없음(`LiteRtModel.open` 이 한다) |
 * | **크래시 마커**(여기) | GPU 를 쓰다 프로세스째 죽음 | 그 기기에서 **1회** 죽는다 |
 * | 모델별 CPU 고정(여기) | 돌기는 하는데 답이 틀림 — 첫 실행 자가 점검이 잡는다 | 그 모델만 느리다 |
 *
 * ## 막은 기록은 (앱 버전 · 기기 빌드) 단위다
 * 드라이버는 OS 업데이트로 바뀐다. 한 번 죽은 기기를 영영 CPU 로 두면 고쳐진 뒤에도 느리다. 그래서
 * 기록을 `versionCode` 와 `Build.FINGERPRINT`(OS · 드라이버를 포함하는 빌드 식별자)에 묶는다 —
 * 둘 중 하나가 바뀌면 다시 GPU 를 시도한다.
 *
 * ⚠️ ORT WebGPU 시절 기록(`webgpu_policy`)은 읽지 않는다. 그때 죽은 것은 WebGPU(Vulkan) 경로였고
 * LiteRT 는 OpenCL 이라 같은 드라이버 코드를 타지 않는다.
 *
 * ## 왜 예외 폴백만으로는 부족한가
 * 드라이버 크래시는 **예외를 던지지 않고 프로세스가 죽는다**(ORT 시절 Adreno 740 `vulkan.adreno.so`
 * SIGSEGV). 잡을 코드가 실행될 프로세스가 없다 — 그래서 다음 실행이 알아채고 열기 전에 막는다.
 */
@Singleton
class GpuPolicy @Inject constructor(@param:ApplicationContext private val context: Context) {

    /**
     * ⚠️ DataStore 가 아니라 SharedPreferences 다. [VisionBackend.allowGpu] 는 모델을 여는
     * 스레드에서 **동기로** 불리는데, DataStore 를 읽으려면 `runBlocking` 이 필요하다.
     */
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 이 프로세스가 GPU 를 썼다는 흔적. 죽으면 남고, 다음 실행이 읽는다. */
    private val sentinel = File(context.filesDir, SENTINEL)

    /** 지금 앱 · 기기 빌드. 막은 기록이 이 값과 같을 때만 유효하다. */
    private val build = "${BuildConfig.VERSION_CODE}|${Build.FINGERPRINT}"

    @Volatile
    private var blockedByCrash = false

    @Volatile
    private var cpuModels: Set<String> = emptySet()

    /**
     * [VisionBackend] 에 정책을 꽂는다. **온디바이스 추론이 처음 돌기 전에** 불려야 한다 —
     * `Application.onCreate` 가 그 자리다.
     */
    fun install() {
        if (prefs.getString(KEY_BUILD, null) != build) {
            // 앱이나 OS 가 바뀌었다 — 예전 판정은 버리고 GPU 를 다시 시도한다.
            prefs.edit().clear().putString(KEY_BUILD, build).apply()
        }
        blockedByCrash = prefs.getBoolean(KEY_BLOCKED, false) || trippedByLastExit()
        cpuModels = prefs.getStringSet(KEY_CPU_MODELS, emptySet()).orEmpty()

        VisionBackend.allowGpu = ::allow
        VisionBackend.onGpuModelOpened = { markAttempt() }

        Log.i(
            TAG,
            "GPU 정책 — SoC ${soc()} · 크래시차단=$blockedByCrash · " +
                "CPU 고정 모델=${cpuModels.joinToString().ifEmpty { "없음" }}"
        )
    }

    /** 모델을 열 때 `LiteRtModel.open` 이 묻는다. */
    private fun allow(modelFileName: String): Boolean = !blockedByCrash && modelFileName !in cpuModels

    /**
     * 이 모델은 이 기기 · 이 빌드에서 CPU 로만 연다 — 자가 점검에서 GPU 답이 틀렸을 때 부른다.
     * 다음에 열 때부터 먹는다(이미 열린 모델은 호출부가 닫고 다시 연다).
     */
    fun pinToCpu(modelFileName: String) {
        cpuModels = cpuModels + modelFileName
        prefs.edit().putStringSet(KEY_CPU_MODELS, cpuModels).apply()
        Log.w(TAG, "$modelFileName 은 이 기기에서 CPU 로 연다")
    }

    /** SoC 모델 이름 — 로그용. `SOC_MODEL` 이 API 31 부터라 그 아래는 `HARDWARE` 로 받는다. */
    private fun soc(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE

    /** 이 프로세스가 GPU 로 모델을 열었다고 적어 둔다. 이미 있으면 건드리지 않는다. */
    private fun markAttempt() {
        runCatching { if (!sentinel.exists()) sentinel.writeText(MARK) }
            .onFailure { Log.w(TAG, "GPU 흔적을 남기지 못했다", it) }
    }

    /**
     * **직전 프로세스가 네이티브로 죽었고, 그때 GPU 를 쓰고 있었는가.**
     *
     * 흔적 파일만으로는 못 가린다 — 사용자가 작업목록에서 치워도 파일은 남는다. OS 에 왜
     * 죽었는지 직접 묻는다(API 30+). 그 아래 판은 판정하지 않는다. 오탐으로 멀쩡한 기기를
     * 느리게 만드는 쪽이, 1회 죽는 것보다 나쁘다.
     */
    private fun trippedByLastExit(): Boolean {
        if (!sentinel.exists()) return false
        // 판정했으면 지운다. 남겨 두면 다음 크래시가 아닌 종료까지 이 흔적에 걸린다.
        val crashed = lastExitWasNativeCrash()
        runCatching { sentinel.delete() }
        if (crashed) {
            prefs.edit().putBoolean(KEY_BLOCKED, true).apply()
            Log.w(TAG, "직전 실행이 네이티브로 죽었다 — 이 빌드에서는 CPU 로 돈다")
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
        const val TAG = "GpuPolicy"
        const val PREFS = "gpu_policy"
        const val KEY_BUILD = "build"
        const val KEY_BLOCKED = "blocked_by_native_crash"
        const val KEY_CPU_MODELS = "cpu_models"
        const val SENTINEL = "gpu_attempt"
        const val MARK = "1"

        /** SIGILL · SIGABRT · SIGBUS · SIGSEGV. 드라이버가 죽을 때 나오는 것들이다. */
        val FATAL_SIGNALS = setOf(4, 6, 7, 11)
    }
}
