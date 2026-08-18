# ==============================================================================
# YANSPROJECT.ID - HARDENED R8 & PROGUARD CONFIGURATION
# ==============================================================================
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# 1. JETPACK COMPOSE & NAVIGATION
-keepclassmembers class * extends androidx.navigation.NavDestination { *; }

# 2. FIRESTORE & FIREBASE DATA MODELS (KEEP FIELD NAMES FOR REFLECTION/SERIALIZATION)
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keepclassmembers class * {
    public <init>();
}

# 3. ROOM ENTITIES & DAOS
-keep @androidx.room.Database class *
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface * { *; }

# 4. JSON SERIALIZATION (MOSHI & GSON)
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}

# 5. HILT & WORKMANAGER & KEEP ANNOTATED CLASSES
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

