# PgpKeyInfo is persisted as Gson JSON. Keep its annotated field metadata stable across R8 builds.
-keepattributes Signature,*Annotation*
-keepclassmembers class com.shrivatsav.monomail.data.pgp.PgpKeyInfo {
    @com.google.gson.annotations.SerializedName <fields>;
}
