import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.util.Properties

plugins {
    alias(libs.plugins.nursemate.android.application)
    alias(libs.plugins.nursemate.android.application.compose)
    alias(libs.plugins.nursemate.hilt)
    // 빌드 타입별 google-services.json 을 읽어 FirebaseApp 설정을 만든다.
    //   debug   → app/src/debug/google-services.json    (Nursemate-dev  · app.nursemate.debug)
    //   release → app/src/release/google-services.json  (Nursemate-prod · app.nursemate)
    // ⚠️ 두 파일은 커밋하지 않는다(.gitignore). 새 환경에서는 Firebase 콘솔에서 받아 넣어야 빌드된다.
    alias(libs.plugins.google.services)
    // 크래시 리포트 (NM-466). google-services 뒤에 와야 FirebaseApp 설정을 물려받는다.
    alias(libs.plugins.firebase.crashlytics)
}

// 카카오 앱 키 주입용. 릴리스 서명은 폰·워치가 같아야 해서 convention plugin 이 준다(ReleaseSigning.kt).
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun secret(key: String): String? = secrets.getProperty(key) ?: System.getenv(key)

/**
 * 카카오 앱 키를 BuildConfig 와 매니페스트 스킴에 함께 주입한다.
 *
 * 둘이 **반드시 같은 값**이어야 한다. 카카오 로그인은 `kakao{앱키}://oauth` 로 돌아오는데,
 * SDK 초기화에 쓴 키와 매니페스트에 박힌 스킴이 어긋나면 카카오톡에서 돌아올 곳을 찾지 못한다.
 * 한 함수에서 같이 세팅해 갈라질 여지를 없앤다.
 *
 * 키가 없으면(secrets.properties 미설정, PR CI 등) 빈 값으로 두고 빌드는 통과시킨다 —
 * 카카오 로그인만 동작하지 않는다.
 */
fun com.android.build.api.dsl.ApplicationBuildType.kakaoAppKey(key: String?) {
    val value = key.orEmpty()
    buildConfigField("String", "KAKAO_APP_KEY", "\"$value\"")
    manifestPlaceholders["kakaoScheme"] = if (value.isEmpty()) "kakao-unset" else "kakao$value"
}

android {
    namespace = "app.nursemate"

    defaultConfig {
        applicationId = "app.nursemate"
        // 증가 정책은 docs/RELEASE.md — versionCode는 Play 업로드마다 +1, versionName은 SemVer
        versionCode = 10
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Play 비공개 테스트 빌드와 **나란히** 설치되게 패키지를 분리한다.
            // 같은 applicationId 면 서명이 달라 INSTALL_FAILED_UPDATE_INCOMPATIBLE 이 나고,
            // 개발하려면 테스터가 쓰고 있는 앱을 지워야 한다.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            kakaoAppKey(secret("KAKAO_APP_KEY_DEBUG"))
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            kakaoAppKey(secret("KAKAO_APP_KEY_RELEASE"))

            // ⚠️ **네이티브 심볼을 올려야 `libonnxruntime.so` 스택이 함수명으로 보인다.**
            // 이게 없으면 주소만 남아, 정확히 이번에 겪은 상황(원격에서 스택을 못 읽음)이 반복된다.
            //
            // release 에만 건다 — debug 는 R8 을 안 써서 올릴 매핑이 없고, 네이티브 심볼도
            // 스트립되지 않아 스택이 그대로 읽힌다. 수집 자체는 양쪽 다 켜 둔다.
            configure<CrashlyticsExtension> { nativeSymbolUploadEnabled = true }
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.timer)
    implementation(projects.core.network)
    implementation(projects.core.vision)
    implementation(projects.core.designsystem)
    // 폰↔워치 동기화(NM-445). WearableListenerService 를 앱이 직접 상속하므로 GMS 도 함께 쓴다.
    implementation(projects.core.datalayer)
    implementation(libs.play.services.wearable)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.navigation.compose)

    // 구글 로그인 — 자격 증명 획득은 UI 레이어의 일이라 app 에 둔다.
    // 세션(FirebaseAuth)은 core:data 의 AuthRepository 가 갖는다.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // 카카오 로그인 — 액세스 토큰까지만 여기서 받고, Firebase 교환은 서버가 한다.
    implementation(libs.kakao.user)

    // 촬영 화면(① 촬영). PreviewView + ImageCapture 만 쓴다.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // 위젯(NM-443)은 RemoteViews 로 직접 그린다. Glance 를 쓰다 걷어냈다 — 사정은
    // [PresetWidgetRenderer]. 의존성도 함께 뺀다: glance-appwidget 이 WorkManager 2.7.1 을
    // 끌고 오는데, 그 WorkManagerInitializer 가 androidx.startup 으로 프로세스 시작 때 돌면서
    // release(R8)에서 WorkDatabase 생성에 실패해 앱이 바로 죽었다(2026-09-11 릴리스 스모크).
    // 슬롯 저장에 쓰는 DataStore 는 그동안 Glance 를 타고 들어와 있었다 — 직접 선언한다.
    implementation(libs.androidx.datastore.preferences)

    // 후보·세부정보의 낱알 이미지(CDN). 없는 품목은 404 가 오므로 폴백이 필요하다(NM-347).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // App Check provider 는 빌드 타입별로 하나씩만 넣는다.
    //   debug   → DebugAppCheckProvider  (Play 스토어 밖이라 Play Integrity 가 통하지 않는다)
    //   release → PlayIntegrityAppCheckProvider
    // 둘을 같이 넣으면 release 에서도 디버그 토큰으로 통과할 수 있어 App Check 가 무의미해진다.
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)

    // 크래시·지표 (NM-466). 식별자 계약은 spec `feature/auth/README.md` §지표·크래시 식별자.
    // 빌드 타입으로 가르지 않는다 — debug 는 Firebase 프로젝트가 dev 라 운영 지표와 섞이지 않고,
    // 크래시는 개발 중에 더 필요하다(iOS `AppEnvironment` 와 같은 판단).
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.firebase.analytics)

    // 강제 업데이트·점검 모드를 원격으로 켠다. 값이 없거나 못 받아도 인앱 기본값으로 동작한다
    // — 네트워크가 막혔다고 멀쩡한 사용자를 가두면 안 된다.
    implementation(libs.firebase.config)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

// 릴리스 산출물에 검출 모델이 빠지지 않게 막는다.
//
// 모델(119MB)은 저장소에 넣지 않으므로(.gitignore) 파일이 없어도 빌드는 그냥 성공한다 —
// 그 AAB 를 올리면 사용자는 알약 식별을 시도할 때마다 '분석 실패'만 본다.
// 조용히 깨진 릴리스보다 큰 소리로 실패하는 편이 낫다.
//
// 디버그는 막지 않는다 — adb 로 밀어 넣은 파일로 돌릴 수 있다.
// `run { }` 으로 감싸 **진짜 지역 변수**로 만든다. 스크립트 최상위 val 로 두면 그것도
// 스크립트 프로퍼티라, doFirst 가 스크립트 객체를 붙들어 설정 캐시가 직렬화하지 못한다.
run {
    val detectionModel = layout.projectDirectory.file("src/main/assets/rfdetr_seg_small.onnx").asFile

    tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
        doFirst {
            check(detectionModel.isFile) {
                """
                검출 모델이 없습니다: $detectionModel

                저장소에 넣지 않는 파일이라 릴리스 빌드 전에 직접 두어야 합니다.
                자세한 절차는 docs/RELEASE.md 참고.
                """.trimIndent()
            }
        }
    }
}
