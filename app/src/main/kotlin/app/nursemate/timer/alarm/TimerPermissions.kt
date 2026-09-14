package app.nursemate.timer.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 타이머가 울리기 위해 필요한 권한 셋.
 *
 * ## 왜 셋인가
 * iOS 는 「알람 권한」 하나인데 Android 는 셋으로 쪼개진다.
 * - **정확 알람**(`SCHEDULE_EXACT_ALARM`) — 없으면 만료 시각에 예약 자체를 못 한다
 * - **알림**(`POST_NOTIFICATIONS`, Android 13+) — 예약은 되지만 울릴 때 표시를 못 띄운다
 * - **전체 화면 알림**(`USE_FULL_SCREEN_INTENT`, Android 14+) — 없으면 **잠금화면에
 *   알람 화면이 뜨지 않는다.** spec §만료·알람이 "잠금: 풀스크린"을 요구하므로 이것도
 *   빠지면 요건 미달이다.
 *
 * 하나만 없어도 "울릴 줄 알았는데 안 울린" 상태가 되므로, 타이머 시작은 **셋 다**
 * 있을 때만 허용한다(spec §알람 권한: 권한이 없으면 시작할 수 없다).
 *
 * ## 정확 알람은 팝업이 없다
 * 카메라 같은 런타임 권한이 아니라 **특별한 앱 접근 권한**이라, 앱이 할 수 있는 건 시스템
 * 설정 화면을 여는 것뿐이다([exactAlarmSettings]). 사용자가 토글을 켜고 돌아와야 한다.
 * 알림 권한만 런타임 팝업으로 받는다.
 */
@Singleton
class TimerPermissions @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun canScheduleExact(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
    } else {
        true
    }

    fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    /**
     * 잠금화면에 알람 화면을 띄울 수 있는가.
     *
     * ⚠️ **매니페스트 선언만으로는 안 된다.** Android 14 부터 이 권한은 알람·통화 앱에만
     * 자동으로 주어지고 나머지는 사용자가 설정에서 켜야 한다. 없으면 시스템이 헤드업으로
     * 낮춰 표시해 **잠금화면에서는 아무것도 못 본다**(실기기 `appops` 에 `rejectTime` 이 찍힌다).
     */
    fun canUseFullScreenIntent(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() == true
    } else {
        true
    }

    /** 전체 화면 알림 토글이 있는 시스템 설정 화면. 우리 앱 전용 페이지로 바로 열린다. */
    fun fullScreenIntentSettings(): Intent {
        val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, appUri())
        } else {
            null
        }
        val resolvable = direct?.resolveActivity(context.packageManager) != null
        return if (direct != null && resolvable) direct else appDetailsSettings()
    }

    /** 타이머를 시작해도 되는가. */
    fun allGranted(): Boolean = canScheduleExact() && canPostNotifications() && canUseFullScreenIntent()

    /**
     * 정확 알람 토글이 있는 시스템 설정 화면.
     *
     * `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 은 **우리 앱 전용 페이지**로 바로 열려 토글
     * 하나만 보인다. 기기에 그 화면이 없으면 앱 상세 설정으로 떨어뜨린다.
     */
    fun exactAlarmSettings(): Intent {
        val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri())
        } else {
            null
        }
        val resolvable = direct?.resolveActivity(context.packageManager) != null
        return if (direct != null && resolvable) direct else appDetailsSettings()
    }

    /** 권한을 영구 거부한 뒤 되돌리는 길 — 앱 상세 설정. */
    fun appDetailsSettings(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri())

    private fun appUri(): Uri = Uri.fromParts("package", context.packageName, null)
}
