# Native installation integrity

Release builds pin `com.shihab.diplay` and the current release certificate SHA-256 in
`shared/src/main/jni/diplay_integrity.c`. Only one current APK signer is accepted;
certificate rotation must be reviewed and the pin updated deliberately. Packaging fails
if the configured release keystore certificate differs from the native pin.

The native library verifies installation identity before performing the local CarPlay
MFi challenge signature or calling the Carlink SDK initializer. The Java layer no longer
ships an unguarded local challenge signer in release builds. Identity validation at bootstrap
continues to validate the P-256 certificate and key. The app's lifecycle observer displays
the native warning after the delayed-notice deadline; deleting that observer does not enable
either native operation. Native connection enforcement still applies immediately.

Successful checks are cached only in process memory. No network request, periodic disk log,
video callback check, or live-session teardown is introduced. Authentication still uses
NONEwithECDSA over the supplied 32-byte digest; DER-to-raw conversion is unchanged.
Concurrent first checks serialize behind a native mutex; successful checks do not reread the APK.
The build includes arm64-v8a, armeabi-v7a, x86 and x86_64. Debug native builds explicitly
disable certificate enforcement at compile time; release has no debuggable/property bypass.
Missing native libraries fail closed in release.

This detects ordinary modified/re-signed APKs. It is not a license server, in-memory code
attestation, or protection against a compromised official signing key, rooted hooks, or a
patched/replaced native library. The accessory identity remains an explicitly provisioned
asset; it is not made unextractable by this change. No third-party SDK internals are modified.

## Release APK content seal

The release pipeline now runs: R8 -> Carlink SDK injection -> content seal -> zipalign ->
original APK signing -> APK signature verification -> content seal verification -> SDK audit.
`scripts/ApkContentSeal.java` hashes every uncompressed APK entry except `META-INF/` and
the two seal files. This covers all DEX files (including the injected SDK), AndroidManifest,
resources, assets, and every ABI's native libraries. Sorted SHA-256 records are signed with
SHA256withRSA using the existing release key. Only the public SPKI key is compiled into
`shared/src/main/jni/seal_authority.h`. No signing private key/password is packaged.
Credentials for build subprocesses are passed through environment variables rather than
command-line password arguments. The build checks both the certificate pin and seal authority
against the configured release certificate before sealing.

The native gate verifies the signed manifest against the independent compiled public key,
then hashes the actual installed APK through standard platform ZipFile/MessageDigest APIs.
Missing/changed/added/duplicate covered entries, missing or forged seals, and split APKs
fail closed. These builds are standalone APKs; AAB/split delivery requires a separate design.
Verification uses bounded heap buffers and streaming reads; no decrypted DEX, extracted
resources, extra disk logs, replacement Application, hidden Android APIs or classloader shell.
Debug source builds skip enforcement at compile time and are not distribution artifacts.

Native operations use RegisterNatives; only JNI_OnLoad is exported from the native
library. This removes descriptive operation symbols, but does not make native code impossible
to disassemble. The APK seal detects on-disk changes; it is not DEX encryption or a hook-proof
execution environment. An attacker who patches/replaces the native gate can still bypass it.
Keep the release keystore private and distribute only the signed release artifact.

## Delayed notice policy

The visible notice is deferred for 30 minutes from native-library initialization in each
app process (initialized by the bootstrap provider). The native CLOCK_BOOTTIME deadline is
independent of the wall clock and survives activity changes; process termination resets it.
Time in the background counts, but a dialog is shown only when an app activity is resumed.
After a background expiry the next resume displays it; if still foreground at expiry, the
scheduled callback displays it without requiring a screen change. A process displays at most
one notice. Official unmodified APKs never display the notice.

`warning()` itself returns null before the native deadline. Connection/authentication gates
do not gain a grace period: invalid installations are still refused immediately, with a generic
connection error instead of the notice text. This prevents those exception messages from
revealing the notice through connection diagnostics before its display deadline. The missing-
library UI fallback uses the process start elapsed time with the same 30-minute delay.
There are no production clock overrides or reduced-delay test modes.

## Verification

Build with `-PintegrityReleaseTest=true :mobile:assembleRelease :mobile:assembleAndroidTest`.
Install the release APK and its test APK on an isolated emulator, then run:

```
adb shell am instrument -w com.shihab.diplay.test/com.shihab.diplay.test.IntegrityInstrumentation
```

Expected: `ACCEPTED: no warning + valid native ECDSA`. The probe also rejects a 31-byte digest.
On a disposable emulator only, uninstall both packages, re-sign copies of both APKs with a
different test key, and install the copies. Run the same instrumentation command.
Add `-e expected rejected` before the instrumentation component for negative cases.
Expected: `REJECTED: signing + Carlink; warning deferred` before 30 minutes. The probe checks
the native countdown, absence of an early notice and the generic exception message.
Launch the app and leave it running for 30 minutes to verify the real-time visible notice.
Never replace the production APK output with the re-signed test copy.

Run `python scripts/test_apk_content_seal.py` (JDK 17+ on PATH) for disposable-key tests of
the host tool: repeat sealing, both DEX files, resource/manifest/native/identity modifications,
forged seals, missing/added entries and duplicate entries. The instrumentation probe asserts
the expected verdict explicitly, verifies native ECDSA, checks invalid input, and reports
first-check/cached-check time. Negative cases test the signing and Carlink gates independently
of the visible warning.

Run the complete CI command from AGENTS.md separately from this device probe. A real-car
connection regression test is still required for both protocols; an emulator cannot emulate
the Hongqi vehicle services or an iPhone MFi handshake.

## Earlier verification on 2026-11-18 (before delayed notice)

- Official release: ACCEPTED; native ECDSA verified and invalid digest length rejected.
- Same APK re-signed with a test certificate: warning returned; both native signing and
  Carlink initialization rejected. Launching NewUiActivity displayed the warning dialog.
- Required CI ran in full: lint and all three debug assemblies passed. The 26 failed unit
  tests exactly match `build/1.4.1-ci.log` (8 shared, 18 common); no new failing test names.
- Evidence: `build/native-integrity-official-device.log`,
  `build/native-integrity-resigned-device.log`, `build/integrity-negative/warning.png`,
  and `build/native-integrity-ci.log` (local ignored build outputs).

### Additional content-seal verification on 2026-11-18

- Release and instrumentation assemblies succeeded; final APK content verification and Carlink
  audit passed with zero audit errors.
- Official sealed APK accepted on isolated API 34 x86_64 emulator; valid native ECDSA.
  First verification 97 ms, 100 cached warning checks <1 ms (emulator, not a vehicle benchmark).
- Four modified APKs signed by the official certificate (changed asset, added entry,
  removed seal, forged manifest) were rejected by the native warning/signing/Carlink gates.
- A changed SDK DEX was rejected by the host verifier. It was not launched on device because
  invalid DEX bytes can fail Android's own verification before reaching the integrity probe.
- A different-certificate re-sign was rejected by all three gates on the emulator.
- Warning text visually verified in `build/content-seal-negative/warning.png`.
- Host-tool disposable-key tests passed (four test methods with multiple payload subcases).
- All four ABI libraries export only JNI_OnLoad (checked with llvm-nm).
- Required CI completed: lint and all three debug assemblies passed; exactly the same 26
  unit test failures as `build/1.4.1-ci.log` (no new failing test names). Full CI is not green.
- Restored official release was accepted again (98 ms first check); its application UI
  opened without the tamper warning. The isolated emulator was shut down after testing.
- Evidence: `build/content-seal-release.log`, `build/content-seal-official-device.log`,
  `build/content-seal-*-device.log`, `build/content-seal-tool-tests.log`, and
  `build/content-seal-ci.log`. Real vehicle regression remains required.

### Delayed-notice verification on 2026-11-18

- Five Robolectric virtual-time tests passed: exact deadline, activity changes, background
  expiry, destroyed activity, and healthy installation. No production test-clock bypass.
- On the isolated API 34 emulator, official release and restored release passed native
  ECDSA checks. Fresh native countdowns were approximately 1,800,000 ms.
- A modified asset APK signed with the official key and a differently re-signed APK both
  rejected native signing and Carlink initialization immediately. Their app UI opened
  without an early notice; native warning access returned null before the deadline.
- Actual 30-minute elapsed device popup testing has not been performed; deadline display
  behavior is covered by virtual-time lifecycle tests. Real vehicle regression remains required.
- Final release content seal and SDK audit passed (zero audit errors). Required CI completed:
  all three lint/debug assemblies passed; the same 26 pre-existing unit test failures as
  `build/1.4.1-ci.log` remain, with no new failing test names. Full CI is not green.
- Evidence: `build/integrity-delayed-warning-ci.log`,
  `build/integrity-delayed-warning-device-tests.log`, and
  `build/integrity-delayed-warning-*-device.log`.
