package app.nursemate.timer.sync

import android.content.Context
import app.nursemate.core.datalayer.TimerSyncTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `core:datalayer` 는 Hilt 를 쓰지 않는다 — 워치 모듈도 같은 클래스를 쓰는데 거기엔 Hilt 가
 * 없기 때문이다. 그래서 폰에서만 여기서 감싸 준다.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimerSyncModule {

    @Provides
    @Singleton
    fun provideTimerSyncTransport(@ApplicationContext context: Context): TimerSyncTransport =
        TimerSyncTransport(context)
}
