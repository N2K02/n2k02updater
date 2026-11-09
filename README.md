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


## Resilient APK downloads

- HTTPS-only redirects, checksum-scoped partial APKs, and safe HTTP Range validation.
- Retry HTTP 408/502/503/504, socket timeouts and unexpected EOF up to three times
  with bounded backoff. Rate limits (403/429), bad checksums, invalid package names
  and untrusted signing certificates never trigger automatic retries.
- Transient HTTP errors leave partial APK bytes intact; ignored or malformed Range
  responses restart at offset zero to prevent file corruption.
- Download logs use the `N2K02Updater` tag and include only the server hostname,
  HTTP status, attempt and retry reason; URLs and authentication query strings
  must never be recorded.
- All successful APKs are verified for SHA-256, package identity, version
  progression and trusted signing certificate before Android's installer opens.
- Consumers must pin a **published** Maven package release; changing the source
  repository alone does not update already shipped APKs.

## Shared "New" update badges

The updater now owns the pending-update state check and a Material 3 **New** badge.
It shows for both stable and experimental releases in `Available`,
`Downloading`, `Ready`, `NeedsInstallPermission`, and recoverable failed
(download/install) states. It does not appear for `Idle`, `Checking`,
`Current`, or failed checks without a known pending release.
The page intentionally does **not** draw a `New` badge: its status panel already says whether a new update is available. Host navigation is the only place the badge belongs.

**Placement policy:** Put the indicator on the top-level **Settings** menu/navigation entry and its **About & updates** submenu item. Do not repeat it on the destination page, the update status/action card, or a second **Updates** navigation tab. The badge stays visible while a pending update exists, including during download and after a recoverable failure.

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



## Expressive single-page layout (app icon + animated update states)

The shared update screen now follows the compact MRadio/NetSpeed design:
- The host's **About & updates** title appears once. Put `N2K02UpdateTitle()`
  in your `TopAppBar.title` to show the **actual installed app launcher icon**
  beside it. The full page may set `showHeader = true` when no host title exists.
- The page starts with the app icon, name, version/build, Installed chip, last
  installed/updated date and last successful check. The experimental toggle
  follows immediately when a host supplies it.
- A single large, animated status panel replaces the old duplicated status
  and installed-app cards. One primary action changes between Check again,
  Download, Install, Permission, Retry and Cancel (download).
- Details are expandable: installed/available versions and release date;
  What's new; verification and signer security; check cadence and mobile-data
  behavior. Expanding them uses a spring-size transition, while update states
  use a fade-and-slide transition with animated container color.
- Back navigation and bottom navigation stay **host-owned**. No new settings
  switches or automatic-install behavior are invented.

```kotlin
TopAppBar(title = { N2K02UpdateTitle() }, navigationIcon = { /* host back */ })
// Below the host top bar, with no duplicate title:
N2K02UpdatePage(
    controller = LiveUpdater.controller,
    installGuard = LiveUpdater.installGuard,
    experimentalEnabled = experimental,
    onExperimentalEnabledChange = LiveUpdater::setExperimentalEnabled,
)
// In an existing scrollable Column, use N2K02UpdateScreen(controller, guard).
```


## Compact host-owned About & updates layout

Each host app supplies **one** page header and its own navigation/back behavior.
Use `N2K02UpdateTitle()` inside the existing top app bar or page header
instead of creating another header inside the updater. Keep the Settings
and About & updates pages in the app's normal navigation structure.
The shared updater page itself has no back button or host navigation bar.

The compact page shows the app name/version, last installed/updated date,
**last checked** time, experimental-release switch, update status and the
single action button. Dynamic release notes and download information
appear only when an actual release is available. The previous static
Version details, What's new, Update security and Update settings menu,
along with repeated security footer text, are removed.

Every manual check records the attempt time **as the check starts**, including
a failed network request; the time updates again when that check completes.
The last successful check is retained independently and is not mislabeled
as the last attempted refresh. The displayed time includes seconds.

Avoid nested scrolling: use `N2K02UpdatePage` as the only scrolling child in
a standalone page, with the host `Scaffold`'s `innerPadding`; use
`N2K02UpdateScreen` only inside an existing scrollable Settings container.
The host is responsible for presenting an appropriate title only once.

## Complete shared About & updates page

Each app can now open **one** shared page from its existing Settings submenu.
Use `N2K02UpdatePage` for a standalone scrollable Settings destination, or
`N2K02UpdateScreen` for an existing scrollable Settings container.

The page inherits the host's Material 3 colors, shapes and typography and includes:
- Installed app name, version name and Android versionCode.
- Last installed/updated time from Android's package manager.
- Last successful update check, and the most recent failure when applicable.
- Available version, build number, release channel, optional publication date and APK size.
- Persistent `New` badge for pending stable and experimental updates.
- User-initiated check, download/progress/cancel, install, install permission, recovery retry,
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
that never refreshed when the status remained `Current`. Successful automatic checks are throttled for 12 hours; failed checks are
throttled to a 15-minute retry interval rather than remaining stale all day. Opening the shared update page
also performs a throttled refresh, and the **Check again** button bypasses
the throttle.

If the previous offer is still newer than the installed APK, it is restored
immediately after restarting the app—even when the next network check is
throttled or temporarily fails. Only release metadata is restored, not a
downloaded APK's verification status. Incomplete downloads retain release-specific partial bytes in the app cache.
If the server supports HTTPS Range requests, a retry or process restart can
resume from the last saved offset. Without Range support the download restarts.
**No partial file is ever installable:** SHA-256, APK package identity,
versionCode and signing identity are checked on the entire APK each time.
A completed APK's verified/Ready state is not restored after process death;
it must be verified again before installation. Changing apps, installed
versions, or release channels invalidates stale cached offers.


## Real-world reliability and compatibility

- **Transient failures:** a failed download keeps the release and its **New** badge;
  retrying does not require checking GitHub again. Failed checks retry on the next
  app foreground after 15 minutes; successful checks retain the 12-hour throttle.
- **Storage:** insufficient available space is reported before a known-size APK download.
- **Connectivity:** known large APKs (20 MiB or larger) require confirmation on
  metered connections when network status is available; downloads remain user initiated.
- **Android install:** denied unknown-source permission does not trigger an automatic
  install on return. User must tap **Install update**. A cancelled installer returns
  to the verified Ready state with a retry message. Actual installed version determines
  whether the offer is cleared on return.
- **Release metadata:** `publishedAt` or `releasedAt` in public update manifests
  may optionally be an ISO-8601 instant or Unix seconds/milliseconds; if absent,
  the page omits the release date. GitHub release `published_at` is also supported.
- **Channels:** skips now apply only to stable or experimental channels separately,
  while existing legacy skipped-tag values remain supported.
- **Scrollable standalone page:** The host Scaffold must pass its `innerPadding`
  into `N2K02UpdatePage(modifier = Modifier.padding(innerPadding))`. The page
  fills the available viewport and scrolls itself, with additional bottom spacing
  so Download/Install and expanded detail controls remain reachable above bottom
  navigation and gesture areas. Do **not** place it inside another scrollable
  Column or LazyColumn. When the host already owns vertical scrolling, use
  `N2K02UpdateScreen` instead.
- **Navigation:** `N2K02UpdatePage` hides its own header by default, avoiding
  a duplicate title when the host supplies a Settings top bar. Set
  `showHeader = true` for a full-screen page without an app-level title.
- **Lifecycle:** host apps MUST retain their `UpdaterController` per application
  process. In `LiveUpdater.init(context)`, do not recreate an existing controller
  just because Android has recreated an Activity on rotation. Continue calling
  `controller.onHostResume(activity, installGuard)` each foreground entry.
  The shared library cannot prevent an app from explicitly replacing its controller.
- **Install guards:** the library never auto-installs; recording, playback, calls,
  casting and other active-operation guards remain host-specific.

## One-batch test checklist

The Android library CI runs compilation and all JVM unit tests covering offer
persistence, version/channel migration, checksum metadata, bad manifests,
automatic-check throttles, failure retry timing, badge visibility,
HTTP Range resume validation, release dates, installation permission return,
cancelled installer and installed-version success decisions.
These tests validate logic, but actual device verification is still required for
Android's installer UI, rotation during an active download, OS process termination,
bad Wi-Fi/mobile-data transitions, and recording/casting guards.

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

**Status:** shared updater UI and reliability features are implemented. Each consuming app must update its package dependency and wire its existing navigation and lifecycle adapter to the shared API; this does not migrate host apps automatically.
