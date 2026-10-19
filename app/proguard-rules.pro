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

# LiteRT — 온디바이스 추론 런타임(NM-628). ONNX Runtime 시절 ⑩ 과 **같은 부류의 위험**이다.
# `liblitert_jni.so` 가 결과를 JVM 으로 돌려줄 때 `LiteRtException` · `TensorBufferRequirements` ·
# `TensorType`(+`ElementType` · `Layout`)을 **이름으로** 찾는다(`.so` 문자열로 확인, 2026-10-19).
# R8 이 그 이름을 바꾸면 FindClass 가 null 을 주고, 그 null 이 GetMethodID 로 들어가는 순간 ART 가
# "JNI DETECTED ERROR IN APPLICATION" 으로 프로세스를 죽인다 — 릴리스에서만.
#
# AAR 에 딸린 규칙(`proguard.txt`)은 `org.tensorflow.lite.annotations.UsedByReflection` 만 지켜서
# 위 클래스들을 막아 주지 않는다. 패키지 통째로 남긴다 — Kotlin API 표면이 작아 크기 영향이 없다.
# 같은 일을 `app/build.gradle.kts` 의 매핑 가드가 빌드 때 확인한다.
#
# ONNX Runtime 시절 사정(0.2.1~0.2.3 릴리스 100% SIGABRT)은 docs/KNOWN-ISSUES.md ⑩.
-keep class com.google.ai.edge.litert.** { *; }

# `core:vision` 이 LiteRT 에서 `play:ai-delivery`(AI Pack 받기, 알파)를 뺐다 — 그게 WorkManager 를 끌고 와
# 릴리스에서 앱이 시작하자마자 죽었다(사정은 core/vision/build.gradle.kts). 그 패키지를 부르는 것은
# `com.google.ai.edge.litert.deployment.AiPackModelProvider` 하나이고 우리는 쓰지 않는다 — 빈 참조만 넘긴다.
-dontwarn com.google.android.play.core.aipacks.**

# OpenCV — 각인 OCR 전처리(NM-485). LiteRT 와 **같은 부류의 위험**이다(⑩).
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
