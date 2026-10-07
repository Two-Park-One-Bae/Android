package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.ImprintSource

/**
 * 앞면·뒷면 조건 카드 — 정본 ② · ⑪ (NM-516).
 *
 * ## 세 칸이 같은 드롭다운이다
 * 각인·구분선·마크가 전부 「전체 · 없음 · 값」 3단이다([FaceInput]). 생김새가 같아야 셋이
 * 같은 규칙으로 움직인다는 것이 보인다 — v0 은 체크박스·세그먼트·체크박스로 제각각이라
 * 「체크를 풀면 없음인가 조건 없음인가」가 칸마다 달랐다.
 *
 * ## 뒷면도 똑같이 있다
 * 사진은 한 면만 찍지만 알약은 양면이다. 뒷면 칸은 전부 「전체」로 시작하고, 사용자가 알약을
 * 뒤집어 본 뒤 채운다. 서버는 앞뒤 순서와 무관하게 정방향·뒤집힘을 모두 맞춰 본다.
 *
 * @param onReadingRevert 각인을 모델값으로 되돌린다. 되돌릴 값이 없으면 null
 */
@Composable
internal fun PillFaceCard(
    faces: FaceInputs,
    reading: FaceReading?,
    onChange: (FaceInputs) -> Unit,
    onEditImprint: (FaceSide) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FaceColumn(
            title = "앞면 (사진 면)",
            side = FaceSide.Front,
            input = faces.front,
            // 모델이 읽은 것은 **사진에 찍힌 면**뿐이다 — 뒷면에는 되돌릴 값이 없다.
            modelImprint = reading?.imprint,
            onInputChange = { onChange(faces.copy(front = it)) },
            onEditImprint = onEditImprint
        )
        FaceColumn(
            title = "뒷면",
            side = FaceSide.Back,
            input = faces.back,
            modelImprint = null,
            onInputChange = { onChange(faces.copy(back = it)) },
            onEditImprint = onEditImprint
        )
    }
}

@Composable
private fun RowScope.FaceColumn(
    title: String,
    side: FaceSide,
    input: FaceInput,
    modelImprint: String?,
    onInputChange: (FaceInput) -> Unit,
    onEditImprint: (FaceSide) -> Unit
) {
    val colors = NmTheme.semanticColors
    var menu by remember { mutableStateOf<FaceMenu?>(null) }

    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(NmColor.Neutral.C50)
            .border(1.dp, NmColor.Neutral.C200, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = title, style = FaceTitle, color = colors.textSecondary)

        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConditionLabel("각인")
                Box(modifier = Modifier.weight(1f))
                ConditionDropdown(
                    value = input.imprintLabel(),
                    certain = input.imprint != null,
                    onClick = { menu = FaceMenu.Imprint },
                    modifier = Modifier.width(74.dp)
                )
            }
            if (menu == FaceMenu.Imprint) {
                ImprintMenu(
                    input = input,
                    onDismiss = { menu = null },
                    onChange = { next ->
                        onInputChange(next)
                        // 「입력」을 고르면 곧바로 칠 수 있게 줄을 띄운다.
                        if (next.imprint?.isNotEmpty() == true) onEditImprint(side)
                    }
                )
            }
        }

        // 각인을 「입력」으로 둔 면에만 칸이 뜬다. 「전체」·「없음」에는 적을 것이 없다.
        if (input.imprint != null && input.imprint.isNotEmpty()) {
            ImprintField(
                value = input.imprint,
                revertTo = modelImprint?.takeIf { it != input.imprint },
                onClick = { onEditImprint(side) },
                onRevert = {
                    onInputChange(input.copy(imprint = modelImprint.orEmpty(), imprintSource = ImprintSource.MODEL))
                }
            )
        }

        // 정본은 여기 **윗선 하나만** 긋는다 — 각인과 나머지 둘을 가르는 선이지 상자가 아니다.
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NmColor.Neutral.C200))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 구분선만 **그림 칸 메뉴**다(정본 ④ · ⑩) — 「(+)형」·「(−)형」은 말보다 그림이
            // 빠르다. 사용자는 손에 든 알약을 보고 고르지 이름으로 떠올리지 않는다.
            Box(modifier = Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ConditionLabel("구분선")
                    ConditionDropdown(
                        value = input.dividingLine.conditionLabel(),
                        certain = input.dividingLine != null,
                        onClick = { menu = FaceMenu.Line },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (menu == FaceMenu.Line) {
                    AttributeMenuPopup(onDismiss = { menu = null }) {
                        DividingLineMenu(
                            selected = input.dividingLine,
                            onSelect = { onInputChange(input.copy(dividingLine = it)) },
                            onClear = { onInputChange(input.copy(dividingLine = null)) }
                        )
                    }
                }
            }
            Box(modifier = Modifier.width(1.dp).height(44.dp).background(NmColor.Neutral.C200))
            ConditionCell(
                label = "마크",
                value = input.hasMark.markConditionLabel(),
                certain = input.hasMark != null,
                open = menu == FaceMenu.Mark,
                options = MarkOptions,
                selected = input.hasMark,
                onOpen = { menu = FaceMenu.Mark },
                onDismiss = { menu = null },
                onSelect = { onInputChange(input.copy(hasMark = it)) }
            )
        }
    }
}

@Composable
private fun <T> RowScope.ConditionCell(
    label: String,
    value: String,
    certain: Boolean,
    open: Boolean,
    options: List<Pair<T, String>>,
    selected: T?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (T?) -> Unit
) {
    Box(modifier = Modifier.weight(1f)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ConditionLabel(label)
            ConditionDropdown(
                value = value,
                certain = certain,
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (open) {
            ConditionMenu(options = options, selected = selected, onDismiss = onDismiss, onSelect = onSelect)
        }
    }
}

/**
 * 각인 메뉴 — 전체 · 없음 · 입력.
 *
 * ⚠️ **「없음」과 「입력」은 겉보기엔 둘 다 빈 칸이지만 뜻이 정반대다.** 없음은 각인이 없는
 * 알약만 남기는 하드 조건이고, 입력은 이제부터 적겠다는 것이다. 그래서 「입력」을 고르면
 * 칸이 빈 문자열이 아니라 **되돌릴 모델값 또는 공백 한 칸**으로 시작한다 — 아무것도 안 적고
 * 떠나도 「각인 없는 알약」이 조건으로 걸리지 않도록.
 */
@Composable
private fun ImprintMenu(input: FaceInput, onDismiss: () -> Unit, onChange: (FaceInput) -> Unit) {
    ConditionMenu(
        options = ImprintOptions,
        selected = input.imprintChoice(),
        onDismiss = onDismiss,
        onSelect = { choice ->
            onChange(
                when (choice) {
                    null -> input.copy(imprint = null, imprintSource = null)
                    ImprintChoice.Blank -> input.typed("")
                    ImprintChoice.Typed -> input.typed(input.imprint.orEmpty().ifEmpty { " " })
                }
            )
        }
    )
}

/**
 * 각인 칸 — 정본 ⑪ 는 높이 34 · `$warning-50` 바탕 · 글자 15/800 이고 오른쪽에 되돌리기다.
 *
 * **여기서 치지 않는다.** 누르면 키보드 위 화면 폭 줄이 올라온다([PillImprintInputRow]) —
 * 이 칸은 약 8자라 좁다(정본 ⑦).
 */
@Composable
private fun ImprintField(value: String, revertTo: String?, onClick: () -> Unit, onRevert: () -> Unit) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(shape)
            .background(NmColor.Warning.C50)
            .border(1.dp, NmColor.Warning.C300, shape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            style = FieldValue,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 되돌릴 모델값이 있을 때만 보인다. 없으면 자리도 비운다 — 누를 수 없는 단추를
        // 흐리게 남겨 두면 「왜 안 눌리지」가 된다.
        if (revertTo != null) {
            Box(
                modifier = Modifier.size(28.dp).clip(shape).clickable(onClick = onRevert),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_rotate_ccw),
                    contentDescription = "모델이 읽은 각인으로 되돌리기",
                    tint = NmColor.Warning.C700,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

private enum class FaceMenu { Imprint, Line, Mark }

/** 각인 드롭다운이 고를 수 있는 것. 「전체」는 null 이라 여기 없다. */
private enum class ImprintChoice { Blank, Typed }

private val ImprintOptions = listOf(ImprintChoice.Blank to "없음", ImprintChoice.Typed to "입력")

private val MarkOptions = listOf(false to "없음", true to "있음")

private fun FaceInput.imprintChoice(): ImprintChoice? = when {
    imprint == null -> null
    imprint.isEmpty() -> ImprintChoice.Blank
    else -> ImprintChoice.Typed
}

private fun FaceInput.imprintLabel(): String = when (imprintChoice()) {
    null -> "전체"
    ImprintChoice.Blank -> "없음"
    ImprintChoice.Typed -> "입력"
}

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val FaceTitle = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val FieldValue = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
