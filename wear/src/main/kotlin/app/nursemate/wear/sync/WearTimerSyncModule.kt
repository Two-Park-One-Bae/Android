package app.nursemate.wear.sync

import android.content.Context
import app.nursemate.core.datalayer.TimerSyncTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** `core:datalayer` 는 Hilt 를 쓰지 않아(폰·워치 둘 다 쓰는 모듈) 여기서 감싼다. */
@Module
@InstallIn(SingletonComponent::class)
object WearTimerSyncModule {

    @Provides
    @Singleton
    fun provideTimerSyncTransport(@ApplicationContext context: Context): TimerSyncTransport =
        TimerSyncTransport(context)
}
