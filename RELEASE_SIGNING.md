# Release signing

Create a private signing key for your own distribution, or use the existing release key when maintaining the official app. Never replace the official key to work around an update failure.

Copy `keystore.properties.example` to the ignored `keystore.properties` and fill in local values. `storeFile` is resolved relative to the app module. Keep the key and credentials backed up privately; do not commit or include them in source archives.

Build with JDK 17:

```powershell
.\gradlew.bat :app:assembleRelease
```

Update with `adb install -r app/build/outputs/apk/release/app-release.apk`. Keep `com.roinur.booktracker` and increment versionCode for every distributed update. Do not uninstall or clear storage.

The visible versionName changes only for a GitHub release, when explicitly requested. Local APK updates retain that versionName and increment only Android versionCode. The initial GitHub release is 1.0.
