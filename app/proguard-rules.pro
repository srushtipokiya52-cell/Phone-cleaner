# Phone Cleaner – R8 / ProGuard rules
# Library rules for AndroidX, Material, Navigation, WorkManager, Coil, Coroutines and Google Mobile Ads
# are bundled with the libraries themselves. Only app-specific needs go here.

# Readable crash reports (keeps line numbers, hides original file names).
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Classes the system creates from the manifest by name.
-keep class com.phone.cleanerapp.storage.mediafiles.PhoneCleanerApp { *; }
-keep class com.phone.cleanerapp.storage.mediafiles.MainActivity { *; }
-keep class com.phone.cleanerapp.storage.mediafiles.service.JunkNotificationListener { *; }
-keep class com.phone.cleanerapp.storage.mediafiles.service.MaintenanceWorker { *; }

# Fragments are created by the Navigation component from their class name in nav_graph.xml.
-keep class * extends androidx.fragment.app.Fragment { <init>(); }

# Custom views are inflated from XML by name.
-keep class com.phone.cleanerapp.storage.mediafiles.ui.RingView { *; }
-keep class com.phone.cleanerapp.storage.mediafiles.ui.ShimmerLayout { *; }
-keep class com.phone.cleanerapp.storage.mediafiles.ui.AutoHideTextView { *; }
-keep class com.phone.cleanerapp.storage.mediafiles.ui.AutoHideLinearLayout { *; }

# ViewBinding classes look views up by id.
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}

# Battery capacity is read from the framework's PowerProfile by reflection (a system class, not ours).
-dontwarn com.android.internal.os.PowerProfile

# Enum helpers used by values()/valueOf() lookups (e.g. FileKind.valueOf from navigation arguments).
-keepclassmembers enum com.phone.cleanerapp.storage.mediafiles.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
