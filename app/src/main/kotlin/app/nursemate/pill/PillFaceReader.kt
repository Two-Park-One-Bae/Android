package app.nursemate.pill

import android.graphics.Bitmap
import android.util.Log
import app.nursemate.core.vision.imprint.ImprintReader
import app.nursemate.core.vision.mark.MarkEmbedding
import app.nursemate.core.vision.mark.MarkPresence
import app.nursemate.core.vision.mark.MarkReader
import java.io.Closeable
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
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
 * ## 알약 하나에 2초가 든다
 * 각인이 144장(1796 ms), 마크가 8장(178 ms)이다 — Galaxy S24 · WebGPU 기준. 셋을 찍으면
 * 6초다. 그래서 **결과 화면을 막지 않는다** — 서버 속성 추출과 나란히 돌고, 늦게 와서
 * 수정 화면에 얹힌다([PillRecognitionViewModel.readFaces]).
 *
 * 저사양 기기 대기 시간은 아직 안 쟀다(NM-485 「정할 것」 1번 · NM-515 DoD).
 */
class PillFaceReader @Inject constructor(private val modelFile: PillModelFile) : Closeable {

    private var imprint: ImprintReader? = null
    private var mark: MarkReader? = null

    /**
     * @param crop 마스크로 오려낸 낱알 한 면(`DetectedPill.crop`)
     * @return 읽은 값. 모델이 확신하지 못한 항목은 null 이다 — **빈 값이 아니다**
     */
    suspend fun read(crop: Bitmap): FaceReading {
        val imprintResult = loadImprint().read(crop)
        val markResult = loadMark().read(crop)

        Log.i(
            TAG,
            "각인 ${imprintResult.imprint ?: "—"} ${imprintResult.timings.totalMs}ms | " +
                "마크 유무 ${"%.3f".format(markResult.presence)} 종 ${markResult.species} " +
                "${markResult.timings.totalMs}ms"
        )

        return FaceReading(
            imprint = imprintResult.imprint,
            species = markResult.species,
            hasMark = MarkPresence.hasMark(markResult.presence, imprintResult.imprint),
            markEmbedding = MarkEmbedding.encode(markResult.embedding)
        )
    }

    private suspend fun loadImprint(): ImprintReader = imprint ?: withContext(Dispatchers.IO) {
        ImprintReader(modelFile.prepare(PillModel.Imprint)).also { imprint = it }
    }

    private suspend fun loadMark(): MarkReader = mark ?: withContext(Dispatchers.IO) {
        MarkReader(modelFile.prepare(PillModel.Mark)).also { mark = it }
    }

    override fun close() {
        imprint?.close()
        mark?.close()
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
