# Wireless Debug Keeper

An Android 12 helper for a Samsung Galaxy Tab S6 with a damaged screen. It restores wireless debugging after boot and after Android turns it off. It also speaks once as a discharging battery crosses 70%, 50%, 30%, or 10%, and announces when external power connects or disconnects.

The app runs on the tablet, independent of the computer. It is unrooted and uses the `WRITE_SECURE_SETTINGS` permission granted during setup. A foreground service responds to Wi-Fi and setting changes; a persisted Android job provides a delayed fallback if the service is killed. Battery announcements use Android's battery broadcast rather than a timer. Text to speech starts only for an announcement and then shuts down.

On this Android 12 tablet, Android sometimes fails to reload its saved wireless-debugging network approval after reboot (`Expected keyStore, but got tag=adbKey`). An optional accessibility service approves **only** the Android wireless-debugging prompt for the Wi-Fi BSSID supplied in a local build file. It does not approve other networks or USB debugging prompts. This service runs in a separate process so Samsung DeX moving the app window does not kill it with the foreground service.

## Build

Install the Android SDK and a JDK, then create `local.properties` in the repository root:

```properties
trustedBssid=aa:bb:cc:dd:ee:ff
```

Replace the example with the BSSID shown on your tablet's **Allow wireless debugging on this network?** prompt. The file is ignored by Git. If the property is omitted, the app builds but will not automatically approve a network. Only configure a network you control.

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.boiyelove.wirelessdebugkeeper android.permission.WRITE_SECURE_SETTINGS
```

Open **Wireless Debug Keeper** once to start the foreground service. Enable **Wireless Debug Keeper** in Android's Accessibility settings to allow the narrowly scoped network approval. Pair the computer with Android Wireless Debugging once. A new ADB port is chosen by Android when wireless debugging starts; use `adb mdns services` to find it if auto-connect does not.

## Behavior and limits

- The battery speaks only while unplugged and crossing a listed level downward. Charging resets the announcements for the next discharge. Charger announcements occur on plug/unplug transitions while the service is running, not on the first battery status received at startup. The tablet's default text-to-speech engine and audio route are used.
- The network approval depends on the configured BSSID and English Android dialog labels. A changed access point or system language requires an updated build.
- The foreground service and persisted job improve recovery from process kills, but Android can still delay background work. Force-stopping the app disables recovery until it is opened again. Accessibility can also be disabled by Android or the user.
- A monitor-free reboot was tested on the Tab S6: the service restored wireless debugging, the accessibility service approved the prompt, and the computer reconnected. Access before the first credential unlock is not guaranteed.
