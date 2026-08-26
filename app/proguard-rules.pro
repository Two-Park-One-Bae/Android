# 앱 전용 R8 규칙
#
# Retrofit·OkHttp·kotlinx.serialization·Hilt는 각 라이브러리가 배포하는
# consumer rules(META-INF/proguard)로 충분하다 — 여기 중복 정의하지 않는다.
# 릴리스 크래시에서 R8 매핑 이슈가 확인될 때만 최소 범위로 추가할 것.

# 크래시 스택트레이스 심볼 복원용 (Play Console 난독화 해제 파일과 함께 사용)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
