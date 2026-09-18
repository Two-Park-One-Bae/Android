package app.nursemate.timer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import app.nursemate.core.timer.TimerAnalytics
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 시스템이 위젯을 잡는 손잡이.
 *
 * Glance 를 걷어내고 RemoteViews 로 직접 그린다 — 사정은 [PresetWidgetRenderer] 주석에 있다.
 *
 * ⚠️ **클래스 이름을 바꾸지 않는다.** 런처는 위젯을 컴포넌트 이름으로 붙들고 있어서, 이름이
 * 바뀌면 **놓여 있던 위젯을 통째로 버린다**(전환 중에 실제로 겪었다). Glance 시절의
 * `PresetWidgetReceiver` 를 그대로 쓰는 이유다.
 */
class PresetWidgetReceiver : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * ⚠️ **여기도 브로드캐스트다.** `goAsync` 없이 코루틴만 띄우면 `onReceive` 가 반환하는
     * 순간 프로세스가 회수 대상이 된다. 이 경로는 위젯을 처음 놓을 때·재부팅 후에 오는데,
     * **그 브로드캐스트 때문에 프로세스가 새로 뜨는** 상황이라 회수되기 쉽다. 렌더가 잘리면
     * 위젯이 초기 레이아웃(`nm_widget_loading`)인 채로 남는다.
     */
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                refresh(context, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != PresetWidgetTap.ACTION_START) return

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID_ID)
        val presetId = intent.getStringExtra(PresetWidgetTap.EXTRA_PRESET_ID) ?: return
        val pending = goAsync()

        scope.launch {
            try {
                start(context, appWidgetId, presetId)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 위젯이 지워지면 슬롯도 지운다 — Glance 상태와 달리 저절로 사라지지 않는다.
     *
     * [onUpdate] 와 같은 이유로 `goAsync` 가 필요하다. 잘리면 고아 슬롯이 남는다.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = context.timerWidgetEntryPoint().slotStore()
        val pending = goAsync()
        scope.launch {
            try {
                appWidgetIds.forEach { store.clear(it) }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 앱을 열지 않고 시작한다.
     *
     * ⚠️ **연타를 막는다.** 위젯은 눌러도 화면이 안 바뀌어 한 번 더 누르기 쉽다
     * (spec §위젯). 같은 프리셋을 짧은 시간 안에 두 번 누르면 두 번째는 넘긴다.
     */
    private suspend fun start(context: Context, appWidgetId: Int, presetId: String) {
        val entry = context.timerWidgetEntryPoint()
        val preset = when {
            // 설정에서 권한을 껐거나 울림 방식을 아직 안 골랐다. 다시 그리면 다음 탭이
            // 앱(관문)으로 간다 — 그 한 번은 헛탭이지만 "울릴 줄 알았는데 안 울린" 것보다 낫다.
            !entry.canStartWithoutApp() -> {
                Log.w(TAG, "권한이 없어 위젯 시작을 중단하고 다시 그린다 ($presetId)")
                refresh(context, intArrayOf(appWidgetId))
                null
            }

            !TapGuard.claim(appWidgetId) -> {
                Log.i(TAG, "방금 시작해 이 탭은 넘긴다 ($presetId)")
                null
            }

            else -> entry.presetRepository().presets.first().firstOrNull { it.id == presetId }
                ?: run {
                    Log.w(TAG, "없는 프리셋으로 시작 요청이 왔다 ($presetId)")
                    refresh(context, intArrayOf(appWidgetId))
                    null
                }
        }
        preset?.let {
            entry.timerRepository().start(it, TimerAnalytics.SOURCE_WIDGET)
            // 눌렀다는 표시. RemoteViews 는 동기라 여기서 그리면 곧바로 보인다.
            PresetWidgetRenderer.render(context, appWidgetId, it, ready = true, justStarted = true)
            scheduleRevert(context, appWidgetId)
        }
    }

    /**
     * 「시작됨」이 머문 뒤 원래 모습으로 돌린다.
     *
     * ⚠️ **리시버 안에서 기다리면 안 된다.** 브로드캐스트는 같은 리시버에 직렬로 전달돼,
     * 여기서 2초를 붙잡으면 **두 번째 탭이 그 2초 뒤에야 평가된다** — 연타를 막으려고 둔 창이
     * 정작 판정 시점에는 닫혀 있다(Glance 시절 실기기 로그로 확인한 실패다).
     */
    private fun scheduleRevert(context: Context, appWidgetId: Int) {
        val appContext = context.applicationContext
        scope.launch {
            delay(TapGuard.WINDOW_MS)
            refresh(appContext, intArrayOf(appWidgetId))
        }
    }

    /**
     * ⚠️ **여기서 코루틴을 새로 띄우지 않는다.** 띄우면 부르는 쪽이 잡아 둔 `goAsync` 밖으로
     * 새어 나가 렌더가 보호받지 못한다. 부르는 쪽이 기다리도록 `suspend` 로 둔다.
     */
    private suspend fun refresh(context: Context, appWidgetIds: IntArray) {
        val entry = context.timerWidgetEntryPoint()
        val ready = entry.canStartWithoutApp()
        val presets = entry.presetRepository().presets.first()
        val slots = entry.slotStore().slots.first()
        appWidgetIds.forEach { id ->
            val preset = presets.firstOrNull { it.id == slots[id] }
            PresetWidgetRenderer.render(context, id, preset, ready)
        }
    }

    private companion object {
        const val TAG = "NM443"
        const val INVALID_ID = AppWidgetManager.INVALID_APPWIDGET_ID
    }
}

/**
 * 같은 탭이 두 번 세어지지 않게 막는다.
 *
 * 브로드캐스트는 같은 리시버에 **직렬로** 전달되므로, 리시버가 기다리는 동안 두 번째 탭이
 * 밀린다. 그래서 창을 시각으로 재고 프로세스 메모리에만 둔다 — 연타는 한 프로세스에서 일어난다.
 *
 * ⚠️ **위젯 id 로 나눈다.** 프리셋 id 로 재면 같은 프리셋을 담은 **다른 위젯**을 누른 것까지
 * 막힌다. 위젯마다 다른 프리셋을 담는 건 권장일 뿐 강제가 아니고(`PresetWidgetSlot`),
 * 막으려는 것은 "눌러도 화면이 안 바뀌어 같은 위젯을 또 누르는" 경우다.
 */
private object TapGuard {

    private val recent = AtomicReference<Map<Int, Long>>(emptyMap())

    /**
     * @return 이 탭을 세어도 되면 true.
     *
     * ⚠️ **CAS 가 실패하면 다시 읽어 판단한다.** 예전에는 곧바로 false 를 돌려, 경합했을 뿐인
     * **정상 탭까지 삼켰다** — 다른 위젯을 누른 탭도 함께 사라졌다.
     */
    fun claim(appWidgetId: Int): Boolean {
        val now = System.currentTimeMillis()
        while (true) {
            val prev = recent.get()
            if (now - (prev[appWidgetId] ?: 0L) < WINDOW_MS) return false
            // 창이 지난 항목은 함께 버린다 — 위젯을 지워도 여기 남기 때문이다.
            val next = prev.filterValues { now - it < WINDOW_MS } + (appWidgetId to now)
            if (recent.compareAndSet(prev, next)) return true
        }
    }

    const val WINDOW_MS = 2_000L
}
