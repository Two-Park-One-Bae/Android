# 릴리스 가이드 (폰 + 워치)

> 릴리스 산출물은 서명된 AAB **2개**다 — `:app:bundleRelease`(폰)와 `:wear:bundleRelease`(워치).
> 워치는 `0.2.1`(NM-445)에서 합류했다. Play 에서 **Wear OS 는 별도 폼 팩터**라 트랙도 따로 판다
> (`wear-alpha`). 두 AAB 는 **같은 태그 커밋에서** 뽑고 **같은 업로드 키로** 서명한다.

## 버전 정책

버전은 `app/build.gradle.kts` `defaultConfig` 한 곳에서 관리한다.

- **versionCode** — Play 업로드마다 **+1** (단조 증가, 재사용·되돌리기 불가). Play Console 업로드용 빌드 전에 커밋으로 올린다.
- **versionName** — SemVer `MAJOR.MINOR.PATCH`.
  - 비공개 테스트 기간(NM-400): 업로드마다 PATCH +1 (`0.1.0` → `0.1.1` → …), versionCode도 함께 +1.
  - 프로덕션 첫 출시에서 `1.0.0`.
- **태그** — `v<versionName>` (예: `v0.1.0`). **`main` 머지 커밋에 붙인다** — `develop`이 아니다.
  CONVENTIONS의 `release/* → main + develop` 규칙과 완료 판정(main 머지) 기준을 따른다.
- **워치 versionCode 는 폰 `+1_000_000_000`** 이다 — 폰 `4` 면 워치 `1000000004`
  (`wear/build.gradle.kts`). versionName 은 폰과 **똑같이** 맞춘다.
  ⚠️ Play 는 이걸 「버전 코드가 이전 버전 코드보다 훨씬 높습니다」라는 **오류**로 잡는다.
  예상된 것이니 「무시하고 계속하기」를 누른다 — 다만 **한 번 누르면 되돌릴 수 없다**
  (나중에 코드를 낮출 수 없다).

## 서명

- **업로드 키**: `certificates` 레포 `android/nursemate-upload.jks` (alias `nursemate-upload`, 비밀번호도 같은 폴더). Play App Signing 사용 — 이 키는 업로드 키이며, 유출·분실 시 Play Console에서 재설정 가능.
- 주입 경로: `secrets.properties`(gitignored) > 환경변수 — `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` (`secrets.properties.sample` 참고).
- 서명 정보가 없으면 release 빌드는 **미서명**으로 산출된다 (PR CI가 여기 해당 — 빌드는 성공하되 Play 업로드 불가).

## 검출 모델 (필수 · 저장소에 없음)

알약 식별용 ONNX 모델은 **저장소에 넣지 않는다.** 119MB 바이너리라 한 번 커밋하면 이후 모든
clone 이 영구히 그 비용을 낸다(git 은 큰 파일을 되돌려 지우지 못한다).

릴리스 빌드 전에 직접 둔다:

```bash
cp <모델 보관처>/rfdetr_seg_small.onnx app/src/main/assets/
```

- 파일이 없으면 `bundleRelease`·`assembleRelease` 가 **실패한다**(`app/build.gradle.kts` 의 가드).
  조용히 모델 없는 AAB 가 나가면 사용자는 식별할 때마다 '분석 실패'만 본다.
- ⚠️ **그래서 릴리스 AAB 는 CI 가 아니라 로컬에서만 만든다.** CI 러너에는 이 파일이 없어
  릴리스 빌드가 위 가드에서 반드시 실패한다. 이것 때문에 `release.yml` 을 삭제했다(아래 참고).
- 앱은 첫 식별 때 asset 을 앱 전용 저장소로 꺼내 쓴다(`PillModelFile`). 저장소를 두 배 쓰므로,
  Play Asset Delivery 나 원격 다운로드로 옮기는 건 NM-396 ADR 에서 정한다.
- 디버그 빌드는 `getExternalFilesDir()` 에 파일이 있으면 그쪽을 먼저 쓴다(양자화 비교용).

## 로컬 릴리스 빌드

```bash
# 사전: certificates 레포가 Android 레포와 같은 부모 폴더에 클론돼 있고,
#       secrets.properties에 RELEASE_* 4개 키가 채워져 있어야 한다.
#       app/src/main/assets/rfdetr_seg_small.onnx 가 있어야 한다(위 참고).
./gradlew :app:bundleRelease
# 산출물: app/build/outputs/bundle/release/app-release.aab
# 서명 확인: jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

R8 적용(코드·리소스 축소) 상태이므로, 업로드 전 실기기 스모크 테스트는 다음으로:

```bash
./gradlew :app:assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk
```

## 릴리스 CI 는 없다 (`release.yml` 삭제, 2026-09-04)

AAB 를 만들어 주는 워크플로가 있었으나 삭제했다. 두 가지 이유다.

1. **성공할 수 없다.** 검출 모델이 저장소에 없어(위 참고) 러너에서 `bundleRelease` 가 가드에
   걸려 반드시 실패한다. NM-394 에서 가드가 들어온 뒤로 계속 빨간불이었다.
2. **성공해도 쓰면 안 되는 산출물이다.** 모델 없는 AAB 를 `app-release-aab` 라는 이름으로
   내놓으니, 사정을 모르는 사람이 그걸 Play 에 올릴 위험이 있었다. 올라가면 사용자는 알약을
   찍을 때마다 '분석 실패'만 본다.

되살리려면 모델을 러너로 가져올 방법(별도 저장소·Play Asset Delivery·원격 다운로드)이 먼저
정해져야 한다 — NM-396 ADR 사항이다.

> PR CI(`ci.yml`)는 그대로 돈다. 다만 **debug 만 빌드하므로 R8 이 걸린 release 전용 문제는
> 잡지 못한다** — 그래서 아래 절차의 스모크 테스트를 건너뛰면 안 된다.
> 이 테스트로만 잡힌 것이 지금까지 둘이다. 둘 다 **앱 시작 즉시 전 사용자 크래시**였다.
> - 카카오 SDK 가 enum 상수를 리플렉션으로 찾는데 R8 이 필드명을 바꿨다 (PR #14).
> - `glance-appwidget` 이 딸려 온 `WorkManagerInitializer` 가 `androidx.startup` 으로
>   자동 실행되다가 R8 을 거친 `WorkDatabase` 생성에 실패했다 (PR #21).
>   Glance 는 쓰지 않는데 의존성만 남아 있었다 — 지금은 빠졌다.

## ⚠️ 폰·워치가 `specialUse` 포그라운드 서비스를 쓴다

`TimerAlarmService`(폰)와 `WearAlarmService`(워치) **둘 다**다. `applicationId` 가 같아
**같은 Play 등록에 묶이므로** 제출할 때 「특별한 용도」 사유를 **둘 다** 적어야 한다 —
하나만 적으면 나머지가 소명 없이 남는다. 문구와 사정은 `docs/KNOWN-ISSUES.md` ③ 에 있다.

## Play 업로드 절차 (NM-397/400)

1. `develop` 에서 릴리스 빌드 + **실기기 스모크 테스트**를 먼저 한다.
   ```bash
   ./gradlew :app:assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk
   ```
   실행해서 크래시 없는지 logcat 까지 본다(`adb logcat -b crash -d` 가 비어 있고
   `adb shell pidof app.nursemate` 가 살아 있어야 한다). 문제가 나오면 여기서 `develop` 에
   고치고 다시.

   워치까지 올리는 릴리스면 `:wear:assembleRelease` 도 같이 본다. **두 APK 의 서명 인증서가
   같아야** Data Layer 가 붙는다 — 서명은 convention plugin 이 두 모듈에 함께 준다
   (`build-logic/.../ReleaseSigning.kt`). 산출물 이름이 `wear-release-unsigned.apk` 면
   `secrets.properties` 의 `RELEASE_*` 가 안 읽힌 것이다.
   ```bash
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   apksigner verify --print-certs wear/build/outputs/apk/release/wear-release.apk
   ```

   ### 사이드로드라서 「고장 난 것처럼」 보이는 것들

   여기서 쓰는 APK 는 **업로드 키로 서명해 `adb install` 한 것**이라, Play 로 내려간 앱과
   다르게 동작하는 자리가 있다. 아래 셋은 **정상인 빌드에서도 그렇게 보인다** — 실제로
   이것들 때문에 릴리스를 세울 뻔했다.

   | 보이는 것 | 왜 | 어떻게 |
   |---|---|---|
   | 알약 식별이 「분석에 실패했어요」로 끝나고 홈의 「오늘 남은 횟수」가 비어 있다 | 릴리스는 App Check 공급자가 **Play Integrity** 인데(`app/src/release/.../AppCheckProvider.kt`) 그건 **Play 로 설치된 앱**을 전제한다. 사이드로드는 `App attestation failed`(403) → 서버가 `401 APP_CHECK_FAILED` | 여기까지 왔으면 **정상이다** — 검출은 이미 통과했다는 뜻이다 |
   | 카카오 로그인이 `Android keyHash validation failed` 로 막힌다 | 카카오 콘솔에 **Play 앱 서명 키**의 해시만 있으면 업로드 키로 서명한 APK 는 거부된다 | 콘솔에 **업로드 키 해시**도 등록해 둔다(SHA-1 을 Base64 로) |
   | 워치 만료 팝업이 안 뜨고 진동만 온다 | 손목에서 벗으면 Wear 가 `off-body` 를 보고 알림 자체를 거른다(`KNOWN-ISSUES.md` ⑥) | **차고** 확인한다 |

   ```
   App attestation failed.                                   ← App Check (Play Integrity)
   AuthError(reason=Misconfigured, Android keyHash validation failed.)   ← 카카오
   [AlertingPipeline] Not alerting: Device is off-body        ← 워치
   ```

   키 해시는 이렇게 뽑는다:
   ```bash
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk \
     | awk '/SHA-1 digest/{print $NF}' | xxd -r -p | base64
   ```

   그래서 **이 스모크가 덮는 범위는 R8 크래시 · 타이머 · 워치 · 위젯 · 잠금화면**이다.
   서버 응답이 필요한 부분(속성 추출 결과 화면)만 Play 내부 테스트로 넘긴다.

   ⚠️ **알약 식별을 「어차피 401 이니까」 하고 건너뛰지 않는다.** 온디바이스 검출은 서버를
   타지 않아 **여기서 그대로 검증된다.** 실제로 R8 이 ONNX 의 JNI 조회 대상을 갈아 버려
   릴리스에서만 `SIGABRT` 로 즉사한 적이 있고(`KNOWN-ISSUES.md` ⑩), 401 만 보고 넘어가는
   바람에 세 번의 릴리스가 그대로 나갔다. 봐야 할 것은 **프로세스가 살아 있는지**와 이 줄이다:

   ```
   NM394: 검출 4개 | 전처리 50ms 추론 661ms 후처리 9ms     ← 이게 찍히면 통과
   ```

   ```bash
   adb logcat -c && adb logcat | grep -E "NM394|Fatal signal"
   ```

   ⚠️ **반대 방향도 있다 — 사이드로드에서 멀쩡한데 Play 에서만 깨지는 것.**
   이 APK 는 **업로드 키**로 서명되는데, Play 배포본은 **Play 앱 서명 키**로 재서명된다.
   서명 지문에 걸린 것(소셜 로그인 전부)은 여기서 100% 정상으로 보이고 Play 에서만 죽는다.
   `0.2.1` 에서 실제로 카카오·구글·애플이 한꺼번에 깨졌다 — `KNOWN-ISSUES.md` ⑦.
2. `release/X.Y.Z` 브랜치를 컷해 **`main` 으로 PR** → 머지.
3. 그 **`main` 머지 커밋에 `v<versionName>` 태그**를 붙여 푸시.

   ⚠️ **`main → develop` 역머지는 `main` 을 지운다 — 저장소 설정이 자동으로 지운다.**
   역머지 PR 은 **head 가 `main`** 인데, 저장소의 `delete_branch_on_merge` 가 켜져 있으면
   머지되는 순간 head 브랜치가 사라진다. **사람이 [Delete branch] 를 누르지 않아도,
   `gh pr merge --delete-branch=false` 를 줘도 지워진다**(2026-09-15 에 실제로 겪었다).
   `v0.2.0` 때 `main` 이 사라진 것도 같은 원인이다(PR #16).

   그래서 설정을 껐다:
   ```bash
   gh api -X PATCH repos/Two-Park-One-Bae/Android -f delete_branch_on_merge=false
   ```
   머지 후에는 브랜치가 남아 있는지 확인한다. 사라졌으면 태그에서 되살린다:
   ```bash
   gh api repos/Two-Park-One-Bae/Android/branches --jq '.[].name'
   git push origin "$(git rev-list -n1 v<마지막 태그>):refs/heads/main"
   ```
4. **태그 커밋을 체크아웃해** 최종 AAB 를 만든다(모델 파일 존재 확인 후).
   ```bash
   git checkout v<versionName>
   ls app/src/main/assets/rfdetr_seg_small.onnx   # 없으면 위 "검출 모델" 절 참고
   ./gradlew :app:bundleRelease :wear:bundleRelease
   jarsigner -verify app/build/outputs/bundle/release/app-release.aab    # "jar verified."
   jarsigner -verify wear/build/outputs/bundle/release/wear-release.aab  # "jar verified."
   ```
5. Play Console 비공개 트랙에 AAB 를 올리고 → 출시 노트 작성 → 검토 요청.
   폰은 `app-release.aab` 를 알파 트랙에, 워치는 `wear-release.aab` 를 **`wear-alpha`** 에.
   - **mapping.txt 는 따로 올리지 않는다.** AAB 안에
     `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` 으로 이미 들어 있다.
   - 관리형 게시가 꺼져 있으면 검토 통과 즉시 자동 게시된다.

   ### Wear OS 는 폼 팩터가 따로다 (0.2.1 에서 처음 겪은 것)

   같은 `applicationId` 라도 Play 는 Wear 를 **별도 폼 팩터**로 다룬다. 폰 트랙에 워치 AAB 를
   얹을 수 없고, 워치용 트랙을 새로 판다. 0.2.1 에서 실제로 막혔던 자리만:

   | 막히는 것 | 왜 | 어떻게 |
   |---|---|---|
   | 새 워치 트랙의 활성 국가가 **0개** | 워치 트랙은 기본으로 **프로덕션과 동기화**되는데 프로덕션이 아직 비활성이라 0개가 내려온다 | 「국가/지역」 탭에서 **동기화를 풀고** 폰 알파와 같은 나라를 직접 고른다 |
   | `1000000004 버전 코드는 이미 사용되었습니다` | 앞선 시도에서 **라이브러리에 이미 올라간** 번들이다. versionCode 는 재사용할 수 없다 | 다시 올리지 말고 **「라이브러리에서 추가」** 로 그 번들을 고른다 |
   | 「정확한 알람 권한을 사용하는지 알려 주셔야 합니다」 | **워치만** `USE_EXACT_ALARM` 을 쓴다(폰은 `SCHEDULE_EXACT_ALARM`). 워치 AAB 가 들어오면서 앱 단위 선언이 새로 요구된다 | 앱 콘텐츠 → 「정확한 알람」 에서 **「알람 시계」** 로 선언한다 |

   워치 쪽은 이 밖에도 ① 모든 스토어 등록정보에 **워치 스크린샷**, ② 워치 AAB 가 테스트 트랙에
   **출시된 상태**, ③ **Wear OS 선택 및 검토 정책 동의** 가 갖춰져야 심사로 넘어간다.
6. `main` → `develop` **역머지 PR**을 열어 이력을 되돌린다(내용 변경이 없어도 한다 —
   안 하면 다음 릴리스 PR 에서 두 브랜치가 갈라져 충돌하고, 충돌이 있으면 GitHub 이
   PR CI 를 아예 돌리지 않아 원인 찾기가 어려워진다).
7. 출시 후 Doply 설치 이력에서 반영 확인 (NM-400: 3~4일 간격 최소 2회)
