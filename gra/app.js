const byId = (id) => document.getElementById(id);
const config = window.ALCARA_CONFIG;
const configured = Boolean(config?.url && config.publishableKey?.startsWith('sb_publishable_') && location.protocol !== 'file:');

const authForm = byId('auth');
const status = byId('status');
const submit = byId('submit');
const logout = byId('logout');
const authSwitch = byId('auth-switch');
const creator = byId('creator');
const beautyDialog = byId('beauty-dialog');
const shopHub = byId('shop-hub');
const creativeHub = byId('creative-hub');
const catalogDialog = byId('catalog-dialog');
const cartDialog = byId('cart-dialog');
const profileDialog = byId('profile-dialog');
const tramDialog = byId('tram-game');
const worldDialog = byId('world-dialog');

const defaultPlayer = {
  version: 1,
  created: false,
  name: 'Nowa osoba',
  preset: 'nova',
  gender: 'female',
  skin: '#c98b62',
  beauty: { eyes: '#5a3828', makeup: 'none' },
  profile: { status: 'Hej! Witaj na moim profilu ✦', description: '', posts: [], likes: 0 },
  outfit3d: { top: 'base', legs: 'base', feet: 'base', head: 'casual' },
  inventory: { top: ['base'], legs: ['base'], feet: ['base'], head: ['casual'] },
  coins: 80,
  xp: 0,
  visited: [],
  rewards: {},
  daily: '',
};

const shopPrices = {
  top: { casual: 90, punk: 160, formal: 230 },
  legs: { casual: 100, punk: 175, formal: 220 },
  feet: { casual: 75, punk: 145, formal: 190 },
  head: { punk: 120, formal: 180, suit: 210 },
};

const catalogLabels = {
  top: { casual: ['T-shirt miejski', '👕'], punk: ['Kurtka neon', '🧥'], formal: ['Koszula wieczorowa', '👔'] },
  legs: { casual: ['Jeansy centrum', '👖'], punk: ['Spodnie punk', '⭐'], formal: ['Spodnie klasyczne', '🩳'] },
  feet: { casual: ['Sneakersy', '👟'], punk: ['Trapery', '🥾'], formal: ['Półbuty', '👞'] },
  head: { punk: ['Fryzura punk', '💇'], formal: ['Upięcie', '🎀'], suit: ['Krótkie włosy', '✂'] },
};

const locations = {
  plac: {
    kicker: 'SERCE DZIELNICY',
    title: 'Plac Iskier',
    description: 'Tu zaczyna się każda dobra historia. Popraw swój styl, odbierz pierwsze zadanie i ruszaj w miasto.',
    actions: [
      { label: 'Edytuj postać', action: () => openCreator() },
      { label: 'Zrób zdjęcie · +5 ✦', primary: true, action: () => claimReward('photo', 0, 5, 'Świetne ujęcie! +5 doświadczenia') },
    ],
  },
  bulwary: {
    kicker: 'NAD WISŁĄ',
    title: 'Bulwary Syreny',
    description: 'Deskorolki, lemoniada i światła odbijające się w rzece. Podobno codziennie można znaleźć tu pocztówkę.',
    actions: [
      { label: 'Znajdź pocztówkę · +15 ◉', primary: true, action: () => claimReward('postcard', 15, 4, 'Pocztówka znaleziona! +15 monet') },
    ],
  },
  metro: {
    kicker: 'SZYBKI PRZEJAZD',
    title: 'Metro Centrum',
    description: 'Przesiądź się do tramwaju linii 9 i łap bilety. Im szybszy refleks, tym więcej monet na nowe ubrania.',
    actions: [
      { label: 'Zagraj: Łap bilety', primary: true, action: () => openTramGame() },
    ],
  },
  neon: {
    kicker: 'PRAGA PO ZMROKU',
    title: 'Neonownia',
    description: 'Kolorowe szyldy, muzyka i najodważniejsze stylizacje w Alcarze. To dobre miejsce na zmianę wizerunku.',
    actions: [
      { label: 'Otwórz garderobę', primary: true, action: () => openCreator() },
      { label: 'Zbierz neon · +8 ◉', action: () => claimReward('neon', 8, 3, 'Neon trafia do kolekcji! +8 monet') },
    ],
  },
};

let client;
let session;
let guest = false;
let ready = false;
let busy = false;
let player;
let playerOwner = '';
let currentLocation = 'plac';
let draft;
let toastTimer;
let saveTimer;
let soundEnabled = false;
let tramTimer;
let tramScore = 0;
let tramTime = 15;
let mainViewer;
let creatorViewer;
let shopViewer;
let profileViewer;
let avatarModulePromise;
let creatorMode = 'wardrobe';
let catalogGender = 'female';
let catalogTab = 'top';
let cart = [];
let catalogDraft;
let presenceChannel;
const presenceId = crypto.randomUUID();

function route() {
  const hash = location.hash;
  if (hash === '#rejestracja') return 'register';
  if (hash === '#logowanie') return 'login';
  if (hash === '#gra') return 'game';
  return 'menu';
}

function localDate() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function storageKey() {
  return session?.user?.id ? `alcara-player:${session.user.id}` : 'alcara-player:guest';
}

function normalizePlayer(value) {
  const savedInventory = value?.inventory || {};
  const savedOutfit = { ...defaultPlayer.outfit3d, ...(value?.outfit3d || {}) };
  return {
    ...defaultPlayer,
    ...(value && typeof value === 'object' ? value : {}),
    visited: Array.isArray(value?.visited) ? value.visited : [],
    rewards: value?.rewards && typeof value.rewards === 'object' ? value.rewards : {},
    outfit3d: savedOutfit,
    inventory: Object.fromEntries(Object.entries(defaultPlayer.inventory).map(([key, items]) => [key, Array.from(new Set([...items, savedOutfit[key], ...(Array.isArray(savedInventory[key]) ? savedInventory[key] : [])]))])),
    beauty: { ...defaultPlayer.beauty, ...(value?.beauty || {}) },
    profile: { ...defaultPlayer.profile, ...(value?.profile || {}), posts: Array.isArray(value?.profile?.posts) ? value.profile.posts : [] },
  };
}

function loadPlayer() {
  const owner = session?.user?.id || (guest ? 'guest' : '');
  if (!owner) return;
  if (owner === playerOwner && player) return;
  playerOwner = owner;
  let saved;
  if (session) saved = session.user.user_metadata?.alcara_profile;
  if (!saved) {
    try { saved = JSON.parse(localStorage.getItem(storageKey())); } catch { saved = null; }
  }
  player = normalizePlayer(saved);
  if (!player.created && session?.user?.email) player.name = session.user.email.split('@')[0].slice(0, 18);
  renderGame();
  if (!player.created) setTimeout(() => openCreator(true), 100);
}

function queueSave() {
  if (!player) return;
  localStorage.setItem(storageKey(), JSON.stringify(player));
  clearTimeout(saveTimer);
  if (!session || !client) return;
  saveTimer = setTimeout(async () => {
    const { data, error } = await client.auth.updateUser({ data: { alcara_profile: player } });
    if (error) {
      showToast('Postęp zapisano na tym urządzeniu. Zapis online spróbuje ponownie później.');
      return;
    }
    session.user = data.user;
  }, 500);
}

function avatarModule() {
  avatarModulePromise ||= import('./avatar3d.js');
  return avatarModulePromise;
}

async function ensureMainViewer() {
  if (mainViewer || !player) return;
  byId('game-loader').hidden = false;
  try {
    const { createAvatarViewer } = await avatarModule();
    mainViewer = await createAvatarViewer(byId('avatar-canvas'), { ...player.outfit3d, skin: player.skin, gender: player.gender, ...player.beauty });
    byId('avatar-loading').hidden = true;
  } catch {
    byId('avatar-loading').textContent = 'Nie udało się wczytać postaci 3D.';
  } finally {
    byId('game-loader').hidden = true;
  }
}

async function ensureCreatorViewer() {
  if (creatorViewer) {
    creatorViewer.update({ ...draft.outfit3d, skin: draft.skin, gender: draft.gender, ...draft.beauty });
    return;
  }
  const { createAvatarViewer } = await avatarModule();
  creatorViewer = await createAvatarViewer(byId('creator-canvas'), { ...draft.outfit3d, skin: draft.skin, gender: draft.gender, ...draft.beauty });
}

async function ensureShopViewer() {
  const selection = { ...catalogDraft.outfit3d, skin: catalogDraft.skin, gender: catalogGender, ...catalogDraft.beauty };
  if (shopViewer) {
    shopViewer.update(selection);
    return;
  }
  const { createAvatarViewer } = await avatarModule();
  shopViewer = await createAvatarViewer(byId('shop-avatar-canvas'), selection);
}

async function ensureProfileViewer() {
  const selection = { ...player.outfit3d, skin: player.skin, gender: player.gender, ...player.beauty };
  if (profileViewer) {
    profileViewer.update(selection);
    return;
  }
  const { createAvatarViewer } = await avatarModule();
  profileViewer = await createAvatarViewer(byId('profile-avatar-canvas'), selection);
}

function updateAvatarViewers(source = player) {
  if (!source) return;
  mainViewer?.update({ ...source.outfit3d, skin: source.skin, gender: source.gender, ...source.beauty });
  creatorViewer?.update({ ...source.outfit3d, skin: source.skin, gender: source.gender, ...source.beauty });
  shopViewer?.update({ ...source.outfit3d, skin: source.skin, gender: source.gender, ...source.beauty });
  profileViewer?.update({ ...source.outfit3d, skin: source.skin, gender: source.gender, ...source.beauty });
}

function render() {
  const screen = route();
  document.body.classList.toggle('playing', screen === 'game');
  const authenticated = Boolean(session || guest);
  if (screen === 'game' && ready && !authenticated) {
    location.hash = '#logowanie';
    return;
  }
  byId('menu').hidden = screen !== 'menu';
  byId('konto').hidden = screen !== 'login' && screen !== 'register';
  byId('gra').hidden = screen !== 'game';
  byId('nav-login').textContent = authenticated ? 'Moje konto' : 'Zaloguj się';
  byId('nav-login').href = authenticated ? '#gra' : '#logowanie';
  if (screen === 'login' || screen === 'register') renderAuth(screen === 'register');
  if (screen === 'game' && authenticated) {
    loadPlayer();
    renderGame();
  }
}

function renderAuth(register) {
  const authenticated = Boolean(session || guest);
  byId('auth-title').textContent = authenticated ? 'Twoje konto' : register ? 'Załóż konto' : 'Zaloguj się';
  authForm.hidden = authenticated;
  authSwitch.hidden = authenticated;
  byId('account-play').hidden = !authenticated;
  logout.hidden = !authenticated;
  logout.disabled = busy;
  submit.disabled = !ready || busy;
  submit.textContent = busy ? 'Chwileczkę…' : register ? 'Załóż konto' : 'Zaloguj się';
  byId('password').autocomplete = register ? 'new-password' : 'current-password';
  byId('password').minLength = register ? 8 : 1;
  authSwitch.href = register ? '#logowanie' : '#rejestracja';
  authSwitch.textContent = register ? 'Mam już konto' : 'Utwórz konto';
}

function renderGame() {
  if (!player) return;
  byId('player-name').textContent = player.name;
  byId('player-level').textContent = `Poziom ${Math.floor(player.xp / 50) + 1}`;
  byId('coins').textContent = player.coins;
  byId('xp').textContent = player.xp;
  ensureMainViewer();
  updateAvatarViewers();
  const claimed = player.daily === localDate();
  byId('daily-button').disabled = claimed;
  byId('daily-button').querySelector('strong').textContent = claimed ? 'Odebrano ✓' : 'Odbierz 25 ◉';
  renderLocation(currentLocation, false);
  if (!presenceChannel) startPresence();
}

function renderOnline(users) {
  const list = byId('online-list');
  const unique = Array.from(new Map(users.map((user) => [user.id || user.name, user])).values());
  list.replaceChildren();
  unique.forEach((user) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'friend-card online-card';
    const avatar = document.createElement('span');
    avatar.className = 'online-avatar';
    avatar.textContent = (user.name || 'G').slice(0, 1).toUpperCase();
    const name = document.createElement('span');
    name.textContent = user.id === presenceId ? `${user.name} (Ty)` : user.name;
    const dot = document.createElement('b');
    dot.textContent = '●';
    dot.title = 'Aktywny teraz';
    button.append(avatar, name, dot);
    button.addEventListener('click', () => showToast(`${user.name} jest teraz aktywny/a.`));
    list.append(button);
  });
  const add = document.createElement('button');
  add.type = 'button';
  add.className = 'friend-card add-friend';
  add.innerHTML = '<strong>♟+</strong><span>Dodaj<br>znajomego</span>';
  add.addEventListener('click', () => showToast('Wyszukiwarka znajomych pojawi się w kolejnym etapie.'));
  list.append(add);
}

function startPresence() {
  if (!player) return;
  const me = { id: presenceId, name: player.name, online_at: new Date().toISOString() };
  renderOnline([me]);
  if (!client) return;
  presenceChannel = client.channel('alcara-online', { config: { presence: { key: presenceId } } });
  presenceChannel.on('presence', { event: 'sync' }, () => {
    const users = Object.values(presenceChannel.presenceState()).flat();
    renderOnline(users.length ? users : [me]);
  }).subscribe(async (state) => {
    if (state === 'SUBSCRIBED') await presenceChannel.track(me);
  });
}

function updatePresence() {
  if (presenceChannel && player) presenceChannel.track({ id: presenceId, name: player.name, online_at: new Date().toISOString() });
}

function renderLocation(id, countVisit = true) {
  const locationData = locations[id] || locations.plac;
  currentLocation = id;
  document.querySelectorAll('.spot').forEach((button) => button.classList.toggle('active', button.dataset.location === id));
  byId('location-scene').dataset.scene = id;
  byId('location-kicker').textContent = locationData.kicker;
  byId('location-title').textContent = locationData.title;
  byId('location-description').textContent = locationData.description;
  const actionArea = byId('location-actions');
  actionArea.replaceChildren();
  locationData.actions.forEach((item) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `button${item.primary ? ' primary' : ''}`;
    button.textContent = item.label;
    button.addEventListener('click', item.action);
    actionArea.append(button);
  });
  if (countVisit && player && !player.visited.includes(id)) {
    player.visited.push(id);
    player.xp += 3;
    queueSave();
    byId('xp').textContent = player.xp;
    showToast(`Nowe miejsce odkryte: ${locationData.title} · +3 ✦`);
  }
}

function claimReward(key, coins, xp, message) {
  const rewardKey = `${key}:${localDate()}`;
  if (player.rewards[rewardKey]) {
    showToast('Ta nagroda została już dziś odebrana.');
    return;
  }
  player.rewards[rewardKey] = true;
  player.coins += coins;
  player.xp += xp;
  queueSave();
  renderGame();
  playTone(620);
  showToast(message);
}

function createOptionButtons() {
  const groups = [
    { id: 'top-options', key: 'top', values: [['base', 'Sportowy top'], ['casual', 'T-shirt'], ['punk', 'Kurtka punk'], ['formal', 'Elegancka']] },
    { id: 'legs-options', key: 'legs', values: [['base', 'Szorty'], ['casual', 'Casual'], ['punk', 'Punk'], ['formal', 'Klasyczne']] },
    { id: 'feet-options', key: 'feet', values: [['base', 'Boso'], ['casual', 'Sneakersy'], ['punk', 'Trapery'], ['formal', 'Półbuty']] },
    { id: 'head-options', key: 'head', values: [['casual', 'Naturalne'], ['punk', 'Punk'], ['formal', 'Upięte'], ['suit', 'Krótkie']] },
  ];
  groups.forEach((group) => {
    const container = byId(group.id);
    group.values.forEach(([value, label]) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'wardrobe-option';
      button.dataset.value = value;
      button.dataset.group = group.key;
      button.dataset.label = label;
      button.addEventListener('click', () => {
        const owned = draft.inventory[group.key].includes(value);
        const price = shopPrices[group.key]?.[value] || 0;
        if (!owned && creatorMode !== 'shop') {
          showToast('Ten przedmiot kupisz w Sklepie za StarCoins.');
          return;
        }
        if (!owned && draft.coins < price) {
          showToast(`Potrzebujesz ${price} StarCoins.`);
          return;
        }
        if (!owned) {
          draft.coins -= price;
          draft.inventory[group.key].push(value);
          player.coins = draft.coins;
          player.inventory[group.key] = [...draft.inventory[group.key]];
          queueSave();
          byId('coins').textContent = player.coins;
          showToast(`${label} kupiono za ${price} StarCoins!`);
        }
        draft.outfit3d[group.key] = value;
        refreshCreatorOptions();
        creatorViewer?.update({ ...draft.outfit3d, skin: draft.skin, gender: draft.gender, ...draft.beauty });
        playTone(480);
      });
      container.append(button);
    });
  });
}

function createSkinButtons() {
  const tones = ['#f5d0b5', '#deb08c', '#c98b62', '#9b6043', '#68402f', '#3f2a22'];
  tones.forEach((tone, index) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'skin-option';
    button.style.setProperty('--tone', tone);
    button.dataset.value = tone;
    button.setAttribute('aria-label', `Odcień skóry ${index + 1}`);
    button.addEventListener('click', () => {
      draft.skin = tone;
      refreshCreatorOptions();
      creatorViewer?.update({ ...draft.outfit3d, skin: draft.skin, gender: draft.gender, ...draft.beauty });
    });
    byId('skin-options').append(button);
  });
}

async function ensureWardrobeThumbnail(button, group, value) {
  const key = `${draft.gender}:${group}:${value}:${draft.skin}`;
  if (button.dataset.thumbKey === key) return;
  button.dataset.thumbKey = key;
  button.classList.add('thumb-loading');
  try {
    const { makeCatalogThumbnail } = await avatarModule();
    const dataUrl = await makeCatalogThumbnail({ gender: draft.gender, skin: draft.skin }, group, value);
    if (button.dataset.thumbKey === key) button.style.setProperty('--item-thumb', `url("${dataUrl}")`);
  } finally {
    if (button.dataset.thumbKey === key) button.classList.remove('thumb-loading');
  }
}

function refreshCreatorOptions() {
  document.querySelectorAll('.wardrobe-option').forEach((button) => {
    const group = button.dataset.group;
    const value = button.dataset.value;
    const owned = draft.inventory[group].includes(value);
    const price = shopPrices[group]?.[value] || 0;
    const ownedIndex = draft.inventory[group].indexOf(value);
    button.hidden = creatorMode === 'wardrobe' && !owned;
    button.style.order = creatorMode === 'wardrobe' ? String(-ownedIndex) : '0';
    button.classList.toggle('selected', draft.outfit3d[group] === value);
    button.classList.toggle('locked', !owned);
    button.textContent = owned ? button.dataset.label : `${button.dataset.label} · ${price} ●`;
    button.title = owned ? 'Masz ten przedmiot' : creatorMode === 'shop' ? `Kup za ${price} StarCoins` : 'Dostępne w Sklepie';
    if (!button.hidden) ensureWardrobeThumbnail(button, group, value);
  });
  byId('skin-options').querySelectorAll('button').forEach((button) => button.classList.toggle('selected', button.dataset.value === draft.skin));
}

function openCreator(required = false, mode = 'wardrobe') {
  if (!player) return;
  creatorMode = mode;
  draft = { ...player, beauty: { ...player.beauty }, outfit3d: { ...player.outfit3d }, inventory: Object.fromEntries(Object.entries(player.inventory).map(([key, items]) => [key, [...items]])) };
  byId('nickname').value = player.created ? player.name : player.name === 'Nowa osoba' ? '' : player.name;
  byId('creator-kicker').textContent = mode === 'shop' ? 'SKLEP ALCARY · STARCOINS' : 'STUDIO POSTACI';
  byId('creator-title').textContent = mode === 'shop' ? 'Kup nowe rzeczy' : 'Stwórz swój styl';
  refreshCreatorOptions();
  document.querySelectorAll('[data-preset]').forEach((button) => button.classList.toggle('selected', button.dataset.preset === draft.preset));
  document.querySelectorAll('[data-gender]').forEach((button) => button.classList.toggle('selected', button.dataset.gender === draft.gender));
  creator.dataset.required = required ? 'true' : 'false';
  creator.querySelector('.close-button').hidden = required;
  creator.showModal();
  requestAnimationFrame(() => ensureCreatorViewer());
  setTimeout(() => byId('nickname').focus(), 50);
}

function createBeautyOptions() {
  const colors = ['#5a3828', '#2e6f91', '#4f7942', '#8b693f', '#4c3d69'];
  colors.forEach((color, index) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'skin-option';
    button.style.setProperty('--tone', color);
    button.dataset.eyes = color;
    button.setAttribute('aria-label', `Kolor oczu ${index + 1}`);
    button.addEventListener('click', () => {
      draft.beauty.eyes = color;
      refreshBeautyOptions();
      creatorViewer?.update({ ...draft.outfit3d, skin: draft.skin, gender: draft.gender, ...draft.beauty });
    });
    byId('eye-options').append(button);
  });
}

function refreshBeautyOptions() {
  document.querySelectorAll('[data-eyes]').forEach((button) => button.classList.toggle('selected', button.dataset.eyes === draft.beauty.eyes));
  document.querySelectorAll('[data-makeup]').forEach((button) => button.classList.toggle('selected', button.dataset.makeup === draft.beauty.makeup));
}

function openBeautyClinic() {
  draft = { ...player, beauty: { ...player.beauty }, outfit3d: { ...player.outfit3d } };
  refreshBeautyOptions();
  beautyDialog.showModal();
}

function openCatalog(gender) {
  catalogGender = gender;
  catalogTab = 'top';
  cart = [];
  catalogDraft = { ...player, beauty: { ...player.beauty }, outfit3d: { ...player.outfit3d }, inventory: Object.fromEntries(Object.entries(player.inventory).map(([key, items]) => [key, [...items]])) };
  byId('catalog-title').textContent = gender === 'male' ? 'Moda męska' : 'Moda damska';
  byId('catalog-search').value = '';
  renderCatalog();
  renderCart();
  catalogDialog.showModal();
  requestAnimationFrame(() => ensureShopViewer());
}

function renderCatalog() {
  document.querySelectorAll('[data-catalog-tab]').forEach((button) => button.classList.toggle('active', button.dataset.catalogTab === catalogTab));
  byId('catalog-coins').textContent = player.coins;
  const filter = byId('catalog-search').value.trim().toLowerCase();
  const container = byId('catalog-items');
  container.replaceChildren();
  Object.entries(catalogLabels[catalogTab]).forEach(([value, [label, icon]]) => {
    if (filter && !label.toLowerCase().includes(filter)) return;
    const price = shopPrices[catalogTab][value];
    const owned = player.inventory[catalogTab].includes(value);
    const inCart = cart.some((item) => item.group === catalogTab && item.value === value);
    const card = document.createElement('article');
    card.className = `catalog-card${owned ? ' owned' : ''}`;
    const art = document.createElement('div');
    art.className = 'catalog-art loading';
    art.textContent = 'Ładowanie…';
    const name = document.createElement('strong');
    name.textContent = label;
    const priceText = document.createElement('span');
    priceText.textContent = owned ? 'Zakupione' : `● ${price} StarCoins`;
    const button = document.createElement('button');
    button.type = 'button';
    button.disabled = owned;
    button.textContent = owned ? 'Masz' : inCart ? 'W koszyku ✓' : 'Dodaj do kosza';
    button.addEventListener('click', () => {
      if (!cart.some((item) => item.group === catalogTab && item.value === value)) cart.push({ group: catalogTab, value, label, price });
      catalogDraft.outfit3d[catalogTab] = value;
      shopViewer?.update({ ...catalogDraft.outfit3d, skin: catalogDraft.skin, gender: catalogGender, ...catalogDraft.beauty });
      renderCatalog();
      renderCart();
    });
    card.append(art, name, priceText, button);
    container.append(card);
    avatarModule().then(({ makeCatalogThumbnail }) => makeCatalogThumbnail({ gender: catalogGender, skin: catalogDraft.skin }, catalogTab, value)).then((dataUrl) => {
      if (!art.isConnected) return;
      const image = document.createElement('img');
      image.src = dataUrl;
      image.alt = label;
      art.replaceChildren(image);
      art.classList.remove('loading');
    }).catch(() => { art.textContent = icon; art.classList.remove('loading'); });
  });
}

function renderCart() {
  byId('cart-count').textContent = cart.length;
  const total = cart.reduce((sum, item) => sum + item.price, 0);
  byId('cart-total').textContent = total;
  const container = byId('cart-items');
  container.replaceChildren();
  if (!cart.length) {
    const empty = document.createElement('p');
    empty.className = 'cart-empty';
    empty.textContent = 'Koszyk jest pusty. Dodaj coś z katalogu.';
    container.append(empty);
  }
  cart.forEach((item) => {
    const row = document.createElement('div');
    const text = document.createElement('span');
    text.textContent = `${item.label} · ${item.price} ●`;
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.textContent = 'Usuń';
    remove.addEventListener('click', () => {
      cart = cart.filter((candidate) => candidate !== item);
      renderCart();
      renderCatalog();
    });
    row.append(text, remove);
    container.append(row);
  });
  byId('buy-cart').disabled = !cart.length;
}

function buyCart() {
  const total = cart.reduce((sum, item) => sum + item.price, 0);
  if (!cart.length) return;
  if (player.coins < total) {
    showToast(`Brakuje ${total - player.coins} StarCoins.`);
    return;
  }
  player.coins -= total;
  cart.forEach((item) => {
    if (!player.inventory[item.group].includes(item.value)) player.inventory[item.group].push(item.value);
  });
  player.gender = catalogGender;
  player.outfit3d = { ...catalogDraft.outfit3d };
  cart = [];
  queueSave();
  renderGame();
  renderCart();
  renderCatalog();
  cartDialog.close();
  showToast(`Zakupy zakończone! Wydano ${total} StarCoins.`);
}

function openProfile() {
  byId('profile-book-name').textContent = player.name;
  byId('profile-status').value = '';
  byId('profile-description').value = player.profile.description || '';
  renderActivity();
  profileDialog.showModal();
  requestAnimationFrame(() => ensureProfileViewer());
}

function renderActivity() {
  const posts = player.profile.posts.length ? player.profile.posts : [
    { text: player.profile.status, likes: 1, time: 'Dzisiaj' },
    { text: 'Odkrywam Warszawę i kompletuję nowy strój ✦', likes: 2, time: 'Wczoraj' },
  ];
  const feed = byId('activity-feed');
  feed.replaceChildren();
  posts.forEach((post, index) => {
    const card = document.createElement('article');
    const avatar = document.createElement('div');
    avatar.className = 'feed-avatar';
    avatar.textContent = player.name.slice(0, 1).toUpperCase();
    const body = document.createElement('div');
    const name = document.createElement('strong');
    name.textContent = player.name;
    const text = document.createElement('p');
    text.textContent = post.text;
    const time = document.createElement('small');
    time.textContent = post.time;
    body.append(name, text, time);
    const like = document.createElement('button');
    like.type = 'button';
    like.textContent = `LOVE IT! ♥ ${post.likes || 0}`;
    like.addEventListener('click', () => {
      if (!player.profile.posts.length) return showToast('Opublikuj własny status, aby zbierać serduszka.');
      player.profile.posts[index].likes = (player.profile.posts[index].likes || 0) + 1;
      queueSave();
      renderActivity();
    });
    card.append(avatar, body, like);
    feed.append(card);
  });
}

function showToast(message) {
  const toast = byId('toast');
  clearTimeout(toastTimer);
  toast.textContent = message;
  toast.hidden = false;
  toastTimer = setTimeout(() => { toast.hidden = true; }, 3000);
}

function playTone(frequency = 520) {
  if (!soundEnabled) return;
  const AudioContext = window.AudioContext || window.webkitAudioContext;
  if (!AudioContext) return;
  const context = new AudioContext();
  const oscillator = context.createOscillator();
  const gain = context.createGain();
  oscillator.frequency.value = frequency;
  gain.gain.setValueAtTime(.06, context.currentTime);
  gain.gain.exponentialRampToValueAtTime(.001, context.currentTime + .12);
  oscillator.connect(gain).connect(context.destination);
  oscillator.start();
  oscillator.stop(context.currentTime + .12);
}

function message(error) {
  return ({
    invalid_credentials: 'Sprawdź adres e-mail i hasło.',
    email_not_confirmed: 'Potwierdź adres e-mail przed zalogowaniem.',
    weak_password: 'Wybierz silniejsze hasło.',
    user_already_exists: 'Konto z tym adresem już istnieje.',
    over_email_send_rate_limit: 'Odczekaj chwilę przed ponowną próbą.',
  })[error.code] || 'Operacja się nie powiodła. Spróbuj ponownie.';
}

function moveTicket() {
  byId('ticket').style.left = `${5 + Math.random() * 82}%`;
  byId('ticket').style.top = `${8 + Math.random() * 75}%`;
}

function openTramGame() {
  clearInterval(tramTimer);
  tramScore = 0;
  tramTime = 15;
  byId('tram-score').textContent = '0';
  byId('tram-time').textContent = '15 s';
  byId('ticket').hidden = true;
  byId('start-tram').disabled = false;
  byId('start-tram').textContent = 'Start';
  tramDialog.showModal();
}

function startTramGame() {
  clearInterval(tramTimer);
  tramScore = 0;
  tramTime = 15;
  byId('tram-score').textContent = '0';
  byId('tram-time').textContent = '15 s';
  byId('ticket').hidden = false;
  byId('start-tram').disabled = true;
  moveTicket();
  tramTimer = setInterval(() => {
    tramTime -= 1;
    byId('tram-time').textContent = `${tramTime} s`;
    if (tramTime <= 0) finishTramGame(true);
  }, 1000);
}

function finishTramGame(reward) {
  clearInterval(tramTimer);
  tramTimer = null;
  byId('ticket').hidden = true;
  byId('start-tram').disabled = false;
  byId('start-tram').textContent = 'Zagraj ponownie';
  if (!reward || tramScore === 0) return;
  const coins = tramScore * 3;
  player.coins += coins;
  player.xp += tramScore;
  queueSave();
  renderGame();
  showToast(`Tramwaj ukończony! +${coins} monet`);
  playTone(720);
}

authForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!ready || busy || session) return;
  const register = route() === 'register';
  const credentials = { email: byId('email').value.trim(), password: byId('password').value };
  busy = true;
  status.textContent = '';
  render();
  try {
    const result = register
      ? await client.auth.signUp({ ...credentials, options: { emailRedirectTo: location.href.split(/[?#]/)[0] } })
      : await client.auth.signInWithPassword(credentials);
    if (result.error) throw result.error;
    session = result.data.session;
    authForm.reset();
    if (session) {
      guest = false;
      playerOwner = '';
      loadPlayer();
      location.hash = '#gra';
    } else {
      status.textContent = 'Sprawdź skrzynkę e-mail i potwierdź konto.';
    }
  } catch (error) {
    status.textContent = message(error);
  } finally {
    busy = false;
    render();
  }
});

byId('guest-button').addEventListener('click', () => {
  guest = true;
  session = null;
  playerOwner = '';
  loadPlayer();
  location.hash = '#gra';
});
byId('account-play').addEventListener('click', () => { location.hash = '#gra'; });
logout.addEventListener('click', async () => {
  if (busy) return;
  busy = true;
  if (session && client) await client.auth.signOut();
  if (presenceChannel && client) await client.removeChannel(presenceChannel);
  presenceChannel = null;
  session = null;
  guest = false;
  player = null;
  playerOwner = '';
  busy = false;
  location.hash = '#menu';
  render();
});

document.querySelectorAll('.spot').forEach((button) => button.addEventListener('click', () => {
  renderLocation(button.dataset.location);
  playTone(380);
}));
document.querySelectorAll('[data-board]').forEach((button) => button.addEventListener('click', () => {
  const action = button.dataset.board;
  if (action === 'games') openTramGame();
  if (action === 'shop') shopHub.showModal();
  if (action === 'chat') showToast('Chat dzielnicy zostanie otwarty w kolejnym etapie.');
  if (action === 'palace') claimReward('palace', 10, 8, 'Widok z Pałacu Kultury! +10 monet');
  if (action === 'events') creativeHub.showModal();
  if (action === 'clinic') openBeautyClinic();
}));
document.querySelectorAll('[data-social]').forEach((button) => button.addEventListener('click', () => {
  const action = button.dataset.social;
  if (action === 'profile') openProfile();
  else if (action === 'shop') shopHub.showModal();
  else if (action === 'games') openTramGame();
  else if (action === 'world') worldDialog.showModal();
  else if (action === 'home') showToast('Jesteś na stronie startowej Warszawy.');
  else if (action === 'settings') location.hash = '#logowanie';
  else showToast(`${button.textContent.trim()} pojawi się w kolejnym etapie.`);
}));
document.querySelectorAll('.friend-card:not(.add-friend)').forEach((button) => button.addEventListener('click', () => showToast(`${button.querySelector('span').textContent} jest online.`)));
document.querySelector('.add-friend').addEventListener('click', () => showToast('Wyszukiwarka znajomych pojawi się w kolejnym etapie.'));
document.querySelector('.mood-box').addEventListener('click', () => showToast('Status profilu: hej :3'));
worldDialog.querySelector('.close-button').addEventListener('click', () => worldDialog.close());
worldDialog.querySelector('.warsaw').addEventListener('click', () => { worldDialog.close(); showToast('Warszawa jest aktywnym miastem.'); });
byId('profile-button').addEventListener('click', () => openCreator());
shopHub.querySelector('.close-button').addEventListener('click', () => shopHub.close());
creativeHub.querySelector('.close-button').addEventListener('click', () => creativeHub.close());
document.querySelectorAll('[data-hub-action]').forEach((button) => button.addEventListener('click', () => {
  const action = button.dataset.hubAction;
  if (action === 'beauty') {
    shopHub.close();
    openBeautyClinic();
    return;
  }
  if (action === 'fashion-female' || action === 'fashion-male') {
    shopHub.close();
    openCatalog(action === 'fashion-male' ? 'male' : 'female');
    return;
  }
  showToast(`${button.querySelector('b').textContent}: dział pojawi się w następnym etapie.`);
}));
document.querySelectorAll('[data-creative]').forEach((button) => button.addEventListener('click', () => {
  const action = button.dataset.creative;
  if (action === 'Design Studio' || action === 'Looks') {
    creativeHub.close();
    openCreator();
    return;
  }
  if (action === 'Filmy') {
    creativeHub.close();
    openTramGame();
    return;
  }
  showToast(`${action}: moduł jest już na mapie i zostanie rozwinięty w kolejnym etapie.`);
}));
document.querySelectorAll('[data-gender]').forEach((button) => button.addEventListener('click', () => {
  draft.gender = button.dataset.gender;
  document.querySelectorAll('[data-gender]').forEach((item) => item.classList.toggle('selected', item === button));
  creatorViewer?.update({ ...draft.outfit3d, skin: draft.skin, gender: draft.gender, ...draft.beauty });
}));
document.querySelectorAll('[data-preset]').forEach((button) => button.addEventListener('click', () => {
  const presets = {
    nova: { gender: 'female', skin: '#c98b62' },
    luna: { gender: 'female', skin: '#f5d0b5' },
    alex: { gender: 'male', skin: '#9b6043' },
    maks: { gender: 'male', skin: '#deb08c' },
  };
  draft.preset = button.dataset.preset;
  Object.assign(draft, presets[draft.preset]);
  document.querySelectorAll('[data-preset]').forEach((item) => item.classList.toggle('selected', item === button));
  document.querySelectorAll('[data-gender]').forEach((item) => item.classList.toggle('selected', item.dataset.gender === draft.gender));
  refreshCreatorOptions();
  creatorViewer?.update({ ...draft.outfit3d, skin: draft.skin, gender: draft.gender, ...draft.beauty });
}));
document.querySelectorAll('[data-makeup]').forEach((button) => button.addEventListener('click', () => {
  draft.beauty.makeup = button.dataset.makeup;
  refreshBeautyOptions();
}));
catalogDialog.querySelector('.catalog-close').addEventListener('click', () => catalogDialog.close());
document.querySelectorAll('[data-catalog-tab]').forEach((button) => button.addEventListener('click', () => {
  catalogTab = button.dataset.catalogTab;
  renderCatalog();
}));
byId('catalog-search').addEventListener('input', renderCatalog);
byId('open-cart').addEventListener('click', () => { renderCart(); cartDialog.showModal(); });
cartDialog.querySelector('.cart-head button').addEventListener('click', () => cartDialog.close());
byId('buy-cart').addEventListener('click', buyCart);
profileDialog.querySelector('.profile-close').addEventListener('click', () => profileDialog.close());
byId('status-form').addEventListener('submit', (event) => {
  event.preventDefault();
  const text = byId('profile-status').value.trim();
  if (!text) return;
  player.profile.status = text;
  player.profile.posts.unshift({ text, likes: 0, time: 'Teraz' });
  player.profile.posts = player.profile.posts.slice(0, 8);
  byId('profile-status').value = '';
  queueSave();
  renderActivity();
  showToast('Status opublikowany!');
});
byId('description-form').addEventListener('submit', (event) => {
  event.preventDefault();
  player.profile.description = byId('profile-description').value.trim();
  queueSave();
  showToast('Opis profilu zapisany.');
});
byId('daily-button').addEventListener('click', () => {
  if (player.daily === localDate()) return;
  player.daily = localDate();
  player.coins += 25;
  player.xp += 5;
  queueSave();
  renderGame();
  playTone(680);
  showToast('Dzisiejsza iskra odebrana! +25 monet');
});

byId('creator-form').addEventListener('submit', (event) => {
  event.preventDefault();
  const nickname = byId('nickname').value.trim();
  if (nickname.length < 2) {
    byId('nickname').setCustomValidity('Pseudonim musi mieć co najmniej 2 znaki.');
    byId('nickname').reportValidity();
    return;
  }
  byId('nickname').setCustomValidity('');
  player = { ...player, ...draft, name: nickname, created: true };
  queueSave();
  updatePresence();
  renderGame();
  creator.close();
  showToast('Postać zapisana. Witaj w Alcarze!');
});

byId('beauty-form').addEventListener('submit', (event) => {
  event.preventDefault();
  player.beauty = { ...draft.beauty };
  queueSave();
  updateAvatarViewers();
  beautyDialog.close();
  showToast('Nowy wygląd zapisany w Klinice piękna.');
});

byId('start-tram').addEventListener('click', startTramGame);
byId('ticket').addEventListener('click', () => {
  if (!tramTimer) return;
  tramScore += 1;
  byId('tram-score').textContent = tramScore;
  moveTicket();
  playTone(520 + Math.min(tramScore, 8) * 25);
});
byId('close-tram').addEventListener('click', () => { finishTramGame(true); tramDialog.close(); });
tramDialog.addEventListener('cancel', () => finishTramGame(true));

byId('sound-toggle').addEventListener('click', () => {
  soundEnabled = !soundEnabled;
  byId('sound-toggle').setAttribute('aria-pressed', String(soundEnabled));
  byId('sound-toggle').textContent = soundEnabled ? '♫' : '♪';
  if (soundEnabled) playTone(540);
});

window.addEventListener('hashchange', () => {
  byId('password').value = '';
  status.textContent = '';
  render();
});

createOptionButtons();
createSkinButtons();
createBeautyOptions();
render();
status.textContent = configured ? 'Łączenie z Supabase…' : 'Supabase nie jest skonfigurowany.';

if (configured) {
  import('https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/+esm').then(async ({ createClient }) => {
    client = createClient(config.url, config.publishableKey);
    const { data, error } = await client.auth.getSession();
    if (error) throw error;
    session = data.session;
    ready = true;
    status.textContent = '';
    if (session) loadPlayer();
    client.auth.onAuthStateChange((event, currentSession) => {
      session = currentSession;
      if (event === 'SIGNED_OUT') {
        player = null;
        playerOwner = '';
      }
      render();
    });
    render();
  }).catch(() => {
    ready = true;
    status.textContent = 'Nie udało się uruchomić Supabase. Odśwież stronę.';
    render();
  });
}
