# Proguard rules for CodeUnlock
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class com.codeunlock.api.dto.** { *; }
-keep class com.codeunlock.api.service.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
