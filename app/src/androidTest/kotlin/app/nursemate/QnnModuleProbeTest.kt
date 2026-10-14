package app.nursemate

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **조건부 feature module 이 이 설치본에 실제로 들어왔는가.**
 *
 * `:qnn_core` 와 세대 모듈은 `device_targeting_configuration.xml` 의 `system-on-chip` 조건으로
 * 전달된다. 그런데 이 길에는 **조용히 틀릴 자리가 두 군데** 있다.
 *
 * 1. 설치 쪽이 SoC 조건을 안 보면 모듈이 **통째로 빠진다.** 그래도 앱은 뜨고 테스트는 통과한다 —
 *    QNN 만 안 붙을 뿐이다. Test Lab 의 device spec 에 SoC 가 실리는지가 미확인이라 먼저 본다.
 * 2. 모듈이 들어와도 `.so` 가 **압축된 채**면 cDSP 가 skel 을 파일로 못 연다(errno 2). EP 는
 *    노드를 하나도 안 가져가고 **CPU 로 돌면서 성공한다.**
 *
 * 그래서 단정하지 않고 **전부 찍는다.** 판단은 숫자를 보고 사람이 한다.
 */
@RunWith(AndroidJUnit4::class)
class QnnModuleProbeTest {

    @Test
    fun `설치된_스플릿과_네이티브_라이브러리를_기록한다`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.applicationInfo
        val lines = mutableListOf<String>()

        lines += "패키지     ${context.packageName}"
        lines += "SoC        ${Build.SOC_MODEL} · ${Build.HARDWARE} · ${Build.MODEL}"

        val splits = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).splitNames?.toList()
        }.getOrNull()
        lines += "splitNames ${splits?.takeIf { it.isNotEmpty() }?.joinToString() ?: "**없음**"}"
        lines += "splitDirs  ${info.splitSourceDirs?.size ?: 0}개"
        info.splitSourceDirs?.forEach { lines += "  split   $it" }

        // ⚠️ 여기가 핵심이다. `.so` 가 **실파일로** 풀려야 cDSP 가 연다.
        val libDir = File(info.nativeLibraryDir)
        lines += "libDir     ${libDir.path} (존재 ${libDir.isDirectory})"
        val libs = libDir.listFiles()?.sortedBy { it.name }.orEmpty()
        lines += "libDir 안  ${libs.size}개"
        libs.forEach { lines += "  so      ${it.name} ${it.length()}바이트" }

        val qnn = libs.filter { it.name.startsWith("libQnn") }
        lines += "QNN so     ${qnn.size}개 ${qnn.joinToString { it.name }.ifEmpty { "**없음**" }}"

        val marker = "ai.onnxruntime.qnnpluginep.QnnPluginEpLibraryKt"
        lines += "플러그인EP ${runCatching { Class.forName(marker) }.fold({ "로드됨" }, { "**없음** ${it.javaClass.simpleName}" })}"

        // `FLAG_EXTRACT_NATIVE_LIBS` 는 공개 상수가 아니라 비트를 직접 본다 — 1 shl 28.
        val extract = (info.flags ushr 28) and 1
        lines += "extractNativeLibs $extract (flags 0x${info.flags.toString(16)})"

        lines.forEach { Log.i(TAG, it) }
    }

    private companion object {
        const val TAG = "NM-QNNMOD"
    }
}
