import com.android.build.api.artifact.SingleArtifact
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.io.File
import java.security.MessageDigest
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

/**
 * Airbridge 앱 이름·SDK 토큰을 BuildConfig 에 넣는다 (NM-543).
 *
 * 카카오 키와 같은 패턴이다 — 비밀값은 `secrets.properties` 에서만 오고 저장소에 남지 않는다.
 * **토큰이 비면 앱이 초기화를 건너뛴다**(`NurseMateApplication`). 그래서 비밀값이 없는 환경
 * (PR CI · 외부 기여자)에서도 빌드는 통과하고, 내부 debug 빌드도 같은 길로 측정에서 빠진다.
 */
fun com.android.build.api.dsl.ApplicationBuildType.airbridge(appName: String?, token: String?) {
    buildConfigField("String", "AIRBRIDGE_APP_NAME", "\"${appName.orEmpty()}\"")
    buildConfigField("String", "AIRBRIDGE_APP_TOKEN", "\"${token.orEmpty()}\"")
}

android {
    namespace = "app.nursemate"

    defaultConfig {
        applicationId = "app.nursemate"
        // 증가 정책은 docs/RELEASE.md — versionCode는 Play 업로드마다 +1, versionName은 SemVer
        //
        // ⚠️ **12 를 건너뛴다.** 워치가 `1_000_000_012` 를 이미 올렸다(2026-09-24, 프로덕션
        // 활성). 오프셋 규칙상 폰 12 는 워치 1_000_000_012 를 요구하는데 versionCode 는
        // 재사용할 수 없다 — 폰·워치를 13 으로 함께 올려 오프셋을 지킨다.
        versionCode = 13
        versionName = "2.0.0"
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
            // ⚠️ debug 는 **일부러 비워 둔다**(NM-543 ⑨). 내부 사용이 유입 지표에 섞이면
            //    광고 성과가 부풀려진다 — Firebase 와 달리 여기는 프로젝트가 하나뿐이라
            //    패키지가 갈려도 지표가 갈리지 않는다.
            airbridge(appName = null, token = null)
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            kakaoAppKey(secret("KAKAO_APP_KEY_RELEASE"))
            airbridge(secret("AIRBRIDGE_APP_NAME"), secret("AIRBRIDGE_APP_TOKEN"))

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
    implementation(libs.airbridge.sdk)

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

// R8 이 ONNX Runtime · OpenCV 의 클래스 이름을 바꾸지 않았는지 **매핑으로** 확인한다.
//
// `KNOWN-ISSUES.md` ⑩ — 릴리스에서만 알약 식별이 100% SIGABRT 로 죽었다(0.2.1~0.2.3).
// ONNX Runtime 의 네이티브 쪽이 추론 **결과를 JVM 으로 돌려줄 때** 클래스를 이름으로 찾는데
// (`FindClass("ai/onnxruntime/TensorInfo")` → `GetMethodID`), Java 코드가 그 클래스들을 직접
// 부르지 않아 R8 이 지워도 되는 것으로 본다. 지금은 `proguard-rules.pro` 의
// `-keep class ai.onnxruntime.** { *; }` 가 막고 있다.
//
// ⚠️ **그 keep 이 사라지면 빌드는 멀쩡히 성공하고 사용자만 죽는다.** 컴파일도 단위 테스트도
// 아무 말을 하지 않는다. 그런데 증거는 매핑 파일에 그대로 남는다 — ⑩ 문서가 적어 둔 형태가
// 바로 이것이다:
//
//     살아 있을 때:  ai.onnxruntime.TensorInfo -> ai.onnxruntime.TensorInfo:
//     깨졌을 때:     ai.onnxruntime.TensorInfo -> at4:
//
// 그래서 매핑을 읽어 대조한다. 에뮬레이터로 실제 추론을 돌려 보는 길도 있었지만, 그러자면
// 계측 테스트를 minify 된 앱에 붙여야 하고 그러면 테스트 하네스가 요구하는 것들을
// (`androidx.tracing.Trace` · `kotlin.LazyKt` …) 운영 R8 규칙에 계속 남겨야 한다 —
// 검사 하나 때문에 출시 산출물을 건드리는 맞바꿈이라 접었다. 이 대조는 같은 결함을
// 에뮬레이터 없이 초 단위로, 결정적으로 잡는다.
//
// ⚠️ **매핑 경로를 손으로 적지 않는다.** `build/outputs/mapping/release/mapping.txt` 를 박고
// `minifyReleaseWithR8` 의 doLast 에서 읽었더니 로컬에서는 통과하고 CI 에서만
// 「매핑 파일이 없습니다」로 깨졌다 — 그 시점에는 아직 그 자리에 없고, 로컬에서는 앞선
// 빌드가 남긴 파일을 읽어 **가짜로 통과**한 것이었다. AGP 아티팩트 API 로 받는다.
androidComponents {
    // JNI 가 이름으로 찾는 것들. 하나라도 리네임되면 그 타입이 나오는 순간 프로세스가 죽는다.
    val jniLookedUp = listOf(
        "ai.onnxruntime.TensorInfo",
        "ai.onnxruntime.OnnxTensor",
        "ai.onnxruntime.MapInfo",
        "ai.onnxruntime.SequenceInfo",
        "ai.onnxruntime.OnnxJavaType",
        // OpenCV 도 같은 부류다(NM-485). 네이티브가 이름으로 찾는 값 타입들이다.
        "org.opencv.core.Mat",
        "org.opencv.core.Size",
        "org.opencv.core.Scalar",
        "org.opencv.core.Point"
    )

    onVariants(selector().withBuildType("release")) { variant ->
        val mapping = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
        val verify = tasks.register("verify${variant.name.replaceFirstChar(Char::uppercase)}OnnxKeep") {
            inputs.file(mapping).withPropertyName("mapping")
            doLast {
                val file = mapping.get().asFile
                check(file.isFile) { "매핑 파일이 없습니다: $file" }

                val renamed = mutableListOf<String>()
                val seen = mutableSetOf<String>()
                file.useLines { lines ->
                    for (line in lines) {
                        // 클래스 줄만 본다 — 멤버 줄은 들여쓰기가 있다.
                        // ⚠️ 접두사를 박아 두지 않는다. `"ai.onnxruntime."` 로 하드코딩했다가
                        // OpenCV 를 목록에 더했을 때 그 줄을 통째로 건너뛰어, 멀쩡히 있는
                        // 클래스를 「매핑에서 찾지 못했다」로 잘못 읽었다(NM-485).
                        if (line.isEmpty() || line[0].isWhitespace() || line[0] == '#') continue
                        val arrow = line.indexOf(" -> ")
                        if (arrow < 0) continue
                        val from = line.substring(0, arrow)
                        if (from !in jniLookedUp) continue
                        seen += from
                        val to = line.substring(arrow + 4).removeSuffix(":")
                        if (to != from) renamed += "  $from -> $to"
                    }
                }

                check(renamed.isEmpty()) {
                    """
                    R8 이 네이티브가 이름으로 찾는 클래스를 바꿨습니다 — 릴리스에서 알약 식별이 죽습니다.
                    ${renamed.joinToString("\n")}

                    `proguard-rules.pro` 의 `-keep class ai.onnxruntime.**` · `org.opencv.**` 를 확인하십시오.
                    사정은 docs/KNOWN-ISSUES.md ⑩.
                    """.trimIndent()
                }
                val missing = jniLookedUp - seen
                check(missing.isEmpty()) {
                    """
                    매핑에서 다음 클래스를 찾지 못했습니다: ${missing.joinToString()}

                    지워졌거나(keep 규칙 확인) 의존성이 빠진 것입니다. 둘 다 릴리스에서
                    알약 식별이 죽는 상태입니다 — docs/KNOWN-ISSUES.md ⑩.
                    """.trimIndent()
                }
                logger.lifecycle("JNI 가 이름으로 찾는 클래스 ${seen.size}개가 리네임되지 않았습니다 — ⑩ 방어 확인.")
            }
        }
        // 산출물을 만드는 태스크가 이 검사를 반드시 거치게 한다 — CI 뿐 아니라 로컬도.
        tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }
            .configureEach { dependsOn(verify) }
    }
}

// 릴리스 산출물에 모델이 빠지거나 **정본이 아닌 것이 들어가지 않게** 막는다.
//
// 모델은 저장소에 넣지 않으므로(.gitignore 의 *.onnx) 파일이 없어도 빌드는 그냥 성공한다 —
// 그 AAB 를 올리면 사용자는 식별을 시도할 때마다 '분석 실패'만 본다.
// 조용히 깨진 릴리스보다 큰 소리로 실패하는 편이 낫다.
//
// ⚠️ **해시까지 본다 — 있는 것만 확인하면 부족했다.** 2026-09-29 까지 assets 에 있던 seg 사본은
// DVC 정본과 md5 가 달랐다(`10ff2d39…` vs `cb20efc7…`). 뜯어보니 내용은 같은 모델이었지만,
// 그걸 알아내는 데 파일을 통째로 비교해야 했다. 해시를 박아 두면 빌드가 즉시 답한다.
//
// 목록은 `app/models.md5` 한 곳이다. 해시가 곧 DVC 원격의 객체 키라 **받는 경로와 검사가 같은
// 값을 쓴다**. CI 도 같은 파일을 읽는다(`.github/workflows/release-smoke.yml`).
//
// 디버그는 막지 않는다 — adb 로 밀어 넣은 파일로 돌릴 수 있다.
// `run { }` 으로 감싸 **진짜 지역 변수**로 만든다. 스크립트 최상위 val 로 두면 그것도
// 스크립트 프로퍼티라, doFirst 가 스크립트 객체를 붙들어 설정 캐시가 직렬화하지 못한다.
run {
    val assetsDir = layout.projectDirectory.dir("src/main/assets").asFile
    val manifest = layout.projectDirectory.file("models.md5").asFile

    tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
        doFirst {
            val entries = manifest.readLines()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val parts = line.split(Regex("\\s+"), limit = 2)
                    check(parts.size == 2) { "models.md5 의 줄이 「해시 두 칸 이름」이 아닙니다: $line" }
                    parts[0] to parts[1]
                }
            check(entries.isNotEmpty()) { "models.md5 에 모델이 하나도 없습니다" }

            for ((expected, name) in entries) {
                val model = File(assetsDir, name)
                check(model.isFile) {
                    """
                    모델이 없습니다: $model

                    저장소에 넣지 않는 파일이라 릴리스 빌드 전에 직접 두어야 합니다.
                    자세한 절차는 docs/RELEASE.md 「검출 모델」.
                    """.trimIndent()
                }
                val digest = MessageDigest.getInstance("MD5")
                model.inputStream().use { stream ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                check(actual == expected) {
                    """
                    모델이 정본이 아닙니다: $model
                      기대 md5: $expected
                      실제 md5: $actual

                    DVC 원격의 것으로 다시 받으십시오 — 절차는 docs/RELEASE.md 「검출 모델」.
                    """.trimIndent()
                }
            }
            logger.lifecycle("모델 ${entries.size}개가 정본과 같습니다 — ${entries.joinToString { it.second }}")
        }
    }
}
