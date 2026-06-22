# Add project specific ProGuard rules here.
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn com.microsoft.identity.common.**
-keep class com.microsoft.identity.common.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn com.google.auto.value.AutoValue

# Gson - preserve generic signatures needed by TypeToken
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Gson serialized/deserialized classes
-keepclassmembers class com.shrivatsav.monomail.auth.UserProfile {
    <fields>;
}
-keepclassmembers class com.shrivatsav.monomail.data.model.** {
    <fields>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep data classes used with Gson in AccountManager
-keepclassmembers class com.shrivatsav.monomail.** {
    <fields>;
}

# Explicitly keep all Gmail/Outlook API response DTOs (R8 can strip these)
-keep class com.shrivatsav.monomail.data.remote.** { *; }
-keep class com.shrivatsav.monomail.data.model.** { *; }
-keep class com.shrivatsav.monomail.data.provider.** { *; }
-keep class com.shrivatsav.monomail.data.local.** { *; }

# Keep Kotlin data classes that Gson creates via UnsafeAllocator
-keep class com.shrivatsav.monomail.auth.UserProfile {
    *;
}

# angus-mail / Jakarta Mail
-keep class org.eclipse.angus.mail.** { *; }
-keep class jakarta.mail.** { *; }
-keep class jakarta.activation.** { *; }
-keep class org.eclipse.angus.mail.handlers.** { *; }
-dontwarn org.eclipse.angus.**
-dontwarn jakarta.mail.**
-dontwarn jakarta.activation.**

# ImapAccountConfig is serialized via Gson — keep all fields
-keep class com.shrivatsav.monomail.data.provider.imap.ImapAccountConfig { *; }
