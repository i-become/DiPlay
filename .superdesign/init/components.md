# Shared UI (Compose) — key primitives

Paths under `common/src/main/java/com/shilapi/xcertplay/newui/`:

| Component | File | Role |
|-----------|------|------|
| NewUiGlassCard | NewUiComponents.kt | Frosted card container |
| NewUiToggle | NewUiComponents.kt | iOS-like switch |
| NewUiSidebar | NewUiShell.kt | Vertical nav |
| NewUiTopBar | NewUiShell.kt | Title + status |
| InternalActionButton | NewUiConnectionScreen.kt | Large tile CTA |
| ConnectionMethodChip | NewUiConnectionScreen.kt | Method picker row |

Redesign may replace visuals but must preserve control semantics and touch sizes.
