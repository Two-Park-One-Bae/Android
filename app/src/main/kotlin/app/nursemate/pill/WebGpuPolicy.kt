package app.nursemate.pill

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import app.nursemate.core.vision.VisionBackend
import app.nursemate.core.vision.imprint.ImprintReader
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
 * | **차단 목록** | 이미 재현된 조합 | 없음 — 처음부터 CPU |
 * | **크래시 마커** | 아직 모르는 조합 | 그 기기에서 **1회** 죽는다 |
 *
 * ## 왜 예외 폴백으로는 부족한가
 * [OrtBackend][app.nursemate.core.vision.OrtBackend] 가 세션 생성 실패를 잡아 CPU 로 되돌리고,
 * [PillRecognitionViewModel.readFaces] 도 `runCatching` 으로 감싼다. 그런데 Adreno 740 은
 * **예외를 던지지 않고 프로세스가 죽는다**(`vulkan.adreno.so` `vkCmdPipelineBarrier` SIGSEGV).
 * 잡을 코드가 실행될 프로세스가 없다. 들어가기 전에 막는 수밖에 없다.
 *
 * ## 차단 목록에는 **잰 것만** 넣는다
 * 2026-10-13 Firebase Test Lab 실기기 실측이다.
 *
 * | 기기 | 각인 | 마크 |
 * |---|---|---|
 * | SM-S916N · Adreno 740 | **SIGSEGV** | 못 가봄 |
 * | SM-S928N · Adreno 750 | **OrtException + 디바이스 사망** | 통과 · 답도 정확(코사인 ≥0.999) |
 *
 * 검출은 Adreno 740 에서 성공한 운영 증거가 있다(Crashlytics 이슈 ①, 9개 검출 완료).
 * 그래서 **각인만** 막는다. Mali · PowerVR · Xclipse 는 재 본 적이 없으므로 **아무것도 적지
 * 않는다** — 모르는 것을 추측해 넣으면 멀쩡한 기기를 느리게 만든다. 그쪽은 마커가 받는다.
 *
 * ## 마커는 기기 단위다 — 모델 단위가 아니다
 * 세션은 플로우 내내 살아 있어서, 죽는 순간 어느 모델이 돌고 있었는지 세션만 보고는 못 가린다.
 * 모델마다 가리려면 추론 전후로 디스크에 흔적을 남겨야 하는데 핫픽스에 넣기엔 위험이 크다.
 * 그래서 **한 번이라도 네이티브로 죽으면 그 기기는 전부 CPU** 로 간다 — 보수적이지만 안전하다.
 * 이미 아는 조합은 차단 목록이 정확히 막으므로, 마커까지 가는 것은 모르는 기기뿐이다.
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
            "WebGPU 정책 — 크래시차단=$blockedByCrash · 퀄컴=${isQualcomm()} · " +
                "SoC=${socName()} · 차단모델=${blockedModels().joinToString().ifEmpty { "없음" }}"
        )
    }

    /** 세션을 열 때 [OrtBackend][app.nursemate.core.vision.OrtBackend] 가 묻는다. */
    private fun allow(modelFileName: String): Boolean = !blockedByCrash && modelFileName !in blockedModels()

    /**
     * 이 기기에서 **처음부터** WebGPU 를 막을 모델.
     *
     * 지금은 퀄컴 × 각인 하나다. 늘리기 전에 **그 조합을 실기기에서 재고** KDoc 표에 적는다.
     */
    private fun blockedModels(): Set<String> = if (isQualcomm()) setOf(ImprintReader.MODEL_FILE_NAME) else emptySet()

    /**
     * 퀄컴 SoC = Adreno GPU.
     *
     * `SOC_MANUFACTURER` 가 API 31 부터라 그 아래는 `HARDWARE` 로 받는다 — Test Lab 프로브가
     * 두 기기에서 `QTI` 와 `qcom` 을 각각 찍었다. GL 렌더러 문자열로도 가릴 수 있지만 EGL
     * 컨텍스트를 띄워야 해서, 앱이 뜨는 길목에 두기엔 무겁다.
     */
    private fun isQualcomm(): Boolean {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else ""
        return soc.contains("QTI", ignoreCase = true) ||
            soc.contains("Qualcomm", ignoreCase = true) ||
            Build.HARDWARE.startsWith("qcom", ignoreCase = true)
    }

    private fun socName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE

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
    }
}
