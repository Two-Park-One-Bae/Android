package app.nursemate.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.nursemate.core.model.TimerCommand
import app.nursemate.wear.R
import app.nursemate.wear.sync.WearTimerStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

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
 * 워치는 프리셋을 만들지 않는다. 폰이 보낸 스냅샷을 [WearTimerStore] 가 들고 있고, 프로세스가
 * 죽어 비어 있으면 [WearTimerStore.restore] 로 마지막 스냅샷을 다시 읽는다.
 */
@AndroidEntryPoint
class PresetTileService : TileService() {

    @Inject lateinit var store: WearTimerStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            requestParams.currentState.lastClickableId
                .takeIf { it.isNotEmpty() }
                ?.let { store.send(TimerCommand.Start(it)) }

            if (store.snapshot.value == null) store.restore()
            val presets = store.snapshot.value?.presets.orEmpty()

            val layout = materialScope(this@PresetTileService, requestParams.deviceConfiguration) {
                presetTileLayout(presets, ::startClickable)
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

    private fun startClickable(presetId: String): Clickable = Clickable.Builder()
        .setId(presetId)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    private companion object {
        const val RESOURCES_VERSION = "1"

        /** 프리셋은 폰에서 바뀔 때만 변한다 — 시스템이 굳이 자주 물어볼 필요가 없다. */
        const val FRESHNESS_MS = 60 * 60 * 1000L
    }
}
