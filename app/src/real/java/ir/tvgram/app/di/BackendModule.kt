package ir.tvgram.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.tvgram.tdlib.TdlibTelegramClient
import ir.tvgram.telegram.TelegramClient
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** `real` flavour: talk to Telegram through TDLib. */
@Module
@InstallIn(SingletonComponent::class)
object BackendModule {

    @Provides
    @Singleton
    fun provideTelegramClient(
        @ApplicationContext context: Context,
        @TelegramScope scope: CoroutineScope,
    ): TelegramClient = TdlibTelegramClient(context, scope)
}
