package app.nursemate.pill

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.content.getSystemService
import app.nursemate.core.model.PillCandidate

/**
 * 최종 결과를 텍스트로 클립보드에 넣는다.
 *
 * MVP 의 '공유'는 공유 시트가 아니라 **텍스트 복사**다(spec §최종 결과·공유 — "결과를
 * 텍스트로 복사한다(MVP·화면 유지)"). 화면은 그대로 둔다.
 *
 * ## 고지를 함께 복사한다
 * 복사한 목록은 인수인계 메모나 메신저로 옮겨 다니는데, 그 자리에서는 이 결과가 앱의
 * 보조 정보라는 맥락이 사라진다. 목록만 떼어 보내지 않도록 고지를 붙여 둔다.
 */
fun Context.copyPillResult(pills: List<PillCandidate>) {
    val text = pills.toShareText()
    getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("알약 식별 결과", text))

    // 안드로이드 13 부터는 시스템이 복사 알림을 대신 띄운다 — 겹쳐 보이지 않게 그 아래만 띄운다.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, "결과를 복사했어요", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 공유 텍스트.
 *
 * ⚠️ 정본이 없다(스펙이 "공유 텍스트 구성은 user-flow 밖"으로 미뤄 뒀다). 옮겨 붙였을 때
 * 그대로 읽히도록 번호·품목명·업체명만 한 줄씩 두고 고지를 끝에 붙였다.
 */
internal fun List<PillCandidate>.toShareText(): String {
    val items = mapIndexed { index, pill ->
        val name = pill.pillName ?: pill.pillCode
        val company = pill.companyName?.let { " ($it)" }.orEmpty()
        "${index + 1}. $name$company"
    }
    return buildString {
        appendLine("[널스메이트] 알약 식별 결과 ${size}개")
        appendLine()
        items.forEach { appendLine(it) }
        appendLine()
        append(
            "널스메이트의 알약 식별 결과는 참고용 보조 정보입니다. " +
                "투약 전 반드시 처방 내용과 약품 라벨을 확인하시고, 최종 판단은 의료진의 확인을 따라 주세요."
        )
    }
}
