#!/usr/bin/env bash
set -euo pipefail

work="$RUNNER_TEMP/legacy-install"
mkdir -p "$work"
exec > >(tee "$work/install-result.txt") 2>&1

curl --fail --location --retry 2 --max-time 90 "$OLD_APK_URL" --output "$work/old.apk"
curl --fail --location --retry 2 --max-time 90 "$NEW_APK_URL" --output "$work/new.apk"
printf '%s  %s\n' "$OLD_APK_SHA" "$work/old.apk" "$NEW_APK_SHA" "$work/new.apk" | sha256sum --check

adb root
adb wait-for-device
test "$(adb shell id -u | tr -d '\r')" = 0

package_code() {
  adb shell dumpsys package "$APK_PACKAGE" > "$work/package-$1.txt"
  grep -m 1 -o 'versionCode=[0-9]*' "$work/package-$1.txt" | cut -d= -f2
}

marker="/data/user/0/$APK_PACKAGE/legacy-update-smoke"
adb install --no-streaming "$work/old.apk"
test "$(package_code old)" = "$OLD_CODE"
adb shell "printf 'legacy-data' > '$marker'"

adb install --no-streaming -r "$work/new.apk"
test "$(package_code new)" = "$NEW_CODE"
test "$(adb shell cat "$marker")" = legacy-data

if adb install --no-streaming -r "$work/old.apk" > "$work/rejected-downgrade.txt" 2>&1; then
  echo "Android accepted an APK with a lower versionCode"
  exit 1
fi
grep -q INSTALL_FAILED_VERSION_DOWNGRADE "$work/rejected-downgrade.txt"
test "$(package_code after-rejected-downgrade)" = "$NEW_CODE"
test "$(adb shell cat "$marker")" = legacy-data
echo "New APK installed in place, original data survived, and Android rejected the downgrade."
