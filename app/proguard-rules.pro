# kotlinx.serialization ships its own rules; keep our @Serializable models' names stable for the JSON on disk.
-keep,includedescriptorclasses class com.kaze.motoplus.**$$serializer { *; }
-keepclassmembers class com.kaze.motoplus.** { *** Companion; }
-keepclasseswithmembers class com.kaze.motoplus.** { kotlinx.serialization.KSerializer serializer(...); }

# ML Kit text recognition finds its components by reflection; R8 stripped them (NPE in TextRecognition.getClient).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }

# Glance builds widget action callbacks by reflection (NoSuchMethodException on LubeAction.<init> in release).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
