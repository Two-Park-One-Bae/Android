import app.nursemate.buildlogic.libs
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import java.io.File
import java.util.zip.ZipFile

/**
 * QNN 조건부 전달 모듈을 만든다 — `qnn_core` 하나와 세대별 `qnn_v73` · `qnn_v75` · `qnn_v79`.
 *
 * ## 왜 세대마다 모듈을 따로 두나
 * QNN 백엔드에서 덩치는 **세대별 Skel** 이다(세대당 16MB). 기기는 자기 세대 하나만 쓰는데
 * 한 모듈에 다 넣으면 전부 받는다 — 48MB 가 그냥 버려진다.
 *
 * `.so` 는 기기그룹별 변형을 만들 수 없다. 에셋은 `#group_` 접미사로 한 팩 안에서 갈리지만
 * `jniLibs` 에는 그런 길이 없고, 조건은 **모듈 단위로만** 걸린다. 그래서 모듈을 쪼갠다.
 *
 * | 모듈 | 담는 것 | 크기 | 설치 조건 |
 * |---|---|---|---|
 * | `qnn_core` | EP 번역기 + `libQnnHtp` · `libQnnSystem` | 9.2MB | 퀄컴 그룹 **전부** |
 * | `qnn_v##` | 그 세대 `Skel`(16MB) · `Stub` | 16.6MB | 그 세대 그룹만 |
 *
 * ## 왜 Maven 의존성을 안 물고 `.so` 를 꺼내 쓰나
 * 세 모듈이 `qnn-runtime` 을 각자 물면 AGP 가 **「여러 APK 가 같은 라이브러리를 패키징한다」**
 * 로 빌드를 막는다 — 파일을 서로 다르게 걸러 담아도 artifact 단위로 본다.
 *
 * 그래서 artifact 는 **풀기만** 하고(`qnnRuntimeAar` 설정) 필요한 파일만 generated jniLibs 로
 * 옮긴다. 덤으로 **제외목록이 허용목록이 된다** — 전에는 「안 쓰는 세대를 전부 적어서 빼는」
 * 방식이라 artifact 에 새 세대가 생기면 조용히 모든 모듈에 딸려 들어갔다. 지금은 적은 것만
 * 들어간다.
 *
 * ## 무엇을 버리나
 * - **`libQnnHtpPrepare.so` (80MB)** — 기기에서 모델을 컴파일하는 **컴파일러**. EPContext 를
 *   미리 구워 실으므로 필요 없다
 * - 다른 세대 `Skel`/`Stub` · `libQnnGpu.so` · `libQnnDsp*.so`
 *
 * ⚠️ **`HtpPrepare` 를 뺐으므로 기기에서 컴파일할 수 없다.** QNN 으로 여는 모델은 반드시
 *    미리 구운 EPContext 여야 한다. 원본 ONNX 를 QNN 으로 열면 세션 생성에서 실패하고,
 *    그때는 base 의 원본을 CPU/WebGPU 로 여는 폴백이 받는다.
 */
abstract class QnnFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val wanted = wantedLibs(name)

            // Kotlin 은 AGP 9 에 내장이라 따로 적용하지 않는다(루트 CLAUDE.md).
            apply(plugin = "com.android.dynamic-feature")

            extensions.configure<DynamicFeatureExtension> {
                namespace = "app.nursemate." + name.replace('-', '_')
                // convention 플러그인(`nursemate.android.*`)이 쓰는 값과 같은 숫자다. 이 모듈에는
                // 코드가 한 줄도 없어서 그쪽 Hilt·테스트 구성은 전부 군더더기다.
                compileSdk = 37
                // base 는 26 인데 여기만 27 이다 — 플러그인 artifact 가 27 을 요구한다.
                // `system-on-chip` 조건 자체가 API 31+ 라 실질적으로는 더 높아도 상관없다.
                defaultConfig.minSdk = 27

                // base 와 **같아야** 한다 — `extractNativeLibs` 가 모듈마다 갈리면 어떤 split 은
                // 풀리고 어떤 split 은 안 풀린다. QNN 은 skel 이 실파일이어야 도므로 여기도 켠다.
                // 사정은 app/build.gradle.kts 의 같은 설정 주석 참고.
                packaging.jniLibs.useLegacyPackaging = true
            }

            // 풀어 쓰기만 하는 설정 — `implementation` 이 아니라서 AGP 가 패키징하지 않는다.
            val aars = configurations.create("qnnRuntimeAar") {
                isCanBeConsumed = false
                isTransitive = false
            }
            dependencies { add(aars.name, libs.findLibrary("qnn.runtime").get()) }

            val jniLibs = tasks.register<QnnJniLibsTask>("generateQnnJniLibs") {
                sourceAars.from(aars)
                libNames.set(wanted)
                outputDir.set(layout.buildDirectory.dir("generated/qnnJniLibs"))
            }
            extensions.configure<DynamicFeatureAndroidComponentsExtension> {
                onVariants { variant ->
                    variant.sources.jniLibs?.addGeneratedSourceDirectory(jniLibs, QnnJniLibsTask::outputDir)
                }
            }

            dependencies {
                "implementation"(project(":app"))
                if (name == CORE_MODULE) {
                    // EP 번역기. **여기 한 곳에서만** 문다 — 클래스(`QnnPluginEpLibraryKt`)가
                    // 들어 있어서 두 모듈에 들어가면 중복 패키징이 된다. base 의 core:vision 은
                    // 이 클래스들을 `compileOnly` 로 컴파일만 하고 런타임에 이 모듈을 쓴다.
                    "implementation"(libs.findLibrary("onnxruntime.qnn.plugin").get())
                }
                // ⚠️ 세대 모듈이 `qnn_core` 를 **의존으로 걸 수 없다.** bundletool 이 막는다 —
                //    「조건부 모듈은 설치 시점이 아닌 모듈에 의존할 수 없다」. 조건부끼리는
                //    서로 의존이 안 된다는 뜻이다.
                //
                //    걸 필요도 없다. `qnn_core` 는 퀄컴 그룹 전부에, 세대 모듈은 그중 자기
                //    그룹에 걸려 있어서 **둘은 언제나 함께 깔린다.** 다만 그래서 그룹 목록이
                //    어긋나면 조용히 토대만 빠진다 — 세대를 더할 때 `qnn_core` 매니페스트에
                //    그룹을 같이 추가해야 하는 이유다.
            }
        }
    }

    private companion object {
        const val CORE_MODULE = "qnn_core"

        /** 세션·메모리·직렬화. 세대와 무관해서 한 번만 싣는다. */
        val CORE_LIBS = setOf("libQnnHtp.so", "libQnnSystem.so")

        fun wantedLibs(moduleName: String): Set<String> {
            if (moduleName == CORE_MODULE) return CORE_LIBS
            val generation = moduleName.substringAfterLast("_v")
            require(generation.toIntOrNull() != null) {
                "QNN feature 모듈 이름은 `$CORE_MODULE` 이거나 `qnn_v<세대>` 꼴이어야 한다 (받은 것: $moduleName)"
            }
            // Skel 은 **DSP 위에서** 도는 연산 커널, Stub 은 앱 CPU 쪽 FastRPC 창구다. 한 쌍이다.
            return setOf("libQnnHtpV${generation}Skel.so", "libQnnHtpV${generation}Stub.so")
        }
    }
}

/**
 * `qnn-runtime` AAR 에서 **적어 준 `.so` 만** 꺼내고, 다른 ABI 용 **빈 `.so`** 를 만든다.
 *
 * ## 왜 빈 파일이 필요한가
 * bundletool 이 「네이티브를 가진 모듈은 ABI 집합이 같아야 한다」고 막는다. base 는 넷
 * (arm64-v8a · armeabi-v7a · x86 · x86_64)인데 QNN artifact 는 **arm64 하나뿐**이다 —
 * Hexagon 이 arm64 스냅드래곤에만 있어서 다른 판이 아예 존재하지 않는다.
 *
 * 이 빈 파일들은 **아무도 받지 않는다.** 그 ABI 기기가 퀄컴 기기그룹에 맞을 리 없어서
 * 조각이 만들어지기만 하고 전달되지 않는다. 저장소에 가짜 바이너리를 커밋하지 않으려고
 * 빌드 때 만든다.
 */
abstract class QnnJniLibsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sourceAars: ConfigurableFileCollection

    @get:Input
    abstract val libNames: SetProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val root = outputDir.get().asFile
        root.deleteRecursively()

        val wanted = libNames.get()
        val found = mutableSetOf<String>()
        val arm64 = File(root, ABI).apply { mkdirs() }

        for (aar in sourceAars) {
            ZipFile(aar).use { zip ->
                for (entry in zip.entries()) {
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || name !in wanted) continue
                    if (!entry.name.contains("/$ABI/")) continue
                    zip.getInputStream(entry).use { input ->
                        File(arm64, name).outputStream().use(input::copyTo)
                    }
                    found += name
                }
            }
        }

        // 조용히 비는 것을 막는다 — `.so` 가 빠지면 세션 생성이 실패하는 게 아니라 QNN 이
        // 노드를 하나도 안 가져가 **CPU 로 돌면서 테스트가 통과한다.** 여기서 끊는다.
        check(found == wanted) {
            "qnn-runtime AAR 에서 못 찾은 라이브러리: ${wanted - found} (찾은 것: $found)"
        }

        for (abi in PLACEHOLDER_ABIS) {
            File(root, abi).apply { mkdirs() }
                .resolve("libqnn_abi_placeholder.so")
                .writeBytes(ByteArray(0))
        }
    }

    private companion object {
        const val ABI = "arm64-v8a"
        val PLACEHOLDER_ABIS = listOf("armeabi-v7a", "x86", "x86_64")
    }
}
