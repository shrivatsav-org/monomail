# Add project specific ProGuard rules here.
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn com.microsoft.identity.common.**
-keep class com.microsoft.identity.common.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn com.google.auto.value.AutoValue
