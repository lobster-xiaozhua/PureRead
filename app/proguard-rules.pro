# PureRead ProGuard rules

# Keep Koin modules and ViewModels
-keep class com.pureread.core.di.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { public <init>(...); }

# Keep Room entities and DAOs
-keep class com.pureread.data.local.entity.** { *; }
-keep class com.pureread.data.local.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep Retrofit models
-keep class com.pureread.data.remote.api.** { *; }
-keep class com.pureread.update.UpdateInfo { *; }

# Keep WebView JS interfaces if any
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions
