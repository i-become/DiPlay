# DiPlay New UI routes (Compose, not web)

Entry activity: `NewUiActivity.kt`

| Destination | Screen composable | Purpose |
|-------------|-------------------|---------|
| CONNECTION | `NewUiConnectionScreen.kt` | Phone link, CarPlay entry, connection method, location toggle |
| DISPLAY | `NewUiDisplayScreen.kt` | CarPlay display size, resolution, FPS subpages |
| SETTINGS | `NewUiSettingsScreen.kt` + subpages | Full settings IA (mirrors legacy DiPlay settings) |
| DIAGNOSTICS | `NewUiDiagnosticsScreen.kt` + storage/QR subpages | Logs, reports, share |
| ABOUT | `NewUiSettingsAboutScreen.kt` | Version, links |

Shell: `NewUiShell.kt` — top bar + left sidebar + content column.

Subpages: `NewUiConnectionSubpage.kt`, phone picker, switch phone, connection settings intents.
