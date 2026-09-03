package app.nursemate.pill

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmTypography

/**
 * 결과 없음(⑥)·분석 실패(⑦)가 함께 쓰는 안내 문구 스타일.
 *
 * 정본 20·700 / 15·400(줄간격 1.5). 타입 스케일에 없는 크기라 여기 한 번만 적어 두고
 * 두 화면이 같은 값을 쓰게 한다.
 */
internal val PillOutcomeTitle: TextStyle =
    NmTypography.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)

internal val PillOutcomeDescription: TextStyle =
    NmTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.5.sp)

/** 실패 사유 표시용. 정본에는 없고 개발 중 원인을 보려고 둔다. */
internal val PillOutcomeDetail: TextStyle = NmTypography.caption
