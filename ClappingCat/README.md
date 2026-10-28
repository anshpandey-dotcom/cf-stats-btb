# Clapping Cat

Camera app that places the clapping black cat in your room. It does not need ARCore or Google Play Services.

- Clap mode: the cat stands on the floor in front of you. Drag to move it (forward, back, left, right), pinch to resize, `Face me: off` plus a two finger twist turns it a full 360 degrees.
- Handshake mode: the cat holds out its paw in front of the camera, reach your hand toward the camera.
- Tracking: the gyroscope gives rotation, and a small floor feature tracker (pure Kotlin, `tracking/`) gives position, so the cat stays in place while you walk. A textured floor works best.
- `app/src/test` simulates a walk over a synthetic floor and checks the position error.
- `tools/` holds the scripts that removed the green screen and produced the sprite frames, audio and icons.
- APK: built by the `Build Clapping Cat APK` workflow, download it from the repository Releases page.
