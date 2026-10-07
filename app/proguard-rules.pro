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

# ONNX Runtime — onnxruntime-android AAR에 consumer rules가 없다(직접 확인, 2026-09-17).
# 네이티브 쪽이 추론 결과를 JVM으로 돌려줄 때 클래스를 **이름으로** 찾는다
# (`OrtJniUtil.c` → `FindClass("ai/onnxruntime/TensorInfo")` → `GetMethodID`).
# R8이 그 이름을 바꾸면 FindClass가 null을 주고, 그 null이 GetMethodID로 들어가는 순간
# ART가 "JNI DETECTED ERROR IN APPLICATION: java_class == null" 로 프로세스를 죽인다.
# Java 코드는 이 클래스들을 직접 부르지 않아 R8 입장에선 지우거나 바꿔도 되는 것으로 보인다.
#
# 알약 식별을 누르면 릴리스에서만 100% SIGABRT 였다(0.2.1~0.2.3 전부, 실기기·에뮬레이터 확인).
# 검출 자체는 성공하고 **결과를 읽는 단계**에서 죽어, 원인이 추론 엔진처럼 보였다 —
# ONNX 버전을 1.29.0 → 1.22.0 으로 내렸지만 R8 문제라 아무 효과가 없었다.
#
# 패키지 통째로 남긴다. JNI가 이름으로 찾는 대상이 TensorInfo 하나가 아니라
# OnnxTensor·OnnxMap·OnnxSequence·MapInfo·SequenceInfo·OnnxJavaType·OrtException 등
# 여럿이고, 어느 것이 언제 불리는지는 모델 출력 타입에 따라 달라진다. Java API 표면은
# 작아서(수십 KB) 남겨도 크기 영향이 없다.
-keep class ai.onnxruntime.** { *; }

# OpenCV — 각인 OCR 전처리(NM-485). ONNX Runtime 과 **같은 부류의 위험**이다(⑩).
# 네이티브 쪽이 JVM 으로 값을 돌려줄 때 클래스를 이름으로 찾으므로, R8 이 이름을 바꾸면
# 빌드는 멀쩡히 성공하고 릴리스에서만 죽는다. Java API 표면이 작아 남겨도 크기 영향이 없다.
-keep class org.opencv.** { *; }

# Airbridge — 앱 삭제 추적(uninstall tracking)이 FCM 의 `RemoteMessage` 를 참조한다.
# 우리는 **그 기능을 쓰지 않아**(NM-543 범위 밖) firebase-messaging 을 넣지 않았는데,
# R8 은 참조만 보고 "Missing class" 로 **빌드를 멈춘다**(경고가 아니라 실패다).
#
# 쓰지 않는 길이라 클래스를 끌어올 이유가 없다 — 없어도 된다고 알려 주는 쪽을 고른다.
# 삭제 추적을 켜게 되면 그때 firebase-messaging 을 넣고 이 줄을 지운다.
-dontwarn com.google.firebase.messaging.**
