package ir.tvgram.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The process-lifetime scope the Telegram client's update pump runs on. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelegramScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @TelegramScope
    fun provideTelegramScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
