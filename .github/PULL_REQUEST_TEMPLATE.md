## 관련 이슈
- NM-XXX

## 변경 요약
<!-- 무엇을, 왜 바꿨는지 1~2줄 -->

## 변경 내용
-

## 테스트
- [ ] 로컬에서 **CI 와 같은 것**을 돌렸다 (`./gradlew spotlessCheck detekt lint test`)
      <!-- 일부만 돌리면 CI 에서 갈린다. lint 누락으로 실제로 두 번 빨개졌다. -->
- [ ] 디버그 빌드 확인 (`./gradlew assembleDebug`)
- [ ] 정상·에러 케이스 확인
- [ ] 실기기 확인 (UI 변경이 있으면 스크린샷 첨부)

## 체크리스트
- [ ] PR 제목이 `[NM-XXX] 요약` 형식
- [ ] 브랜치명 `<type>/NM-XXX-요약` 형식
- [ ] 커밋 메시지 `[NM-XXX] type: 요약` 형식
- [ ] 셀프 리뷰 완료
- [ ] 비밀값(`secrets.properties` · keystore · `google-services.json`)이 커밋에 없음
