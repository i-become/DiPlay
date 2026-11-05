# HS5 Sport cluster

User reference: fdb15352ca58e47c22aec303c8901e97.png, 2048 × 768.

Rename existing basic style to 都市 while preserving its stored basic preference key. Add 运动 with sport preference key; preserve 原始.

Reproduce reference red framing, road artwork, concentric dials, music well, right map aperture and bottom tire arrangement. Never draw the top-left speed/gear card because the head unit supplies it. Actual HS5 scales remain 0–260 km/h and 0–7000 rpm. Live data and existing smooth needle motion replace demonstration values. No new vehicle polling is introduced.

The imported preview is a screenshot of the actual Android renderer, not an independent HTML mockup. Map and song in this screenshot are test fixtures; production uses existing independent CarPlay map Surface and metadata subscriptions. The APK contains no demonstration map/song. Missing guidance hides its local card. Unknown vehicle values remain unknown. Tire warnings turn their own wheel and pressure red.

2026-10-27 refinement: scale content to 96% and translate down by 24 reference pixels, using the same transform for artwork, needles and map Surface. This clears the OEM top mode label while retaining the bottom coolant/fuel area. Superseded by the 2026-10-28 contour refinement below. Increase source rendering density by 1.5x (capped at 150%) in both browsing and navigation; confirm actual geographic zoom on the head unit.

2026-10-28 refinement: the 4:3 video Surface overscans from x=1232..2048 and y=48..660 behind the tachometer. The native artwork follows the curved inner red frame, and a short left-edge alpha ramp dissolves the lower-left map corner into the red lower arc instead of leaving a right angle. Preserve original luminous red lines. Extend only the RPM needle inward by one third; retain its outer tip and event-driven animation. Restore the fixed 120 outer-scale tick and remove the dynamic top speed readout and its unit; retain the bottom speed unit. Default style remains Original.

Native source: common/src/main/java/com/shilapi/xcertplay/SportClusterRenderer.kt
2026-10-28 lane guidance and compact refinement: retain the same red design and map aperture. Share a content-sized turn/lane card with Urban, preserving existing distance/road fonts and turn-arrow size. The single left-to-right lane row is 56 design pixels tall, with minimal padding. Turn and lanes appear independently; hide the whole card when both are absent. Recommended branches are cyan, thicker and underlined; other branches grey. Remaining distance and duration use a separate content-sized strip with a small text gap, bottom edge moved from 610 to 624. Show each supplied value independently and hide the strip without valid route totals. Route totals and lanes no longer require next-turn metadata. No additional polling; current-junction index and showing status govern lane visibility. Preview map, song and lane data are fixtures from native rendering tests.
Preview: common/build/reports/cluster-dashboard/sport-reference-composite.png
Uploaded native asset: https://vgbujcuwptvheqijyjbe.supabase.co/storage/v1/object/public/hmac-uploads/projects/39839b53-6199-4aec-abff-348506995bd9/content-assets/hs5-sport-native-20261027/sport-reference-composite.png

Validate full, compact, high-density, large-font and Arabic RTL instrument management. Actual head-unit compositor appearance still requires in-car confirmation.

