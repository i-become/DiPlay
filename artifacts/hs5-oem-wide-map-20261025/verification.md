# HS5 OEM cluster: taller map opening, 2026-10-25

- Removed the centre digital speed, gear and unit and the app-drawn road/turn/distance footer.
- Top and bottom bars share the same 72-unit height in the 1920 × 720 reference plane.
- Map stream and viewport: 960 × 576, 5:3, beginning at (480, 72).
- Native instrument foreground masks the map along circular rims with a cached 24-unit alpha fade.
- Header has time, position/low-beam lights, drive mode, belt/parking warnings and temperature; unavailable vehicle signals remain unknown.

## Validation

All 18 focused dashboard, geometry, compositing and surface-lifecycle tests passed.
The required CI command was run in full with `--continue`. Mobile, home and maphost
lint and Debug assembly passed; home unit tests passed. Shared unit tests had
3 failures, and common had 15 failures. The 18 failure names exactly match
`oem-cluster-final-ci-20261025.log` from the prior round. Full CI is not green.

Release assembly and APK v2 signature verification passed. Runtime asset entries
were checked for presence and nonzero length without reading their contents.
Public-tree credential scan and `git diff --check` passed.

APK: `DiPlay-1.2.1-hs5-20261025-oem-wide-map.apk`, version 1.2.1 (1201).

SHA-256: `B63D33109E99645C4A98D4054A8D66EEBF91C9CE2A292ABF81F595FE947AA408`.

Build evidence is under `.gradle/codex-validation-20261020/`:

- `oem-wide-map-targeted-20261025.log`
- `oem-wide-map-full-ci-20261025.log`
- `oem-wide-map-release-20261025.log`

## Images and scope

The full, compact, day, night, Arabic and waiting screenshots are Android native
renders with a large font scale. CarPlay video cannot be captured by View.draw.
`map-fixture-only.png` uses a labelled synthetic map pattern to inspect the
foreground opening and gradients. `reference-fixture-only.png` uses test-only
vehicle examples to inspect all header glyphs. Neither fixture populates APK data.
The same labelled native compositing preview is saved as Superdesign draft
`f23cfce5-ab23-43e6-afee-512d7bc412fa`, version 4.

Actual CarPlay video and SurfaceView compositing on the head unit still require
vehicle verification. Reconnect CarPlay after installing to negotiate the new
map dimensions. Unsupported vehicle telemetry is not supplied by this UI change.
