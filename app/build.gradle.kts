import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Load keystore properties from file (if exists)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.motebaya.vaulten"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.motebaya.vaulten"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Vector drawable support for older APIs
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Split APKs by ABI for smaller per-device downloads
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    // Assign unique versionCode per ABI so each split APK is distinguishable
    val abiCodes = mapOf(
        "armeabi-v7a" to 1,
        "arm64-v8a" to 2,
        "x86_64" to 3
    )

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val abiFilter = output.getFilter(com.android.build.OutputFile.ABI)
            // universal = 0, per-abi = abiCodes[abi]
            val abiCode = abiCodes[abiFilter] ?: 0
            output.versionCodeOverride = abiCode * 1000 + (variant.versionCode ?: 0)
        }
    }

    // Only create release signing config if keystore.properties exists AND the keystore file exists
    // Note: storeFile path is relative to project root, not app/ directory
    val keystoreFile = keystoreProperties["storeFile"]?.let { rootProject.file(it as String) }
    val hasValidSigningConfig = keystorePropertiesFile.exists() && keystoreFile?.exists() == true
    
    if (hasValidSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing config if available, otherwise builds unsigned
            if (hasValidSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            
            // Optimize for release
            isDebuggable = false
            isJniDebuggable = false
            renderscriptOptimLevel = 3
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/versions/9/previous-compilation-data.bin"
            )
        }
    }
    
    // Lint configuration
    lint {
        abortOnError = false
        checkReleaseBuilds = true
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }
}

dependencies {
    // Core
    implementation(libs.core.ktx)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)

    // Compose
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Security
    implementation(libs.biometric)
    implementation(libs.security.crypto)
    implementation(libs.bouncycastle)
    implementation(libs.jbcrypt)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
