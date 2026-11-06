# StandBy for Android

[![Android](https://github.com/realfamousbae/android-custom-always-on/actions/workflows/android.yml/badge.svg)](https://github.com/realfamousbae/android-custom-always-on/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/realfamousbae/android-custom-always-on)](https://github.com/realfamousbae/android-custom-always-on/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

[Русский](README.md) · **English**

An iOS StandBy-style bedside mode for Android. Stand the phone on its side (on a dock) and
let the screen turn off — a large clock appears instead of the lock screen. Works both on
and off the charger.

Tested on Samsung Galaxy S24 Ultra (Android 17, One UI 9). Requires Android 12 or newer.
The app UI is in Russian.

## Features

- **Starts by itself.** Screen off and the phone standing on its long edge — the mode shows
  over the lock screen. It never pops up while the phone lies flat or while you use it.
- **On or off the charger.** Off the charger the mode runs while the battery is above a
  threshold (20 % by default). A "charging only" option is available.
- **Clock and a system widget.** A full-screen clock or two cards: the clock and any home
  screen widget (calendar, weather, etc.). Widget edges softly fade into the card.
- **Notification banners.** New notifications appear as a clean banner on top. Choose which
  apps; text is hidden by the same rules as on the lock screen.
- **No system heads-up.** While the mode is on screen, system heads-up notifications are
  hidden (via the app's own Do Not Disturb mode; calls and alarms work as usual). Can be
  turned off in settings.
- **Auto brightness.** Brightness follows ambient light; in a dark room the screen barely glows.

## Installation

1. Download `standby-<version>.apk` from [Releases](https://github.com/realfamousbae/android-custom-always-on/releases/latest)
   and open it on the phone (allow installs from this source).
2. Open the app and grant everything in the permissions section: display over other apps,
   no battery optimization, service notification; in App info set Battery → Unrestricted.
   Turn off Samsung's system screen saver.
3. For banners, enable notification access. If the toggle is greyed out ("Restricted
   setting"): App info → ⋮ → "Allow restricted settings".

New versions install over the old one; settings are kept.

## Limitations

- Not on Google Play and meant for sideloading: it needs permissions a store would not
  approve (launching over the lock screen from the background).
- Tested on S24 Ultra only. Background launches and widgets may behave differently on other
  firmware — a diagnostic log is in settings under the developer section.
- Planned: red night mode, OLED burn-in protection, built-in widgets (weather, calendar,
  music), first-run wizard. Details (in Russian) — [docs/PLAN.md](docs/PLAN.md).

## Building

Requires JDK 21 and Android SDK (platform 37).

```bash
./gradlew :core:test :app:lintDebug :app:assembleDebug
```

Release builds are built and signed by GitHub Actions ([workflow](.github/workflows/android.yml)):
a push to `main` runs checks, a `v*` tag publishes a signed APK to Releases.

## Code layout

- `core/` — pure Kotlin, no Android: `TriggerEngine` (when to show the mode),
  `OrientationClassifier`, `AutoBrightness`, notification rules, settings model.
  Fully unit-tested.
- `app/` — Android layer: a foreground service executing `TriggerEngine` decisions, the
  lock-screen Activity, sensor adapters, Compose UI, DataStore settings.
- `docs/PLAN.md` — architecture, decision log and risks (in Russian).

## License

[MIT](LICENSE).

Fonts (SIL Open Font License 1.1): the clock uses [Manrope](https://github.com/googlefonts/manrope)
([licenses/Manrope-OFL.txt](licenses/Manrope-OFL.txt)), all other text uses
[Inter](https://github.com/rsms/inter) ([licenses/Inter-OFL.txt](licenses/Inter-OFL.txt)).
