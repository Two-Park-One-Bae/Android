# 앱 전용 R8 규칙
#
# Retrofit·OkHttp·kotlinx.serialization·Hilt는 각 라이브러리가 배포하는
# consumer rules(META-INF/proguard)로 충분하다 — 여기 중복 정의하지 않는다.
# 릴리스 크래시에서 R8 매핑 이슈가 확인될 때만 최소 범위로 추가할 것.

# 크래시 스택트레이스 심볼 복원용 (Play Console 난독화 해제 파일과 함께 사용)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 카카오 SDK — v2-common AAR에 consumer rules가 없다(직접 확인, 2026-09-04).
# *ErrorCause enum(ClientErrorCause 등)이 앱 시작 시 자기 상수를
# `reason.javaClass.getField(reason.name)` 로 리플렉션 조회해 @Description 애노테이션을
# 읽는다. 카카오 로그인을 쓰지 않아도 SDK 자체 초기화 과정에서 실행돼 전 사용자에게
# 즉시 크래시로 번진다(NoSuchFieldException: TokenNotFound, 실기기 스모크 테스트로 확인).
# R8이 enum 상수 필드명을 바꾸는 것과 애노테이션을 지우는 것 둘 다 원인이라 둘 다 막는다.
-keepclassmembers class com.kakao.sdk.**.*ErrorCause { *; }
-keep @interface com.kakao.sdk.common.model.Description
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
