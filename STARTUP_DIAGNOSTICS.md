# Hongqi startup evidence (schema 2)

Enable session log recording before the test, then use one genuine power-off/cold-start cycle.
Leave DiPlay to auto-connect without toggling Bluetooth, Wi-Fi or Reconnect during observation.
Export one report after the picture appears and remains stable for about 10 seconds. If it never
connects, export after three minutes. Do not clear logs or force-stop the app before exporting.

The Diagnostics page's session recording switch is the single opt-in for application logcat,
session files, startup/update/display evidence, media statistics, audio captures and report
generation. Its saved value is loaded before boot prewarm; the default is off. Turning it off
cancels diagnostic observers and pending log writes, and stops QR report sharing. Captured
callbacks from an older recording cannot write after the switch is enabled again. Previously
saved reports and logs remain available to view or clear. Report export requires recording
to be enabled. Disabled diagnostic paths return before formatting, sampling or writing;
normal connection, rendering, vehicle-light synchronization and UI progress remain active.

The report contains a separate process startup timeline, independent of the UI and the session log.
Events carry sequence number, elapsed time since boot, time since capture, uptime and thread name.
It records boot delivery, automatic launch, saved-phone preconnect, controller/run identifiers,
Bluetooth enable requests/results, both public adapter API paths, Bluetooth/ACL/bond/discovery
broadcasts, Wi-Fi/P2P broadcasts, permissions, P2P callbacks, interface/kernel receive counters,
Bonjour, TCP, iAP2/MFi and AirPlay progress, decoder configuration/input/output and first frame.

Radio API state is sampled every 250 ms; changed state is emitted immediately and unchanged state
once per second. The two adapter paths can return the same object; `sameAdapter` and adapter IDs
make this explicit. Broadcasts have a separate HandlerThread. Registration completion and failures
are recorded so a missing broadcast is not mistaken for an absent state change. The independent
health observer records CPU time, main-queue delay, observer call age and elapsed/uptime differences.
When a main/radio call stalls, it includes bounded stacks. Wi-Fi startup snapshots run every second,
on their existing background observer, starting before the controller waits for Bluetooth. After
three minutes of that connection attempt, Wi-Fi snapshots return to the original ten-second rate.

The boot radio observer ends after 180 seconds. Key connection events remain eligible for 600 seconds.
The memory budget keeps the first 512 and last 1536 events, explicitly counting any omitted middle
events. Disk writes are queued, bounded and redacted, without synchronous transport/UI file writes
for this new trace. Export merges the disk history (up to 2 MiB) with the memory head/tail by sequence
number, recovering middle events already evicted from memory and the tail not yet written to disk.
The previous process trace survives process recreation; its boot counter and
monotonic times must be checked before treating it as the same boot. Storage errors, dropped queued
entries and limits are reported. Clearing session logs also clears this trace; disabling session
recording stops accepting trace events. No scan, Bluetooth enable, connection or probe is added by
the observer.

Interpretation limits: Android APIs/broadcasts cannot prove hardware readiness before the app
process starts, or expose every vendor Bluetooth firmware state. Some legacy P2P firmware does
not report associated iPhones; first accepted TCP is stronger evidence of end-to-end connectivity.
`enable accepted=true` is only the return value of a request, not proof that the radio is ready.
API reads and broadcasts disagreeing supports delayed/stale observation; both remaining OFF does
not distinguish a genuinely off radio from a vendor service that has not published its state.
