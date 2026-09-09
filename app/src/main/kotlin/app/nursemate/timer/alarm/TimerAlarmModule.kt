package app.nursemate.timer.alarm

import app.nursemate.core.data.timer.TimerAlarmScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 알람 예약 구현을 `core:data` 의 계약에 묶는다. AlarmManager 를 아는 건 `:app` 뿐이다. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TimerAlarmModule {

    @Binds
    @Singleton
    abstract fun bindScheduler(impl: AlarmManagerTimerScheduler): TimerAlarmScheduler
}
