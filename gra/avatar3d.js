import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

const modelFiles = {
  'female:casual': 'Casual.glb',
  'female:punk': 'Punk.glb',
  'female:formal': 'Formal.glb',
  'female:suit': 'Suit.glb',
  'male:casual': 'MaleCasual.glb',
  'male:punk': 'MalePunk.glb',
  'male:formal': 'MaleSuit.glb',
  'male:suit': 'MaleCasual2.glb',
};

const sourceFor = (value) => value === 'base' ? 'casual' : value;

export async function createAvatarViewer(canvas, initialSelection) {
  const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.08;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.7));

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(28, 1, .1, 100);
  camera.position.set(2.4, 1.7, 4.8);

  const controls = new OrbitControls(camera, canvas);
  controls.enablePan = false;
  controls.enableDamping = true;
  controls.minDistance = 3.2;
  controls.maxDistance = 6;
  controls.minPolarAngle = Math.PI * .28;
  controls.maxPolarAngle = Math.PI * .62;
  controls.target.set(0, 1.05, 0);

  scene.add(new THREE.HemisphereLight(0xfff4dd, 0x263c49, 2.4));
  const key = new THREE.DirectionalLight(0xffd7b3, 4.2);
  key.position.set(3, 5, 4);
  key.castShadow = true;
  scene.add(key);
  const rim = new THREE.DirectionalLight(0x64c8dd, 2.2);
  rim.position.set(-4, 2, -3);
  scene.add(rim);

  const floor = new THREE.Mesh(
    new THREE.CircleGeometry(1.25, 48),
    new THREE.MeshStandardMaterial({ color: 0xf3c8a3, transparent: true, opacity: .4, roughness: 1 }),
  );
  floor.rotation.x = -Math.PI / 2;
  floor.receiveShadow = true;
  scene.add(floor);

  const loader = new GLTFLoader();
  const models = {};
  await Promise.all(Object.entries(modelFiles).map(async ([keyName, filename]) => {
    const url = new URL(`assets/characters/${filename}`, import.meta.url).href;
    const gltf = await loader.loadAsync(url);
    const root = gltf.scene;
    root.traverse((object) => {
      if (object.isBone && /^UpperArm.*L$/.test(object.name)) object.rotation.x -= 1.08;
      if (object.isBone && /^UpperArm.*R$/.test(object.name)) object.rotation.x -= 1.08;
      if (!object.isMesh) return;
      object.castShadow = true;
      object.frustumCulled = false;
      object.material = Array.isArray(object.material)
        ? object.material.map((material) => material.clone())
        : object.material.clone();
      const materials = Array.isArray(object.material) ? object.material : [object.material];
      materials.forEach((material) => {
        material.roughness = .82;
        material.metalness = 0;
      });
    });
    const bounds = new THREE.Box3().setFromObject(root);
    const size = bounds.getSize(new THREE.Vector3());
    const center = bounds.getCenter(new THREE.Vector3());
    const scale = 2.35 / Math.max(size.y, .001);
    root.scale.setScalar(scale);
    root.position.set(-center.x * scale, -bounds.min.y * scale, -center.z * scale);
    models[keyName] = root;
    scene.add(root);
  }));

  let selection = { ...initialSelection };

  function recolorBase() {
    Object.entries(models).forEach(([modelKey, model]) => model.traverse((object) => {
      if (!object.isMesh) return;
      const materials = Array.isArray(object.material) ? object.material : [object.material];
      materials.forEach((material) => {
        if (material.name === 'Skin') {
          material.color.set(selection.skin || '#c98b62');
          if (object.name.includes('_Head') && selection.makeup === 'rose') material.color.lerp(new THREE.Color('#d56b8b'), .12);
          if (object.name.includes('_Head') && selection.makeup === 'smoky') material.color.lerp(new THREE.Color('#67506d'), .08);
        }
        if (material.name === 'Brown' || /eye/i.test(material.name)) material.color.set(selection.eyes || '#5a3828');
        if (!modelKey.endsWith(':casual')) return;
        if (material.name === 'White') material.color.set(selection.top === 'base' ? '#e78f98' : '#f4f2e8');
        if (material.name === 'Orange') material.color.set(selection.legs === 'base' ? '#31475a' : '#e87945');
        if (material.name === 'Grey') {
          material.transparent = selection.feet === 'base';
          material.opacity = selection.feet === 'base' ? 0 : 1;
          material.depthWrite = selection.feet !== 'base';
        }
      });
    }));
  }

  function applySelection(nextSelection) {
    selection = { ...selection, ...nextSelection };
    const selectedSources = {
      Body: `${selection.gender || 'female'}:${sourceFor(selection.top)}`,
      Legs: `${selection.gender || 'female'}:${sourceFor(selection.legs)}`,
      Feet: `${selection.gender || 'female'}:${sourceFor(selection.feet)}`,
      Head: `${selection.gender || 'female'}:${sourceFor(selection.head)}`,
    };
    Object.entries(models).forEach(([modelKey, root]) => {
      root.traverse((object) => {
        if (!object.isMesh) return;
        const part = Object.keys(selectedSources).find((partName) => object.name.includes(`_${partName}`));
        object.visible = Boolean(part && selectedSources[part] === modelKey);
      });
    });
    recolorBase();
  }

  function resize() {
    const width = Math.max(canvas.clientWidth, 1);
    const height = Math.max(canvas.clientHeight, 1);
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
  }

  const observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();
  applySelection(selection);

  let active = true;
  function frame() {
    if (!active) return;
    controls.update();
    renderer.render(scene, camera);
    requestAnimationFrame(frame);
  }
  frame();

  return {
    update: applySelection,
    dispose() {
      active = false;
      observer.disconnect();
      controls.dispose();
      renderer.dispose();
    },
  };
}
