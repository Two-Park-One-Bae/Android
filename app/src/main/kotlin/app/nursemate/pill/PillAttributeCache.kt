package app.nursemate.pill

import android.content.Context
import android.util.Log
import app.nursemate.BuildConfig
import app.nursemate.core.model.PillAttribute
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 마지막 속성 추출 결과를 디버그 빌드에서 재사용한다.
 *
 * ## 왜 필요한가
 * 속성 추출은 Gemini 호출이라 **한 번에 5~8초 걸리고 하루 15회 한도를 깎는다.** 화면을
 * 고칠 때마다 실제 호출을 하면 오후 한 나절에 한도가 바닥나고, 그때부터는 한도 안내만
 * 보게 된다. 그래서 한 번 받은 응답을 파일에 두고 다시 쓴다.
 *
 * ## 릴리스에서는 아무 일도 하지 않는다
 * [BuildConfig.DEBUG] 가 아니면 저장도 조회도 건너뛴다. 실제 사용자가 남의 사진에서 나온
 * 속성을 보게 되는 일은 없어야 한다.
 *
 * ## 사진이 달라도 그대로 쓴다
 * 이건 **UI 확인용**이라 값의 정확성이 목적이 아니다. 검출 개수가 다르면 앞에서부터 잘라
 * 맞추고 모자라면 그만큼만 채운다. 실제 값을 봐야 할 때는 캐시를 지운다:
 * ```
 * adb shell run-as app.nursemate.debug rm files/debug/last-attributes.json
 * ```
 */
@Singleton
class PillAttributeCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json
) {

    private val file: File get() = File(context.filesDir, "debug/last-attributes.json")

    /** @return 재사용할 값이 없으면 null. 릴리스에서는 항상 null. */
    suspend fun load(pillIds: List<String>): List<PillAttribute>? {
        if (!BuildConfig.DEBUG) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val saved = json.decodeFromString(ListSerializer(PillAttribute.serializer()), file.readText())
                // 저장된 값의 pillId 는 그때의 검출 순서다. 지금 순서에 맞춰 다시 붙인다.
                pillIds.mapIndexedNotNull { index, id ->
                    saved.getOrNull(index)?.copy(pillId = id)
                }.takeIf { it.isNotEmpty() }
            }.getOrNull()
                ?.also { Log.i(TAG, "캐시된 속성 ${it.size}개 재사용 — Gemini 호출 건너뜀") }
        }
    }

    suspend fun save(items: List<PillAttribute>) {
        if (!BuildConfig.DEBUG) return
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(ListSerializer(PillAttribute.serializer()), items))
                Log.i(TAG, "속성 ${items.size}개 캐시에 저장 — 다음 테스트는 호출 없이 돈다")
            }
        }
    }

    private companion object {
        const val TAG = "NM393"
    }
}
