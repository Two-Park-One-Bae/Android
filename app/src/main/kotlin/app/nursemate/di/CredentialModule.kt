package app.nursemate.di

import android.content.Context
import androidx.credentials.CredentialManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CredentialModule {

    /**
     * 생성에는 application 컨텍스트로 충분하다 — Activity 컨텍스트가 필요한 쪽은
     * `getCredential()` 호출 시점이라 그때 따로 넘긴다.
     */
    @Provides
    @Singleton
    fun provideCredentialManager(@ApplicationContext context: Context): CredentialManager =
        CredentialManager.create(context)
}
