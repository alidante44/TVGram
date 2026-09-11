package ir.tvgram.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.tvgram.telegram.FakeTelegramClient
import ir.tvgram.telegram.TelegramClient
import javax.inject.Singleton

/** `mock` flavour: generated content, no account and no native library. */
@Module
@InstallIn(SingletonComponent::class)
object BackendModule {

    @Provides
    @Singleton
    fun provideTelegramClient(
        @ApplicationContext context: Context,
    ): TelegramClient = FakeTelegramClient(context)
}
