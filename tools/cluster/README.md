# Basic HS5 cluster artwork

The blue instrument surround and three vehicle cutouts derive from the user-supplied
2048 x 768 reference image (SHA-256 d9e2ca89be7389351c04446d46815a07f794802a52c94671f054f7a503bf3aff).
The extraction tool removes illustrative map, media, navigation, pressure, needle and
scale content. Android draws actual HS5 scales (0–7 x1000 rpm; 0–260 km/h with
200/230/260 upper marks), sharing angle functions with the moving needles.

Rebuild using Node.js and Sharp:

	node tools/cluster/extract-basic-art.cjs <reference.jpg>

The complete dials and their lower light trails move 100 reference pixels outward.
The empty central lower frame stretches to meet them; the footer line rises 8 pixels.
The transparent video aperture is 1008 x 504 (2:1), starting at (493, 80).
The cached foreground feathers all four video edges and the curved dial rims.
A narrow horizontal marker safe area is centred on the persisted driver setting;
its declaration is identical while browsing and navigating, and the map draws outside it.
Media controls draw over the continuous dial shading without a rectangular patch.
Turn/route cards draw only with current connected guidance. Each wheel and its pressure
text follow that wheel's OEM warning flag; no pressure threshold is invented.

The reference map and sample metadata exist only in the screenshot test fixture.
Production map content comes from the independent CarPlay surface; metadata comes
from the live vehicle/CarPlay subscriptions. An unavailable signal stays unknown.

