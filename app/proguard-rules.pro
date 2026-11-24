# Keep line numbers in crash reports (Play Console de-obfuscates with the uploaded mapping file).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager (pulled in by the Ads SDK) creates its Room database by reflection (WorkDatabase_Impl's
# no-arg constructor); R8 full mode strips it otherwise and the release build crashes at startup.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
