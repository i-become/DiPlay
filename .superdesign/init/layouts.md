# Layout: NewUiShell

Path: `common/src/main/java/com/shilapi/xcertplay/newui/NewUiShell.kt`

Structure (1920×720 landscape):
1. Full-screen background (`NewUiScreenBackground`)
2. Top: glass top bar (`NewUiTopBar`) — branding + `NewUiTopBarStatus`
3. Horizontal divider
4. Body Row:
   - Left: `NewUiSidebar` (~fixed width) — 5 nav items + head-unit home
   - Right: content column with padding — page composable

Connection page fills content with:
- Top weighted hero card (status + phone + method chip)
- Middle row: 3 equal action tiles
- Bottom: location reporting card with toggle
