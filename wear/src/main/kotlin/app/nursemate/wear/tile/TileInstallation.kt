package app.nursemate.wear.tile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await

/**
 * 타일을 붙이라고 권할 때인지 판단하고, 「타일 추가」 화면까지 데려다준다.
 *
 * ## 앱이 타일을 직접 꽂을 수는 없다
 * [TileService] 의 공개 API 는 갱신([TileService.getUpdater])·조회
 * ([TileService.getActiveTilesAsync])·감지(`onTileAddEvent`)뿐이고 **추가하는 API 가 없다.**
 * 그래서 여기서 하는 일은 목록을 대신 열어 주는 것까지고, 마지막 한 번은 사용자가 누른다.
 *
 * 플랫폼에는 있다 — `com.google.wear.services.tiles.TilesManager` 의 `addTile`·`canAddTile`.
 * 다만 `com.google.wear.permission.MANAGE_TILES` 를 요구하고 그 권한이 `signature|privileged`
 * 라 **서드파티 앱은 받을 수 없다**(실기기에서 리플렉션으로 불러 확인 — `SecurityException`).
 * 실제로 그 권한을 가진 건 플레이 스토어 같은 시스템 앱이다. 우회 대상이 아니라 설계 제약이다.
 *
 * ## 여는 방법이 표준이 아니다
 * [ACTION_SHOW_TILE_ADDABLE] 은 삼성 Wear 시스템 UI 가 여는 액션이다(갤럭시 워치 실기기에서
 * 확인). 다른 워치에는 없을 수 있어 [addTileIntent] 가 먼저 확인하고, 없으면 null 을 준다 —
 * 화면은 그때 버튼을 내리고 말로만 안내한다.
 */
@Singleton
class TileInstallation @Inject constructor(@ApplicationContext private val context: Context) {

    private val component = ComponentName(context, PresetTileService::class.java)

    /**
     * 지금 권해도 되는가 — **아직 안 물었고, 아직 안 붙어 있을 때만** true.
     *
     * 둘 중 하나만 봐서는 안 된다. 물어본 적이 없어도 사용자가 스스로 붙였을 수 있고,
     * 붙어 있지 않아도 한 번 거절했으면 다시 묻지 않아야 한다.
     */
    suspend fun shouldOfferAdd(): Boolean {
        val asked = asked()
        val placed = if (asked) false else isPlaced()
        // 안 뜨는 이유를 밖에서 알 수 없어(둘 다 "조용히 아무 일 없음") 판단 근거를 남긴다.
        Log.i(TAG, "타일 안내 판단 물어봤나=$asked 붙어있나=$placed")
        return !asked && !placed
    }

    /**
     * 한 번 물었다고 적는다. [추가하기]·[나중에] 어느 쪽을 눌러도 적는다.
     *
     * 「추가하기」 를 눌러도 실제로 붙였는지는 알 수 없는데(목록을 열어 줄 뿐이다),
     * 그렇다고 다시 묻지는 않는다 — 안 붙였다면 그건 사용자의 선택이다.
     */
    suspend fun markAsked() {
        context.tilePrompt.edit { it[KEY_ASKED] = true }
    }

    /** 「타일 추가」 목록을 여는 인텐트. 이 워치가 그 화면을 안 열어 주면 null. */
    fun addTileIntent(): Intent? {
        val intent = Intent(ACTION_SHOW_TILE_ADDABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }

    private suspend fun asked(): Boolean = context.tilePrompt.data.first()[KEY_ASKED] == true

    /**
     * 이 앱의 타일이 이미 캐러셀에 있는가.
     *
     * ⚠️ **실패하면 "붙어 있다"로 친다.** 조회가 안 될 때 안 붙은 것으로 보면, 이미 타일을
     * 쓰고 있는 사용자에게 붙이라고 권하게 된다 — 안 권해서 놓치는 쪽이 덜 나쁘다.
     */
    private suspend fun isPlaced(): Boolean = runCatching {
        TileService.getActiveTilesAsync(context, Dispatchers.IO.asExecutor()).await()
            .any { it.componentName == component }
    }.getOrElse {
        Log.w(TAG, "타일 조회 실패 — 붙어 있는 것으로 친다", it)
        true
    }

    private companion object {
        /** 삼성 Wear 시스템 UI 의 「타일 추가」 화면. 표준 액션이 아니다. */
        const val ACTION_SHOW_TILE_ADDABLE = "com.samsung.android.wearable.sysui.ACTION_SHOW_TILE_ADDABLE"

        const val TAG = "TileInstallation"

        val KEY_ASKED = booleanPreferencesKey("tile_prompt_asked")
    }
}

private val Context.tilePrompt: DataStore<Preferences> by preferencesDataStore("tile_prompt")
