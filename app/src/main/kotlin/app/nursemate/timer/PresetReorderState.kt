package app.nursemate.timer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nursemate.core.model.TimerPreset
import kotlin.math.roundToInt

/**
 * 드래그 정렬의 상태.
 *
 * ## 왜 순서를 상태로 들고 있지 않는가
 * 처음에는 이동할 때마다 목록 상태를 갈아치우고, 다음 이동을 계산할 때 **그 상태를 다시
 * 읽었다.** 그런데 상태 읽기는 재구성 뒤에야 새 값이 되고 포인터 이벤트는 한 프레임에
 * 여러 번 온다. 두 번째 이벤트가 아직 옛 순서를 읽어 같은 이동을 또 계산하면, 행이 제자리에서
 * 위아래로 아주 빠르게 떨렸다.
 *
 * 그래서 **끌린 누적 거리 하나만** 상태로 두고, 순서와 화면 오프셋을 그 거리의 함수로 계산한다.
 * 같은 거리에 늘 같은 결과가 나오므로 이벤트가 몇 번 오든, 프레임이 밀리든 결과가 같다.
 */
@Stable
internal class PresetReorderState {

    /** 드래그를 시작한 시점의 순서. 계산의 기준이라 끄는 동안 바뀌지 않는다. */
    private var base by mutableStateOf<List<TimerPreset>>(emptyList())

    /** 쥔 행의 시작 자리. `-1` 이면 끌고 있지 않다. */
    private var startIndex by mutableIntStateOf(NOT_DRAGGING)

    /** 시작점에서 끌린 누적 거리. 이것만이 진짜 상태다. */
    private var totalDrag by mutableFloatStateOf(0f)

    /** 손을 뗀 뒤 0 으로 되돌아가는 잔여 오프셋. */
    private var settleOffset by mutableFloatStateOf(0f)

    /** 되돌림이 끝날 때까지 이 행을 위에 띄워 둔다. */
    var settlingId by mutableStateOf<String?>(null)
        private set

    /** 행 하나가 차지하는 높이(간격 포함). 행이 측정되면 채워진다. */
    var step by mutableFloatStateOf(0f)

    /** 저장이 돌아오기 전까지 화면에 유지할 순서. */
    var committed by mutableStateOf<List<TimerPreset>>(emptyList())

    val dragging: Boolean get() = startIndex >= 0

    /** 지금 그려야 할 순서. */
    val order: List<TimerPreset> get() = if (dragging) base.moved(startIndex, targetIndex) else committed

    /** 쥐고 있거나 되돌아가는 중인 행. */
    val heldId: String? get() = if (dragging) base.getOrNull(startIndex)?.id else settlingId

    /** 쥔 행에 줄 세로 오프셋. 옮긴 칸만큼 뺀 나머지라 손가락과 행이 붙어 있다. */
    val offset: Float get() = if (dragging) totalDrag - (targetIndex - startIndex) * step else settleOffset

    private val targetIndex: Int
        get() {
            if (!dragging) return NOT_DRAGGING
            val shift = if (step > 0f) (totalDrag / step).roundToInt() else 0
            return (startIndex + shift).coerceIn(0, base.lastIndex.coerceAtLeast(0))
        }

    /** 저장소에서 새 목록이 오면 기준을 맞춘다. 끄는 도중에는 건드리지 않는다. */
    fun sync(presets: List<TimerPreset>) {
        if (!dragging) committed = presets
    }

    fun start(presetId: String) {
        base = committed
        startIndex = committed.indexOfFirst { it.id == presetId }
        totalDrag = 0f
    }

    fun drag(delta: Float) {
        totalDrag += delta
    }

    /**
     * 손을 뗀다. 확정된 순서를 돌려주고, 남은 어긋남은 [settle] 이 되돌린다.
     *
     * 순서를 먼저 확정해 두는 이유는 되돌림이 [totalDrag] 를 줄이는 동안 목표 자리가 따라
     * 바뀌면 안 되기 때문이다.
     */
    fun finish(): List<TimerPreset> {
        if (!dragging) return committed
        val result = order
        settlingId = heldId
        settleOffset = offset
        committed = result
        startIndex = NOT_DRAGGING
        totalDrag = 0f
        return result
    }

    /** 놓은 자리에서 제자리까지 부드럽게 되돌린다. 즉시 0 으로 만들면 툭 튄다. */
    suspend fun settle() {
        animate(settleOffset, 0f, animationSpec = SettleSpec) { value, _ -> settleOffset = value }
        settlingId = null
    }

    fun cancelSettle() {
        settleOffset = 0f
        settlingId = null
    }

    private companion object {
        const val NOT_DRAGGING = -1
        val SettleSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> = if (from == to || from !in indices || to !in indices) {
    this
} else {
    toMutableList().apply { add(to, removeAt(from)) }
}
