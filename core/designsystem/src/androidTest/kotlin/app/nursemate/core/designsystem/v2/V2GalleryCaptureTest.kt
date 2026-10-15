package app.nursemate.core.designsystem.v2

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.designsystem.NurseMateTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * V2.1.0 컴포넌트를 정본 `Foundation / Components · V2.1.0` 의 구역 순서대로 그려 PNG 로 남긴다.
 *
 * **단정이 없다 — 눈으로 대조하는 용도다.** 정본을 Pencil 에서 같은 구역으로 찍어 나란히 본다(NM-608).
 * 결과는 `/sdcard/Android/data/app.nursemate.core.designsystem.test/files/v2gallery/` 에 쌓인다.
 */
class V2GalleryCaptureTest {

    @get:Rule val rule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            NurseMateTheme {
                // 여백은 카드 그림자(y14 + 흐림34)가 다 들어가도록 56 이다 — 잘리면 그림자를 대조할 수 없다.
                Box(Modifier.testTag(TAG).background(Color.White).padding(56.dp).wrapContentSize()) { content() }
            }
        }
        save(rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap(), name)
    }

    private fun save(bitmap: Bitmap, name: String) {
        val root = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val dir = File(root, "v2gallery").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun surfaces() = capture("01_surfaces") {
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            NmV2Surface(NmV2SurfaceStyle.Card, Modifier.size(220.dp, 110.dp)) {}
            NmV2Surface(NmV2SurfaceStyle.ListCard, Modifier.size(220.dp, 84.dp)) {}
            NmV2Surface(NmV2SurfaceStyle.Floating, Modifier.size(260.dp, 64.dp)) {}
            NmV2Surface(NmV2SurfaceStyle.CardCaution, Modifier.size(168.dp, 110.dp)) {}
            NmV2Surface(NmV2SurfaceStyle.ListCardSuccess, Modifier.size(168.dp, 84.dp)) {}
            NmV2Surface(NmV2SurfaceStyle.CardSelected, Modifier.size(168.dp, 110.dp)) {}
        }
    }

    @Test fun buttons() = capture("02_buttons") {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NmV2ButtonPrimary("버튼", {})
                NmV2ButtonSecondary("버튼", {})
                NmV2ButtonCaution("완료", {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NmV2ButtonDestructive("삭제", {})
                NmV2ButtonText("텍스트 버튼", {})
                NmV2ButtonFloating(NmSymbols.Add, "추가", {})
            }
            // Button States — 기본 · 누름 · 비활성. 누름은 같은 모양을 정적으로 그린다.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NmV2ButtonSecondary("버튼", {})
                StaticButton(NmV2ButtonKind.Secondary, pressed = true)
                NmV2ButtonSecondary("버튼", {}, enabled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NmV2ButtonPrimary("버튼", {})
                StaticButton(NmV2ButtonKind.Primary, pressed = true)
                NmV2ButtonPrimary("버튼", {}, enabled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                NmV2IconButton(NmSymbols.Close, "닫기", {})
                Box(Modifier.background(Color(0xFF8A9BB0)).padding(12.dp)) {
                    NmV2IconButtonGlass(NmSymbols.OpenInFull, "크게 보기", {})
                }
            }
        }
    }

    @Test fun chipsAndFields() = capture("03_chips_fields") {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NmV2DropdownChip("전체", {})
                NmV2DropdownChip("입력", {}, confirmed = true)
                NmV2Chip("표시용")
                NmV2Tag("처치")
            }
            NmV2TextField("", {}, Modifier.width(240.dp), label = "라벨", placeholder = "입력하세요")
            NmV2TextField("AST", {}, Modifier.width(240.dp), label = "라벨", isError = true)
            NmV2TextField("AST", {}, Modifier.width(240.dp), label = "라벨", enabled = false)
        }
    }

    @Test fun cardsAndRows() = capture("04_cards_rows") {
        Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            NmV2Card(Modifier.width(320.dp)) {
                NmV2CardTitle("카드 제목")
                NmV2CardBody("카드 본문 내용이 들어갑니다.")
                NmV2ButtonRow {
                    NmV2ButtonSecondary("취소", {}, Modifier.weight(1f))
                    NmV2ButtonPrimary("확인", {}, Modifier.weight(1f))
                }
            }
            NmV2ListRow("항목 제목", {}, Modifier.width(350.dp), subtitle = "보조 설명", icon = NmSymbols.History)
            NmV2PresetRow("AST", "검사", "15분", {})
            NmV2Banner("안내 메시지가 여기에 표시됩니다.")
            NmV2Menu(
                listOf(
                    NmV2MenuItem("수정", NmSymbols.Edit) {},
                    NmV2MenuItem("삭제", NmSymbols.Delete, destructive = true) {}
                )
            )
        }
    }

    @Test fun bars() = capture("05_bars") {
        Column(Modifier.width(390.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            NmV2NavBar("제목", onBack = {})
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                NmV2TabBar(
                    listOf(
                        NmV2Tab("홈", NmSymbols.Home),
                        NmV2Tab("알약", NmSymbols.Pill),
                        NmV2Tab("타이머", NmSymbols.Timer),
                        NmV2Tab("설정", NmSymbols.Settings)
                    ),
                    selectedIndex = 0,
                    onSelect = {}
                )
            }
            NmV2BottomBar { NmV2BottomBarButton("선택하기", {}) }
            NmV2ModalPanel("이 프리셋을 삭제할까요?", "삭제", {}, dismissText = "취소", confirmKind = NmV2ButtonKind.Destructive)
            NmV2Wordmark()
        }
    }

    @Test fun icons() = capture("06_icons") {
        val all = NmSymbols::class.java.declaredFields.filter {
            it.type == Int::class.javaPrimitiveType &&
                !it.name.startsWith("$")
        }
            .onEach { it.isAccessible = true }
            .map { it.getInt(NmSymbols) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            all.chunked(10).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach {
                        Icon(painterResource(it), null, tint = Color(0xFF1E293B), modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }

    /** 실제 [NmV2Modal] 창 — 판 그림자가 창 경계에서 잘리지 않는지 본다. */
    @Test fun modalDialog() {
        rule.setContent {
            NurseMateTheme {
                NmV2Modal(
                    title = "이 프리셋을 삭제할까요?",
                    confirmText = "삭제",
                    onConfirm = {},
                    onDismissRequest = {},
                    dismissText = "취소",
                    confirmKind = NmV2ButtonKind.Destructive
                )
            }
        }
        save(rule.onNode(isDialog()).captureToImage().asAndroidBitmap(), "07_modal_dialog")
    }

    /** 누름 상태를 손가락 없이 그린다 — 볼록 면을 평면으로 바꾼 모양이 맞는지 본다. */
    @Composable
    private fun StaticButton(kind: NmV2ButtonKind, pressed: Boolean) {
        val look = kind.look(pressed = pressed, enabled = true)
        Box(Modifier.v2Raised(look, NmV2ButtonShape).padding(horizontal = 24.dp, vertical = 14.dp)) {
            androidx.compose.material3.Text("버튼", style = NmV2Type.button, color = look.content)
        }
    }

    private companion object {
        const val TAG = "gallery"
    }
}
