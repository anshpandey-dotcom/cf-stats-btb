Offline renderer for the Pomodoro Ladder scene frames in `assets/climber`.

The knight is the CC0 "Knight Character" by Quaternius (https://opengameart.org/content/lowpoly-animated-knight).
The ladder climb and the sitting poses are solved here with two-bone IK; the model ships no climbing clip.
The banner emblem uses the Font Awesome Free chess knight glyph (CC BY 4.0).

To rebuild the frames:

1. Download the Quaternius zip and copy `KnightCharacter.fbx`, `Helmet2.fbx` and `ShoulderPads.fbx` into a working folder.
2. `npm i --no-save three` in that folder and copy `scene.js` and `knightpath.js` next to the models.
3. Serve the folder, open a page that imports `scene.js`, call `createBake(canvas)` and then `frame('climb' | 'rest', t)` for `t` in `[0, 1)`.
   Use 24 climb frames and 30 rest frames, 540x510, JPEG.
