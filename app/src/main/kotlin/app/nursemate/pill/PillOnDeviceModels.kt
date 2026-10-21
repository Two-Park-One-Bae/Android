package app.nursemate.pill

import android.graphics.Bitmap
import androidx.annotation.MainThread
import app.nursemate.core.vision.DetectionResult
import app.nursemate.core.vision.PillDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 알약 플로우의 온디바이스 모델 — 검출기와 각인 · 마크 판독기. 객체는 플로우(`PillRecognitionViewModel`) 수명이지만, 모델은
 * 플로우 화면이 떠 있는 동안만 연다([hold] · [unhold]).
 *
 * ## 미리 연다
 * 모델 열기에 1~3초가 걸린다(LiteRT · GPU 셰이더 준비 — 디스크 캐시가 있으면 짧다). 촬영 화면에 들어오면 [prewarm] 이
 * 검출기를 미리 열어, 사용자가 구도를 잡는 동안 준비를 끝낸다 — 앱을 새로 연 뒤 첫 식별이 3초 늦던 것(2026-10-19 실기기,
 * NM-632 체크리스트 3번). 미리 열기와 실제 검출이 겹치면 잠금에서 기다려 같은 모델을 쓴다. 열다 실패하면 다음 호출이 다시 연다.
 *
 * ## 검출기와 각인 · 마크를 번갈아 연다(NM-636)
 * 둘은 동시에 쓰이지 않는다 — 검출이 끝나야 판독할 크롭이 생긴다. 함께 열면 S24 에서 약 1.5GB, 번갈아 열면 정점이
 * 955MB 다(앱 전체 Native + GL, 2026-10-21). 그래서 검출 전에 각인 · 마크를, 각인 · 마크를 열기 전에 검출기를 닫는다.
 * 다시 여는 비용은 디스크 캐시라 1초 안팎이고, 각인 · 마크는 원래 결과 화면을 막지 않고 늦게 붙는 값이다.
 *
 * ## 플로우 화면이 모두 사라지면 내려놓는다(NM-636)
 * 알약 탭 그래프는 홈 · 타이머 탭에 가 있어도 살아 있어, 모델을 쥐고 있으면 식별하지 않는 동안에도 수백 MB 를 잡는다.
 * 그래서 플로우 화면(촬영 ~ 결과 · 수정 · 상세)마다 떠 있는 동안 [hold] 하고, 마지막 화면이 사라지면 닫는다 — 식별을
 * 마쳐 목록으로 돌아가든, 탭을 옮기든, 시스템 뒤로든 같다. 화면 전환 중에는 새 화면이 먼저 떠서 0 을 거치지 않는다.
 * 내려놓은 뒤에는 남은 판독이 모델을 다시 열지 않는다. 다시 열 때는 GPU 번역이 디스크 캐시(`VisionBackend.gpuCache`)라 짧다.
 *
 * ## 닫기는 열기 · 실행이 끝난 뒤
 * 화면을 떠나 코루틴이 취소돼도 네이티브 열기 · 실행은 멈추지 않는다. 그 사이에 닫으면 모델이 새거나(열기) 닫힌 모델을
 * 건드려 죽는다(실행). 그래서 검출은 [opening] 을 쥔 채 실행하고, 닫는 쪽은 모두 같은 잠금 · 각 모델의 실행 잠금을 잡은
 * 뒤 닫는다. 모델을 연 쪽이 취소돼도 열린 값은 잠금 안에서 저장되므로 닫을 주인이 남는다.
 */
internal class PillOnDeviceModels(private val modelFile: PillModelFile) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 검출기 열기 · 실행 · 닫기를 줄 세운다 — 실행 중에는 닫지 못한다. */
    private val opening = Mutex()
    private var detector: PillDetector? = null
    private var closed = false

    /** 내려놓았다 — [hold] 전까지 열지 않는다. */
    @Volatile
    private var released = false

    /** 내려놓기 · 다시 쓰기의 순서 — 내려놓은 직후 다시 들어오면 늦게 도착한 내려놓기를 무시한다. */
    private var generation = 0

    /** 지금 떠 있는 알약 플로우 화면 수(메인 스레드). 0 이 되면 내려놓는다. */
    private var holders = 0

    /** 각인 · 마크. 의존이 [modelFile] 하나뿐이라 주입하지 않고 여기서 만든다. */
    val faceReader = PillFaceReader(modelFile, canOpen = { !released }, beforeOpen = ::closeDetector)

    /** 검출한다. 잠금을 쥔 채 실행해, 도는 동안 각인 · 마크 쪽이나 내려놓기가 검출기를 닫지 못하게 한다. */
    suspend fun detect(bitmap: Bitmap): DetectionResult {
        // 번갈아 연다 — 각인 · 마크를 먼저 내려놓는다(읽는 중이면 다 읽은 뒤).
        faceReader.release()
        return opening.withLock { openDetector().detect(bitmap) }
    }

    /** [opening] 안에서만 부른다. */
    private suspend fun openDetector(): PillDetector {
        check(!closed && !released) { "닫혔거나 내려놓은 모델이다" }
        return detector ?: withContext(Dispatchers.IO) {
            PillDetector(modelFile.prepare().single()).also { detector = it }
        }
    }

    /** 검출기를 내려놓는다 — 각인 · 마크를 열기 직전([PillFaceReader] 가 부른다). 검출 중이면 끝난 뒤 닫는다. */
    private suspend fun closeDetector() = opening.withLock {
        detector?.closeWhenIdle()
        detector = null
    }

    /** 촬영 화면에 들어왔다 — 검출기만 미리 연다(각인 · 마크는 검출이 끝난 뒤). 실패는 삼킨다 — 실제 검출 때 다시 드러난다. */
    fun prewarm() {
        scope.launch {
            faceReader.release()
            runCatching { opening.withLock { openDetector() } }
        }
    }

    /** 알약 플로우 화면이 떴다. 첫 화면이면 내려놓은 모델을 다시 쓸 수 있게 한다(여는 것은 쓸 때). */
    @MainThread
    fun hold() {
        if (holders++ == 0) resume()
    }

    /** 알약 플로우 화면이 사라졌다. 마지막 화면이면 모델을 닫아 메모리를 돌려준다 — 식별을 마쳤거나 플로우를 떠났다. */
    @MainThread
    fun unhold() {
        if (--holders == 0) release()
    }

    @Synchronized
    private fun resume() {
        generation++
        released = false
    }

    private fun release() {
        val at = synchronized(this) {
            released = true
            generation
        }
        scope.launch {
            opening.withLock {
                if (current() != at) return@withLock
                detector?.closeWhenIdle()
                detector = null
            }
            if (current() == at) faceReader.release()
        }
    }

    @Synchronized
    private fun current() = generation

    fun close() {
        scope.launch {
            opening.withLock {
                closed = true
                detector?.closeWhenIdle()
                detector = null
            }
            faceReader.close()
        }
    }
}
