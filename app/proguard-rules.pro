# Google Drive API & HTTP client proguard rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers,allowobfuscation class * {
    @com.google.api.client.util.Key <fields>;
}

-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
-dontwarn org.apache.http.**

# Room Database & DAOs
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class com.ranit.thingcount.data.model.** { *; }
-keep class com.ranit.thingcount.data.local.** { *; }

# Gson Serialization & Models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-keep class com.ranit.thingcount.data.update.UpdateInfo { *; }
