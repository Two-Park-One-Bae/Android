package app.nursemate.pill

import android.graphics.Bitmap
import android.util.Log
import app.nursemate.core.vision.imprint.ImprintReader
import app.nursemate.core.vision.mark.MarkEmbedding
import app.nursemate.core.vision.mark.MarkPresence
import app.nursemate.core.vision.mark.MarkReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 찍힌 면의 각인·마크를 온디바이스로 읽는다 — NM-485(각인)·NM-515(마크)를 화면과 요청에 잇는다.
 *
 * ## 사진에 찍힌 면만 읽는다
 * 계약의 `front` 는 **처음 찍은 사진의 면**이다. 뒷면은 사진이 없으니 읽을 것도 없고, 조건도
 * 「전체」로 남는다. 정본 ②도 앞면에는 읽은 각인을, 뒷면에는 '전체'를 그린다.
 *
 * ## 각인을 먼저 읽는다 — 마크 임계가 그 답에 걸려 있다
 * 같은 면의 각인을 읽었으면 마크 유무 임계가 0.80, 못 읽었으면 0.60 이다([MarkPresence]).
 * 순서를 바꾸면 임계가 늘 느슨한 쪽으로 고정돼 **마크 오탐이 하드 필터로 나간다.**
 *
 * ## 마크를 먼저 연다 — 읽는 순서와 별개다(NM-636)
 * 마크 모델은 여는 동안 잠깐 네이티브 메모리를 약 200MB 더 쓴다. 각인을 연 뒤에 열면 그 위에 얹혀 S24 정점이
 * 1172MB, 먼저 열면 955MB 다(2026-10-21, 디스크 캐시가 찬 상태). 여는 데 드는 시간은 같다.
 *
 * ## 알약 하나에 약 0.9초가 든다
 * 각인이 144장(680 ms), 마크가 8장(157 ms)이다 — Galaxy S24 · LiteRT GPU 기준(NM-628). 셋을 찍으면
 * 2.6초다. 그래서 **결과 화면을 막지 않는다** — 서버 속성 추출과 나란히 돌고, 늦게 와서
 * 수정 화면에 얹힌다([PillRecognitionViewModel.readFaces]).
 *
 * 기기별 시간은 ML 실측표(`_handoff/litert-ondevice-migration.md`)에 있다 — 플래그십 8종 각인 0.4~1.2초.
 * GPU 를 못 쓰는 기기는 CPU 로 1.4~5.0초다. 중급 · 구형은 ML 이 재는 중이다.
 */
class PillFaceReader(
    private val modelFile: PillModelFile,
    /** 지금 열어도 되는가 — 내려놓은 뒤([PillOnDeviceModels.unhold])에는 남은 판독이 모델을 다시 열지 않게 막는다. */
    private val canOpen: () -> Boolean = { true },
    /** 각인 · 마크 모델을 새로 열기 직전 — 검출기를 내려놓아 둘을 한꺼번에 쥐지 않는다(NM-636 번갈아 열기). */
    private val beforeOpen: suspend () -> Unit = {}
) {

    private var imprint: ImprintReader? = null
    private var mark: MarkReader? = null

    /** [close] 뒤에는 열지 않는다 — 판독이 닫기 뒤에 잠금을 얻으면 닫은 뒤에 모델이 새로 열려 샌다. */
    private var closed = false

    /** 열기 · 판독 · 닫기를 줄 세운다 — 판독 중에는 닫지 못하고, 모델을 두 번 열지 않는다. */
    private val opening = Mutex()

    /**
     * 잠금을 쥔 채 열고 읽는다 — 읽는 동안 검출기 쪽이나 내려놓기가 각인 · 마크를 닫지 못하게.
     *
     * @param crop 마스크로 오려낸 낱알 한 면(`DetectedPill.crop`)
     * @return 읽은 값. 모델이 확신하지 못한 항목은 null 이다 — **빈 값이 아니다**
     */
    suspend fun read(crop: Bitmap): FaceReading = opening.withLock {
        check(!closed && canOpen()) { "닫혔거나 내려놓은 판독기다" }
        if (imprint == null || mark == null) beforeOpen()
        // 여는 것은 마크가 먼저다 — 위 「마크를 먼저 연다」.
        val markReader = mark ?: withContext(Dispatchers.IO) {
            MarkReader(modelFile.prepare(PillModel.Mark).single()).also { mark = it }
        }
        val imprintReader = imprint ?: withContext(Dispatchers.IO) {
            ImprintReader(modelFile.prepare(PillModel.Imprint)).also { imprint = it }
        }
        val imprintResult = imprintReader.read(crop)
        val markResult = markReader.read(crop)

        Log.i(
            TAG,
            "각인 ${imprintResult.imprint ?: "—"} ${imprintResult.timings.totalMs}ms | " +
                "마크 유무 ${"%.3f".format(markResult.presence)} 종 ${markResult.species} " +
                "${markResult.timings.totalMs}ms"
        )

        FaceReading(
            imprint = imprintResult.imprint,
            species = markResult.species,
            hasMark = MarkPresence.hasMark(markResult.presence, imprintResult.imprint),
            markEmbedding = MarkEmbedding.encode(markResult.embedding)
        )
    }

    /** 모델을 닫되 다시 열 수 있게 둔다 — 식별이 끝나 메모리를 돌려줄 때(NM-636). 읽는 중이면 다 읽은 뒤 닫는다. */
    suspend fun release() = opening.withLock {
        imprint?.closeWhenIdle()
        mark?.closeWhenIdle()
        imprint = null
        mark = null
    }

    /**
     * 여는 중이면 다 연 뒤, 읽는 중이면 다 읽은 뒤 닫는다 — 열기 · 판독과 닫기가 엇갈려 모델이 새거나 닫힌 모델을
     * 건드리지 않게. 이 뒤로는 다시 열지 않는다.
     */
    suspend fun close() = opening.withLock {
        closed = true
        imprint?.closeWhenIdle()
        mark?.closeWhenIdle()
        imprint = null
        mark = null
    }

    private companion object {
        const val TAG = "NM515"
    }
}

/**
 * 한 면에서 모델이 읽어 낸 것.
 *
 * ## null 과 빈 값은 다르다
 * [imprint] 가 null 이면 「못 읽었다」고, 빈 문자열은 「각인이 없는 알약만」이라는 하드 조건이다.
 * [hasMark] 도 마찬가지로 `false` 가 나오지 않는다 — 모델은 `true` 아니면 null 이다.
 *
 * @param species 종 번호(1..103). 사람에게 보여 줄 식약처 마크 그림을 고르는 데 쓴다
 * @param markEmbedding base64 fp16 8×768. 서버가 카탈로그와 코사인으로 **재정렬**한다
 */
data class FaceReading(
    val imprint: String? = null,
    val species: Int = 0,
    val hasMark: Boolean? = null,
    val markEmbedding: String? = null
)
