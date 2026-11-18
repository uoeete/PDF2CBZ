# Keep PDFBox-Android classes and Serialization models
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.pdf2cbz.app.data.model.**$$serializer { *; }
-keepclassmembers class com.pdf2cbz.app.data.model.** {
    *** Companion;
}
