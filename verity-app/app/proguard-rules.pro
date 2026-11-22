-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*
-renamesourcefileattribute SourceFile

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

-keep class androidx.core.content.FileProvider { *; }

-keep interface com.fleetmc.verity.ads.RewardedAdProvider { *; }
