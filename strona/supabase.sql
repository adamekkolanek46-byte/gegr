-- Uruchom ten plik w Supabase SQL Editor dla projektu gry Zauroczeni.
create table if not exists public.game_saves (
  user_id uuid primary key references auth.users(id) on delete cascade,
  save_data jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

alter table public.game_saves enable row level security;

create policy "Gracze odczytują własny zapis"
on public.game_saves for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "Gracze tworzą własny zapis"
on public.game_saves for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "Gracze aktualizują własny zapis"
on public.game_saves for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

grant select, insert, update on table public.game_saves to authenticated;
