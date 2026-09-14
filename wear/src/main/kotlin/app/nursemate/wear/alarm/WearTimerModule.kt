package app.nursemate.wear.alarm

import app.nursemate.core.model.ORIGIN_WATCH
import app.nursemate.core.timer.ReplicaOrigin
import app.nursemate.core.timer.TimerAlarmScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 워치가 `core:timer` 에 자기 사정을 알려 주는 자리. 폰의 `TimerAlarmModule` 과 짝이다. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WearTimerModule {

    @Binds
    @Singleton
    abstract fun bindScheduler(impl: WearTimerAlarmScheduler): TimerAlarmScheduler

    companion object {
        /** 복제본에서 이 앱은 「워치」다. 판이 같을 때 폰을 이기는 쪽이기도 하다. */
        @Provides
        @ReplicaOrigin
        fun provideReplicaOrigin(): String = ORIGIN_WATCH
    }
}
