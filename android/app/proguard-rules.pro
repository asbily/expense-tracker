# Asbily Expense Tracker Android WebView Wrapper Proguard Rules
-keepclassmembers class com.asbily.expensetracker.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
