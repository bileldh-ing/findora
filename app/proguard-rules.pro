# kotlinx.serialization: keep generated serializers for @Serializable navigation routes and DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class com.findora.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.findora.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
