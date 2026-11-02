# Blockcut Studio (Android)

Minecraft animation video maker that runs fully on the phone. No server, no internet needed after install.

**Download:** [`release/BlockcutStudio-v1.0.0.apk`](release/BlockcutStudio-v1.0.0.apk) or the latest build on the Releases page (tag `blockcut-latest`).

## Features

- InShot style editor: preview on top, timeline with a fixed centre playhead, icon toolbar at the bottom, tools open as bottom sheets. Works in portrait and landscape.
- Keyframes for characters, props, camera and sky. Drag keys and clips, drag the right edge to change length, pinch to zoom the timeline.
- Projects: unlimited projects, autosave, thumbnails, rename, duplicate, save as template.
- Templates: Steve meets Alex, Shock reaction, Night horror, Talk show, Empty, plus your own.
- Characters: Steve, Alex, **Shocked Steve**, zombies, skeletons, creeper, pig, cow, chicken, spider, enderman, villager, iron golem. Add any skin PNG with a name, or a character `.zip` (`.geo.json` + texture + `.animation.json`). Skin packs and add-on entities are imported too.
- Faces on every character: shock (eyes pop out), scared, happy, laugh, angry, sad, cry, love, sleep, wink, dizzy, hmm, talk.
- Voice and lip sync: record with the mic, pick an audio file, or type a line (Android text to speech). The mouth follows the loudness at the exact time on the timeline.
- Sounds: full vanilla sound library, quick effects, import music or record your own.
- World: build mode with a Minecraft style hotbar, place, break, pick, fill box, clear box, fly camera with joystick. World templates (plains, village, desert, snow, castle, nether, sky island). Save maps, save builds, export `.mcstructure`, import `.mcworld` (area around the spawn).
- Props: put builds, items or blocks in the scene and animate them: build up, explode, appear, vanish, spin, bounce, shrink, plus move/rotate/scale keys.
- Text: titles and captions burned into the video.
- AI Studio: copy a ready prompt for ChatGPT, Claude or Gemini, paste the JSON reply and the whole video is created. Download the full ID list `.txt` (characters, skins, custom items, blocks, sounds, moves, faces, shots, vanilla entity and item IDs) for making add-ons with AI.
- Packs: vanilla resource pack is built in (instant start). Load your latest resource pack or `.mcaddon` once and it stays saved. Custom items, blocks and entities from add-ons become usable.
- Vanilla Behavior Pack (26.50) included, its IDs are in the ID list and the pack can be saved from the Packs tab.
- Export MP4 from 480p up to 4K, 24/30/60 fps, 16:9, 9:16, 1:1, 4:3, 21:9 with all sounds mixed in. Saved to `Movies/Blockcut`, share from the app.

## AI script format

Open AI Studio, describe the video, tap **Copy AI prompt**, paste it into any AI chat, copy the JSON reply back and tap **New project**. A full description of the format is inside the copied prompt and the ID list. Short example:

```json
{
  "title": "Creeper surprise",
  "duration": 12,
  "aspect": "9:16",
  "world": { "template": "village", "time": 17 },
  "characters": [
    { "id": "steve", "skin": "steve", "at": [-6, 2], "facing": 90, "actions": [
      { "t": 0, "walk_to": [0, 0], "dur": 3 },
      { "t": 3, "say": "What a nice day!" },
      { "t": 5, "turn_to": "creeper" },
      { "t": 5.2, "face": "shock", "dur": 2.5 }
    ] },
    { "id": "creeper", "mob": "creeper", "at": [4, -6], "actions": [{ "t": 1, "walk_to": [1.5, -1.5], "dur": 3.5 }] }
  ],
  "camera": [{ "t": 0, "shot": "wide", "on": "steve" }, { "t": 5.2, "shot": "face", "on": "steve", "shake": 0.5 }],
  "sounds": [{ "t": 4.6, "sound": "random/fuse" }]
}
```

## Build

```
cd blockcut-app
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Pushing changes under `blockcut-app/` runs the GitHub workflow that builds the APK and publishes it to the `blockcut-latest` release.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
