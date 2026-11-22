# Verity Mod for MCPE

Offline Minecraft Bedrock add-on catalogue. Package `com.fleetmc.verity`.

## Adding an add-on

1. Create `app/src/main/assets/addons/<id>/` and put the `.mcaddon` or `.mcpack` file in it.
2. Add 16:9 WebP previews (for example `preview_1.webp` to `preview_3.webp`, 640x360) to the same folder. The first one is the large card image, the next two are the small ones.
3. Add an entry to `app/src/main/assets/catalog.json`:

```json
{
  "id": "my_addon",
  "name": "My Add-on",
  "description": "What it does.",
  "category": "Mobs",
  "minVersion": "1.21.0",
  "maxVersion": "1.26.60",
  "file": "MyAddon.mcaddon",
  "sizeBytes": 12345,
  "previews": ["preview_1.webp", "preview_2.webp", "preview_3.webp"]
}
```

`sizeBytes` is the exact file size (`stat -c %s MyAddon.mcaddon`). New categories appear as filter chips automatically.
Run `./gradlew testDebugUnitTest` to validate the catalogue, archives, manifests and previews.

`tools/generate_content.py` rebuilds the 10 demo add-ons (sources in `content-src/`).

## Release signing

Set these as environment variables or Gradle properties. Nothing is committed.

| Name | Purpose |
| --- | --- |
| `VERITY_KEYSTORE_PATH` | Path to the keystore file |
| `VERITY_KEYSTORE_PASSWORD` | Keystore password |
| `VERITY_KEY_ALIAS` | Key alias |
| `VERITY_KEY_PASSWORD` | Key password (defaults to the keystore password) |

For GitHub Actions add the secrets `VERITY_KEYSTORE_BASE64` (`base64 -w0 keystore.jks`), `VERITY_KEYSTORE_PASSWORD`, `VERITY_KEY_ALIAS` and `VERITY_KEY_PASSWORD`.

## Replacing the fake ad

`ads/RewardedAdProvider` is the only contract the screens use. Write an AdMob implementation of it and change the single line in `di/AppContainer.kt`.
