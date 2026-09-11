# 릴리스 가이드 (:app — 폰 전용)

> 릴리스 산출물은 서명된 AAB 1개(`:app:bundleRelease`)다. **wear는 릴리스 체인에서 제외** — 타이머 기능 완성 후 별도 티켓으로 합류. (릴리스 체인: NM-398 → NM-397 → NM-399 → NM-400)

## 버전 정책

버전은 `app/build.gradle.kts` `defaultConfig` 한 곳에서 관리한다.

- **versionCode** — Play 업로드마다 **+1** (단조 증가, 재사용·되돌리기 불가). Play Console 업로드용 빌드 전에 커밋으로 올린다.
- **versionName** — SemVer `MAJOR.MINOR.PATCH`.
  - 비공개 테스트 기간(NM-400): 업로드마다 PATCH +1 (`0.1.0` → `0.1.1` → …), versionCode도 함께 +1.
  - 프로덕션 첫 출시에서 `1.0.0`.
- **태그** — `v<versionName>` (예: `v0.1.0`). **`main` 머지 커밋에 붙인다** — `develop`이 아니다.
  CONVENTIONS의 `release/* → main + develop` 규칙과 완료 판정(main 머지) 기준을 따른다.
- wear 합류 시: 동일 versionName, versionCode는 `+1_000_000_000` 오프셋 (README 참고).

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
> (실제로 카카오 SDK 리플렉션이 R8 에 깨져 앱 시작 즉시 크래시하던 것을 이 테스트로만 잡았다.)

## ⚠️ 워치 앱이 `specialUse` 포그라운드 서비스를 쓴다

`WearAlarmService` 가 그렇다. `applicationId` 가 폰과 같아 **같은 Play 등록에 묶이므로**
제출할 때 「특별한 용도」 사유를 적어야 한다. 사정은 `docs/KNOWN-ISSUES.md` ③ 에 있다.

## Play 업로드 절차 (NM-397/400)

1. `develop` 에서 릴리스 빌드 + **실기기 스모크 테스트**를 먼저 한다.
   ```bash
   ./gradlew :app:assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk
   ```
   실행해서 크래시 없는지 logcat 까지 본다. 문제가 나오면 여기서 `develop` 에 고치고 다시.
2. `release/X.Y.Z` 브랜치를 컷해 **`main` 으로 PR** → 머지.
3. 그 **`main` 머지 커밋에 `v<versionName>` 태그**를 붙여 푸시.
4. **태그 커밋을 체크아웃해** 최종 AAB 를 만든다(모델 파일 존재 확인 후).
   ```bash
   git checkout v<versionName>
   ls app/src/main/assets/rfdetr_seg_small.onnx   # 없으면 위 "검출 모델" 절 참고
   ./gradlew :app:bundleRelease
   jarsigner -verify app/build/outputs/bundle/release/app-release.aab   # "jar verified."
   ```
5. Play Console 비공개 트랙에 `app/build/outputs/bundle/release/app-release.aab` 업로드 →
   출시 노트 작성 → 검토 요청.
   - **mapping.txt 는 따로 올리지 않는다.** AAB 안에
     `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` 으로 이미 들어 있다.
   - 관리형 게시가 꺼져 있으면 검토 통과 즉시 자동 게시된다.
6. `main` → `develop` **역머지 PR**을 열어 이력을 되돌린다(내용 변경이 없어도 한다 —
   안 하면 다음 릴리스 PR 에서 두 브랜치가 갈라져 충돌하고, 충돌이 있으면 GitHub 이
   PR CI 를 아예 돌리지 않아 원인 찾기가 어려워진다).
7. 출시 후 Doply 설치 이력에서 반영 확인 (NM-400: 3~4일 간격 최소 2회)
