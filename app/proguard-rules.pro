# Add project specific ProGuard rules here.
# Control bytecode optimization, obfuscation and dead-code stripping.

# Keep Kotlin reflection and serialization attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Preserve Data Models and Firestore Entities
-keepclassmembers class com.example.data.** { *; }
-keep class com.example.data.** { *; }

# Preserve Room DB Entities and DAOs
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Preserve Firebase Authentication & Firestore SDK reflection
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
