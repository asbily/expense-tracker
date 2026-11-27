# Asbily Expense Tracker Android WebView Wrapper Proguard Rules
-keepclassmembers class com.asbily.expensetracker.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Start.io SDK rules
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable
-dontwarn com.startapp.**
-keep class com.startapp.** { *; }

