package app.nursemate.pill

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.vision.PillDetector
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R8 을 거친 릴리스 빌드에서 **ONNX Runtime 왕복이 살아남는지**만 본다.
 *
 * ## 왜 이 테스트가 있나
 * `KNOWN-ISSUES.md` ⑩ — R8 이 `ai.onnxruntime.TensorInfo` 등의 이름을 바꿔 릴리스에서만
 * 알약 식별이 100% SIGABRT 로 죽었다(0.2.1·0.2.2·0.2.3). 네이티브 쪽이 **추론 결과를 JVM 으로
 * 돌려줄 때** 클래스를 이름으로 찾기 때문이다(`FindClass` → `GetMethodID`). Java 코드가 그
 * 클래스들을 직접 부르지 않아 R8 은 지워도 되는 것으로 본다 — 지금은
 * `proguard-rules.pro` 의 `-keep class ai.onnxruntime.** { *; }` 가 막고 있다.
 *
 * 그 keep 규칙이 사라지거나 R8 이 달라지면 **이 테스트만 빨개진다.** 단위 테스트로는 못 잡는다
 * (JVM 에는 R8 도 네이티브 라이브러리도 없다). `assembleRelease` 로도 못 잡는다 — 빌드는
 * 멀쩡히 성공하고 실행할 때 죽는다. 그래서 `testBuildType = "release"` 다.
 *
 * ## 왜 빈 이미지로 충분한가
 * 죽던 자리는 검출이 아니라 **결과를 읽는 단계**다(`OrtSession.Result.tensor`). 알약이 한 알도
 * 없는 이미지에서도 모델은 100 개 쿼리를 그대로 내놓고, 그걸 읽는 순간 같은 경로를 탄다.
 * 검출 정확도는 이 테스트가 볼 일이 아니다 — 그건 ML 저장소의 테스트셋 대조가 한다.
 *
 * ⚠️ **약포 사진을 픽스처로 두지 않는다.** 단색 비트맵을 만들어 쓴다. 사진을 넣으면 저장소에
 * 약포 이미지가 들어오고, 그건 크기와 무관하게 하지 않기로 한 것이다.
 */
@RunWith(AndroidJUnit4::class)
class PillDetectorReleaseSmokeTest {

    // ⚠️ 백틱 이름에 공백이 들어간다 — dex 가 이걸 받아 주는 것은 **API 30 이상**이다.
    // minSdk 는 26 이지만 이 테스트를 돌리는 것은 CI 에뮬레이터(API 34)뿐이라 문제가 없다.
    // 더 낮은 기기에서 돌릴 일이 생기면 이름에서 공백을 뺀다.
    @Test
    fun `릴리스 빌드에서 ONNX 추론 결과를 읽을 수 있다`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.cacheDir, "release-smoke/$MODEL_NAME").apply {
            parentFile?.mkdirs()
        }
        if (!model.isFile || model.length() == 0L) {
            context.assets.open(MODEL_NAME).use { source ->
                model.outputStream().use(source::copyTo)
            }
        }
        assertTrue("asset 에서 꺼낸 모델이 비어 있다", model.length() > 0)

        // ⚠️ `createBitmap` 은 기본이 투명이라 `ARGB_8888` 로 만들고 한 번 칠한다. 전처리가
        // RGB888 로 읽으므로 알파만 있는 비트맵이 들어가면 입력이 0 으로 쏠려, 죽지 않아도
        // 「무엇을 통과했는지」가 흐려진다.
        val bitmap = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }

        val result = PillDetector(model).use { detector ->
            runBlocking { detector.detect(bitmap) }
        }

        // 여기까지 왔다는 것이 곧 통과다 — 결과를 읽는 경로가 R8 을 넘었다는 뜻이다.
        // 개수는 검사하지 않는다(빈 이미지라 0 이 정상이지만, 그건 이 테스트의 관심이 아니다).
        assertEquals(SIDE, result.imageWidth)
        assertEquals(SIDE, result.imageHeight)
    }

    private companion object {
        const val MODEL_NAME = "rfdetr_seg_small.onnx"

        /** 전처리가 어차피 576 으로 리사이즈한다. 같은 값을 써 불필요한 스케일을 없앤다. */
        const val SIDE = 576
    }
}
