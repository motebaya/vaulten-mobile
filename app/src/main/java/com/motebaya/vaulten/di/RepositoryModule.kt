package com.motebaya.vaulten.di

import com.motebaya.vaulten.data.repository.AuthRepositoryImpl
import com.motebaya.vaulten.data.repository.CredentialRepositoryImpl
import com.motebaya.vaulten.data.repository.PlatformRepositoryImpl
import com.motebaya.vaulten.data.repository.VaultRepositoryImpl
import com.motebaya.vaulten.domain.repository.AuthRepository
import com.motebaya.vaulten.domain.repository.CredentialRepository
import com.motebaya.vaulten.domain.repository.PlatformRepository
import com.motebaya.vaulten.domain.repository.VaultRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds repository interfaces to their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCredentialRepository(
        impl: CredentialRepositoryImpl
    ): CredentialRepository

    @Binds
    @Singleton
    abstract fun bindPlatformRepository(
        impl: PlatformRepositoryImpl
    ): PlatformRepository

    @Binds
    @Singleton
    abstract fun bindVaultRepository(
        impl: VaultRepositoryImpl
    ): VaultRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}
