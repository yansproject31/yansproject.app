# ==============================================================================
# YANSPROJECT.ID - RELEASE PROGUARD & R8 CONFIGURATION (ANTI-CRASH HARDENED)
# ==============================================================================
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# 1. CORE ANDROID & JETPACK COMPOSE
-dontwarn android.**
-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# 2. NAVIGATION COMPOSE
-keep class androidx.navigation.** { *; }
-keep interface androidx.navigation.** { *; }
-keepclassmembers class * extends androidx.navigation.NavDestination { *; }

# 3. FIREBASE SUITE (Firestore, Auth, Messaging, App Check, Crashlytics, AI)
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }

-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keepclassmembers class * {
    public <init>();
}

# 4. HILT & DAGGER (DI) & LIFECYCLE / VIEWMODEL
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class dagger.** { *; }
-dontwarn dagger.**
-keep class javax.inject.** { *; }

# 5. SQLCIPHER & ROOM (ENCRYPTED DATABASE)
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class *
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface * { *; }

# 6. RETROFIT, OKHTTP, MOSHI, GSON (NETWORK & JSON SERIALIZATION)
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-keep class *JsonAdapter {
    public <init>(...);
}
-keep class com.google.gson.** { *; }

# 7. GOOGLE PLAY IN-APP UPDATES & WORKMANAGER
-keep class com.google.android.play.core.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# 8. YANSPROJECT.ID ALL APPLICATION PACKAGES (MENCEGAH R8 MENGAPUS UTIL & SECURITY)
-keep class com.yansproject.app.** { *; }
-keepclassmembers class com.yansproject.app.** { *; }
