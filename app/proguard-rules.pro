# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep necessary classes from the Flutter library
#-keep class io.flutter.app.** { *; }
#-keep class io.flutter.facade.** { *; }
#-keep class io.flutter.embedding.** { *; }
#-keep class io.flutter.plugins.** { *; }

# Exclude debug, profile, and release embedding modules
#-dontwarn com.afex.flutter_module**

# Keep your own classes (adjust as needed)
#-keep class com.afex.mapx.** { *; }
#-keep class com.afex.mapxlicense.** { *; }
#-keep class io.flutter.** { *; }
#-keep class io.flutter.plugins.** { *; }
