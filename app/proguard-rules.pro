# ============================================================================
# ProGuard/R8 rules for Vaulten - Secure Password Manager
# Production-optimized configuration for security-sensitive application
# ============================================================================

# ============================================================================
# GENERAL OPTIMIZATION
# ============================================================================

# Aggressive obfuscation
-repackageclasses 'v'
-allowaccessmodification
-optimizationpasses 5

# Keep source file names and line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================================
# MISSING CLASSES - Suppress warnings for optional annotations
# ============================================================================

# Google Error Prone annotations (used by Tink/security-crypto, not needed at runtime)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Google Tink (used by security-crypto)
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# ============================================================================
# APPLICATION CLASSES
# ============================================================================

# Keep application entry points
-keep class com.motebaya.vaulten.VaultApplication { *; }
-keep class com.motebaya.vaulten.MainActivity { *; }

# Keep all entities (Room needs them)
-keep class com.motebaya.vaulten.domain.entity.** { *; }
-keep class com.motebaya.vaulten.data.local.entity.** { *; }

# Keep data classes used for serialization
-keep class com.motebaya.vaulten.data.session.PendingExportImportFlow { *; }
-keep class com.motebaya.vaulten.data.session.ExportResultData { *; }
-keep class com.motebaya.vaulten.domain.repository.BackupPreview { *; }
-keep class com.motebaya.vaulten.domain.repository.BackupMetadata { *; }
-keep class com.motebaya.vaulten.domain.repository.ExportResult { *; }

# ============================================================================
# HILT / DAGGER
# ============================================================================

-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keepclasseswithmembernames class * {
    @dagger.* <fields>;
}
-keepclasseswithmembernames class * {
    @javax.inject.* <fields>;
}

# Hilt generated classes
-keep class *_HiltModules* { *; }
-keep class *_Factory { *; }
-keep class *_MembersInjector { *; }

# ============================================================================
# ROOM DATABASE
# ============================================================================

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }

# Room type converters
-keep class com.motebaya.vaulten.data.local.Converters { *; }

# Keep room schemas
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ============================================================================
# SECURITY LIBRARIES
# ============================================================================

# Bouncy Castle - keep all crypto implementations
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keepclassmembers class org.bouncycastle.** { *; }

# jBCrypt - password hashing
-keep class org.mindrot.jbcrypt.** { *; }

# Android Security Crypto
-keep class androidx.security.crypto.** { *; }

# ============================================================================
# JETPACK COMPOSE
# ============================================================================

-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep Compose runtime
-keepclassmembers class androidx.compose.runtime.** { *; }

# Keep Composable functions (needed for some reflection)
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ============================================================================
# KOTLIN
# ============================================================================

-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ============================================================================
# ANDROID FRAMEWORK
# ============================================================================

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable implementations
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================================
# SECURITY: REMOVE DEBUG CODE IN RELEASE
# ============================================================================

# Remove all logging statements
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Remove debug print statements
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
    public void printf(...);
}

# Remove System.out/err
-assumenosideeffects class java.lang.System {
    public static java.io.PrintStream out;
    public static java.io.PrintStream err;
}

# ============================================================================
# DATASTORE
# ============================================================================

-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ============================================================================
# NAVIGATION
# ============================================================================

-keep class androidx.navigation.** { *; }
-keepclassmembers class * {
    @androidx.navigation.* <methods>;
}

# ============================================================================
# BIOMETRIC
# ============================================================================

-keep class androidx.biometric.** { *; }

# ============================================================================
# JSON (if using org.json)
# ============================================================================

-keep class org.json.** { *; }

# ============================================================================
# ENUMS
# ============================================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
# NATIVE METHODS
# ============================================================================

-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================================
# ANNOTATIONS
# ============================================================================

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
