# 1. ANDROID & R8 BASICS & ATTRIBUTES
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn android.**
-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# 2. HILT & DAGGER (DEPENDENCY INJECTION)
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class dagger.** { *; }
-dontwarn dagger.**
-keep class javax.inject.** { *; }

# 3. ROOM DATABASE & SQLCIPHER
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Room Entities, DAOs, Databases, and TypeConverters
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep class * extends androidx.room.RoomDatabase { *; }

# 4. FIREBASE & FIRESTORE DATA MODELS
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep Firebase PropertyName annotations and model members
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}

# 5. PROJECT DATA MODELS, SECURITY & UTIL ENTITIES (com.yansproject.app)
-keep class com.yansproject.app.data.** { *; }
-keepclassmembers class com.yansproject.app.data.** { *; }
-keep class com.yansproject.app.security.** { *; }
-keepclassmembers class com.yansproject.app.security.** { *; }
-keep class com.yansproject.app.util.** { *; }
-keepclassmembers class com.yansproject.app.util.** { *; }

# 6. RETROFIT, OKHTTP, MOSHI, GSON & SERIALIZATION
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Moshi JSON Rules
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-keep class *JsonAdapter {
    public <init>(...);
}

# Gson Rules
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 7. WORKMANAGER & CAMERA
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# 8. KOTLIN & COROUTINES
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**


