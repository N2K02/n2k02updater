# Migration plan

## Design decision
The updater owns update-specific states and presentation. `n2k02theme` owns general-purpose visual primitives. Do not duplicate the updater screen in both repositories.

## First integration: mRadio
- Inventory `AppUpdater`, `UpdateUi`, `UpdateBadge`, `UpdateFileProvider`.
- Move platform-independent release parsing and policy behind an interface.
- Keep existing preferences, release channel semantics and notifications.
- Validate parity using existing tests and new failure-mode tests.

## Second integration: BCam
- Adapt current `AppUpdater` to the shared engine.
- Keep recording stop/wait checks in a host-controlled install guard.
- Preserve verified download and installation permissions.
- Replace updater UI with the shared screen.

## Remaining apps
MRadio, BCam, HFreebuds, PixelCast, NetSpeed, PBar, PCL, ShizuCallRecorder, SpeedTester, AAYoutube and VREC.

Use review branches and CI checks. Do not merge app main branches automatically.

## Packaging
Start with versioned source integration for the pilot; move to authenticated GitHub Packages once CI access and compatible Gradle/Compose versions are confirmed. Never commit tokens or signing keys.
