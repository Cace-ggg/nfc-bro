# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class it.zorn.nfcbro.** {
    *** Companion;
}
-keepclasseswithmembers class it.zorn.nfcbro.** {
    kotlinx.serialization.KSerializer serializer(...);
}
