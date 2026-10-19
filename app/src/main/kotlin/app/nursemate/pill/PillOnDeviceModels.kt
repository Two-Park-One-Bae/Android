package app.nursemate.pill

import app.nursemate.core.vision.PillDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 알약 플로우의 온디바이스 모델 — 검출기와 각인 · 마크 판독기. 플로우(`PillRecognitionViewModel`) 수명만큼 들고 있는다.
 *
 * ## 한 번만 연다 · 미리 연다
 * 모델 열기에 1~3초가 걸린다(LiteRT · GPU 셰이더 준비). 촬영 화면에 들어오면 [prewarm] 이 미리 열어, 사용자가 구도를
 * 잡는 동안 준비를 끝낸다 — 앱을 새로 연 뒤 첫 식별이 3초 늦던 것(2026-10-19 실기기, NM-632 체크리스트 3번).
 * 미리 열기와 실제 검출이 겹치면 잠금에서 기다려 같은 모델을 쓴다. 열다 실패하면 다음 호출이 다시 연다.
 *
 * ## 닫기는 열기 · 실행이 끝난 뒤
 * 화면을 떠나 코루틴이 취소돼도 네이티브 열기 · 실행은 멈추지 않는다. 그 사이에 닫으면 모델이 새거나(열기) 닫힌 모델을
 * 건드려 죽는다(실행). 그래서 [close] 는 별도 범위에서 열기 잠금 · 각 모델의 실행 잠금을 차례로 잡은 뒤 닫는다.
 * 모델을 연 쪽이 취소돼도 열린 값은 열기 블록 안에서 저장되므로 닫을 주인이 남는다.
 */
internal class PillOnDeviceModels(private val modelFile: PillModelFile) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val opening = Mutex()
    private var detector: PillDetector? = null
    private var closed = false

    /** 각인 · 마크. 의존이 [modelFile] 하나뿐이라 주입하지 않고 여기서 만든다. */
    val faceReader = PillFaceReader(modelFile)

    suspend fun detector(): PillDetector = opening.withLock {
        check(!closed) { "닫힌 모델이다" }
        detector ?: withContext(Dispatchers.IO) {
            PillDetector(modelFile.prepare().single()).also { detector = it }
        }
    }

    /** 실패는 삼킨다 — 실제 검출 · 판독 때 다시 드러난다(그때 다시 연다). */
    fun prewarm() {
        scope.launch { runCatching { detector() } }
        scope.launch { runCatching { faceReader.prewarm() } }
    }

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
