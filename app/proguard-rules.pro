# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit / OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Moshi Data Models
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json *;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass *;
}