// Logika ekranów i połączenia z Supabase Auth.
const byId = (id) => document.getElementById(id);
const form = byId('auth');
const status = byId('status');
const submit = byId('submit');
const logout = byId('logout');
const authSwitch = byId('switch');
const config = window.ALCARA_CONFIG;
const configured = Boolean(config?.url && config.publishableKey?.startsWith('sb_publishable_') && location.protocol !== 'file:');
const unavailable = 'Supabase nie jest skonfigurowany. Menu jest dostępne w trybie podglądu.';
let client;
let session;
let ready = false;
let busy = false;

function route() {
  const hash = location.hash;
  return hash === '#rejestracja' ? 'register' : hash === '#logowanie' ? 'login' : 'menu';
}

function render(focus = false) {
  const screen = route();
  const register = screen === 'register';
  const menu = screen === 'menu';
  byId('menu').hidden = !menu;
  byId('konto').hidden = menu;
  byId('title').textContent = session ? 'Twoje konto' : register ? 'Załóż konto' : 'Zaloguj się';
  byId('nav-login').textContent = session ? 'Moje konto' : 'Zaloguj się';
  form.hidden = Boolean(session);
  authSwitch.hidden = Boolean(session);
  logout.hidden = !session;
  logout.disabled = busy;
  submit.disabled = !ready || busy;
  submit.textContent = busy ? 'Chwileczkę…' : register ? 'Załóż konto' : 'Zaloguj się';
  byId('password').autocomplete = register ? 'new-password' : 'current-password';
  byId('password').minLength = register ? 8 : 1;
  authSwitch.href = register ? '#logowanie' : '#rejestracja';
  authSwitch.textContent = register ? 'Mam już konto' : 'Utwórz konto';
  if (focus && !menu) byId('title').focus();
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

form.addEventListener('submit', async (event) => {
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
    form.reset();
    status.textContent = session ? 'Zalogowano do Alcary.' : 'Jeśli rejestracja wymaga potwierdzenia, sprawdź skrzynkę e-mail.';
  } catch (error) {
    status.textContent = message(error);
  } finally {
    busy = false;
    render();
  }
});

logout.addEventListener('click', async () => {
  if (busy || !session) return;
  busy = true;
  render();
  try {
    const { error } = await client.auth.signOut();
    if (error) throw error;
    session = null;
    status.textContent = 'Wylogowano.';
  } catch (error) {
    status.textContent = message(error);
  } finally {
    busy = false;
    render(true);
  }
});

function initialStatus() {
  return ready ? '' : configured ? 'Łączenie z Supabase…' : unavailable;
}

window.addEventListener('hashchange', () => {
  byId('password').value = '';
  status.textContent = initialStatus();
  render(true);
});

render();
status.textContent = initialStatus();
if (configured) {
  import('https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/+esm').then(async ({ createClient }) => {
    client = createClient(config.url, config.publishableKey);
    const { data, error } = await client.auth.getSession();
    if (error) throw error;
    session = data.session;
    ready = true;
    status.textContent = '';
    client.auth.onAuthStateChange((event, currentSession) => {
      session = currentSession;
      if (event === 'SIGNED_IN' && route() === 'menu') location.hash = '#logowanie';
      render();
    });
    render();
  }).catch(() => {
    status.textContent = 'Nie udało się uruchomić Supabase. Sprawdź konfigurację i odśwież stronę.';
  });
}

