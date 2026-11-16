import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

const files = {
  'female:casual': 'Casual.glb', 'female:punk': 'Punk.glb', 'female:formal': 'Formal.glb', 'female:suit': 'Suit.glb',
  'male:casual': 'MaleCasual.glb', 'male:punk': 'MalePunk.glb', 'male:formal': 'MaleSuit.glb', 'male:suit': 'MaleCasual2.glb',
};
const style = (value) => value === 'base' ? 'casual' : value;

export async function createWorld(canvas, initialSelection, hooks = {}) {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.08;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.setPixelRatio(Math.min(devicePixelRatio, 1.45));

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x8ed7ef);
  scene.fog = new THREE.Fog(0x9ed9e6, 34, 86);
  const camera = new THREE.OrthographicCamera(-12, 12, 12, -12, .1, 130);
  camera.position.set(18, 20, 18);
  scene.add(new THREE.HemisphereLight(0xfff7dc, 0x31506d, 2.5));
  const sun = new THREE.DirectionalLight(0xffe3b5, 3.7);
  sun.position.set(-18, 28, 16);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  sun.shadow.camera.left = sun.shadow.camera.bottom = -32;
  sun.shadow.camera.right = sun.shadow.camera.top = 32;
  scene.add(sun);

  const world = new THREE.Group();
  scene.add(world);
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(74, 74), new THREE.MeshStandardMaterial({ color: 0x8acb8b, roughness: 1 }));
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  world.add(ground);

  const roadMat = new THREE.MeshStandardMaterial({ color: 0x59626d, roughness: .95 });
  const pavementMat = new THREE.MeshStandardMaterial({ color: 0xd7d0c6, roughness: 1 });
  addBox(world, [0, .03, 0], [10, .06, 74], roadMat);
  addBox(world, [0, .04, 0], [74, .07, 9], roadMat);
  addBox(world, [-8.2, .06, 0], [5.6, .12, 74], pavementMat);
  addBox(world, [8.2, .06, 0], [5.6, .12, 74], pavementMat);
  addBox(world, [0, .07, -7.4], [74, .13, 5.3], pavementMat);
  addBox(world, [0, .07, 7.4], [74, .13, 5.3], pavementMat);
  for (let z = -32; z <= 32; z += 6) addBox(world, [0, .08, z], [.18, .03, 3], new THREE.MeshBasicMaterial({ color: 0xf6e9b0 }));
  for (let x = -32; x <= 32; x += 6) addBox(world, [x, .09, 0], [3, .03, .18], new THREE.MeshBasicMaterial({ color: 0xf6e9b0 }));

  const interactions = [];
  buildPalace(world, 22, -20);
  buildMall(world, -22, -19);
  buildClinic(world, -22, 20);
  buildStudio(world, 22, 20);
  const colliders = [
    { minX: 16.8, maxX: 27.2, minZ: -24.2, maxZ: -15.8 },
    { minX: -29.5, maxX: -14.5, minZ: -24, maxZ: -14 },
    { minX: -28, maxX: -16, minZ: 15.5, maxZ: 24.5 },
    { minX: 15.5, maxX: 28.5, minZ: 15.5, maxZ: 24.5 },
  ];
  addPortal(world, interactions, 'palace', 'Pałac Kultury', 22, -12, 0xffce55);
  addPortal(world, interactions, 'shop', 'Złote Tarasy · Sklep', -22, -12, 0xff4da7);
  addPortal(world, interactions, 'clinic', 'Klinika piękna', -22, 12, 0x4ce5e8);
  addPortal(world, interactions, 'events', 'Studio i filmy', 22, 12, 0xa77cff);
  addPortal(world, interactions, 'games', 'Przystanek · Minigra', 0, 24, 0xff8750);

  for (let i = 0; i < 26; i += 1) {
    const side = i % 2 ? -1 : 1;
    addTree(world, side * (13 + (i % 4) * 5), -31 + Math.floor(i / 2) * 5);
  }
  for (let i = 0; i < 8; i += 1) addNpc(world, -13 + i * 3.7, 5 + (i % 2) * 4, i);

  const collectibles = [];
  [[-4,-16],[4,14],[-15,3],[16,-3],[3,29],[-29,1]].forEach(([x,z], index) => {
    const coin = new THREE.Mesh(new THREE.CylinderGeometry(.36,.36,.12,20), new THREE.MeshStandardMaterial({ color: 0xffc62f, emissive: 0x6d3900, emissiveIntensity: .8, metalness: .45, roughness: .25 }));
    coin.rotation.x = Math.PI / 2;
    coin.position.set(x, .75, z);
    coin.userData.id = `world-coin-${index}`;
    world.add(coin);
    collectibles.push(coin);
  });

  const player = new THREE.Group();
  player.position.set(0, 0, 9);
  world.add(player);
  const playerShadow = new THREE.Mesh(new THREE.CircleGeometry(.65, 28), new THREE.MeshBasicMaterial({ color: 0x17222a, transparent: true, opacity: .22, depthWrite: false }));
  playerShadow.rotation.x = -Math.PI / 2;
  playerShadow.position.y = .03;
  player.add(playerShadow);
  let avatarRoots = [];
  let avatarVersion = 0;
  let lastSelectionKey = '';
  async function update(selection) {
    const selectionKey = JSON.stringify([selection.gender,selection.skin,selection.eyes,selection.top,selection.legs,selection.feet,selection.head]);
    if (selectionKey === lastSelectionKey) return;
    lastSelectionKey = selectionKey;
    const version = ++avatarVersion;
    const loader = new GLTFLoader();
    const selected = { Body: style(selection.top), Legs: style(selection.legs), Feet: style(selection.feet), Head: style(selection.head) };
    const unique = [...new Set(Object.values(selected))];
    const loaded = await Promise.all(unique.map(async (look) => {
      const filename = files[`${selection.gender || 'female'}:${look}`] || files['female:casual'];
      const gltf = await loader.loadAsync(new URL(`assets/characters/${filename}`, import.meta.url).href);
      return [look, gltf.scene];
    }));
    if (version !== avatarVersion) return;
    avatarRoots.forEach((root) => player.remove(root));
    avatarRoots = [];
    loaded.forEach(([look, root]) => {
      root.traverse((object) => {
        if (object.isBone && /^UpperArm.*L$/.test(object.name)) object.rotation.x -= 1.08;
        if (object.isBone && /^UpperArm.*R$/.test(object.name)) object.rotation.x -= 1.08;
        if (!object.isMesh) return;
        const part = Object.keys(selected).find((name) => object.name.includes(`_${name}`));
        object.visible = Boolean(part && selected[part] === look);
        object.castShadow = object.visible;
        object.frustumCulled = false;
        object.material = Array.isArray(object.material) ? object.material.map((m) => m.clone()) : object.material.clone();
        const mats = Array.isArray(object.material) ? object.material : [object.material];
        mats.forEach((material) => {
          material.roughness = .82;
          material.metalness = 0;
          if (material.name === 'Skin') material.color.set(selection.skin || '#c98b62');
          if (material.name === 'Brown' || /eye/i.test(material.name)) material.color.set(selection.eyes || '#5a3828');
        });
      });
      const bounds = new THREE.Box3().setFromObject(root);
      const size = bounds.getSize(new THREE.Vector3());
      const center = bounds.getCenter(new THREE.Vector3());
      const scale = 2.5 / Math.max(size.y, .001);
      root.scale.setScalar(scale);
      root.position.set(-center.x * scale, -bounds.min.y * scale, -center.z * scale);
      player.add(root);
      avatarRoots.push(root);
    });
  }
  update(initialSelection).catch(() => addFallbackPlayer(player));

  let pointerStart = [0, 0];
  let targetPoint = null;
  const raycaster = new THREE.Raycaster();
  const groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);
  const onPointerDown = (event) => { pointerStart = [event.clientX, event.clientY]; };
  const onPointerUp = (event) => {
    if (Math.hypot(event.clientX - pointerStart[0], event.clientY - pointerStart[1]) < 8 && event.target === canvas) {
      const rect = canvas.getBoundingClientRect();
      const pointer = new THREE.Vector2(((event.clientX - rect.left) / rect.width) * 2 - 1, -((event.clientY - rect.top) / rect.height) * 2 + 1);
      raycaster.setFromCamera(pointer, camera);
      const hit = new THREE.Vector3();
      if (raycaster.ray.intersectPlane(groundPlane, hit)) targetPoint = hit;
    }
  };
  canvas.addEventListener('pointerdown', onPointerDown);
  addEventListener('pointerup', onPointerUp);

  let nearest = null;
  function activateNearest() { if (nearest) hooks.onInteract?.(nearest.id, nearest.label); }
  const actionButton = document.getElementById('world-action');
  actionButton?.addEventListener('click', activateNearest);
  const clock = new THREE.Clock();
  let frame = 0;
  function animate() {
    frame = requestAnimationFrame(animate);
    const delta = Math.min(clock.getDelta(), .05);
    let direction = null;
    if (targetPoint) {
      direction = targetPoint.clone().sub(player.position); direction.y = 0;
      if (direction.length() < .28) targetPoint = direction = null;
      else direction.normalize();
    }
    if (direction) {
      const next = player.position.clone().addScaledVector(direction, delta * 6.2);
      next.x = THREE.MathUtils.clamp(next.x, -34, 34); next.z = THREE.MathUtils.clamp(next.z, -34, 34);
      const blocked = colliders.some((box) => next.x > box.minX && next.x < box.maxX && next.z > box.minZ && next.z < box.maxZ);
      if (!blocked) player.position.copy(next); else targetPoint = null;
      player.rotation.y = Math.atan2(direction.x, direction.z);
      avatarRoots.forEach((root) => { root.position.y = Math.abs(Math.sin(performance.now() * .009)) * .045; });
    }
    const offset = new THREE.Vector3(18, 20, 18);
    camera.position.lerp(player.position.clone().add(offset), 1 - Math.pow(.001, delta));
    camera.lookAt(player.position.x, 1.35, player.position.z);
    nearest = null;
    let minDistance = 3.5;
    interactions.forEach((entry) => {
      entry.ring.rotation.z += delta * .8;
      entry.ring.material.opacity = .48 + Math.sin(performance.now() * .003) * .14;
      const distance = player.position.distanceTo(entry.position);
      if (distance < minDistance) { minDistance = distance; nearest = entry; }
    });
    if (actionButton) {
      actionButton.hidden = !nearest;
      if (nearest) actionButton.querySelector('b').textContent = nearest.label;
    }
    collectibles.forEach((coin) => {
      if (!coin.visible) return;
      coin.rotation.z += delta * 2.4;
      coin.position.y = .82 + Math.sin(performance.now() * .004 + coin.position.x) * .16;
      if (coin.position.distanceTo(player.position) < 1.25) {
        coin.visible = false;
        hooks.onCollect?.(coin.userData.id, 5);
      }
    });
    renderer.render(scene, camera);
  }
  function resize() {
    const width = Math.max(canvas.clientWidth, 1);
    const height = Math.max(canvas.clientHeight, 1);
    renderer.setSize(width, height, false);
    const aspect = width / height;
    const view = 15;
    camera.left = -view * aspect;
    camera.right = view * aspect;
    camera.top = view;
    camera.bottom = -view;
    camera.updateProjectionMatrix();
  }
  const observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();
  animate();
  return {
    update(selection) { update(selection).catch(() => {}); },
    dispose() {
      cancelAnimationFrame(frame); observer.disconnect(); renderer.dispose();
      canvas.removeEventListener('pointerdown', onPointerDown);
      removeEventListener('pointerup', onPointerUp);
    },
  };
}

function addBox(parent, position, scale, material) {
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(...scale), material);
  mesh.position.set(...position); mesh.castShadow = true; mesh.receiveShadow = true; parent.add(mesh); return mesh;
}
function building(parent, x, z, w, h, d, color) {
  const root = new THREE.Group(); root.position.set(x, 0, z); parent.add(root);
  addBox(root, [0,h/2,0], [w,h,d], new THREE.MeshStandardMaterial({ color, roughness: .72 }));
  const glass = new THREE.MeshStandardMaterial({ color: 0x74d9ef, emissive: 0x123c55, emissiveIntensity: .25, metalness: .1, roughness: .25 });
  for (let y=1.4; y<h-.5; y+=1.7) for (let px=-w/2+.9; px<w/2-.3; px+=1.5) addBox(root,[px,y,d/2+.03],[.8,.72,.08],glass);
  return root;
}
function buildPalace(parent,x,z) { const root=building(parent,x,z,9,15,7,0xcab38f); addBox(root,[0,17,0],[2.2,4,2.2],new THREE.MeshStandardMaterial({color:0xb89d72})); addBox(root,[0,20,0],[.18,2,.18],new THREE.MeshStandardMaterial({color:0xe1b54b})); }
function buildMall(parent,x,z) { const root=building(parent,x,z,14,7,9,0xf3d7c2); addBox(root,[0,7.5,0],[10,1,9.4],new THREE.MeshStandardMaterial({color:0xef4c9f})); }
function buildClinic(parent,x,z) { const root=building(parent,x,z,11,8,8,0xe7f8f7); addBox(root,[0,9,4.1],[3.6,1.2,.3],new THREE.MeshStandardMaterial({color:0x42d4d5,emissive:0x164f50})); }
function buildStudio(parent,x,z) { const root=building(parent,x,z,12,8,8,0x7c63ae); addBox(root,[0,9,0],[8,.7,8.4],new THREE.MeshStandardMaterial({color:0xffd34e})); }
function addPortal(parent,list,id,label,x,z,color) {
  const ring=new THREE.Mesh(new THREE.TorusGeometry(1.05,.12,10,36),new THREE.MeshStandardMaterial({color,emissive:color,emissiveIntensity:1.4,transparent:true,opacity:.55}));
  ring.rotation.x=Math.PI/2; ring.position.set(x,.14,z); parent.add(ring); const tag=makeLabel(label,color); tag.position.set(x,2.15,z); parent.add(tag); list.push({id,label,ring,position:new THREE.Vector3(x,0,z)});
}
function addTree(parent,x,z) { const root=new THREE.Group(); root.position.set(x,0,z); parent.add(root); addBox(root,[0,.9,0],[.34,1.8,.34],new THREE.MeshStandardMaterial({color:0x755139})); const crown=new THREE.Mesh(new THREE.IcosahedronGeometry(1.15,1),new THREE.MeshStandardMaterial({color:0x3a9c68,roughness:1})); crown.position.y=2.2; crown.castShadow=true; root.add(crown); }
function addNpc(parent,x,z,index) { const root=new THREE.Group(); root.position.set(x,0,z); const colors=[0xf1459a,0x41cbd2,0xffc84e,0x8d72d8]; const body=new THREE.Mesh(new THREE.CapsuleGeometry(.35,.9,5,10),new THREE.MeshStandardMaterial({color:colors[index%colors.length],roughness:.75})); body.position.y=1.05; body.castShadow=true; root.add(body); const head=new THREE.Mesh(new THREE.SphereGeometry(.34,16,12),new THREE.MeshStandardMaterial({color:[0xf2c7a9,0xb97855,0xe0aa86][index%3]})); head.position.y=1.9; head.castShadow=true; root.add(head); parent.add(root); }
function addFallbackPlayer(parent) { const mesh=new THREE.Mesh(new THREE.CapsuleGeometry(.42,1.2,6,12),new THREE.MeshStandardMaterial({color:0xef3d9a})); mesh.position.y=1.1; mesh.castShadow=true; parent.add(mesh); }
function makeLabel(text,color) { const canvas=document.createElement('canvas'); canvas.width=512; canvas.height=96; const context=canvas.getContext('2d'); context.fillStyle='rgba(17,21,31,.88)'; context.beginPath(); context.roundRect(5,5,502,86,28); context.fill(); context.strokeStyle=`#${new THREE.Color(color).getHexString()}`; context.lineWidth=5; context.stroke(); context.fillStyle='#fff'; context.font='800 28px system-ui'; context.textAlign='center'; context.textBaseline='middle'; context.fillText(text,256,49); const sprite=new THREE.Sprite(new THREE.SpriteMaterial({map:new THREE.CanvasTexture(canvas),transparent:true,depthTest:false})); sprite.scale.set(5.4,1,1); return sprite; }
