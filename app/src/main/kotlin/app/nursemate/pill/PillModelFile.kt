package app.nursemate.pill

import android.content.Context
import android.util.Log
import app.nursemate.BuildConfig
import app.nursemate.core.vision.PillDetector
import app.nursemate.core.vision.imprint.ImprintReader
import app.nursemate.core.vision.mark.MarkReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 앱이 품고 다니는 온디바이스 모델 셋. 파일 이름의 정본은 각 모델을 여는 쪽에 있다. */
enum class PillModel(val fileName: String) {
    /** 알약 검출·분할 (RF-DETR-seg). 119MB 로 셋 중 가장 크다. */
    Detector(PillDetector.MODEL_FILE_NAME),

    /** 각인 OCR (CRNN + CTC). */
    Imprint(ImprintReader.MODEL_FILE_NAME),

    /** 마크 종·유무·임베딩 (ConvNeXt-Tiny species). */
    Mark(MarkReader.MODEL_FILE_NAME)
}

/**
 * 온디바이스 모델 파일을 준비한다.
 *
 * ONNX Runtime 은 세션을 만들 때 **파일 경로**를 요구하는데 APK 안의 asset 에는 경로가 없다.
 * 그래서 처음 한 번 앱 전용 저장소로 꺼내 두고 그다음부터는 그걸 쓴다.
 *
 * ## 왜 통째로 메모리에 올리지 않나
 * ORT 는 바이트 배열로도 세션을 만들 수 있지만 셋을 합치면 196MB 다. 힙에 그만큼 올리면
 * 저사양 기기에서 OOM 이 난다. 디스크에 한 번 쓰는 편이 안전하다.
 *
 * ## 저장소를 두 배 쓴다
 * APK 안(압축)과 꺼낸 파일 양쪽에 있게 된다. Play Asset Delivery 를 쓰면 꺼내는 단계 없이
 * 경로를 받을 수 있는데, 그건 모델 배포 방식을 정할 때 함께 본다 — **아직 티켓이 없다.**
 * (NM-396 을 가리키고 있었으나 그건 「RF-DETR-seg Android 변환 스파이크」로 2026-09-01 에
 * 닫혔고 배포 방식과는 무관하다. 모델 수급 경로는 NM-483 에서 DVC 로 정했다 —
 * `docs/RELEASE.md` 「검출 모델」.)
 */
@Singleton
class PillModelFile @Inject constructor(@param:ApplicationContext private val context: Context) {

    /**
     * @return 바로 열 수 있는 모델 파일
     * @throws IllegalStateException 꺼내지 못한 경우 — 화면은 ⑦ 분석 실패로 간다
     */
    suspend fun prepare(model: PillModel = PillModel.Detector): File = withContext(Dispatchers.IO) {
        val name = model.fileName
        developerOverride(name)?.let { return@withContext it }

        // 버전을 경로에 박아 둔다. 앱을 올렸는데 예전 모델을 그대로 쓰는 사고를 막는다.
        val target = File(context.filesDir, "model/${BuildConfig.VERSION_CODE}/$name")
        if (target.isFile && target.length() > 0) return@withContext target

        target.parentFile?.mkdirs()
        extract(name, target)
        cleanUpOldVersions(target.parentFile)
        target
    }

    /**
     * 개발용 우회 — 앱 전용 외부 저장소에 파일을 밀어 넣으면 그쪽을 쓴다.
     * ```
     * adb push rfdetr_seg_small.onnx /sdcard/Android/data/app.nursemate.debug/files/
     * ```
     * 다른 양자화(int8·fp16)를 실기기에서 비교할 때 앱을 다시 빌드하지 않아도 된다.
     * **디버그 빌드에서만** 본다 — 릴리스에서 외부 파일을 읽으면 남이 바꿔치기할 수 있다.
     */
    private fun developerOverride(name: String): File? {
        if (!BuildConfig.DEBUG) return null
        return File(context.getExternalFilesDir(null), name).takeIf { it.isFile }
            ?.also { Log.i(TAG, "개발용 모델 사용: ${it.absolutePath}") }
    }

    /**
     * asset → 파일. **임시 이름으로 쓰고 마지막에 옮긴다** — 복사 도중 앱이 죽으면
     * 잘린 파일이 남고, 다음 실행에서 그걸 정상으로 오인해 세션 생성이 깨진다.
     */
    private fun extract(name: String, target: File) {
        val temp = File(target.parentFile, "$name.tmp")
        runCatching {
            context.assets.open(name).use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            check(temp.renameTo(target)) { "모델 파일을 옮기지 못했습니다" }
            Log.i(TAG, "$name 추출 완료 ${target.length() / 1024 / 1024}MB")
        }.onFailure {
            temp.delete()
            throw IllegalStateException("모델을 준비하지 못했습니다", it)
        }
    }

    /**
     * 이전 **앱 버전**에서 꺼내 둔 파일. 지우지 않으면 업데이트할 때마다 196MB 씩 쌓인다.
     *
     * ⚠️ 지우는 것은 형제 **디렉터리**(= 다른 versionCode)뿐이다. 같은 디렉터리 안의 다른
     * 모델은 건드리지 않는다 — 셋이 한 폴더를 함께 쓰므로 파일 단위로 쓸면 방금 꺼낸 검출
     * 모델을 각인 모델이 지운다.
     */
    private fun cleanUpOldVersions(current: File?) {
        val root = current?.parentFile ?: return
        root.listFiles()?.filter { it != current }?.forEach { it.deleteRecursively() }
    }

    private companion object {
        const val TAG = "NM394"
    }
}
