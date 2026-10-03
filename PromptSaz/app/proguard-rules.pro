# PromptSaz R8 rules.

# --- kotlinx.serialization (official rules from the kotlinx.serialization README) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep generated serializers for our @Serializable models.
-keep,includedescriptorclasses class com.promptsaz.app.**$$serializer { *; }

# Keep `Companion` objects of serializable classes (serializer() lookup).
-keepclassmembers class com.promptsaz.app.** {
    *** Companion;
}

# Keep the serializer() method on serializable classes.
-keepclasseswithmembers class com.promptsaz.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room / Hilt ship their own consumer rules; nothing extra required. ---

# --- EncryptedSharedPreferences (androidx.security) works with default rules. ---
