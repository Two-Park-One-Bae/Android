package app.nursemate.core.timer

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 타이머 계층의 기본 바인딩.
 *
 * [TimerAlarmScheduler] 는 여기서 묶지 않는다 — 구현이 AlarmManager 라 `:app` 에 있다.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TimerModule {

    @Binds
    @Singleton
    abstract fun bindTimerStore(impl: DataStoreTimerStore): TimerStore

    companion object {
        /** 실제 시계. 테스트는 [TimerRepository] 를 직접 만들며 다른 걸 넣는다. */
        @Provides
        @Singleton
        fun provideTimerClock(): TimerClock = TimerClock { System.currentTimeMillis() }
    }
}
