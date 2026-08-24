# NurseMate Android

널스메이트(NurseMate) Android / wearOS 앱.

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
