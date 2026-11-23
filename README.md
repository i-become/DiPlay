# DiPlay

**CarPlay for the Hongqi HS5 Android head unit.** Wired and wireless, with the familiar DiAuto interface. Independent app: `com.shihab.diplay`.

> **Hongqi HS5 support scope:** This project is customized for the Hongqi HS5 head unit and its QiSi navigation stack. Other vehicle platforms are outside the supported scope.

[Download & website](https://shihabal3amri.github.io/DiPlay/) · [Release](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.18) · [Report a problem](https://github.com/shihabal3amri/DiPlay/issues/new/choose)

![DiPlay home](site/assets/home.png)

## 0.2.18 — public preview

Install on the **car**, not the iPhone. No jailbreak, dongle, Mac, account or authentication server is required for use. Core CarPlay does not require ADB; optional dashboard, battery, wheel-speed and parked-video features do. Your head unit must permit APK installation. The APK supports Android 7.1+ (API 25); Android 7.1–8.1 support is not yet confirmed on a vehicle. Wireless supports Wi-Fi Direct, the car’s existing hotspot or Existing Wi-Fi / Same LAN. Android 7.1–9 Wi-Fi Direct uses a firmware-dependent legacy path; Android 10+ verifies its negotiated group frequency.

- Wired USB and wireless CarPlay with local authentication.
- Hongqi HS5 cluster navigation with arrows, distance and street names on verified firmware.
- Car hotspot support, improved audio buffering and saved receive diagnostics.
- Automatic address discovery, fixed-channel Wi-Fi fallbacks and successful-configuration memory.
- Icon/text size, resolution and frame rate; applying a display change reconnects CarPlay.
- Local diagnostic export. Reports are sent only if you choose to share them.
- Separate installation alongside DiAuto. Run one projection app at a time.

This is **not an Apple-certified product**. The APK bundles an experimental accessory identity recovered from public Carlinkit firmware, not a newly provisioned MFi identity for DiPlay. A bundled private key is extractable. Acceptance after future iOS updates, reliability across head units and suitability of that identity for general distribution are unresolved. This release invites community testing; it is not a guarantee of universal compatibility.

Earlier releases were tested on a development Hongqi HS5. Broader head-unit and iOS compatibility is not guaranteed.

## What’s new in 0.2.18

- Wired CarPlay no longer drops every few minutes when a USB read finishes just as DiPlay stops waiting for it, and it now starts on Android 7.1 head units, on head units whose USB audio driver held the iPhone, and without a VPN dialog when network ADB is approved.
- Fewer short music dropouts, and the connection screen shows the real stage and failure in every app language.
- On head units whose Android Bluetooth cannot reach the iPhone, wireless CarPlay stops with a clear message and suggests USB.
- The wheel's next and previous keys change CarPlay tracks on the CarPlay screen without a separate wheel-key service, and wheel keys stuck on "Allowed, starting…" recover by themselves.
- Back up and restore settings from a phone through a QR code in **Settings → About**, and pick another installer, such as MT Manager, for updates.
- New experimental, off-by-default options: clearer navigation voice over USB, low-latency decoding, a media-key audio focus opt-out and car phone audio for calls.

See [CHANGELOG.md](CHANGELOG.md) for release history. General stutter, calls/Siri, decoder and model-specific reports need current-device evidence.

## Privacy and diagnostic reports

Diagnostic reports stay on the device until you choose to view, copy or share them. Review the `.txt` before attaching it to an issue and remove private details such as phone identifiers or network credentials. Reports are not uploaded automatically. Include the HS5 firmware and Android version, phone/iOS, connection backend, relevant settings, reproduction steps and failure time. Never post your hotspot password.

## Documentation

- [Build from source](BUILD.md) — select `mobile` for the main DiPlay app; `maphost` is a map sample.
- [Release notes](CHANGELOG.md)
- [Security policy](SECURITY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

The app and release website are available in English, Arabic, Russian, Ukrainian, Spanish, Simplified Chinese and Traditional Chinese (Taiwan). Traditional Chinese uses Taiwan wording; the app also recognizes Hong Kong/Macao and explicit Hant selections without claiming separate regional translations. Choose the app language in Settings; on Android 13+, it stays synchronized with Android’s per-app language setting.

## Source and credits

Based on [xcertplay](https://github.com/shilapi/xcertplay), GPL-3.0. The home/settings UI and website adapt [DiAuto](https://github.com/shihabal3amri/DiAuto), AGPL-3.0; preserve their notices when distributing modifications. See [third-party notices](THIRD_PARTY_NOTICES.md) and [LICENSES/DiAuto-AGPL-3.0.txt](LICENSES/DiAuto-AGPL-3.0.txt). CarPlay and its icon belong to Apple Inc.; no Apple affiliation or endorsement is implied.

Design prototypes, tester reports, release packages and signing secrets are not part of the source tree. Third-party source and binary components are identified in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); verify their redistribution terms before publishing a mirror or APK.

## Local release packaging

The release APK intentionally contains the experimental accessory identity. The Git repository and source archive exclude all accessory and Android signing keys; tests generate synthetic identities at runtime. Source/CI builds omit runtime identity assets by default. Local release builds explicitly select an external asset directory. Publishing the APK makes its bundled identity extractable; building locally does not preserve that identity's confidentiality.
