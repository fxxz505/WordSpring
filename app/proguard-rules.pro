# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep,allowobfuscation,allowshrinking class * extends androidx.lifecycle.ViewModel
-keep,allowobfuscation,allowshrinking class * extends androidx.lifecycle.AndroidViewModel

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.wordspring.app.**$$serializer { *; }
-keepclassmembers class com.wordspring.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.wordspring.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
