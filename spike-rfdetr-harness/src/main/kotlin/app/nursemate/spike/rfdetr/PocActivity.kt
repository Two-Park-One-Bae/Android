package app.nursemate.spike.rfdetr

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NM-396 스파이크 PoC.
 *
 * 사진 선택 → 온디바이스 RF-DETR 추론 → BBOX 표시. 단계별 소요 시간을 화면에 띄운다.
 *
 * 합성 벤치마크 대신 이걸 만든 이유: 앞서 잰 숫자들이 출력 마샬링 오버헤드·발열·실행 순서에
 * 오염돼 계속 흔들렸다. 실제로 손으로 써보면서 재는 쪽이 "2초가 감내 가능한가"를 판단하는 데
 * 훨씬 낫다.
 *
 * 모델 준비 (APK에 넣지 않는다 — 125 MB):
 *   adb push rfdetr_seg_small.onnx /sdcard/Android/data/app.nursemate.spike.rfdetr/files/
 */
private const val RUNS = 3

/** adb logcat -s NM396:I 로 뽑는다. 측정이 목적인 앱이니 결과는 화면과 로그 양쪽에 남긴다. */
private const val TAG = "NM396"

class PocActivity : ComponentActivity() {

    private val state = MutableStateFlow<UiState>(UiState.Idle)
    private val provider = MutableStateFlow(ExecutionProvider.CPU)
    private val modelName = MutableStateFlow(PillDetector.MODEL_FP32)
    private var detectorModel: String? = null
    private var detector: PillDetector? = null
    private var detectorProvider: ExecutionProvider? = null
    private var modelLoadMs: Long = 0
    private var lastUri: Uri? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(::process) }

    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok -> if (ok) cameraUri?.let(::process) }

    private var cameraUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    PocScreen(
                        state = state.collectAsStateWithLifecycle().value,
                        provider = provider.collectAsStateWithLifecycle().value,
                        modelName = modelName.collectAsStateWithLifecycle().value,
                        onModelChange = { m ->
                            modelName.value = m
                            lastUri?.let(::process)
                        },
                        onProviderChange = { ep ->
                            provider.value = ep
                            lastUri?.let(::process)   // 같은 사진으로 즉시 재측정
                        },
                        onPickGallery = {
                            pickImage.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onTakePhoto = { launchCamera() },
                    )
                }
            }
        }
    }

    private fun launchCamera() {
        val file = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file,
        )
        cameraUri = uri
        takePhoto.launch(uri)
    }

    /** 모델은 한 번만 로드한다. 세션 생성이 수백 ms 걸린다. EP가 바뀌면 다시 만든다. */
    private fun ensureDetector(): PillDetector? {
        val want = provider.value
        val wantModel = modelName.value
        detector?.let {
            if (detectorProvider == want && detectorModel == wantModel) return it else it.close()
        }
        val file = File(getExternalFilesDir(null), wantModel)
        if (!file.isFile) return null
        val t = System.nanoTime()
        return PillDetector(file, want).also {
            modelLoadMs = (System.nanoTime() - t) / 1_000_000
            detector = it
            detectorProvider = want
            detectorModel = wantModel
        }
    }

    private fun process(uri: Uri) {
        lastUri = uri
        state.value = UiState.Running
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val det = ensureDetector()
                        ?: error(
                            "모델이 없습니다.\n\nadb push rfdetr_seg_small.onnx \\\n" +
                                "  ${getExternalFilesDir(null)}/",
                        )
                    // 추론에 필요한 건 576뿐이다. 2048까지 디코딩하면 그것만으로 ~490ms가 든다.
                    // 원본 해상도는 마스크 크롭을 뜰 때만 필요하므로 여기서는 읽지 않는다.
                    val t0 = System.nanoTime()
                    val bitmap = ImageLoader.loadForInference(this@PocActivity, uri)
                    val decodeMs = (System.nanoTime() - t0) / 1_000_000

                    // 첫 추론은 워밍업이라 느리다. 3회 돌려 warm 수치까지 같이 보여준다.
                    val runs = (1..RUNS).map { det.detect(bitmap, decodeMs) }
                    Triple(bitmap, runs.first(), runs.drop(1).map { it.timings.inferenceMs })
                }
            }
            state.value = result.fold(
                onSuccess = { (bitmap, r, warm) ->
                    logResult(r, warm)
                    UiState.Done(
                        bitmap, r, modelLoadMs, warm,
                        detector?.providerNote.orEmpty(), modelName.value,
                    )
                },
                onFailure = {
                    Log.e(TAG, "실패: ${it.message}", it)
                    UiState.Error(it.message ?: it.toString())
                },
            )
        }
    }

    /**
     * 사람이 읽는 줄 + 스프레드시트에 붙일 CSV 줄을 함께 남긴다.
     * 반복 측정할 때 스크린샷을 찍어 눈으로 옮겨적는 건 실수하기 쉽다.
     */
    private fun logResult(r: DetectionResult, warm: List<Long>) {
        val t = r.timings
        val ep = detector?.providerNote.orEmpty()
        Log.i(
            TAG,
            "model=${modelName.value} ep=${provider.value} " +
                "src=${r.imageWidth}x${r.imageHeight} model=${RfDetrSpec.INPUT_SIZE}x${RfDetrSpec.INPUT_SIZE} "+
                "dets=${r.detections.size} " +
                "total=${t.totalMs}ms decode=${t.decodeImageMs} pre=${t.preprocessMs} " +
                "infer=${t.inferenceMs} post=${t.postprocessMs} " +
                "warm=[${warm.joinToString(",")}] load=${modelLoadMs}ms ($ep)",
        )
        Log.i(
            TAG,
            "CSV,${modelName.value},${provider.value},${r.imageWidth}x${r.imageHeight}," +
                "${r.detections.size},${t.totalMs},${t.decodeImageMs},${t.preprocessMs}," +
                "${t.inferenceMs},${t.postprocessMs},${warm.joinToString("|")},$modelLoadMs," +
                r.detections.joinToString(";") {
                    "%.4f@%.4f,%.4f,%.4f,%.4f".format(it.score, it.x, it.y, it.width, it.height)
                },
        )
        r.detections.forEachIndexed { i, d ->
            Log.i(
                TAG,
                "  det[$i] score=%.4f box=%.4f,%.4f,%.4f,%.4f"
                    .format(d.score, d.x, d.y, d.width, d.height),
            )
        }
    }

    override fun onDestroy() {
        detector?.close()
        super.onDestroy()
    }
}

private sealed interface UiState {
    data object Idle : UiState
    data object Running : UiState
    data class Done(
        val bitmap: Bitmap,
        val result: DetectionResult,
        val modelLoadMs: Long,
        val warmInferenceMs: List<Long>,
        val providerNote: String,
        val modelLabel: String,
    ) : UiState
    data class Error(val message: String) : UiState
}

@Composable
private fun PocScreen(
    state: UiState,
    provider: ExecutionProvider,
    modelName: String,
    onModelChange: (String) -> Unit,
    onProviderChange: (ExecutionProvider) -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("RF-DETR-Seg 온디바이스 PoC", style = MaterialTheme.typography.titleLarge)
        Text(
            "NM-396 · ONNX Runtime · 576×576 · 4 threads",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTakePhoto, modifier = Modifier.weight(1f)) { Text("촬영") }
            Button(onClick = onPickGallery, modifier = Modifier.weight(1f)) { Text("갤러리") }
        }

        Text("모델", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PillDetector.MODEL_FP32 to "fp32 (125MB)",
                PillDetector.MODEL_FP16 to "fp16 (63MB)",
                PillDetector.MODEL_INT8 to "INT8 (35MB)",
            ).forEach { (file, label) ->
                FilterChip(
                    selected = modelName == file,
                    onClick = { onModelChange(file) },
                    label = { Text(label) },
                )
            }
        }

        Text("실행 백엔드 (바꾸면 같은 사진으로 재측정)", style = MaterialTheme.typography.bodySmall)
        // 칩이 5개라 Row에 두면 화면 밖으로 밀린다. 줄바꿈되게 한다.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExecutionProvider.entries.forEach { ep ->
                FilterChip(
                    selected = provider == ep,
                    onClick = { onProviderChange(ep) },
                    label = { Text(ep.label) },
                )
            }
        }

        when (state) {
            UiState.Idle -> Text(
                "사진을 고르면 알약을 검출하고 소요 시간을 표시합니다.",
                style = MaterialTheme.typography.bodyMedium,
            )

            UiState.Running -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text("추론 중…")
            }

            is UiState.Error -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )

            is UiState.Done -> {
                TimingCard(state)
                DetectionOverlay(state.bitmap, state.result.detections)
            }
        }
    }
}

@Composable
private fun TimingCard(state: UiState.Done) {
    val t = state.result.timings
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F4F6))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "검출 ${state.result.detections.size}개  ·  원본 ${state.result.imageWidth}×${state.result.imageHeight} → 모델 ${RfDetrSpec.INPUT_SIZE}×${RfDetrSpec.INPUT_SIZE}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "총 ${t.totalMs} ms",
            style = MaterialTheme.typography.headlineSmall,
        )
        Row {
            Column(Modifier.weight(1f)) {
                Mono("이미지 디코딩", t.decodeImageMs)
                Mono("전처리", t.preprocessMs)
            }
            Column(Modifier.weight(1f)) {
                Mono("추론", t.inferenceMs)
                Mono("후처리", t.postprocessMs)
            }
        }
        if (state.warmInferenceMs.isNotEmpty()) {
            Text(
                "추론 warm: ${state.warmInferenceMs.joinToString(" / ") { "$it" }} ms " +
                    "(첫 회 ${t.inferenceMs} ms는 워밍업 포함)",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
        Text(
            "${state.providerNote}  ·  ${state.modelLabel}  ·  로드 ${state.modelLoadMs} ms",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
        state.result.detections.forEachIndexed { i, d ->
            Text(
                "  #$i  score ${"%.3f".format(d.score)}  " +
                    "box(${"%.3f".format(d.x)}, ${"%.3f".format(d.y)}, " +
                    "${"%.3f".format(d.width)}, ${"%.3f".format(d.height)})",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun Mono(label: String, ms: Long) {
    Text(
        "$label  $ms ms",
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
    )
}

/**
 * 원본 위에 BBOX를 그린다.
 *
 * 좌표는 **0~1 정규화, 좌상단 원점**이다. 전처리에서 y를 뒤집지 않았으므로 후처리에서도
 * 뒤집지 않는다 — 여기서 화면에 그릴 때도 그대로 곱하기만 하면 된다.
 * (iOS는 입력을 미러링해 넣는 바람에 후처리 4곳에서 되돌린다. 그 관례를 따르지 않았다.)
 */
@Composable
private fun DetectionOverlay(bitmap: Bitmap, detections: List<Detection>) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio),
    ) {
        Image(
            bitmap = image,
            contentDescription = "검출 결과",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Canvas(Modifier.fillMaxSize()) {
            detections.forEach { d ->
                drawRect(
                    color = Color(0xFF00E050),
                    topLeft = Offset(d.x * size.width, d.y * size.height),
                    size = Size(d.width * size.width, d.height * size.height),
                    style = Stroke(width = 5f),
                )
            }
        }
    }
}
