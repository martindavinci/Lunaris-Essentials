<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="96" alt="Lunaris Scanner icon">
</p>

<h1 align="center">Lunaris Scanner</h1>

<p align="center"><b>A QR code and barcode scanner and generator for Android that works offline and shows no ads.</b></p>

<p align="center">by <a href="https://github.com/martindavinci">Martin DaVinci</a></p>

<p align="center">
  <a href="https://github.com/martindavinci/lunaris-essentials/releases?q=lunaris-scanner&expanded=true"><img src="https://img.shields.io/github/v/release/martindavinci/lunaris-essentials?filter=lunaris-scanner-*&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0+">
  <a href="../LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue" alt="License: GPL-3.0-or-later"></a>
</p>

## Download

<p>
  <a href="https://github.com/martindavinci/lunaris-essentials/releases?q=lunaris-scanner&expanded=true">Get the APK from GitHub Releases</a>
  &nbsp;·&nbsp;
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.martindavinci.lunaris.scanner%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fmartindavinci%2Flunaris-essentials%22%2C%22author%22%3A%22martindavinci%22%2C%22name%22%3A%22Lunaris%20Scanner%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5ELunaris%20Scanner%20v%5C%22%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Elunaris-scanner-%5B0-9.%5D%2B%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%7D%22%7D"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="40" align="center" alt="Get it on Obtainium"></a>
</p>

Each release has one APK, `lunaris-scanner-<version>.apk`, for Android 8.0 or newer. With
[Obtainium](https://github.com/ImranR98/Obtainium) the app updates itself from this repository; the button sets it
up for you. To add it by hand, use this repository's URL and filter the release titles by `^Lunaris Scanner v`,
since other Lunaris apps publish their releases here too.

## What it does

**Scanning**
- 2D codes: QR Code, Aztec, Data Matrix, PDF417, MaxiCode
- Barcodes: EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, Code 93, Codabar, ITF, RSS-14, RSS Expanded
- Reads from the live camera, from an image you pick from the gallery, or from an image shared from another app
- With the camera, only what the viewfinder window shows is read, so a code next to the one you want stays out
- Barcodes with a weak check must read the same across several rows and two frames in a row, which keeps QR codes and
  pieces of other barcodes from passing for them; a Code 39 shorter than 3 characters is not accepted
- MaxiCode is the exception: it is read only from a clean image that the symbol fills, not from the camera or a photo
- Also reads inverted codes, light on a dark background

**Results**
- Recognizes links, Wi-Fi networks, contacts, email addresses, phone numbers, SMS, locations, calendar events and
  product codes; anything else is shown as plain text
- Warns about a link that is not HTTPS, uses punycode or non-ASCII characters in its host, points to an IP address,
  embeds a username or password, or uses an unusual port
- Only `http` and `https` links can be opened

**Generating**
- QR codes and barcodes from text, links, Wi-Fi networks, contacts (vCard), email addresses, phone numbers and SMS
- Checks the length, the allowed characters and the check digit against the chosen format before generating

**History and settings**
- Keeps your last 500 scans on the phone, newest first; you can delete single entries, clear them all or turn the
  history off
- Light, dark or system theme, 11 languages, and vibration on scan that you can turn off

## Privacy

The only system permission the app asks for is the camera, and it is there for one thing: the live view that codes
are read from. Each frame is decoded on the phone and then discarded; the app takes no photos and records no video.
Images you pick from the gallery or share to the app go through Android's own picker and sharing, which need no
permission at all.

There is no internet permission, so the app has no way to send anything anywhere, and it looks nothing up: a product
code is shown as it is, and Search the web hands it to your browser only if you tap it. There are no ads, no analytics
and no account. Android's cloud backup is turned off for the app and its data is excluded from device-to-device
transfer, so the scan history stays on the phone, although Android lets some phone makers' migration tools ignore that
setting. A link you open is handled by your browser, which uses its own network access, not the app's.

You can check it yourself: the [manifest](app/src/main/AndroidManifest.xml) declares the camera permission, and on a
downloaded APK

```
aapt2 dump permissions lunaris-scanner-<version>.apk
```

lists `android.permission.CAMERA` and `io.github.martindavinci.lunaris.scanner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
AndroidX adds the second one so that only the app itself can send messages to its own internal receivers; it gives
access to nothing.

## Build from source

You need JDK 17 or newer and the Android SDK (platform 36).

```
git clone https://github.com/martindavinci/lunaris-essentials.git
cd lunaris-essentials/lunaris-scanner
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

Point Gradle to your SDK with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=…`.

## License

GPL-3.0-or-later, see [LICENSE](../LICENSE). The name "Lunaris Scanner" and its icon are not licensed as trademarks:
see [TRADEMARKS.md](../TRADEMARKS.md). Codes are read and generated with [ZXing](https://github.com/zxing/zxing) core
and the camera runs on [CameraX](https://developer.android.com/media/camera/camerax). ZXing, CameraX, Jetpack Compose,
DataStore and Dagger Hilt are under the Apache-2.0 license.
