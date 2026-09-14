# 알려진 문제 (Android)

> 실기기 검증에서 드러났지만 **그 자리에서 안 고친 것**을 모아 둔다. 범위 밖이거나, 플랫폼
> 제약이라 코드로 못 고치거나, 제품 결정이 먼저 필요한 것들이다.
>
> 각 항목은 `무엇이 · 어떻게 재현하나 · 왜 그런가 · 어떻게 할까` 순서로 적는다.
> 고치면 이 파일에서 지운다.

## ① 오프라인에서 앱 전체가 막힌다 — 타이머까지 못 본다

**무엇이.** 네트워크가 없으면 폰 앱이 「연결이 원활하지 않아요 / 잠시 후 다시 시도해 주세요」
전체 화면으로 덮인다. **탭바조차 없어** 진행 중인 타이머에 접근할 수 없다.

**재현.** 폰을 완전히 격리하고(비행기 모드 + 블루투스 끄기) 앱을 새로 실행 → 타이머 탭 진입 불가.
(검증 시트 `T-71`)

**왜 그런가.** `AppSessionViewModel` 이 회원 조회 실패를 `initialLoadFailed` 로 올리고
`AppEntry.Unavailable` 을 내면, `NmNavHost` 가 **셸 전체**를 `ServiceUnavailable` 로 덮는다.
그 화면의 주석은 500·503 같은 **서버 장애**를 염두에 뒀는데, 오프라인도 같은 실패로 취급된다.

**왜 문제인가.** 처치 타이머는 서버에 보관하지 않는 **오프라인 기능**이다(spec §개요).
신호가 약한 병동에서 앱을 열면 돌고 있는 타이머를 확인할 방법이 없다 — 이 기능이 가장 필요한
상황에서 막힌다. 워치는 정상 동작하므로 폰만의 문제다.

**어떻게 할까.** 마지막으로 받은 회원 정보를 캐시해 두고, 못 받으면 캐시로 진행한다.
캐시조차 없을 때(최초 로그인 직후 오프라인)만 막으면 된다. 인증 셸(NM-407) 영역이라
NM-445 브랜치에서 건드리지 않았다.

## ② 5초 미만 타이머는 정확히 울릴 수 없다

**무엇이.** 1초짜리 프리셋이 약 5초 뒤에 울린다.

**재현.** 1초 프리셋 시작 → 실측 **5.032초**. (검증 시트 `T-59`)

**왜 그런가.** 안드로이드가 막는다. `dumpsys alarm` 에 `min_futurity=+5s0ms` 로 찍히고,
`AlarmManagerService` 가 모든 알람을 「지금 + 5초」보다 앞당겨 걸지 못하게 한다.
`window=0`·`exactAllowReason=permission` 인 정확 알람도 예외가 아니다. **앱이 고칠 수 없다.**

**어떻게 할까.** 제품 결정이다. 프리셋 최소 시간을 두는 쪽을 권한다 — 간호 업무에 5초 미만
타이머는 쓸 일이 없고, 앱이 살아 있을 때만 정확한 방식으로 때우면 **반쯤 동작하는 알람**이
된다(spec 이 가장 위험하다고 본 경우다). 지금은 그대로 두고 여기 적어만 둔다.

## ③ 폰·워치가 `specialUse` 포그라운드 서비스를 쓴다 — Play 심사 소명 대상

**무엇이.** **두 서비스**가 `android:foregroundServiceType="specialUse"` 다. `applicationId`
가 같아 **같은 Play 등록에 묶이므로** 제출 때 둘 다 소명해야 한다.

| 서비스 | `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` |
|---|---|
| `app/…/timer/alarm/TimerAlarmService` | 처치 타이머 만료 알림 — [완료] 를 누를 때까지 알람음·진동을 이어 준다 |
| `wear/…/wear/alarm/WearAlarmService` | 처치 타이머 만료 알림 — [완료] 를 누를 때까지 손목에 진동을 이어 준다 |

정해진 유형 중 맞는 것이 없다. `mediaPlayback` 은 사용자가 고른 콘텐츠 재생용이고 이건
알람음이며, Android 는 알람용 포그라운드 유형을 두지 않았다.

**왜 적어 두나.** PR #19 에서 이 선택을 **명시적으로 피했다**:

> 이기려면 포그라운드 서비스로 울림 주체를 옮겨야 하는데 Android 14+ 의 FGS 타입
> (`specialUse` — Play 심사 소명 대상)이라 MVP 에서 채택하지 않았다.

이번에 **결정을 뒤집었다.** 워치가 만료를 손목에 알리려면 진동을 [완료] 까지 이어 줘야 하고,
그러려면 프로세스가 살아 있어야 한다. 알람 화면도 그 서비스가 포그라운드 자격으로 띄운다 —
`fullScreenIntent` 경로는 `BAL_BLOCK` 으로 막혔다.

**폰도 뒤따랐다**(`519264e`). 알림 채널의 소리·진동은 링어 모드에 걸려 무음에서 통째로
막히는데 spec 은 "기기 무음과 무관하게"를 요구한다. 채널로는 못 지켜서 앱이 알람 스트림으로
직접 내고, 그러려면 역시 프로세스가 살아 있어야 한다.

`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 에 사유를 적어 뒀지만, **뒤집혔다는 사실 자체를 모르면**
릴리스 담당이 소명 없이 제출해 심사에서 막힌다.

**어떻게 할까.** 제출 전에 Play Console 의 「특별한 용도」 사유란에 위 표의 두 문구를 **둘 다**
적는다. 하나만 적으면 나머지가 소명 없는 `specialUse` 로 남아 심사에서 되돌아온다.

## ④ 워치가 둘 이상이면 서로 구분되지 않는다

**무엇이.** 복제본의 `origin` 이 `phone`·`watch` 두 값뿐이라, 워치가 두 대면 둘 다 `watch` 다.
판이 같을 때의 승부와 자리표 확인이 어긋날 수 있다.

**왜 그런가.** 기기가 둘뿐이라는 전제로 설계했다. `TimerReplica.ackSeq` 도 상대가 하나일 때만
성립한다 — 셋 이상이면 상대별로 하나씩 있어야 한다.

**어떻게 할까.** 지금 제품 범위 밖이다. 지원하게 되면 `origin` 을 기기 고유값으로 바꾸고
`ackSeq` 를 상대별 맵으로 늘린다.

## ⑤ 옛 자리표는 30일을 기다린다

**무엇이.** 확인 기반 청소(`removedSeq`) 이전에 만들어진 자리표는 판 번호가 없어 확인 규칙이
안 걸리고, 기한(30일)으로만 사라진다.

**왜 그런가.** 이관 코드를 넣지 않았다. 자리표를 세는 순간 상대가 아직 그 삭제를 못 받았을 수
있어, 섣불리 지우면 **지운 것이 되살아난다.**

**어떻게 할까.** 그대로 둔다. 개수가 적고(수십 개, 수 KB) 되살아날 위험은 없다.
새 삭제부터는 왕복 한 번에 정리된다.

## ⑥ 워치 만료 화면은 시스템이 그린다 — 우리 액티비티가 아니다

**무엇이.** 워치에서 만료를 덮는 화면은 정본 「W3 만료」(종 아이콘·긴 [완료] 버튼)가 아니라
**Wear SysUI 가 그리는 알림 팝업**이다. [WearAlarmActivity] 는 알림을 눌렀을 때만 열린다.

**왜 그런가.** 자체 액티비티를 백그라운드에서 띄울 수 없다. 아래를 전부 실기기에서 시도했고
전부 `BAL_BLOCK` 이었다(2026-09-11):

| 시도 | 확인된 것 | 결과 |
|---|---|---|
| `PendingIntent` 생성자 옵트인 | `balAllowedByPiCreator` 가 `BSP.NONE` → `BSP.ALLOW_BAL` | ✗ |
| 포그라운드 서비스 대신 **알람 리시버**에서 호출 | `callingUidProcState` 가 `FOREGROUND_SERVICE` → `RECEIVER` | ✗ |
| `setExactAndAllowWhileIdle` → `setAlarmClock` | `RTC_WAKEUP flags=0x3` 으로 폰과 동일 | ✗ |
| 알림의 `fullScreenIntent` | 시스템이 실제로 시도함 | ✗ |
| 화면 켜짐(`ACTION_SCREEN_ON`·폴링)에 재시도 | 손목 들기를 놓치거나, 잡아도 FSI 미발사 | ✗ |
| `SYSTEM_ALERT_WINDOW` | 워치에 **권한 부여 화면이 없다** | 불가 |

가끔 뜨던 것은 `BAL_ALLOW_GRACE_PERIOD` — **직전에 앱 화면이 떠 있던 유예**를 탄 것이라
실사용에서는 재현되지 않는다. 「된다」고 판단하면 안 되는 종류다.

**삼성 기본 타이머는 왜 되나.** 가질 수 없는 권한을 갖고 있다:

    com.samsung.android.watch.timer  (SYSTEM, PRIVILEGED)
      android.permission.START_ACTIVITIES_FROM_BACKGROUND: granted=true

`signature|privileged` 라 Play 로 배포하는 앱은 받을 수 없다.

**대신 하는 것.** Wear SysUI 의 전체화면 팝업에 맡긴다:

    DataForFullPopup launch → LaunchResult: 0
    START ... sysui/...wnotification.detail2.activity.DetailActivity2
           (BAL_ALLOW_ALLOWLISTED_COMPONENT) result code=0

시스템이 자기 권한으로 띄우므로 **화면이 꺼져 있든 켜져 있든 워치를 쓰는 중이든** 뜬다.
[완료] 도 그 팝업에서 바로 눌린다.

⚠️ **단, 손목에서 벗으면 안 뜬다.** Wear 는 `off-body` 를 보고 알림 자체를 거른다:

    [AlertingPipeline]           Not alerting: Device is off-body
    [WearSdkAlertingProcessor]   Not alerting: Device is off-body, item id: …|app.nursemate|444001
    [WNotiDataConverter]         Should drop popup (Reason : No Alert : Silent Notification)

알림이 「안 울린 것」이 되면 팝업도 함께 버려진다. 진동만 남는데, 그건 우리가 `Vibrator` 를
직접 몰기 때문이다([WearAlarmService]). 차고 있으면 정상이므로 결함이 아니라 **책상에
올려 두고 시험할 때 헷갈리는 지점**이다 — 실기기 확인에서 네 번 연속 이걸로 헤맸다.

⚠️ **`setFullScreenIntent` 를 붙이면 이 경로를 안 탄다.** 붙어 있으면 Wear 가 그것을 띄우는
것으로 알림 표시를 대신하는데, 그 시작이 막혀 아무것도 안 나온다. 붙였을 때 우리 앱이 이
경로를 탄 횟수는 **0건**, 뺐더니 정상으로 갈렸다. 채널 진동과 `ongoing` 해제도 같은 전제다 —
셋 중 하나만 어긋나도 손목에는 진동만 남는다.

**남는 제약.** 만료 순간 화면이 **스스로 켜지지는 않는다.** 진동으로 알아차리고 손목을 들면
그때 팝업이 떠 있다. [완료] 까지 두 동작이다.

**정본과의 차이.** 팝업 모양은 시스템이 정하므로 「W3 만료」의 종 아이콘·긴 버튼을 그대로
낼 수 없다. `docs/SPEC-FEEDBACK.md` 에 올린다.
