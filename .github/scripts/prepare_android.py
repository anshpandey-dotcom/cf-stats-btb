import pathlib
import re
import sys

from PIL import Image, ImageOps

root = pathlib.Path(sys.argv[1])
source = pathlib.Path(sys.argv[2])
role = sys.argv[3]
package = sys.argv[4]
label = sys.argv[5]

manifest_path = root / 'android/app/src/main/AndroidManifest.xml'
manifest = manifest_path.read_text()

permissions = [
    'RECORD_AUDIO',
    'MODIFY_AUDIO_SETTINGS',
    'CAMERA',
    'VIBRATE',
    'WAKE_LOCK',
    'ACCESS_WIFI_STATE',
    'FOREGROUND_SERVICE',
    'FOREGROUND_SERVICE_DATA_SYNC',
    'POST_NOTIFICATIONS',
    'REQUEST_IGNORE_BATTERY_OPTIMIZATIONS'
]
lines = ''.join(f'    <uses-permission android:name="android.permission.{p}" />\n' for p in permissions)
manifest = manifest.replace('</manifest>', lines + '</manifest>')
manifest = re.sub(r'android:label="[^"]*"', f'android:label="{label}"', manifest, count=1)

service = (
    '        <receiver android:name="io.capawesome.capacitorjs.plugins.foregroundservice.NotificationActionBroadcastReceiver" />\n'
    '        <service android:name="io.capawesome.capacitorjs.plugins.foregroundservice.AndroidForegroundService" '
    'android:foregroundServiceType="dataSync" />\n'
)
manifest = manifest.replace('</application>', service + '    </application>')

deep_link = (
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.VIEW" />\n'
    '                <category android:name="android.intent.category.DEFAULT" />\n'
    '                <category android:name="android.intent.category.BROWSABLE" />\n'
    '                <data android:scheme="https" android:host="anshpandey-dotcom.github.io" '
    'android:pathPrefix="/cf-stats-btb/horror-chat" />\n'
    '            </intent-filter>\n'
)
manifest = manifest.replace('</activity>', deep_link + '        </activity>', 1)
manifest_path.write_text(manifest)

res = root / 'android/app/src/main/res'
for xml in (res / 'mipmap-anydpi-v26').glob('*.xml'):
    xml.unlink()

image = Image.open(source).convert('RGB')
if role == 'ghost':
    image = ImageOps.colorize(ImageOps.grayscale(image), black='#050102', white='#ff2a36', mid='#7a0b14')

sizes = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for density, size in sizes.items():
    folder = res / f'mipmap-{density}'
    folder.mkdir(parents=True, exist_ok=True)
    icon = image.resize((size, size), Image.LANCZOS)
    for name in ('ic_launcher.png', 'ic_launcher_round.png', 'ic_launcher_foreground.png'):
        icon.save(folder / name)

drawable = res / 'drawable'
drawable.mkdir(parents=True, exist_ok=True)
(drawable / 'ic_stat_sync.xml').write_text(
    '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" '
    'android:viewportWidth="24" android:viewportHeight="24">'
    '<path android:fillColor="#FFFFFFFF" android:pathData="M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z"/>'
    '</vector>\n'
)

activity = root / 'android/app/src/main/java' / package.replace('.', '/') / 'MainActivity.java'
activity.write_text(f'''package {package};

import android.os.Bundle;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {{
    @Override
    public void onCreate(Bundle savedInstanceState) {{
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }}
}}
''')
