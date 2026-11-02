# Google Drive API & HTTP client proguard rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers,allowobfuscation class * {
    @com.google.api.client.util.Key <fields>;
}

-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }

# Keep Room schemas / entities
-keep class androidx.room.** { *; }
-keep class com.ranit.thingcount.data.local.** { *; }
