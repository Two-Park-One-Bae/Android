package app.nursemate.wear.sync

import android.util.Log
import androidx.wear.tiles.TileService
import app.nursemate.core.datalayer.DataLayerPaths
import app.nursemate.core.model.decodePresets
import app.nursemate.wear.tile.PresetTileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 폰이 보낸 프리셋 목록을 받는다.
 *
 * 타이머는 복제본([TimerReplicaListenerService])으로 오간다. 프리셋은 편집이 폰 전용이라
 * (spec §워치) 한 방향이고, 그 통로가 여기다.
 *
 * 앱이 꺼져 있어도 시스템이 이 서비스를 깨워 전달한다.
 */
@AndroidEntryPoint
class PresetListenerService : WearableListenerService() {

    @Inject lateinit var store: WearPresetStore

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { events ->
            events.forEach { event ->
                // ⚠️ **삭제 이벤트는 건너뛴다.** DELETED 의 DataItem 은 값이 비어 있어,
                // 읽으려 들면 빈 스냅샷으로 화면을 지우게 된다.
                if (event.type != DataEvent.TYPE_CHANGED) return@forEach
                if (event.dataItem.uri.path != DataLayerPaths.PRESET_SNAPSHOT) return@forEach

                val json = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap.getString(DataLayerPaths.KEY_PRESET_JSON)
                val snapshot = json?.let(::decodePresets)
                if (snapshot == null) {
                    Log.w(TAG, "읽을 수 없는 프리셋 목록을 버렸다")
                    return@forEach
                }
                store.offer(snapshot)

                // 폰에서 프리셋이 바뀌면 타일도 따라가야 한다. 타일은 우리가 그리는 게 아니라
                // 런처가 그리므로, 다시 물어봐 달라고 알리는 수밖에 없다.
                TileService.getUpdater(this).requestUpdate(PresetTileService::class.java)
            }
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
