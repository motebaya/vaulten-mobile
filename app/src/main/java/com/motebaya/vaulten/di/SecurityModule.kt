package com.motebaya.vaulten.di

import android.content.Context
import com.motebaya.vaulten.security.crypto.Bip39Generator
import com.motebaya.vaulten.security.crypto.DekManager
import com.motebaya.vaulten.security.crypto.EncryptionService
import com.motebaya.vaulten.security.crypto.KdfService
import com.motebaya.vaulten.security.keystore.KeystoreManager
import com.motebaya.vaulten.security.container.ContainerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing security-related dependencies.
 * 
 * All security components are singletons to ensure:
 * - Consistent key management
 * - Proper lifecycle handling
 * - Centralized security state
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideKeystoreManager(
        @ApplicationContext context: Context
    ): KeystoreManager {
        return KeystoreManager(context)
    }

    @Provides
    @Singleton
    fun provideKdfService(): KdfService {
        return KdfService()
    }

    @Provides
    @Singleton
    fun provideEncryptionService(): EncryptionService {
        return EncryptionService()
    }

    @Provides
    @Singleton
    fun provideDekManager(
        keystoreManager: KeystoreManager,
        encryptionService: EncryptionService
    ): DekManager {
        return DekManager(keystoreManager, encryptionService)
    }

    @Provides
    @Singleton
    fun provideContainerService(
        kdfService: KdfService,
        encryptionService: EncryptionService
    ): ContainerService {
        return ContainerService(kdfService, encryptionService)
    }

    @Provides
    @Singleton
    fun provideBip39Generator(
        @ApplicationContext context: Context
    ): Bip39Generator {
        return Bip39Generator(context)
    }
}
