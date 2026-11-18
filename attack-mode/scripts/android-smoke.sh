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

echo "---- reinstall and restore from Documents/Attack Mode ----"
adb uninstall "$package" > /dev/null
today=$(adb shell date +%F | tr -d '\r')
adb root > /dev/null 2>&1 || true
adb shell mkdir -p "/sdcard/Documents/Attack Mode"
adb shell "cat > '/sdcard/Documents/Attack Mode/attack-mode-data.json'" <<JSON
{"nextId":2,"savedAt":"2099-01-01T00:00:00.000Z","tasks":[{"id":1,"date":"$today","what":"Restored smoke task","howMuch":"","when":"","where":"","perceivedDifficulty":7,"actualDifficulty":null,"completed":false,"completedAt":null,"createdAt":"2099-01-01T00:00:00.000Z","updatedAt":"2099-01-01T00:00:00.000Z","time":"","remindBefore":10}]}
JSON
adb install -r "$apk" || { echo "reinstall failed"; exit 1; }
adb shell appops set "$package" MANAGE_EXTERNAL_STORAGE allow
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n "$package/.MainActivity" > /dev/null
sleep 25
adb exec-out screencap -p > smoke-restored.png
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb shell cat /sdcard/ui.xml > smoke-ui.txt 2>/dev/null
if grep -q "Restored smoke task" smoke-ui.txt; then
  echo "RESTORE OK: the task was loaded from the Documents backup"
else
  echo "RESTORE NOT CONFIRMED: task text not found on screen"
fi
adb logcat -d > smoke-restore.log
if grep -q "FATAL EXCEPTION" smoke-restore.log; then
  echo "app crashed after restore"
  exit 1
fi
