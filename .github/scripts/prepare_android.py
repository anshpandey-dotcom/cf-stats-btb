import pathlib
import re
import sys

from PIL import Image

root = pathlib.Path(sys.argv[1])
source = pathlib.Path(sys.argv[2])
package = 'com.fleetmc.unknown'

manifest_path = root / 'android/app/src/main/AndroidManifest.xml'
manifest = manifest_path.read_text()
permissions = [
    'RECORD_AUDIO',
    'MODIFY_AUDIO_SETTINGS',
    'CAMERA',
    'VIBRATE',
    'WAKE_LOCK',
    'ACCESS_WIFI_STATE',
    'CHANGE_NETWORK_STATE'
]
lines = ''.join(f'    <uses-permission android:name="android.permission.{p}" />\n' for p in permissions)
manifest = manifest.replace('</manifest>', lines + '</manifest>')
manifest = re.sub(r'android:label="[^"]*"', 'android:label="Unknown"', manifest, count=1)
manifest_path.write_text(manifest)

res = root / 'android/app/src/main/res'
for xml in (res / 'mipmap-anydpi-v26').glob('*.xml'):
    xml.unlink()

image = Image.open(source).convert('RGB')
sizes = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for density, size in sizes.items():
    folder = res / f'mipmap-{density}'
    folder.mkdir(parents=True, exist_ok=True)
    icon = image.resize((size, size), Image.LANCZOS)
    icon.save(folder / 'ic_launcher.png')
    icon.save(folder / 'ic_launcher_round.png')
    icon.save(folder / 'ic_launcher_foreground.png')

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
