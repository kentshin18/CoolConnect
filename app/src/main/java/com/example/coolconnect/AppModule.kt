package com.example.coolconnect

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
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
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CoolConnectDatabase {
        return CoolConnectDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideDao(db: CoolConnectDatabase): CoolConnectDao = db.dao()

    @Provides
    @Singleton
    fun provideRepository(
        db: FirebaseFirestore, 
        auth: FirebaseAuth, 
        storage: FirebaseStorage, 
        dao: CoolConnectDao,
        @ApplicationContext context: Context,
    ): CoolConnectRepository {
        return CoolConnectRepository(db, auth, storage, dao, context)
    }

    @Provides
    @Singleton
    fun provideAiAssistantRepository(
        auth: FirebaseAuth,
        db: FirebaseFirestore,
        @ApplicationContext context: Context
    ): AiAssistantRepository = AiAssistantRepository(auth = auth, db = db, context = context)
}
