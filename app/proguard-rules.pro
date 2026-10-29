# StandBy release rules. Compose, AndroidX and org.json ship their own consumer rules.
# Services, receivers and activities are kept via the manifest automatically.

# Keep enum names stable: settings are stored by enum name (Prefs.kt).
-keepclassmembers enum com.dockstandby.data.** { *; }
