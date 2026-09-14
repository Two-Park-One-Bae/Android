import app.nursemate.buildlogic.configureKotlinAndroid
import app.nursemate.buildlogic.configureReleaseSigning
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

abstract class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                // AGP 9: 미지정 시 compileSdk를 따라가므로 반드시 명시
                defaultConfig.targetSdk = 36
                testOptions.animationsDisabled = true
                // 폰·워치가 같은 키로 서명돼야 Data Layer 가 붙는다 — 모듈이 아니라 여기서 준다.
                configureReleaseSigning(this)
            }
        }
    }
}
