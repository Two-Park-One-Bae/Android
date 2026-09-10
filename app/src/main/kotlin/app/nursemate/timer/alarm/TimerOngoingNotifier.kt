package app.nursemate.timer.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.nursemate.core.model.CareTimer
import app.nursemate.core.timer.TimerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 진행 중인 타이머를 알림 하나로 계속 비춘다 — spec §앱 밖 진행 중 표시.
 *
 * ## 매초 다시 그리지 않는다
 * 남은 시간은 알림의 크로노미터가 시스템 쪽에서 흐른다([TimerOngoingNotification]).
 * 그래서 **목록의 모양이 바뀔 때만** 다시 그린다 — 시작·정지·일시정지·만료. 매초 갱신하면
 * 배터리만 먹고 알림이 깜빡인다.
 *
 * ## 무엇이 "모양이 바뀐 것"인가
 * id·상태·만료 시각·남은 시간의 묶음이다([timerRenderSignature]). 메모를 고치는 것처럼
 * 알림에 안 드러나는 변화로는 다시 그리지 않는다.
 */
@Singleton
class TimerOngoingNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: TimerRepository
) {

    /** 앱이 살아 있는 동안 목록을 따라간다. Application 에서 한 번 부른다. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            repository.timers
                .map { timers -> timers to timerRenderSignature(timers) }
                .distinctUntilChanged { old, new -> old.second == new.second }
                .collect { (timers, _) -> render(timers) }
        }
    }

    // 권한은 아래에서 검사한다. lint 가 호출 지점을 따라가지 못해 오탐을 낸다.
    @SuppressLint("MissingPermission")
    private fun render(timers: List<CareTimer>) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val notification = TimerOngoingNotification.build(context, timers, System.currentTimeMillis())

        when {
            // 보여 줄 타이머가 없으면 내린다.
            notification == null -> manager.cancel(TimerOngoingNotification.ID)

            canPostNotifications() ->
                NotificationManagerCompat.from(context).notify(TimerOngoingNotification.ID, notification)

            else -> Log.w(TAG, "알림 권한이 없어 진행 중 표시를 띄우지 못했다")
        }
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "NM442"
    }
}

/**
 * 알림에 드러나는 것만 모은다 — 이게 그대로면 다시 그릴 이유가 없다.
 *
 * ⚠️ **`remainingSeconds` 를 빼면 안 된다.** 일시정지 중에 [+1분] 을 누르면 그 값만 바뀌고
 * `state`·`endAt` 은 그대로다. 그런데 정렬의 마지막 키가 남은 시간이라 순서는 뒤집힌다 —
 * 시그니처가 같으면 알림이 옛 lead 를 붙들고 **조작 버튼이 엉뚱한 타이머를 가리킨다.**
 *
 * 「여러 개 동시 진행」에서 고친 것과 같은 실패다. 거기서는 *시간이 흐르는* 경로를 묶음
 * 분리로 막았는데, *값을 고치는* 경로가 남아 있었다.
 *
 * 클래스 밖에 두는 이유는 **테스트가 직접 확인할 수 있게** 하기 위해서다. private 로 두면
 * 회귀를 도메인 테스트로 에둘러 지킬 수밖에 없다.
 */
internal fun timerRenderSignature(timers: List<CareTimer>): String = timers.joinToString("|") {
    "${it.id}:${it.state}:${it.endAtEpochMillis}:${it.remainingSeconds}"
}
