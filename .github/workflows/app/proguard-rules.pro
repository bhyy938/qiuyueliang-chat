# Keep kotlinx-serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.example.liuruyi.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.liuruyi.**$$serializer { *; }
