'use strict';

const key = 'slodki-filtr-chapter-one-v1';
const people = {
  antek:'Antek', emozosia:'Emozosia', dobosz:'Dobosz', blazej:'Błażej',
  piotr:'Piotr', adam:'Adam', antosia:'Antosia', martyna:'Martyna', kasia:'Kasia'
};
const apps = [
  ['messages','Wiadomości','✉','#a23465'], ['instagram','Instagram','◎','#a4427e'],
  ['facebook','Facebook','f','#445a91'], ['contacts','Kontakty','♙','#69518a'],
  ['calendar','Kalendarz','▦','#9a4354'], ['gallery','Galeria','✿','#8b4a78']
];
const fresh = () => ({
  step:'invite', place:'', time:'', ending:'', history:[], likes:[], photos:[],
  trust:Object.fromEntries(Object.keys(people).map(id => [id,0])),
  clue:false, spotlight:''
});
let state = fresh();
let storageOK = true;
try {
  const saved = JSON.parse(localStorage.getItem(key));
  if (saved && typeof saved === 'object') state = {...state,...saved};
} catch { storageOK = false; }
let page = 'home', person = 'antosia';
const screen = document.querySelector('#screen');
const header = document.querySelector('header');
const back = document.querySelector('#back');
const nameOf = id => people[id] || (id === 'you' ? 'Witold' : 'Genzie');
const img = (id, cls = '') => `<img class="${cls}" alt="${people[id]}" src="postacie/${id}.webp">`;
const button = (label, attribute, value, cls = 'choice') =>
  `<button type="button" class="${cls}" data-${attribute}="${value}">${label}</button>`;
const actions = (...items) => `<div class="actions">${items.join('')}</div>`;
const place = () => state.place === 'park' ? 'Park' : 'Kawiarnia';
const photo = (setting, ids, label) =>
  `<div class="photo ${setting}" role="img" aria-label="${label}">${ids.map(id => img(id)).join('')}</div>`;
const goals = {
  invite:['Wiadomość od Antosi','Otwórz zaproszenie','messages'],
  plan:['Ustal pierwsze spotkanie','Wybierz miejsce i godzinę','calendar'],
  meet:['Pierwsze spotkanie','Ruszaj na zapisane wyjście','calendar'],
  bond:['Wspólny stolik','Spędź czas z ekipą','scene'],
  rumor:['Wiadomość od Kasi','Sprawdź, skąd wzięła się plotka','messages'],
  finale:['Wspólna rozmowa','Wróć do ekipy i podejmij decyzję','scene'],
  done:['Rozdział ukończony','Zobacz, jak potoczył się wieczór','scene']
};
const consequences = {
  direct:['Stolik dla wszystkich','Przy stole wychodzi na jaw, że drugie zaproszenie było szkicem. Gdy ekipa widzi obie wersje planu, napięcie opada. Martyna przesuwa krzesło w twoją stronę. „Następnym razem sam wybierasz miejsce”.'],
  quiet:['Spokojny finał','Prosisz Kasię, żeby pogadała z Martyną na osobności. Obie wracają z ustaleniem, że następne zaproszenie pójdzie do wszystkich jednocześnie. Piotr jeszcze przez chwilę zastanawia się, skąd wziął się szum.'],
  twist:['Pęknięcie w kadrze','Wrzucasz na grupę przycięty fragment, żeby wymusić reakcję. Piotr ci przytakuje, ale Martyna pokazuje pełne zaproszenie. Antosia usuwa twój zrzut i prosi, żebyś dał ekipie czas.']
};
function save() {
  try { localStorage.setItem(key,JSON.stringify(state)); storageOK = true; }
  catch { storageOK = false; }
}
function tell(...lines) { state.history.push(...lines); }
function change(delta) {
  for (const [id, plus] of Object.entries(delta)) state.trust[id] = Math.min(5,Math.max(-3,state.trust[id]+plus));
}
function home() {
  const [label, description, dest] = goals[state.step];
  return `<div class="hero"><p class="small">Rozdział 1 · Pierwsze zaproszenie</p><h1>Słodki Filtr<em>Genzie</em></h1></div>
  <button class="task" data-page="${dest}"><span><strong>${label}</strong><small>${description}</small></span><span aria-hidden="true">→</span></button>
  <nav class="apps" aria-label="Aplikacje">${apps.map(([id,title,icon,color]) =>
    `<button class="app" data-page="${id}"><span class="icon" style="--tile:${color}" aria-hidden="true">${icon}</span>${title}</button>`).join('')}</nav>
  <div class="save"><small>${storageOK ? 'Autozapis · Witold' : 'Zapis niedostępny · Witold'}</small>${button('Zacznij od nowa','act','reset','link')}</div>`;
}
const intro = ['antosia','Hej, Witold! Ekipa szuka miejsca na małe wyjście. Wpadasz?'];
function messages() {
  const history = [intro,...state.history];
  const choices = {
    invite:actions(button('Jasne! Z chęcią was poznam.','act','warm','choice primary'),button('Chętnie. Mogę pomóc z organizacją.','act','help')),
    plan:actions(button('Otwórz kalendarz','page','calendar','choice primary')),
    meet:actions(button('Sprawdź termin','page','calendar')),
    bond:actions(button('Wróć na spotkanie','page','scene')),
    rumor:actions(button('Poproś Kasię o cały wątek','act','context','choice primary'),button('Porozmawiaj z ekipą na miejscu','act','skip')),
    finale:actions(button('Otwórz scenę finałową','page','scene','choice primary')),
    done:actions(button('Zobacz zakończenie','page','scene'))
  };
  return `<section aria-label="Rozmowa z ekipą"><div class="history">${history.map(([id,line]) =>
    `<div class="bubble ${id === 'you' ? 'you' : ''}"><small>${nameOf(id)}</small><p>${line}</p></div>`).join('')}</div>${choices[state.step]}</section>`;
}
function calendar() {
  if (state.step === 'invite') return '<p>Brak spotkań. Odpowiedz na zaproszenie w Wiadomościach.</p>'+actions(button('Wiadomości','page','messages'));
  if (state.step === 'plan') return `<h2>Umów wyjście</h2><p>Ekipa ma dwa wolne terminy.</p><span class="day">Piątek</span>${actions(
    button('16:30 · Kawiarnia','act','cafe','choice primary'),button('17:15 · Park','act','park'))}`;
  return `<span class="day">Piątek · ${state.time}</span><h2>${place()}</h2><p>${state.step === 'meet' ? 'Zaproszenie wysłane do całej grupy.' : 'Spotkanie rozpoczęte.'}</p>${actions(
    state.step === 'meet' ? button('Idź na spotkanie','act','arrive','choice primary') : button('Otwórz scenę','page','scene','choice primary'),
    ...(state.step === 'meet' ? [button('Zmień termin','act','replan')] : []))}`;
}
function scene() {
  if (state.step === 'done') {
    const [title,text] = consequences[state.ending];
    return `<p class="small">Koniec rozdziału 1</p><h2>${title}</h2>${photo('studio',['martyna','antosia'],'Po rozmowie z ekipą')}<p>${text}</p>${actions(button('Zobacz relacje','page','contacts','choice primary'),button('Ekran główny','page','home'))}`;
  }
  if (state.step === 'bond') return `<p class="small">Piątek · ${state.time}</p><h2>${place()}</h2>${photo(state.place,['antosia','piotr'],'Antosia i Piotr witają Witolda')}<p>${state.place === 'park' ? 'Piotr łapie uciekający podmuchowi koc. Antosia macha ci z ławki.' : 'Antosia macha zza szyby. Piotr dosuwa stolik, żeby zrobić miejsce dla reszty.'} Po chwili cała ekipa jest w komplecie. Dobosz ustawia samowyzwalacz.</p><p>Jak chcesz się włączyć?</p>${actions(
    button('Zaproś wszystkich do wspólnego zdjęcia','act','group','choice primary'),
    button('Pomóż Martynie ogarnąć playlistę','act','music'),
    button('Podbij do Piotra z żartem o filtrach','act','joke'))}`;
  if (state.step === 'rumor') return `<h2>Chwila przerwy</h2>${photo(state.place,['kasia','martyna'],'Kasia i Martyna na wyjściu')}<p>Wibruje ci telefon. Kasia właśnie coś wysłała.</p>${actions(button('Przeczytaj wiadomość','page','messages','choice primary'))}`;
  if (state.step === 'finale') return `<p class="small">Po zdjęciu grupowym</p><h2>Druga wersja zaproszenia</h2>${photo('studio',['kasia','martyna'],'Kasia i Martyna czekają na wyjaśnienie')}<p>${state.clue ? 'W całej rozmowie widać, że drugie zaproszenie było szkicem. Kasia zgadza się pokazać je ekipie.' : 'Przy stole krążą dwie wersje tej samej historii. Martyna pyta, co właściwie zobaczyłeś.'}</p>${actions(
    button(state.clue ? 'Pokaż pełny wątek za zgodą Kasi' : 'Poproś Martynę o wyjaśnienie przy wszystkich','act','direct','choice primary'),
    button('Zaproponuj rozmowę na osobności','act','quiet'),
    button('Podkręć plotkę przyciętym zrzutem','act','twist'))}`;
  return actions(button('Otwórz kalendarz','page','calendar'));
}
function contacts() {
  return Object.keys(people).map(id => {
    const score = state.trust[id];
    const label = score >= 3 ? 'Bliska relacja' : score > 0 ? 'Nić sympatii' : score < 0 ? 'Napięcie' : 'Na razie neutralnie';
    return `<button class="row" data-person="${id}">${img(id,'avatar')}<span><strong>${people[id]}</strong><small>${label}</small></span><span class="arrow" aria-hidden="true">›</span></button>`;
  }).join('');
}
function profile() {
  const score = state.trust[person];
  return `<h2>${people[person]}</h2>${photo('studio',[person],people[person])}<div class="card"><strong>Relacja z Witoldem</strong><p>${score > 0 ? '+' : ''}${score} pkt</p><meter class="meter" min="-3" max="5" value="${score}" aria-label="Relacja z Witoldem"></meter></div>`;
}
function social(network) {
  const facebook = network === 'facebook';
  const posts = facebook
    ? [['plan','adam','Genzie · grupa prywatna', state.place ? `Plan wyjścia · ${place()}, piątek, ${state.time}.` : 'Piątkowy plan w przygotowaniu. Kto ma pomysł?']]
    : [['test','emozosia','@emozosia','Nowy filtr. W świetle zachodu działa najlepiej.', 'studio',['emozosia','dobosz']]];
  if (state.photos.length) posts.unshift(facebook
    ? ['after','blazej','Genzie · po spotkaniu', state.step === 'done' ? consequences[state.ending][0] : 'Na czacie pojawiły się dwie wersje zaproszenia. Wyjaśnimy to razem?']
    : ['outing','martyna','@martyna','Nasze pierwsze wspólne wyjście. Dobosz, dzięki za zdjęcie!',state.place,['martyna','antosia']]);
  return posts.map(([id,who,handle,copy,setting,cast]) => {
    const liked = state.likes.includes(id);
    return `<article class="card post"><div class="row">${img(who,'avatar')}<strong>${handle}</strong></div>${setting ? photo(setting,cast,copy) : ''}<p>${copy}</p>
    <button class="choice" aria-pressed="${liked}" data-like="${id}">${liked ? '♥ Lubię to · 1' : '♡ Polub'}</button></article>`;
  }).join('');
}
function gallery() {
  return `<h2>Album Witolda</h2>${state.photos.length ? `<figure class="card">${photo(state.place,['martyna','antosia'],'Zdjęcie z pierwszego wyjścia')}<figcaption>Pierwsze wyjście · ${place()}</figcaption></figure>` : '<p>Zdjęcie z wyjścia trafi tutaj po spotkaniu.</p>'}
  <h2>Portrety Genzie</h2><div class="cast">${Object.keys(people).map(id => `<figure class="portrait">${img(id)}<figcaption>${people[id]}</figcaption></figure>`).join('')}</div>`;
}
const views = {home,messages,calendar,scene,contacts,profile,gallery,instagram:()=>social('instagram'),facebook:()=>social('facebook'),
  reset:()=>`<h2>Rozpocząć od nowa?</h2><p>To usunie obecny zapis rozdziału.</p>${actions(button('Tak, usuń zapis','act','erase','choice primary'),button('Wróć do gry','page','home'))}`};
function render(next, focus = true) {
  page = next;
  header.hidden = next === 'home';
  back.dataset.page = next === 'profile' ? 'contacts' : 'home';
  document.querySelector('#app-title').textContent = apps.find(([id]) => id === next)?.[1] ||
    ({scene:'Rozdział 1',profile:'Kontakt',reset:'Zapis gry'}[next] || '');
  document.querySelector('#clock').textContent = ['bond','rumor','finale','done'].includes(state.step) ? state.time : '15:42';
  screen.className = next;
  screen.innerHTML = views[next]();
  if (focus) {
    screen.focus({preventScroll:true});
    screen.scrollTop = next === 'messages' ? screen.scrollHeight : 0;
  }
}
function advance(action) {
  switch (action) {
    case 'warm':
    case 'help': {
      const warm = action === 'warm';
      tell(['you',warm ? 'Jasne! Z chęcią was poznam.' : 'Chętnie. Mogę pomóc z organizacją.'],['antosia','Super. Wybierz w kalendarzu kawiarnię albo park. Reszta potwierdziła oba terminy.']);
      change(warm ? {antosia:1} : {adam:1,antosia:1});
      state.step = 'plan';
      return 'messages';
    }
    case 'cafe':
    case 'park':
      state.place = action;
      state.time = action === 'park' ? '17:15' : '16:30';
      state.step = 'meet';
      tell(['adam',`Plan potwierdzony. ${place()}, piątek o ${state.time}.`]);
      return 'calendar';
    case 'replan':
      state.step = 'plan';
      state.place = '';
      state.time = '';
      tell(['you','Zaraz wyślę zaktualizowaną godzinę.']);
      return 'calendar';
    case 'arrive':
      state.step = 'bond';
      return 'scene';
    case 'group':
    case 'music':
    case 'joke': {
      const activity = {
        group:['Dobra, zmieścimy się wszyscy!',{antosia:1,kasia:1,dobosz:2,adam:1}],
        music:['Martyna dopisuje twoje trzy propozycje do kolejki.',{martyna:2,emozosia:1,blazej:1}],
        joke:['Piotr parska śmiechem i prosi, żebyś powtórzył żart dla Antka.',{piotr:2,antek:2}]
      }[action];
      change(activity[1]);
      state.photos.push('outing');
      tell(['dobosz','Zdjęcie już w galerii. Fajnie, że wpadłeś!'],['genzie',activity[0]],['kasia','Ktoś podesłał mi drugą wersję zaproszenia. Wygląda, jakby Martyna miała spotkać się tylko z częścią ekipy. Mam cały wątek. Chcesz zobaczyć?']);
      state.step = 'rumor';
      return 'scene';
    }
    case 'context':
    case 'skip':
      state.clue = action === 'context';
      tell(['you',state.clue ? 'Podeślij całość. Chcę sprawdzić kontekst.' : 'Pogadamy o tym przy stole.']);
      if (state.clue) tell(['kasia','Tu jest całość. Drugie zaproszenie to stary szkic. Możesz pokazać ekipie, jeśli pomoże.']);
      state.step = 'finale';
      return 'scene';
    case 'direct':
    case 'quiet':
    case 'twist':
      change({direct:{martyna:2,kasia:2,antosia:1},quiet:{kasia:2,martyna:1},twist:{martyna:-3,antosia:-2,kasia:-2,piotr:1}}[action]);
      state.ending = action;
      state.step = 'done';
      tell(['genzie',consequences[action][0]+'.']);
      return 'scene';
    case 'erase':
      state = fresh();
      return 'home';
    default:
      return page;
  }
}
document.querySelector('.phone').addEventListener('click', event => {
  const target = event.target.closest('button');
  if (!target) return;
  const {page:destination,act,person:id,like} = target.dataset;
  if (destination) render(destination);
  else if (id) { person = id; render('profile'); }
  else if (like) {
    state.likes = state.likes.includes(like) ? state.likes.filter(x => x !== like) : [...state.likes,like];
    save();
    render(page,false);
    screen.querySelector('[data-like="'+like+'"]').focus({preventScroll:true});
  } else if (act === 'reset') render('reset');
  else if (act) {
    const next = advance(act);
    save();
    render(next);
  }
});
render('home',false);
