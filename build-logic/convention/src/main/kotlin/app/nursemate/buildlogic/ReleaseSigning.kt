package app.nursemate.buildlogic

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

/**
 * 릴리스 서명을 **폰·워치 두 모듈에 같은 값으로** 넣는다.
 *
 * Data Layer 는 `applicationId` 가 같고 **서명 인증서까지 같은** 앱끼리만 주고받는다. 한쪽만
 * 서명하면 릴리스에서 폰↔워치가 끊긴다. 실제로 `:wear` 에만 설정이 없어 `wear-release-unsigned.apk`
 * 가 나왔고, 그 APK 는 설치조차 되지 않았다(2026-09-11 릴리스 스모크). 모듈 스크립트마다 적으면
 * 또 갈라지므로 여기 한 곳에서 준다.
 *
 * 서명 정보가 없으면(PR CI 등) release 는 미서명으로 빌드되고 빌드 자체는 통과한다 —
 * Play 업로드 산출물은 로컬에서만 만든다(`docs/RELEASE.md`).
 */
internal fun Project.configureReleaseSigning(extension: ApplicationExtension) {
    val secrets = Properties().apply {
        val file = rootProject.file("secrets.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
    fun secret(key: String): String? = secrets.getProperty(key) ?: System.getenv(key)

    val storeFilePath = secret("RELEASE_STORE_FILE") ?: return

    // 공개 DSL 의 `signingConfigs` 는 `out ApkSigningConfig` 로 투영돼 있어 그대로는 create 가
    // 안 된다(`buildTypes` 와 달리 불변 오버로드가 없다). 컨테이너에 실제로 담기는 원소 타입이
    // ApkSigningConfig 라 캐스트해 쓴다.
    @Suppress("UNCHECKED_CAST")
    val signingConfigs = extension.signingConfigs as NamedDomainObjectContainer<ApkSigningConfig>

    signingConfigs.create(RELEASE) {
        storeFile = rootProject.file(storeFilePath)
        storePassword = secret("RELEASE_STORE_PASSWORD")
        keyAlias = secret("RELEASE_KEY_ALIAS")
        keyPassword = secret("RELEASE_KEY_PASSWORD")
    }
    extension.buildTypes {
        getByName(RELEASE).signingConfig = signingConfigs.getByName(RELEASE)
    }
}

private const val RELEASE = "release"
