package com.almasawa.files.di

import android.content.Context
import androidx.room.Room
import com.almasawa.files.data.db.AppDatabase
import com.almasawa.files.data.db.PanelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "almasawa.db").build()

    @Provides
    fun panelDao(db: AppDatabase): PanelDao = db.panelDao()
}
