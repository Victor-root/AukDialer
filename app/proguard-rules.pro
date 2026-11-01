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

# Room instantiates its generated *_Impl database class by reflection
# (getDeclaredConstructor().newInstance()), so nothing in the visible call graph appears to use
# its no-arg constructor. Without this, R8 strips it as dead code and the app crashes on startup
# in release only, with a NoSuchMethodException on that constructor.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}

# Shizuku starts the call recorder service in its own process by reflection, through the
# constructor taking a Context, so nothing in the visible call graph uses it.
-keep class auk.dialer.vroot.controller.PrivilegedRecorderService {
    public <init>(android.content.Context);
}

# Jakarta Mail (com.sun.mail:android-mail) locates its IMAP providers, MIME
# content handlers and SASL mechanisms reflectively through META-INF resources.
# Stripping any of them breaks the carrier IMAP connection at runtime with a
# misleading NoSuchProviderException.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**

# Tink (behind androidx.security:security-crypto, which encrypts the voicemail credentials)
# references Error Prone annotations that only matter at compile time and are not shipped.
-dontwarn com.google.errorprone.annotations.**
