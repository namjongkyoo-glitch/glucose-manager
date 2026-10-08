# kotlinx.serialization ships its own R8 rules; keep backup DTO serializers explicitly as a safety net.
-keep,includedescriptorclasses class com.jadennam.glucose.domain.backup.**$$serializer { *; }
-keepclassmembers class com.jadennam.glucose.domain.backup.** { *** Companion; }

# OkHttp (pulled in by Kakao SDK) references optional TLS providers that are not on Android.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
