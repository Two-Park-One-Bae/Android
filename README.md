# NurseMate Android

널스메이트(NurseMate) Android / wearOS 앱.

## 모듈 구조

```
app/                  폰 앱 (Compose M3) — applicationId app.nursemate
wear/                 워치 앱 (Wear Compose M3) — 동일 applicationId, versionCode +1e9 오프셋
core/
├── model/            순수 Kotlin 도메인 모델
├── data/             Repository · DataStore (기기 UUID 등)
├── network/          Retrofit · BASE_URL(BuildConfig) · RFC 9457 에러 모델
├── datalayer/        폰↔워치 Wearable Data Layer 래퍼 (경로 계약)
└── designsystem/     DS 토큰 (Pretendard · 컬러 램프 · spacing · radius)
build-logic/          Gradle convention 플러그인 (AGP 9 신DSL)
spec/                 스펙 서브모듈 (API·기능 스펙·디자인 정본)
```

- 빌드: AGP 9 / Kotlin 2.3 (built-in — `org.jetbrains.kotlin.android` 미사용) / JDK 17 / compileSdk·targetSdk 36 / 폰 minSdk 26 · 워치 minSdk 30

## 시작하기

1. 서브모듈 포함 클론: `git clone --recurse-submodules https://github.com/Two-Park-One-Bae/Android.git`
2. `secrets.properties.sample`을 `secrets.properties`로 복사하고 값 채우기
3. `./gradlew assembleDebug`

## 컨벤션

브랜치 · 커밋 · PR · Jira 자동 전환 규칙의 정본은 [spec/docs/CONVENTIONS.md](spec/docs/CONVENTIONS.md).

- 브랜치: `<type>/NM-XXX-요약` (develop에서 분기)
- 커밋: `[NM-XXX] type: 한글 요약`
- PR: `[NM-XXX] 요약` → develop

## 스펙

API 명세·기능 스펙·디자인의 정본은 `spec/` 서브모듈 (`spec/api/openapi.yaml`, `spec/feature/`).
