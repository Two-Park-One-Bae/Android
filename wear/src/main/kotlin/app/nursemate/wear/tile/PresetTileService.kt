package app.nursemate.wear.tile

import android.content.ComponentName
import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.nursemate.core.timer.TimerRepository
import app.nursemate.wear.MainActivity
import app.nursemate.wear.R
import app.nursemate.wear.sync.WearPresetStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * 워치 페이스에서 위로 쓸어 올리면 나오는 프리셋 시작 타일(Smart Stack).
 *
 * ## 탭을 어떻게 받는가
 * ProtoLayout 의 클릭은 액티비티를 띄우거나([ActionBuilders.LaunchAction]) **타일을 다시
 * 요청하게 하는 것**([ActionBuilders.LoadAction]) 둘뿐이다. 앱을 열지 않고 시작해야 하므로
 * 후자를 쓴다 — 누른 셀의 id 가 [RequestBuilders.TileRequest.getCurrentState] 에 실려 돌아오고,
 * 여기서 그 프리셋을 시작한 뒤 새 화면을 그려 준다.
 *
 * ## 프리셋은 폰에서 온다
 * 워치는 프리셋을 만들지 않는다. 폰이 보낸 스냅샷을 [WearPresetStore] 가 들고 있고, 프로세스가
 * 죽어 비어 있으면 [WearPresetStore.restore] 로 마지막 스냅샷을 다시 읽는다.
 */
@AndroidEntryPoint
class PresetTileService : TileService() {

    @Inject lateinit var store: WearPresetStore

    @Inject lateinit var repository: TimerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            val clicked = requestParams.currentState.lastClickableId
                .takeIf { it.isNotEmpty() && it != MORE_ID }
            if (store.snapshot.value == null) store.restore()
            val presets = store.snapshot.value?.presets.orEmpty()

            // 타일에서도 워치가 직접 시작한다 — 폰에 묻지 않는다.
            if (clicked != null && claimStart(clicked)) {
                presets.firstOrNull { it.id == clicked }?.let { repository.start(it) }
                scheduleRevert()
            }

            val layout = materialScope(this@PresetTileService, requestParams.deviceConfiguration) {
                presetTileLayout(presets, justStarted(), ::startClickable, ::moreClickable)
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = Futures.immediateFuture(
        ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(
                ICON_TIMER,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.nm_ic_timer)
                            .build()
                    )
                    .build()
            )
            .build()
    )

    /**
     * 같은 탭이 두 번 세어지지 않게 막는다.
     *
     * 타일은 눌러도 화면이 그대로라 한 번 더 누르기 쉽다 — 위젯에서 겪은 것과 같은 구멍이라
     * 같은 방식으로 막는다. **"지금부터 [FEEDBACK_MS] 동안은 내가 시작한다"를 한 번의 원자적
     * 교체로 선점**하고, 찍는 값이 시각이라 시간이 지나면 저절로 풀린다.
     */
    private fun claimStart(presetId: String): Boolean {
        val now = System.currentTimeMillis()
        val claimed = recentStart.get()
        if (claimed != null && claimed.first == presetId && now - claimed.second < FEEDBACK_MS) {
            Log.i(TAG, "방금 시작해 이 탭은 넘긴다 ($presetId)")
            return false
        }
        return recentStart.compareAndSet(claimed, presetId to now)
    }

    /** 방금 시작한 프리셋 — [FEEDBACK_MS] 동안 「시작됨」으로 보여 준다. */
    private fun justStarted(): String? = recentStart.get()
        ?.takeIf { System.currentTimeMillis() - it.second < FEEDBACK_MS }
        ?.first

    /** 피드백이 끝나면 원래 모습으로 돌려 놓는다 — 타일은 스스로 다시 그리지 않는다. */
    private fun scheduleRevert() {
        scope.launch {
            delay(FEEDBACK_MS)
            getUpdater(applicationContext).requestUpdate(PresetTileService::class.java)
        }
    }

    private fun moreClickable(): Clickable = Clickable.Builder()
        .setId(MORE_ID)
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(ComponentName(this, MainActivity::class.java).className)
                        .addKeyToExtraMapping(
                            MainActivity.EXTRA_OPEN_PRESETS,
                            ActionBuilders.booleanExtra(true)
                        )
                        .build()
                )
                .build()
        )
        .build()

    private fun startClickable(presetId: String): Clickable = Clickable.Builder()
        .setId(presetId)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    private companion object {
        const val RESOURCES_VERSION = "1"

        /** [더 보기] 는 프리셋 id 가 아니다 — 시작 명령으로 새어 들어가면 안 된다. */
        const val MORE_ID = "__more__"

        /** 눌렀다는 표시를 유지하는 시간. 위젯과 같게 둔다. */
        const val FEEDBACK_MS = 2_000L

        const val TAG = "NM445Tile"

        /** 연타는 한 프로세스 안에서 일어난다 — 디스크까지 갈 것 없다. */
        val recentStart = AtomicReference<Pair<String, Long>?>(null)

        /** 프리셋은 폰에서 바뀔 때만 변한다 — 시스템이 굳이 자주 물어볼 필요가 없다. */
        const val FRESHNESS_MS = 60 * 60 * 1000L
    }
}
