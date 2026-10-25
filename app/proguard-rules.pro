# kotlinx.serialization ships its own rules; keep our @Serializable models' names stable for the JSON on disk.
-keep,includedescriptorclasses class com.kaze.motoplus.**$$serializer { *; }
-keepclassmembers class com.kaze.motoplus.** { *** Companion; }
-keepclasseswithmembers class com.kaze.motoplus.** { kotlinx.serialization.KSerializer serializer(...); }
