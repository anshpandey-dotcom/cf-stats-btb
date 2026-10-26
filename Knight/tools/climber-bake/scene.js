import * as THREE from 'three';
import { FBXLoader } from 'three/addons/loaders/FBXLoader.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';

const W = 540;
const H = 510;
const GAP = 0.28;
const LADDER_Z = 0.2;

export async function createBake(canvas) {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
  renderer.setSize(W, H, false);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const loader = new FBXLoader();
  const load = (url) => new Promise((res, rej) => loader.load(url, res, undefined, rej));
  const [knight, helmet, pads] = await Promise.all([
    load('/KnightCharacter.fbx'),
    load('/Helmet2.fbx'),
    load('/ShoulderPads.fbx'),
  ]);

  const camera = new THREE.PerspectiveCamera(36, W / H, 0.1, 50);
  camera.position.set(2.1, 0.75, 6.2);
  camera.lookAt(0.2, -0.05, 0.3);

  const bones = {};
  knight.traverse((o) => {
    if (o.isBone) bones[o.name] = o;
  });

  const group = new THREE.Group();
  group.add(knight);
  const box = new THREE.Box3().setFromObject(knight);
  const scale = 1.88 / (box.max.y - box.min.y);
  knight.scale.setScalar(scale);
  knight.updateMatrixWorld(true);

  knight.traverse((o) => {
    if (o.isMesh) {
      o.castShadow = true;
      o.receiveShadow = true;
      const mats = Array.isArray(o.material) ? o.material : [o.material];
      mats.forEach((m) => {
        m.roughness = 0.65;
        m.metalness = 0.1;
        m.flatShading = true;
        if (m.name === 'Armor') m.color.set(0x1d6a3c);
        if (m.name === 'Skin') m.color.set(0xd29a6e);
        if (m.name === 'Grey') m.color.set(0x8e9aa0);
        m.needsUpdate = true;
      });
    }
  });

  const probeW = (n) => new THREE.Vector3().setFromMatrixPosition(bones[n].matrixWorld);
  function fit(mesh, bone, center, factor, tint, tilt) {
    mesh.scale.setScalar(scale * factor);
    mesh.rotation.copy(knight.rotation);
    mesh.position.set(0, 0, 0);
    if (tilt) mesh.rotateX(tilt);
    mesh.updateMatrixWorld(true);
    const b = new THREE.Box3().setFromObject(mesh);
    mesh.position.add(center.clone().sub(b.getCenter(new THREE.Vector3())));
    mesh.updateMatrixWorld(true);
    mesh.traverse((o) => {
      if (o.isMesh) {
        o.castShadow = true;
        o.receiveShadow = true;
        o.frustumCulled = false;
        o.material = new THREE.MeshStandardMaterial({ color: tint, roughness: 0.5, metalness: 0.3, side: THREE.DoubleSide, flatShading: true });
      }
    });
    window.__dbg = window.__dbg || []; const bb = new THREE.Box3().setFromObject(mesh); window.__dbg.push(bone.name + ' ' + JSON.stringify(bb.min.toArray().map(v=>+v.toFixed(2))) + JSON.stringify(bb.max.toArray().map(v=>+v.toFixed(2))));
    bone.attach(mesh);
    const bb2 = new THREE.Box3().setFromObject(mesh); mesh.updateMatrixWorld(true); window.__dbg.push('after ' + JSON.stringify(bb2.min.toArray().map(v=>+v.toFixed(2))) + JSON.stringify(bb2.max.toArray().map(v=>+v.toFixed(2))) + ' parent=' + mesh.parent.name + ' vis=' + mesh.visible);
  }
  knight.updateMatrixWorld(true);
  const headCenter = probeW('Head').clone().add(probeW('Head_end')).multiplyScalar(0.5);
  fit(helmet, bones.Head, headCenter.add(new THREE.Vector3(0, 0.05, -0.01)), 0.98, 0xb7c3ca);
  const shoulderCenter = probeW('ShoulderL').clone().add(probeW('ShoulderR')).multiplyScalar(0.5);
  fit(pads, bones.Torso, shoulderCenter.add(new THREE.Vector3(0, 0.03, 0)), 0.58, 0xc3ced4);

  const matInfo = [];
  knight.traverse((o) => { if (o.isMesh) (Array.isArray(o.material) ? o.material : [o.material]).forEach((m) => matInfo.push(m.name + '#' + m.color.getHexString())); });
  window.__dbg = (window.__dbg || []).concat(matInfo);
  const rest = {};
  knight.updateMatrixWorld(true);
  const probe = {};
  ['Head','Head_end','ShoulderL','ShoulderR','Hips','Torso','Abdomen','UpperArmL','LowerLegL_end','PalmL'].forEach((n) => { probe[n] = new THREE.Vector3().setFromMatrixPosition(bones[n].matrixWorld).toArray().map((v) => +v.toFixed(3)); });
  console.log('PROBE ' + JSON.stringify(probe) + ' scale ' + scale);

  Object.values(bones).forEach((b) => {
    rest[b.name] = { q: b.quaternion.clone(), p: b.position.clone() };
  });

  const wp = (b) => new THREE.Vector3().setFromMatrixPosition(b.matrixWorld);
  knight.updateMatrixWorld(true);
  const lens = {};
  const chains = {
    armL: ['UpperArmL', 'LowerArmL', 'PalmL'],
    armR: ['UpperArmR', 'LowerArmR', 'PalmR'],
    legL: ['UpperLegL', 'LowerLegL', 'LowerLegL_end'],
    legR: ['UpperLegR', 'LowerLegR', 'LowerLegR_end'],
  };
  for (const [key, [a, b, c]] of Object.entries(chains)) {
    lens[key] = [wp(bones[a]).distanceTo(wp(bones[b])), wp(bones[b]).distanceTo(wp(bones[c]))];
  }
  const hipsRest = wp(bones.Hips).clone();

  function resetPose() {
    Object.values(bones).forEach((b) => {
      b.quaternion.copy(rest[b.name].q);
      b.position.copy(rest[b.name].p);
    });
    group.updateMatrixWorld(true);
  }

  function aim(bone, dir) {
    bone.updateWorldMatrix(true, false);
    const wq = new THREE.Quaternion();
    bone.getWorldQuaternion(wq);
    const cur = new THREE.Vector3(0, 1, 0).applyQuaternion(wq);
    const d = new THREE.Quaternion().setFromUnitVectors(cur, dir.clone().normalize());
    const nwq = d.multiply(wq);
    const pq = new THREE.Quaternion();
    bone.parent.getWorldQuaternion(pq);
    bone.quaternion.copy(pq.invert().multiply(nwq));
    bone.updateMatrixWorld(true);
  }

  function solve(key, target, pole) {
    const [a, b, c] = chains[key].map((n) => bones[n]);
    const [l1, l2] = lens[key];
    const pa = wp(a);
    const toT = target.clone().sub(pa);
    let d = toT.length();
    d = Math.min(Math.max(d, Math.abs(l1 - l2) + 0.01), l1 + l2 - 0.005);
    const u = toT.clone().normalize();
    const along = (l1 * l1 - l2 * l2 + d * d) / (2 * d);
    const h = Math.sqrt(Math.max(l1 * l1 - along * along, 0));
    const perp = pole.clone().sub(u.clone().multiplyScalar(pole.dot(u))).normalize();
    const mid = pa.clone().add(u.clone().multiplyScalar(along)).add(perp.multiplyScalar(h));
    aim(a, mid.clone().sub(pa));
    const endTarget = pa.clone().add(u.clone().multiplyScalar(d));
    aim(b, endTarget.clone().sub(mid));
    return endTarget;
  }

  function rotWorld(bone, axis, angle) {
    bone.updateWorldMatrix(true, false);
    const wq = new THREE.Quaternion();
    bone.getWorldQuaternion(wq);
    const nwq = new THREE.Quaternion().setFromAxisAngle(axis, angle).multiply(wq);
    const pq = new THREE.Quaternion();
    bone.parent.getWorldQuaternion(pq);
    bone.quaternion.copy(pq.invert().multiply(nwq));
    bone.updateMatrixWorld(true);
  }

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x0b1410);
  scene.fog = new THREE.Fog(0x0b1410, 6, 14);
  scene.add(group);
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
  scene.environmentIntensity = 0.45;

  function stoneTexture() {
    const c = document.createElement('canvas');
    c.width = c.height = 1024;
    const g = c.getContext('2d');
    g.fillStyle = '#1b231d';
    g.fillRect(0, 0, 1024, 1024);
    let seed = 7;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    const rows = 8;
    const bh = 1024 / rows;
    for (let r = 0; r < rows; r++) {
      const bw = 256 + (r % 3) * 32;
      const off = (r % 2) * (bw / 2);
      for (let x = -bw; x < 1024 + bw; x += bw) {
        const tone = 52 + rnd() * 26;
        g.fillStyle = `rgb(${tone * 0.9},${tone + 12},${tone * 0.85})`;
        g.fillRect(x + off + 5, r * bh + 5, bw - 10, bh - 10);
        for (let i = 0; i < 90; i++) {
          g.fillStyle = `rgba(${rnd() > 0.5 ? 255 : 0},${rnd() > 0.5 ? 255 : 0},${rnd() > 0.5 ? 255 : 0},${rnd() * 0.05})`;
          g.fillRect(x + off + rnd() * bw, r * bh + rnd() * bh, 2 + rnd() * 9, 2 + rnd() * 7);
        }
        const moss = g.createLinearGradient(0, r * bh, 0, r * bh + bh);
        moss.addColorStop(0, 'rgba(60,110,50,0.0)');
        moss.addColorStop(1, `rgba(70,130,55,${rnd() * 0.22})`);
        g.fillStyle = moss;
        g.fillRect(x + off + 5, r * bh + 5, bw - 10, bh - 10);
      }
    }
    const t = new THREE.CanvasTexture(c);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.colorSpace = THREE.SRGBColorSpace;
    t.anisotropy = 4;
    return t;
  }
  const tex = stoneTexture();
  tex.repeat.set(2.2, 2.2);
  const wall = new THREE.Mesh(
    new THREE.PlaneGeometry(12, 12),
    new THREE.MeshStandardMaterial({ map: tex, bumpMap: tex, bumpScale: 4, roughness: 0.95 }),
  );
  wall.position.set(0, 0, 0);
  wall.receiveShadow = true;
  scene.add(wall);

  scene.add(new THREE.HemisphereLight(0x8fd0b0, 0x0a120d, 0.9));
  const moon = new THREE.DirectionalLight(0xcfe6ff, 0.8);
  moon.position.set(-3, 5, 5);
  moon.castShadow = true;
  moon.shadow.mapSize.set(1024, 1024);
  Object.assign(moon.shadow.camera, { left: -4, right: 4, top: 4, bottom: -4, near: 1, far: 14 });
  scene.add(moon);
  const torch = new THREE.PointLight(0xffa24a, 12, 8, 1.8);
  torch.position.set(-1.7, 0.9, 1.1);
  torch.castShadow = true;
  torch.shadow.mapSize.set(512, 512);
  scene.add(torch);

  const fill = new THREE.PointLight(0xbfe6d0, 7, 9, 1.6);
  fill.position.set(1.2, -0.3, 3.6);
  scene.add(fill);

  const wood = new THREE.MeshStandardMaterial({ color: 0x5a3719, roughness: 0.85 });
  const woodLight = new THREE.MeshStandardMaterial({ color: 0x8a5a2c, roughness: 0.8 });
  const iron = new THREE.MeshStandardMaterial({ color: 0x2c2f33, roughness: 0.5, metalness: 0.8 });

  const ladder = new THREE.Group();
  scene.add(ladder);
  const railGeo = new THREE.BoxGeometry(0.09, 9, 0.1);
  const railL = new THREE.Mesh(railGeo, wood);
  const railR = new THREE.Mesh(railGeo, wood);
  railL.position.set(-0.34, 0, LADDER_Z);
  railR.position.set(0.34, 0, LADDER_Z);
  [railL, railR].forEach((r) => {
    r.castShadow = true;
    r.receiveShadow = true;
    scene.add(r);
  });
  const rungGeo = new THREE.CylinderGeometry(0.032, 0.032, 0.7, 12);
  rungGeo.rotateZ(Math.PI / 2);
  const rungs = [];
  for (let i = -14; i < 14; i++) {
    const rung = new THREE.Mesh(rungGeo, woodLight);
    rung.castShadow = true;
    rung.receiveShadow = true;
    rungs.push({ mesh: rung, n: i });
    ladder.add(rung);
    for (const sx of [-0.34, 0.34]) {
      const peg = new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.045, 0.035, 10).rotateZ(Math.PI / 2), iron);
      peg.position.set(sx, 0, 0);
      rung.add(peg);
    }
  }
  const brackets = [];
  for (let i = -5; i < 6; i++) {
    for (const sx of [-0.34, 0.34]) {
      const br = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.08, 0.2), iron);
      br.castShadow = true;
      scene.add(br);
      brackets.push({ mesh: br, sx, i });
    }
  }

  const props = new THREE.Group();
  scene.add(props);
  const slabMat = new THREE.MeshStandardMaterial({ map: tex, roughness: 0.9, color: 0xb9c4b8 });
  const slab = new THREE.Mesh(new THREE.BoxGeometry(2.7, 0.22, 1.55), slabMat);
  slab.position.set(0.75, -0.62, 0.78);
  slab.castShadow = true;
  slab.receiveShadow = true;
  props.add(slab);
  const lip = new THREE.Mesh(new THREE.BoxGeometry(2.8, 0.1, 0.1), slabMat);
  lip.position.set(0.75, -0.5, 1.5);
  lip.castShadow = true;
  props.add(lip);
  for (const sx of [-0.3, 1.8]) {
    const corbel = new THREE.Mesh(new THREE.BoxGeometry(0.25, 0.45, 1.3), slabMat);
    corbel.position.set(sx, -0.95, 0.68);
    corbel.castShadow = true;
    props.add(corbel);
  }

  const fire = new THREE.Group();
  fire.position.set(1.75, -0.51, 0.95);
  for (let i = 0; i < 4; i++) {
    const log = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.055, 0.5, 8), new THREE.MeshStandardMaterial({ color: 0x3a2412, roughness: 1 }));
    log.rotation.z = Math.PI / 2;
    log.rotation.y = (i * Math.PI) / 4;
    log.position.y = 0.04;
    log.castShadow = true;
    fire.add(log);
  }
  const flames = [];
  const flameMat = new THREE.MeshBasicMaterial({ color: 0xffb13b, transparent: true, opacity: 0.9, blending: THREE.AdditiveBlending, depthWrite: false });
  const flameMat2 = new THREE.MeshBasicMaterial({ color: 0xff5a1a, transparent: true, opacity: 0.85, blending: THREE.AdditiveBlending, depthWrite: false });
  for (let i = 0; i < 5; i++) {
    const cone = new THREE.Mesh(new THREE.ConeGeometry(0.1 - i * 0.008, 0.38, 8), i % 2 ? flameMat : flameMat2);
    cone.position.set(Math.cos(i * 1.7) * 0.07, 0.24, Math.sin(i * 1.7) * 0.07);
    flames.push(cone);
    fire.add(cone);
  }
  const fireLight = new THREE.PointLight(0xff8a2a, 14, 5, 1.8);
  fireLight.position.set(0, 0.45, 0.1);
  fireLight.castShadow = true;
  fireLight.shadow.mapSize.set(512, 512);
  fire.add(fireLight);
  props.add(fire);

  const rod = new THREE.Mesh(new THREE.CylinderGeometry(0.025, 0.025, 0.85, 8).rotateZ(Math.PI / 2), iron);
  rod.position.set(1.15, 1.25, 0.2);
  props.add(rod);
  const bc = document.createElement('canvas');
  bc.width = 256;
  bc.height = 420;
  const bg = bc.getContext('2d');
  const grad = bg.createLinearGradient(0, 0, 0, 420);
  grad.addColorStop(0, '#2edc6e');
  grad.addColorStop(1, '#0b6b30');
  bg.fillStyle = grad;
  bg.beginPath();
  bg.moveTo(0, 0);
  bg.lineTo(256, 0);
  bg.lineTo(256, 400);
  bg.lineTo(128, 340);
  bg.lineTo(0, 400);
  bg.closePath();
  bg.fill();
  bg.strokeStyle = '#f5b301';
  bg.lineWidth = 10;
  bg.stroke();
  bg.save();
  bg.translate(128 - 192 * 0.5, 60);
  bg.scale(0.5, 0.5);
  bg.fillStyle = '#f5b301';
  bg.fill(new Path2D(window.__KNIGHT_PATH));
  bg.restore();
  const bannerTex = new THREE.CanvasTexture(bc);
  bannerTex.colorSpace = THREE.SRGBColorSpace;
  const clothGeo = new THREE.PlaneGeometry(0.62, 1.0, 10, 14);
  const clothBase = clothGeo.attributes.position.array.slice();
  const cloth = new THREE.Mesh(clothGeo, new THREE.MeshStandardMaterial({ map: bannerTex, roughness: 0.85, side: THREE.DoubleSide, transparent: true, alphaTest: 0.5 }));
  cloth.position.set(1.15, 0.74, 0.22);
  cloth.castShadow = true;
  props.add(cloth);
  const lantern = new THREE.Group();
  const lbody = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.22, 0.16), new THREE.MeshStandardMaterial({ color: 0xffd27a, emissive: 0xffa640, emissiveIntensity: 1.6 }));
  lantern.add(lbody);
  lantern.position.set(-0.45, 0.25, 0.35);
  const lightL = new THREE.PointLight(0xffc266, 5, 3, 2);
  lantern.add(lightL);
  props.add(lantern);

  const sconce = new THREE.Group();
  sconce.position.set(-1.7, 0.9, 0.12);
  const flame = new THREE.Mesh(new THREE.ConeGeometry(0.1, 0.34, 8), flameMat);
  flame.position.y = 0.2;
  sconce.add(flame);
  const holder = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.035, 0.3, 8), iron);
  sconce.add(holder);
  scene.add(sconce);

  function applyLadder(shift, lx) {
    railL.position.x = lx - 0.34;
    railR.position.x = lx + 0.34;
    rungs.forEach(({ mesh, n }) => {
      mesh.position.set(lx, shift + n * GAP, LADDER_Z);
    });
    brackets.forEach(({ mesh, sx, i }) => {
      mesh.position.set(lx + sx, i * 1.4 - 0.7, LADDER_Z - 0.12);
    });
  }

  function ease(x) {
    return x * x * (3 - 2 * x);
  }

  const LIM = { hand: 0.7, foot: -0.42 };

  function climb(t) {
    resetPose();
    const bodyY = 0;
    group.position.set(0.0, 0, 0);
    group.rotation.set(0, Math.PI, 0);
    group.position.y = 0;
    group.updateMatrixWorld(true);
    const hips = wp(bones.Hips);
    const desiredPelvis = new THREE.Vector3(0.02 * Math.sin(Math.PI * 2 * t), 0.02 * Math.sin(Math.PI * 4 * t), LADDER_Z + 0.52);
    group.position.add(desiredPelvis.sub(hips));
    group.updateMatrixWorld(true);

    const shift = -2 * GAP * t;
    const G0 = bodyY + LIM.hand + GAP / 2;
    applyLadder(G0 + (shift % GAP), 0);

    const limbs = [
      { key: 'armL', phase: 0, x: -0.24, off: LIM.hand, z: LADDER_Z + 0.0, pole: new THREE.Vector3(-0.8, -0.5, 0.9) },
      { key: 'armR', phase: 0.5, x: 0.24, off: LIM.hand, z: LADDER_Z + 0.0, pole: new THREE.Vector3(0.8, -0.5, 0.9) },
      { key: 'legR', phase: 0, x: 0.15, off: LIM.foot, z: LADDER_Z + 0.14, pole: new THREE.Vector3(0.45, 0.1, 1) },
      { key: 'legL', phase: 0.5, x: -0.15, off: LIM.foot, z: LADDER_Z + 0.14, pole: new THREE.Vector3(-0.45, 0.1, 1) },
    ];
    const pel = wp(bones.Hips);
    for (const limb of limbs) {
      const p = (t + limb.phase) % 1;
      let rel;
      let lift = 0;
      if (p < 0.5) {
        rel = GAP / 2 - GAP * (p / 0.5);
      } else {
        const s = (p - 0.5) / 0.5;
        rel = -GAP / 2 + GAP * ease(s);
        lift = Math.sin(Math.PI * s) * 0.1;
      }
      const target = new THREE.Vector3(limb.x, bodyY + limb.off + rel, limb.z + lift);
      const isArm = limb.key.startsWith('arm');
      if (isArm) target.z -= 0.03;
      solve(limb.key, target, limb.pole);
    }
    rotWorld(bones.Torso, new THREE.Vector3(0, 1, 0), 0.12 * Math.sin(Math.PI * 2 * t));
    rotWorld(bones.Torso, new THREE.Vector3(0, 0, 1), 0.05 * Math.sin(Math.PI * 2 * t + 1));
    rotWorld(bones.Head, new THREE.Vector3(1, 0, 0), -0.18);
    props.visible = false;
    sconce.visible = true;
    torch.intensity = 11 + 2 * Math.sin(t * Math.PI * 12);
    flame.scale.y = 0.9 + 0.2 * Math.sin(t * Math.PI * 14);
  }

  function sit(t) {
    resetPose();
    props.visible = true;
    sconce.visible = true;
    const seatTop = -0.51;
    group.rotation.set(0, 0, 0);
    group.position.set(0, 0, 0);
    group.updateMatrixWorld(true);
    const hips = wp(bones.Hips);
    const breath = Math.sin(Math.PI * 2 * t);
    const pelvis = new THREE.Vector3(0.45, seatTop + 0.1, 1.22);
    group.position.add(pelvis.sub(hips));
    group.updateMatrixWorld(true);
    const sw = Math.sin(Math.PI * 2 * t);
    solve('legL', new THREE.Vector3(0.62, seatTop - 0.3 + 0.03 * sw, 1.74), new THREE.Vector3(0.1, 0.2, 1));
    solve('legR', new THREE.Vector3(0.28, seatTop - 0.3 - 0.03 * sw, 1.74), new THREE.Vector3(-0.1, 0.2, 1));
    solve('armL', new THREE.Vector3(0.7, seatTop + 0.2, 1.5), new THREE.Vector3(0.9, -0.2, -0.5));
    solve('armR', new THREE.Vector3(0.2, seatTop + 0.2, 1.5), new THREE.Vector3(-0.9, -0.2, -0.5));
    rotWorld(bones.Torso, new THREE.Vector3(1, 0, 0), 0.06 + 0.012 * breath);
    rotWorld(bones.Head, new THREE.Vector3(0, 1, 0), 0.35 * Math.sin(Math.PI * 2 * t));
    rotWorld(bones.Head, new THREE.Vector3(1, 0, 0), 0.1);
    applyLadder(0.2, -1.15);
    fire.children.forEach(() => {});
    flames.forEach((f, i) => {
      f.scale.set(1 + 0.15 * Math.sin(t * Math.PI * 16 + i), 0.85 + 0.35 * Math.sin(t * Math.PI * 12 + i * 1.3), 1);
      f.rotation.z = 0.12 * Math.sin(t * Math.PI * 10 + i);
    });
    fireLight.intensity = 14 + 3 * Math.sin(t * Math.PI * 22) + 2 * Math.sin(t * Math.PI * 14);
    torch.intensity = 11 + 2 * Math.sin(t * Math.PI * 12);
    flame.scale.y = 0.9 + 0.2 * Math.sin(t * Math.PI * 14);
    const arr = clothGeo.attributes.position.array;
    for (let i = 0; i < arr.length; i += 3) {
      const down = (0.5 - clothBase[i + 1]) / 1.0;
      arr[i + 2] = Math.sin(down * 5 - t * Math.PI * 2) * 0.035 * (0.2 + down);
    }
    clothGeo.attributes.position.needsUpdate = true;
    clothGeo.computeVertexNormals();
  }

  return {
    frame(mode, t, cam) {
      const preset = cam || (mode === 'climb' ? { pos: [1.6, 0.65, 4.9], look: [0.05, 0.22, 0.3] } : { pos: [2.1, 0.55, 5.7], look: [0.6, -0.12, 0.5] });
      camera.position.set(...preset.pos);
      camera.lookAt(...preset.look);
      if (mode === 'climb') climb(t);
      else sit(t);
      renderer.render(scene, camera);
      return canvas.toDataURL('image/jpeg', 0.82);
    },
    info: { lens, scale },
  };
}
