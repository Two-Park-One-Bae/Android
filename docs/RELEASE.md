# 릴리스 가이드 (:app — 폰 전용)

> 릴리스 산출물은 서명된 AAB 1개(`:app:bundleRelease`)다. **wear는 릴리스 체인에서 제외** — 타이머 기능 완성 후 별도 티켓으로 합류. (릴리스 체인: NM-398 → NM-397 → NM-399 → NM-400)

## 버전 정책

버전은 `app/build.gradle.kts` `defaultConfig` 한 곳에서 관리한다.

- **versionCode** — Play 업로드마다 **+1** (단조 증가, 재사용·되돌리기 불가). Play Console 업로드용 빌드 전에 커밋으로 올린다.
- **versionName** — SemVer `MAJOR.MINOR.PATCH`.
  - 비공개 테스트 기간(NM-400): 업로드마다 PATCH +1 (`0.1.0` → `0.1.1` → …), versionCode도 함께 +1.
  - 프로덕션 첫 출시에서 `1.0.0`.
- **태그** — Play에 실제 업로드한 커밋에 `v<versionName>` 태그 (예: `v0.1.0`). 태그 푸시가 릴리스 CI를 트리거한다.
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
- ⚠️ **그래서 릴리스 AAB 는 CI 가 아니라 로컬에서 만든다.** `release.yml` 이 만드는 산출물에는
  모델이 없다 — Play 에 올리지 말 것.
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

## CI 릴리스 (.github/workflows/release.yml)

- **트리거**: Actions 탭 수동 실행(workflow_dispatch) 또는 `v*` 태그 푸시.
- **산출물(아티팩트)**: `app-release-aab`(서명된 AAB) · `app-release-mapping`(R8 mapping.txt — Play Console 업로드 시 함께 등록).
- **Secrets**: `BASE_URL_DEV` · `BASE_URL_PROD` · `RELEASE_KEYSTORE_BASE64` · `RELEASE_STORE_PASSWORD` · `RELEASE_KEY_ALIAS` · `RELEASE_KEY_PASSWORD` (등록 완료, 2026-08-26).

## Play 업로드 절차 (NM-397/400)

1. versionCode·versionName 올려 커밋 → `v<versionName>` 태그 푸시 (또는 수동 실행)
2. Actions 아티팩트에서 AAB·mapping.txt 다운로드
3. Play Console 비공개 트랙에 AAB 업로드, 난독화 해제 파일(mapping.txt) 등록
4. 출시 후 Doply 설치 이력에서 반영 확인 (NM-400: 3~4일 간격 최소 2회)
