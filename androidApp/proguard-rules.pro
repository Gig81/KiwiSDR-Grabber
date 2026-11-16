# R8 per KiwiSDR Grabber Android.

# kotlinx.serialization: mantiene i serializer generati delle classi @Serializable
# (impostazioni, preferiti, etichette DX).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.grabber.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.grabber.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio: avvisi su classi opzionali di piattaforma non presenti su Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**

# BouncyCastle: si usa solo l'API leggera Ed25519; le parti JCA/provider non servono.
-dontwarn org.bouncycastle.**
