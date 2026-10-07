package app.nursemate.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 권한 시트 문구 — NM-530.
 *
 * 이 버그의 본질은 「세 단계가 글자 하나 다르지 않은 같은 시트를 그린다」였다. 그러니 문구
 * 하나하나를 박아 두기보다 **단계끼리 겹치지 않는다**를 지키는 편이 회귀를 잡는다.
 */
class PermissionSheetCopyTest {

    private val steps = PermissionStep.entries

    @Test
    fun `단계마다 제목이 다르다`() {
        for (denied in listOf(false, true)) {
            val titles = steps.map { it.title(denied) }
            assertEquals("단계 수만큼 서로 다른 제목이어야 한다", steps.size, titles.toSet().size)
        }
    }

    @Test
    fun `단계마다 본문이 다르다`() {
        for (denied in listOf(false, true)) {
            val bodies = steps.map { it.body(denied) }
            assertEquals("단계 수만큼 서로 다른 본문이어야 한다", steps.size, bodies.toSet().size)
        }
    }

    @Test
    fun `안내와 거부가 같은 문구를 쓰지 않는다`() {
        for (step in steps) {
            assertNotEquals("$step 안내·거부 제목이 같다", step.title(denied = false), step.title(denied = true))
            assertNotEquals("$step 안내·거부 본문이 같다", step.body(denied = false), step.body(denied = true))
        }
    }

    /**
     * 제목은 **OS 설정 화면의 항목 이름 그대로**여야 한다 — 설정으로 나간 사용자가 찾을 글자와
     * 같아야 하기 때문이다. S24(One UI · Android 16)에서 `uiautomator` 로 읽은 표기다.
     */
    @Test
    fun `설정으로 보내는 단계는 제목에 OS 항목 이름을 쓴다`() {
        for (denied in listOf(false, true)) {
            assertTrue(
                "정확 알람 제목에 「알람 및 리마인더」가 있어야 한다",
                PermissionStep.EXACT_ALARM.title(denied).contains("알람 및 리마인더")
            )
            assertTrue(
                "전체화면 제목에 「전체 화면 알림」이 있어야 한다",
                PermissionStep.FULL_SCREEN.title(denied).contains("전체 화면 알림")
            )
        }
    }

    /**
     * 알림만 앱 안에서 팝업으로 받고 나머지 둘은 설정 화면으로 나간다. 버튼이 그 차이를 말해야
     * 사용자가 화면이 바뀌는 것에 놀라지 않는다.
     */
    @Test
    fun `버튼이 팝업과 설정 이동을 구분한다`() {
        assertEquals("알림 켜기", PermissionStep.NOTIFICATION.primaryLabel())
        assertEquals("설정 열기", PermissionStep.EXACT_ALARM.primaryLabel())
        assertEquals("설정 열기", PermissionStep.FULL_SCREEN.primaryLabel())
    }

    /**
     * 전체화면 권한이 없어도 **알람이 사라지지는 않는다** — 헤드업 알림으로 낮춰 표시된다
     * (`AndroidManifest.xml` 주석). 「안 울려요」로 쓰면 사실과 다르다.
     */
    @Test
    fun `전체화면 거부 문구가 알람이 안 울린다고 말하지 않는다`() {
        val body = PermissionStep.FULL_SCREEN.body(denied = true)
        assertTrue("잠금화면 이야기여야 한다", body.contains("잠금화면"))
        assertTrue("「안 울」이 들어가면 틀린 설명이다", !body.contains("안 울"))
    }

    /** 본문 첫 줄은 **왜 필요한지**다. 어디서 켜는지는 버튼과 열린 설정 화면이 말한다. */
    @Test
    fun `본문이 설정에서 앱을 찾으라고 시키지 않는다`() {
        // 인텐트에 `package:` 가 붙어 앱 전용 페이지로 바로 떨어진다(토글 하나뿐) —
        // 목록에서 널스메이트를 찾는 화면이 아니다.
        for (step in steps) {
            for (denied in listOf(false, true)) {
                assertTrue(
                    "$step(denied=$denied) 본문이 없는 절차를 시킨다",
                    !step.body(denied).contains("찾아")
                )
            }
        }
    }
}
