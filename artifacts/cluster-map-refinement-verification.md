# HS5 cluster map refinement — 2026-10-27

- Urban: map extends from the left dial centre to the right dial centre, from dial tops to tire panel top. Dial artwork overlays live video; broad side gradients removed.
- Both custom styles: source rendering density multiplied by 1.5 and capped at 150%, widening map context without switching Surfaces on navigation state changes. Actual Amap zoom must be verified on the head unit. Existing marker/crop preferences remain applied.
- Sport: one consistent 96% content transform with 24px downward offset for art, needles and video; bottom OEM coolant/fuel clearance retained. Top/bottom map fades enlarged; dial seam narrowed.
- Original remains the default style. No additional vehicle polling.

## Verification

Targeted geometry, compositing, rendering, navigation-state, crop, animation and style tests passed. Native full/compact screenshots inspected. Previews use fixture map/music, not captured live CarPlay video.

Required CI command executed with the local validation init script and --continue. All three lint tasks and APK builds passed; full unit suite retains the exact 18 pre-existing baseline failures (3 shared, 15 common), with no added failures. Compare artifacts/cluster-map-refinement-ci.log against artifacts/cluster-default-original-ci-final.log.

APK: DiPlay-HS5-20261027-cluster-map-refinement.apk
SHA-256: BB1EBC56DCB12EF35F53514F08313AD2994EFDCC49E083ED6C0954C7396D4F52

Preview images: cluster-urban-map-refinement.png and cluster-sport-map-refinement.png.

