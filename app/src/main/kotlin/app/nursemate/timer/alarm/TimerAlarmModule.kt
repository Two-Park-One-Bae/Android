package app.nursemate.timer.alarm

import app.nursemate.core.model.ORIGIN_PHONE
import app.nursemate.core.timer.ReplicaOrigin
import app.nursemate.core.timer.TimerAlarmScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 알람 예약 구현을 `core:timer` 의 계약에 묶는다. AlarmManager 를 아는 건 `:app` 뿐이다. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TimerAlarmModule {

    @Binds
    @Singleton
    abstract fun bindScheduler(impl: AlarmManagerTimerScheduler): TimerAlarmScheduler

    companion object {
        /** 복제본에서 이 앱은 「폰」이다. 워치 앱이 같은 자리에 자기 이름을 넣는다. */
        @Provides
        @ReplicaOrigin
        fun provideReplicaOrigin(): String = ORIGIN_PHONE
    }
}
