# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Retrofit + R8 full mode
#
# AGP 8.2 turns on R8 full mode. Retrofit 2.9.0's shipped rules predate full mode: they keep
# the service interface but not the generic types reachable from its methods, so R8 strips
# the generic signatures. The type then degrades to the raw Class and Retrofit throws
# "java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType" the first time a
# call is made — before any HTTP request leaves the device, so the backend log stays empty
# and the failure looks like a mystery UI error rather than a network problem.
#
# These are the rules later Retrofit releases ship; carrying them here keeps us correct on
# 2.9.0. (Alternative considered: bump Retrofit — the rules stay harmless if we do.)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# A suspend function is compiled to a Continuation<Response<T>>, and the type argument is what
# Retrofit reads to pick a converter.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Gson data models (com.tavuno.tv.data.model)
#
# These are parsed reflectively, and most fields carry no @SerializedName, so Gson falls
# back to the *Java field name*. Without this rule R8 renames the classes and strips or
# renames fields it reckons are unused — and because reflective access is invisible to R8,
# nothing fails loudly: the JSON still parses and the values silently arrive as null/0
# (empty catalogs, blank titles, missing artwork).
#
# -keep also pins the names, so no trailing -keepnames is needed. The package is a few
# dozen small data classes; the cost of keeping it is negligible against a silent
# wrong-data release build.
-keep class com.tavuno.tv.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Media3
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }
