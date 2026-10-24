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


## Shared "New" update badges

The updater now owns the pending-update state check and a Material 3 **New** badge.
It shows for both stable and experimental releases in `Available`,
`Downloading`, `Ready`, and `NeedsInstallPermission` states. It does not
appear for `Idle`, `Checking`, `Current`, or `Failed`.
`N2K02UpdateScreen` automatically shows the badge inside its update card.

Apps can opt into the same indicator in navigation without duplicating UI or state logic.
The host app still decides where its Settings, submenu, and update entries appear:

```kotlin
import dev.n2k02.updater.N2K02UpdateBadge
import dev.n2k02.updater.N2K02UpdateBadgedIcon
import dev.n2k02.updater.hasPendingUpdate

val updateStatus by LiveUpdater.controller.status.collectAsState()

// In an existing NavigationBarItem:
icon = {
    N2K02UpdateBadgedIcon(updateStatus) {
        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
    }
}

// In the existing "About & updates" ListItem:
trailingContent = { N2K02UpdateBadge(updateStatus) }
supportingContent = {
    Text(if (updateStatus.hasPendingUpdate()) "A new update is available"
         else "App version and update checks")
}
```

Existing host navigation and install guards are unchanged. The package does not
automatically inject navigation items; each consuming app must wire its menu and
submenu slots to these shared components. Update badges remain visible until the
pending-update state clears; simply opening the update page does not clear them.


## Complete shared About & updates page

Each app can now open **one** shared page from its existing Settings submenu.
Use `N2K02UpdatePage` for a standalone scrollable Settings destination, or
`N2K02UpdateScreen` for an existing scrollable Settings container.

The page inherits the host's Material 3 colors, shapes and typography and includes:
- Installed app name, version name and Android versionCode.
- Last installed/updated time from Android's package manager.
- Last completed update check, retained across restarts.
- Available version, build number, release channel and known APK size.
- Persistent `New` badge for pending stable and experimental updates.
- User-initiated check, download/progress/cancel, install, install permission, retry,
  skip-version, diagnostics and expandable release notes.
- Optional experimental-release switch driven by the host's existing preferences.
- No automatic installation; the app-supplied install guard is always enforced.

```kotlin
// Settings > About & updates page; the host still supplies Back navigation.
val experimental by LiveUpdater.experimentalEnabled.collectAsState()
N2K02UpdatePage(
    controller = LiveUpdater.controller,
    installGuard = LiveUpdater.installGuard,
    experimentalEnabled = experimental,
    onExperimentalEnabledChange = LiveUpdater::setExperimentalEnabled,
)
```

To reliably detect releases while the app stays alive, call this on **every**
`Activity.onResume()`, regardless of the current update status:

```kotlin
LiveUpdater.controller.onHostResume(this, LiveUpdater.installGuard)
```

This replaces the older `if (status == Idle) check(manual = false)` pattern
that never refreshed when the status remained `Current`. The check interval is
still 12 hours to avoid needless requests. Opening the shared update page
also performs a throttled refresh, and the **Check again** button bypasses
the throttle.

If the previous offer is still newer than the installed APK, it is restored
immediately after restarting the app—even when the next network check is
throttled or temporarily fails. Only release metadata is restored, not a
downloaded APK's verification status. After process death, the user must
download and verify the APK again before installing. Changing apps, installed
versions, or release channels invalidates stale cached offers.

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

**Status:** shared updater implementation exists; the common badge API is available on this branch. Publish a new versioned package after the change passes CI and is merged. Existing consumers must update their dependency version and connect their navigation slots.
