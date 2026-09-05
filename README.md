<div align="center">

<img src="app/src/main/res/drawable/logo.png" width="120" height="120" alt="AukDialer logo">

# AukDialer

**A modern Android dialer with no ads, no tracking, and no Google dependency.**

[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg?style=flat-square)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat-square&logo=android)](https://www.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Built_with-Jetpack_Compose-4285F4.svg?style=flat-square&logo=android)](https://developer.android.com/jetpack/compose)
[![No ads](https://img.shields.io/badge/Ads-none-success.svg?style=flat-square)](#why-this-fork-exists)

A phone app should place calls and stay out of the way. That is the whole brief.

</div>

---

## Why this fork exists

AukDialer is a fork of an open source dialer that did one job well. In August 2026 that project added AdMob banner advertising to the contact list, the call log, search results and the settings screen, along with the `play-services-ads` dependency that comes with it.

A dialer reads your entire contact list and your full call history. Putting an advertising SDK in the same process as that data is not a trade-off worth making, whatever the banner is worth. It also makes the app dependent on Google Play Services, which not every Android phone has and not everyone wants.

This fork takes the project in its own direction from that point on. It is maintained independently and it will not carry advertising.

## What is different here

- **No advertising.** No banners, no ad SDK, no ad unit IDs, nothing to switch off in settings because there is nothing to switch off.
- **No Google Play Services.** Not a single component. The app works identically on a phone with no Google apps installed at all, including LineageOS, GrapheneOS, /e/OS and Huawei devices.
- **One build.** Upstream splits into a `play` flavour and a `foss` flavour. There is only one build here, and it is the clean one.
- **No app bundle.** A plain APK, nothing shaped for a store.

## Features

- **Material 3 Expressive** interface, built entirely with Jetpack Compose
- **T9 search** to reach a contact in a couple of taps
- **Custom in-call screen** with proximity sensor handling
- **Readable call history**, grouped and easy to scan
- **Private contacts** you can keep out of the main list
- **Call blocking** with its own visibility rules
- **Backup and restore** of your settings
- **Per-app language** on Android 13 and above

## Building it yourself

Android Studio, JDK 17, and:

```bash
./gradlew assembleDebug     # debug build
./gradlew assembleRelease   # release build
```

Signing is read from `keystore.properties` at the root of the repository. That file is untracked because it holds passwords, so copy `keystore.properties.example` to `keystore.properties` and fill it in. Both the debug and the release build then use that key, which means a debug build installs straight over a release build without an uninstall.

Without that file the debug build falls back to Android's throwaway debug key and the release build comes out unsigned, on purpose: an APK that refuses to install is easier to notice than one signed with the wrong key.

On CI the same values come from the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets.

## Contributing

Issues and pull requests are welcome on [this repository](https://github.com/Victor-root/AukDialer/issues).

Translations live in `app/src/main/res/values-<language>/strings.xml`. Copy `values/strings.xml`, translate the values, leave the `name` attributes alone, and open a pull request.

Two rules for code: no advertising, and no dependency that needs Google Play Services to work.

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).

This is a modified version of the Rivo dialer. It was forked in September 2026 and has been maintained separately since.
