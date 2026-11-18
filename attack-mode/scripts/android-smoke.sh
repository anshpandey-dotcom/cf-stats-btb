#!/usr/bin/env bash
set -u

apk="$1"
package="com.attackmode.planner"

adb install -r "$apk" || { echo "install failed"; exit 1; }
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n "$package/.MainActivity"
sleep 25

adb exec-out screencap -p > smoke-home.png
adb logcat -d > smoke.log

echo "---- crash lines ----"
grep -E "FATAL EXCEPTION|AndroidRuntime|Process: $package|ANR in" smoke.log | head -40

if ! adb shell pidof "$package" > /dev/null; then
  echo "app is not running after launch"
  exit 1
fi
if grep -q "FATAL EXCEPTION" smoke.log; then
  echo "app crashed"
  exit 1
fi

echo "---- posting a test reminder ----"
adb root > /dev/null 2>&1 || true
sleep 3
adb shell am broadcast -n "$package/.ReminderReceiver" -a com.attackmode.planner.REMINDER \
  --ei attackmode.id 7 --es attackmode.title "Starting now: Smoke test" \
  --es attackmode.body "09:00 - at Desk" --es attackmode.url /today
sleep 4
adb shell dumpsys notification --noredact | grep -A6 "pkg=$package" | head -30 > smoke-notification.txt
cat smoke-notification.txt
adb exec-out screencap -p > smoke-after.png
echo "app is running"
