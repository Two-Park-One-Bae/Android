package app.nursemate.pill

import app.nursemate.core.vision.PillDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * 알약 플로우의 온디바이스 모델 — 검출기와 각인 · 마크 판독기. 플로우(`PillRecognitionViewModel`) 수명만큼 들고 있는다.
 *
 * ## 한 번만 연다 · 미리 연다
 * 모델 열기에 1~3초가 걸린다(LiteRT · GPU 셰이더 준비). 촬영 화면에 들어오면 [prewarm] 이 미리 열어, 사용자가 구도를
 * 잡는 동안 준비를 끝낸다 — 앱을 새로 연 뒤 첫 식별이 3초 늦던 것(2026-10-19 실기기, NM-632 체크리스트 3번).
 * 검출기는 지연값이라 미리 열기와 실제 검출이 겹쳐도 같은 열기를 기다린다. 각인 · 마크는 [PillFaceReader] 가 잠근다.
 *
 * ## 별도 범위에서 연다
 * `viewModelScope` 에서 열면 화면을 떠날 때 취소되는데, 네이티브 열기는 멈추지 않아 열린 모델을 닫을 주인이 없어진다.
 * 그래서 [scope] 에서 열고, [close] 는 열기가 끝난 뒤 닫는다.
 */
internal class PillOnDeviceModels(modelFile: PillModelFile) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val detector: Deferred<PillDetector> = scope.async(start = CoroutineStart.LAZY) {
        PillDetector(modelFile.prepare().single())
    }

    /** 각인 · 마크. 의존이 [modelFile] 하나뿐이라 주입하지 않고 여기서 만든다. */
    val faceReader = PillFaceReader(modelFile)

    suspend fun detector(): PillDetector = detector.await()

    /** 실패는 삼킨다 — 실제 검출 · 판독 때 다시 드러난다. */
    fun prewarm() {
        detector.start()
        scope.launch { runCatching { faceReader.prewarm() } }
    }

    fun close() {
        // 열고 있는 중이면 다 열린 뒤 닫는다. 열다 실패했으면 닫을 것이 없다.
        detector.invokeOnCompletion { cause -> if (cause == null) detector.getCompleted().close() }
        scope.launch { faceReader.closeAfterOpening() }
    }
}
