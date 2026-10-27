# HS5 都市 / 运动仪表 — 2026-10-27

## Delivered

- 基础 display name is now 都市; stored basic preferences are preserved.
- New 运动 style is selectable from 显示 → 仪表管理 alongside 都市 and 原始.
- Red reference framing, road scene, concentric speed/RPM dials, centre music, right 3:2 CarPlay map and bottom tire layout.
- No top-left speed/gear card is drawn. Actual HS5 scales remain 0–260 km/h and 0–7000 rpm.
- Uses existing vehicle/CarPlay subscriptions and smooth independent needle layer. No new polling loop.
- No maneuver means no local turn card. Per-wheel warnings affect that wheel and its pressure.
- Seven locale sets updated. Existing apply/reconnect confirmation is retained.

## Verification

48 relevant unit tests passed in both the targeted run and the final full CI run, including Sport geometry, absent OEM card, full needle sweeps, map composition, warning colors, saved style compatibility and management page actions.

Full repository-required CI command was executed on this version. mobile/home/maphost Lint and debug assemble all passed. Full CI exits nonzero for the same 18 previously recorded test failures; exact failure identities match artifacts/cluster-viewport-ci.log with no additional failing test.

Logs: artifacts/cluster-sport-targeted.log, artifacts/cluster-sport-ci.log.

Existing failures:
- CarHotspotFallbackTest > existingWifiBypassesHotspotControlEvenWithAutoStartupSavedAndApOff
- CarPlayHostThemeDiagnosticsTest > changedThemeIsSentOnceAndTheUnavailableEventChannelIsInTheSavedReport
- CarPlayHostThemeDiagnosticsTest > fixedDayIsNotOverriddenBySystemNightAndDiagnosticsKeepBothStates
- CarPlayHostThemeDiagnosticsTest > undefinedSignalKeepsThePreviousModeAndIsRecordedWithoutASend
- CarPlayStageTextTest > chineseStages
- CarPlayStageTextTest > chineseStages[25]
- CarPlayStageTextTest > englishStages
- CarPlayStageTextTest > englishStages[25]
- HostConnectionSettingsRefreshTest > projectionScreenUsesConnectionSettingsSavedWhileItWasInTheBackground
- HotspotJoinNormalizedTransactionTest > rejectedAndCancelledWritesRestoreTheFullOriginalWithTheNormalizingSetter
- HotspotJoinNormalizedTransactionTest > unexpectedNormalizationStillRetainsTheOriginalAndRefusesDrift
- HotspotJoinNormalizedTransactionTest > userConfigurationAppliesAndRestoresTheCompleteParcelAfterRecreation
- SmoothVideoHostLifecycleTest > aHostRebuildingItsVideoViewReleasesTheSessionSlotFirst
- SmoothVideoHostLifecycleTest > aNewSmoothHostStopsANonPacingBackgroundSessionOnceAndThenStartsItsOwn
- StartupDiagnosticSnapshotTest > aSecondBootReplacesOldLaunchEvidence
- StartupDiagnosticSnapshotTest > successfulLaunchMeansAnActivityRequestAndRetainsTheExistingFlags
- WifiP2pGroupManagerTest > disabledWifiHasAnActionableErrorWithoutCreatingAnything
- WifiP2pLegacyGroupManagerTest > legacySecondStartReusesGroupWithoutRecreating

## Review artifacts

- APK: artifacts/DiPlay-HS5-20261027-sport.apk
- APK SHA-256: 8662857BA7F019C838EE7543C29890EB3781996E12E8AE29968698E3F5B30128
- Native preview: artifacts/cluster-sport-preview.png (Android renderer, not a separate HTML reimplementation)
- Full/compact/Arabic RTL font-scale 1.4 screenshots: common/build/reports/cluster-manager/
- Native idle/waiting/tire-warning/compact screenshots: common/build/reports/cluster-dashboard/
- Verified canvas draft: e02d6834-75fa-42e4-aa54-538d7af93b20, version 1

The screenshot map and song are test-only reference fixtures. Production uses the independent CarPlay video Surface and live metadata. This implementation has not yet been tested on the physical HS5; actual OEM overlay, map framing and screen color need an in-car check.

