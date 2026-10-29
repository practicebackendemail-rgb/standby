# Releasing StandBy

## 1. Signing key (one time)

```
keytool -genkeypair -v -keystore standby-release.jks -alias standby -keyalg RSA -keysize 4096 -validity 10000
```

Create `keystore.properties` in the project root (both files are git-ignored):

```
storeFile=standby-release.jks
storePassword=…
keyAlias=standby
keyPassword=…
```

Without `keystore.properties`, release builds are produced **unsigned**.

## 2. Build

```
./gradlew bundleRelease      # app/build/outputs/bundle/release/app-release.aab (Play Store)
./gradlew assembleRelease    # app/build/outputs/apk/release/ (sideload)
```

Release builds are shrunk with R8, demo mode is compiled out of use (`BuildConfig.DEBUG`), and
`StandByActivity` is not exported. Bump `versionCode` / `versionName` in `app/build.gradle.kts`
for each upload.

## 3. Play Console declarations

- **Foreground service type `specialUse`:** "StandBy listens for the charger being connected so it
  can open its full-screen clock display immediately. There is no user-visible work type that fits:
  it is not media, location, data sync or a connected device; it only waits for the power-connected
  broadcast, which Android delivers only to running components." Record a short video of plugging
  in → StandBy opening.
- **Display over other apps (`SYSTEM_ALERT_WINDOW`):** needed to start the StandBy activity from the
  background when charging starts; requested only when the user enables "Auto-open when docked".
- **Notification listener:** core functionality (now playing, banners, Clock timers); explain in the
  data-safety form that content is processed on device and not stored or shared.
- **Photos (`READ_MEDIA_IMAGES`):** core to the Photos page; partial access is supported.
- **Data safety:** location (approximate) shared with Open-Meteo for weather; nothing else collected
  or shared. Privacy policy: publish `PRIVACY.md` at a public URL.

## 4. Screenshots

Debug builds can show every page with sample data:

```
adb shell am start -n com.dockstandby/.StandByActivity --ez demo true --ei page 0 --es theme Colorful
```

`--ei page -1` is the night clock, `-2` the glance clock, `--es time 07:00` sets the demo time.

## 5. Pre-release checklist

- [ ] Fresh install: walkthrough → every permission → StandBy opens on plug-in
- [ ] Every page in both themes, portrait setup and landscape setup
- [ ] Night mode in a dark room; glance dim after the idle delay; wake by touch / approach
- [ ] Banners locked vs unlocked; Clock timer chip; quick timer rings and stops
- [ ] Release build installs and runs (R8 can break reflection — none is used today)
