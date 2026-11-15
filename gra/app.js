const byId = (id) => document.getElementById(id);
const config = window.ALCARA_CONFIG;
const configured = Boolean(config?.url && config.publishableKey?.startsWith('sb_publishable_') && location.protocol !== 'file:');

const authForm = byId('auth');
const status = byId('status');
const submit = byId('submit');
const logout = byId('logout');
const authSwitch = byId('auth-switch');
const creator = byId('creator');
const tramDialog = byId('tram-game');

const defaultPlayer = {
  version: 1,
  created: false,
  name: 'Nowa osoba',
  skin: '#d89c71',
  hair: '#3b2929',
  hairStyle: '1',
  outfit: '#ef795f',
  coins: 80,
  xp: 0,
  visited: [],
  rewards: {},
  daily: '',
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
  return {
    ...defaultPlayer,
    ...(value && typeof value === 'object' ? value : {}),
    visited: Array.isArray(value?.visited) ? value.visited : [],
    rewards: value?.rewards && typeof value.rewards === 'object' ? value.rewards : {},
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

function setAvatarStyle(element, source = player) {
  if (!element || !source) return;
  element.style.setProperty('--skin', source.skin);
  element.style.setProperty('--hair', source.hair);
  element.style.setProperty('--outfit', source.outfit);
  element.dataset.hair = source.hairStyle;
}

function render() {
  const screen = route();
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
  setAvatarStyle(byId('mini-avatar'));
  setAvatarStyle(byId('scene-avatar'));
  const claimed = player.daily === localDate();
  byId('daily-button').disabled = claimed;
  byId('daily-button').querySelector('strong').textContent = claimed ? 'Odebrano ✓' : 'Odbierz 25 ◉';
  renderLocation(currentLocation, false);
}

function renderLocation(id, countVisit = true) {
  const locationData = locations[id] || locations.plac;
  currentLocation = id;
  document.querySelectorAll('.place').forEach((button) => button.classList.toggle('active', button.dataset.location === id));
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
    { id: 'skin-options', key: 'skin', values: ['#f4c9a5', '#d89c71', '#9c634c', '#603f35'], type: 'swatch' },
    { id: 'hair-options', key: 'hairStyle', values: ['1', '2', '3'], type: 'choice' },
    { id: 'outfit-options', key: 'outfit', values: ['#ef795f', '#f4b84d', '#57a7b7', '#4a725c', '#d07aa5'], type: 'swatch' },
  ];
  groups.forEach((group) => {
    const container = byId(group.id);
    group.values.forEach((value, index) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = group.type;
      button.dataset.value = value;
      button.setAttribute('aria-label', group.type === 'choice' ? `Fryzura ${index + 1}` : `Kolor ${index + 1}`);
      if (group.type === 'swatch') button.style.background = value;
      else button.textContent = `Styl ${index + 1}`;
      button.addEventListener('click', () => {
        draft[group.key] = value;
        container.querySelectorAll('button').forEach((item) => item.classList.toggle('selected', item === button));
        setAvatarStyle(byId('avatar-preview'), draft);
        playTone(440 + index * 60);
      });
      container.append(button);
    });
  });
}

function openCreator(required = false) {
  if (!player) return;
  draft = { ...player };
  byId('nickname').value = player.created ? player.name : player.name === 'Nowa osoba' ? '' : player.name;
  ['skin-options', 'hair-options', 'outfit-options'].forEach((id) => {
    const key = id === 'skin-options' ? 'skin' : id === 'hair-options' ? 'hairStyle' : 'outfit';
    byId(id).querySelectorAll('button').forEach((button) => button.classList.toggle('selected', button.dataset.value === draft[key]));
  });
  creator.dataset.required = required ? 'true' : 'false';
  creator.querySelector('.close-button').hidden = required;
  setAvatarStyle(byId('avatar-preview'), draft);
  creator.showModal();
  setTimeout(() => byId('nickname').focus(), 50);
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
  session = null;
  guest = false;
  player = null;
  playerOwner = '';
  busy = false;
  location.hash = '#menu';
  render();
});

document.querySelectorAll('.place').forEach((button) => button.addEventListener('click', () => {
  renderLocation(button.dataset.location);
  playTone(380);
}));
byId('profile-button').addEventListener('click', () => openCreator());
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
  renderGame();
  creator.close();
  showToast('Postać zapisana. Witaj w Alcarze!');
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
