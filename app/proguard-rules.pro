# LR Patrol Pro ProGuard Rules

# 保留 WebView JavaScript 介面
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留 AppCompatActivity 相關
-keep public class * extends androidx.appcompat.app.AppCompatActivity

# 保留 WebView 相關
-keep class android.webkit.** { *; }
-keep class androidx.webkit.** { *; }

# 保留註解
-keepattributes *Annotation*
-keepattributes JavascriptInterface
-keepattributes SourceFile,LineNumberTable

# 保留 Enum
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-renamesourcefileattribute SourceFile
