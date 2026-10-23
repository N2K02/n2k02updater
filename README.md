# N2K02 Updater

Private, reusable Android in-app updater for N2K02 apps, based on the existing mRadio updater.

## Reference implementation
- `N2K02/MRadio` branch `ui-rounded-menus`
- `app/src/main/java/com/mradio/app/AppUpdater.kt`
- `app/src/main/java/com/mradio/app/UpdateUi.kt`
- `app/src/main/java/com/mradio/app/UpdateFileProvider.kt`
- `app/src/main/java/com/mradio/app/UpdateBadge.kt`

## Architecture
1. **Updater core:** GitHub release discovery, stable/experimental channels, rate-limited checks, download progress, cache cleanup, errors.
2. **APK security:** verify expected package name, version progression, archive integrity, and trusted signing certificate before handing to Android's installer.
3. **Compose UI:** match mRadio's About & updates page, diagnostics, release notes, loading and permission states; use N2K02Theme design tokens.
4. **Host adapter:** each app supplies repository, installed version, update policy, and lifecycle hooks. Recording, casting, or active call sessions must not be interrupted.
5. **Distribution:** versioned private dependency; public consuming repositories must authenticate without exposing credentials.

## Rollout
- Stage 1: extract and test mRadio's existing behaviour without regression.
- Stage 2: pilot mRadio and BCam; preserve BCam's recording-safety gate.
- Stage 3: adopt in the remaining apps individually, with build checks and reviewable PRs.

## Acceptance checks
- Stable and experimental release selection.
- Network failures and GitHub API rate limits.
- No matching APK and malformed release metadata.
- Download cancellation and retry.
- SHA-256, package identity, version, and signer checks.
- Android unknown-app install permission.
- Process recreation, accessibility, dark/light and large text.
- No automatic install during active recording or other protected operations.

**Status:** architecture and migration plan only. No shared updater implementation has been extracted or published yet.
