# Project-specific ProGuard rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# kotlinx serialization
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.piash.priya.**$$serializer { *; }
-keepclassmembers class com.piash.priya.** {
    *** Companion;
}
-keepclasseswithmembers class com.piash.priya.** {
    kotlinx.serialization.KSerializer serializer(...);
}
