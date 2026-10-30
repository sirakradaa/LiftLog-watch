# LiftLog for Wear OS

Watch companion for [LiftLog](https://github.com/sirakradaa/LiftLog), the phone workout tracker. Start a workout on the watch, log sets, and keep the live workout in sync with the phone over the Wearable Data Layer.

The watch uses the same application id as the phone (`com.sirak.liftlog`). Wear OS only shares data between apps signed with the same certificate, so a debug watch build has to use the phone app's debug keystore.

## What you can do on the watch

- Start an empty workout, or open one of the five most recently used phone templates
- Log weight, reps, duration, distance, jumps, and steps
- Switch between lb and kg
- Pause the workout, add sets, and finish or discard
- See the previous performance for a set, and when a set is a personal record
- Follow the phone's rest timer and theme colors

The phone is the source for templates, exercise history, and saved workouts. The watch does not replace it.

## Requirements

- JDK 17
- Android SDK 36
- A Wear OS device or emulator (min SDK 30)
- The LiftLog phone app, installed and signed with the same debug certificate

## Build

Open this folder in Android Studio, or from the project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Signing

Debug builds use the standard Android debug keystore (`%USERPROFILE%\.android\debug.keystore`) unless you point at another one.

To match the phone app, add this to `local.properties` (Android Studio creates that file, and git ignores it):

```properties
liftlog.debug.keystore=C:/path/to/the/phone/debug.keystore
```

Optional overrides, if the keystore is not an Android or Expo debug keystore:

```properties
liftlog.debug.storePassword=android
liftlog.debug.keyAlias=androiddebugkey
liftlog.debug.keyPassword=android
```

Do not commit `local.properties` or any `.keystore` / `.jks` file.

## License

[MIT](LICENSE)
