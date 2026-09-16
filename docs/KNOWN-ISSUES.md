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

### 곁가지 — 워치만 `USE_EXACT_ALARM` 을 쓴다

폰은 `SCHEDULE_EXACT_ALARM`(사용자 승인) 이고, **워치만** `USE_EXACT_ALARM`(자동 허용) 이다.
손목에서 「설정으로 가서 권한을 켜라」를 시킬 수 없어서 갈랐다(`wear/…/AndroidManifest.xml` 주석).

`applicationId` 가 같아 **앱 단위 선언 하나**로 묶인다. 워치 AAB 를 올리는 순간 Play 가
「정확한 알람 권한을 사용하는지 알려 주셔야 합니다」로 출시를 막으므로, 앱 콘텐츠 →
「정확한 알람」 에서 **「알람 시계」** 로 선언해 둔다(0.2.1 에서 처음 걸렸다).

⚠️ 선택지가 「알람 시계」와 「Calendar」 뿐이고, 안내문은 **핵심 기능이 둘 중 하나가 아니면
모든 트랙에서 권한을 빼라**고 한다. 널스메이트는 앱 전체로 보면 알약 식별이 핵심이라
심사에서 다툴 여지가 있다 — 되돌아오면 워치를 `SCHEDULE_EXACT_ALARM` 으로 바꾸는 쪽이
대안이다(그 경우 손목에서 설정 유도 문제가 되돌아온다).

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

## ⑦ Play 설치본에서 소셜 로그인 셋이 한꺼번에 깨진다 — 서명 지문을 잘못 읽어서

**무엇이.** `0.2.1` Play 알파 설치본에서 **카카오·구글·애플 로그인이 모두** 실패했다.
증상이 공급자마다 달라 각각 다른 문제처럼 보였다.

| 공급자 | 보이는 것 | 실제 |
|---|---|---|
| 카카오 | 계정 선택까지 가고 「로그인하지 못했어요」 | 카카오 인증은 통과, Firebase 교환에서 실패 |
| 구글 | 계정 선택 후 **아무 일도 안 일어남** | 실패가 `AuthError.Cancelled` 로 뭉개져 문구 없이 종료 |
| 애플 | 누르자마자 실패, **웹 리다이렉트조차 없음** | Firebase 가 웹 플로우를 시작조차 못 함 |

**원인.** Play 가 배포하는 APK 의 서명 SHA-1 이 Firebase 에 등록돼 있지 않았다. Firebase Auth 는
요청에 `X-Android-Cert`(서명 SHA-1)를 실어 보내고 등록값과 대조하므로, 틀리면 **공급자와 무관하게**
Auth 진입 자체가 막힌다. 그래서 셋이 동시에, 서로 다른 모습으로 깨졌다.

**왜 못 찾았나 — 이게 핵심이다.** Play Console 「앱 서명」 화면에는 지문 칸이 **둘** 있다.

    기존 키                    양자 내성 암호화 키
    [SHA-256] [SHA-1]         [SHA-256] [SHA-1]     ← 생김새가 같다

오른쪽(양자 내성 암호화 키)의 SHA-1 `15:DF:9D:DC:…:1F:89` 를 앱 서명 키로 착각해 Firebase 에
등록해 두었다. 그래서 **콘솔만 보면 「Play 앱 서명 키가 정상 등록됨」으로 보인다.** 실제 배포본의
서명은 `79:74:B2:2C:…:FD:B0` 인데 어디에도 없었다.

이 가짜 일치 때문에 App Check 강제, API 키 제한, OAuth 클라이언트 삭제, keyHash 미등록을 차례로
의심하다 전부 헛짚었다. 콘솔 화면은 모든 층위에서 정상으로 보였다.

**어떻게 확인하나 — 콘솔 말고 바이너리에서 읽는다.**
Play Console → App Bundle 탐색기 → 해당 버전 → 다운로드 → **「서명됨, 범용 APK」** 를 받아:

```bash
apksigner verify --print-certs 4.apk | grep "SHA-1 digest"
# Signer #1 certificate SHA-1 digest: 7974b22c71d6ebb6792c45284e77eeaa4310fdb0
```

이 값이 **유일한 정본**이다. 카카오용 Base64 keyHash 는 여기서 만든다:

```bash
printf '7974b22c…' | xxd -r -p | base64   # eXSyLHHW67Z5LEUoTnfuqkMQ/bA=
```

**등록해야 하는 곳 셋.** 하나라도 빠지면 Play 설치본에서 그 공급자가 죽는다.

| 대상 | 값 | 무엇이 걸려 있나 |
|---|---|---|
| Firebase → SHA 인증서 지문 (**SHA-1**) | `79:74:B2:2C:71:D6:EB:B6:79:2C:45:28:4E:77:EE:AA:43:10:FD:B0` | 소셜 로그인 셋 |
| Firebase → SHA 인증서 지문 (**SHA-256**) | `5C:1A:96:D3:A2:97:F8:D6:24:DC:6C:E2:9B:F3:04:5C:4A:DB:D8:17:94:5F:FF:16:B8:D0:71:6B:B8:22:58:B9` | **App Check(Play Integrity)** |
| 카카오 콘솔 → 플랫폼 → Android → 키 해시 | `eXSyLHHW67Z5LEUoTnfuqkMQ/bA=` | 카카오 로그인 |
| GCP OAuth 클라이언트 | Firebase 에 SHA-1 을 넣으면 자동 생성된다 | — |

⚠️ **SHA-1 과 SHA-256 을 둘 다 넣어야 한다.** 로그인은 SHA-1 로 검증하고 **App Check 의
Play Integrity 는 SHA-256 으로** 검증한다. SHA-1 만 넣으면 로그인은 살아나는데 알약 식별이
`401 APP_CHECK_FAILED` 로 계속 죽는다 — 0.2.1 에서 실제로 이 순서로 겪었다.
App Check 화면의 「확인된 요청 54% / 미확인 46%」가 그 증상이었다(사이드로드는 통과,
Play 설치본만 실패). 두 값 모두 배포 APK 에서 한 번에 읽을 수 있다:

```bash
apksigner verify --print-certs 4.apk | grep -E "SHA-1 digest|SHA-256 digest"
```

⚠️ 카카오 키 해시는 **끝의 `=` 까지** 넣는다. 빠뜨리면 조용히 안 맞는다(실제로 겪었다).

⚠️ **Firebase 에 지문만 넣고 끝내면 안 된다.** 앱 안의 OAuth 클라이언트 목록은
`google-services.json` 에 박혀 있다. **새로 내려받아 `app/src/release/` 를 교체하고 재빌드**해야
반영된다. 이미 Play 에 올라간 빌드는 지문을 추가해도 계속 실패한다.

**사이드로드로는 절대 못 잡는다.** 로컬 `assembleRelease` 는 **업로드 키**로 서명되고 그 지문은
정상 등록돼 있어서, 같은 코드가 사이드로드에서는 셋 다 멀쩡히 로그인된다(에뮬레이터에서 확인).
`docs/RELEASE.md` 의 「사이드로드라서 고장 난 것처럼 보이는 것들」과 **반대 방향**의 함정이다 —
이쪽은 사이드로드에서 **멀쩡해 보이는데 Play 에서만 깨진다.**

## ⑧ ONNX Runtime 을 올릴 때는 SME 명령어가 들어왔는지 본다

**무엇이.** `1.29.0` 에서 알약 식별이 **SIGILL 로 즉사**했다. 「이 사진 사용」 직후 프로세스가
통째로 사라진다. 원인은 SME(Scalable Matrix Extension) 명령어를 **가드 없이** 실행하는 것이다.

    a8a7b4: 04bf5820    rdsvl x0, #0x1      ← 여기서 죽는다
    a8a7b8: d65f03c0    ret

호출부에 `HWCAP2_SME` 확인이 없다 — 함수에 들어가는 순간 실행한다. Exynos 2400
(Cortex-X4/A720/A520)은 SME 를 지원하지 않아 만나는 즉시 `ILL_ILLOPC` 다.

**올리는 방향은 답이 아니다.** 버전별 바이너리를 직접 받아 확인했다:

| 버전 | `rdsvl` | sme2 커널 | 가드 |
|---|---|---|---|
| **1.22.0** | **0** | **0** | — (코드 자체가 없다) |
| 1.23.2 | 38 | 7 | 없음 |
| 1.24.3 | 1 | 0 | 없음 |
| 1.25.1 · 1.26.0 · 1.29.0 | 1 | 4 | 없음 |

1.26.0 의 호출부가 1.29.0 과 글자 그대로 같다. 그래서 `1.22.0` 으로 내렸다.

**올릴 때 이렇게 본다.** aar 을 받아 arm64 `.so` 를 열어 보면 된다:

```bash
unzip -o -q onnxruntime-android-<버전>.aar "jni/arm64-v8a/libonnxruntime.so" -d /tmp/ort
llvm-objdump -d /tmp/ort/jni/arm64-v8a/libonnxruntime.so | grep -cE "\brdsvl\b"
```

0 이 아니면 호출부에 가드가 붙었는지까지 확인한다. `bl <rdsvl 주소>` 앞에 조건 분기가 없으면
그 버전은 쓸 수 없다.

**에뮬레이터로 검증할 수 있다 — 이것만은.** 에뮬레이터 CPU 도 SME 를 지원하지 않아 실기기와
같은 조건이다. 성공하면 이렇게 찍힌다:

    NM394: 검출 0개 | 전처리 106ms 추론 646ms 후처리 0ms

⚠️ **반대로, 다른 알약 식별 문제를 에뮬레이터로 판단하면 안 된다.** 1.29.0 에서는 debug·release·
split·universal 을 가리지 않고 전부 같은 주소에서 죽어서, 어떤 비교를 해도 변별력이 없었다.
실제로 그걸 모르고 「split APK 문제」·「Play 배포 문제」를 한참 뒤졌다.

**업스트림 버그다.** 1.22.0 은 회피이지 해결이 아니다. 바이너리에
`Failed to initialize PyTorch cpuinfo library. May cause CPU EP performance degradation due to
undetected CPU features.` 문자열이 있는 것으로 보아, CPU 기능 감지가 실패하면 SME 미지원
기기에서도 이 경로를 타는 구조로 보인다.

## ⑨ `firebase-analytics` 는 광고 ID 권한을 몰래 끼워 넣는다

**무엇이.** `0.2.2` 를 알파에 올렸더니 게시 개요가 **검토 전송을 막았다.**

> 광고 ID 선언이 불완전함 — Android 13 이상을 타겟팅하는 모든 개발자는 앱에서 광고 ID를
> 사용하는지 여부를 Google Play에 알려야 합니다.

`firebase-analytics` 가 `com.google.android.gms.permission.AD_ID` 를 자동으로 병합한다.
**우리 매니페스트에는 없다** — 그래서 코드를 아무리 봐도 안 보인다. 병합 결과를 봐야 나온다:

```bash
grep -oE 'android:name="[^"]*permission[^"]*"' \
  app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml \
  | sort -u
```

**선언이 아니라 제거를 골랐다.** 개인정보처리방침 3항이 「광고 식별자(IDFA) 및 광고·추적 목적의
데이터」를 수집하지 않는다고 못박았고, 데이터 보안 신고에서도 「기기 또는 기타 ID」를 선택하지
않았다. 권한만 남기면 공개한 약속과 실제가 어긋난다.

```xml
<manifest xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_AD_ID" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_ATTRIBUTION" tools:node="remove" />
```

Privacy Sandbox 쪽 둘은 Play 의 선언 요구를 촉발하지 않지만, 남기면 **스토어에 광고 관련
권한이 보인다** — 광고를 쓰지 않는다고 공개한 앱에서 설명할 수 없다.

**확인.** Analytics 는 광고 ID 없이도 동작한다(앱 인스턴스 ID 로 센다).

```bash
aapt2 dump badging app-release.apk | grep -cE "uses-permission.*(AD_ID|ADSERVICES)"   # → 0
```

⚠️ **광고를 붙이게 되면** 이 줄들을 지우고 방침 3항·데이터 보안 신고·Play 광고 ID 선언을
**함께** 고쳐야 한다. 셋 중 하나만 바꾸면 어긋난 채로 남는다.
