-- =============================================================================
-- Kiroku web inbox
-- Links added from a browser wait here until the Kiroku app on the phone picks
-- them up. Run this once in Supabase: Dashboard -> SQL Editor -> New query ->
-- paste everything -> Run. Safe to run again (it only creates/replaces).
--
-- Security model: the tables are locked (row level security, no policies), so the
-- public API key alone can read or write nothing. Everything goes through the
-- functions below, which require the inbox key that only your phone and your
-- connected browsers know. Only a SHA-256 hash of that key is stored.
-- =============================================================================

create table if not exists public.kiroku_inbox (
  id          uuid primary key default gen_random_uuid(),
  inbox       text not null,                 -- sha256(inbox key), never the key itself
  url         text not null check (char_length(url) between 1 and 4000),
  title       text check (char_length(title) <= 500),
  folder      text check (char_length(folder) <= 80),
  tags        text[] not null default '{}',
  note        text check (char_length(note) <= 4000),
  created_at  timestamptz not null default now()
);
create index if not exists kiroku_inbox_inbox_idx on public.kiroku_inbox (inbox, created_at);

-- Folder names the phone shares so the web page can suggest them.
create table if not exists public.kiroku_inbox_meta (
  inbox       text primary key,
  folders     text[] not null default '{}',
  updated_at  timestamptz not null default now()
);

alter table public.kiroku_inbox      enable row level security;
alter table public.kiroku_inbox_meta enable row level security;
-- (No policies on purpose: direct table access is denied; use the functions.)

create or replace function public.kiroku_inbox_hash(p_key text)
returns text language plpgsql immutable
set search_path = pg_catalog, public
as $$
begin
  if p_key is null or char_length(p_key) < 32 then
    raise exception 'invalid inbox key';
  end if;
  return encode(sha256(convert_to(p_key, 'UTF8')), 'hex');
end $$;

-- Add a link (called by the web page).
create or replace function public.kiroku_inbox_add(
  p_key    text,
  p_url    text,
  p_title  text   default null,
  p_folder text   default null,
  p_tags   text[] default '{}',
  p_note   text   default null
) returns uuid language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare
  v_inbox text := public.kiroku_inbox_hash(p_key);
  v_id    uuid;
begin
  if p_url is null or btrim(p_url) = '' then
    raise exception 'url is required';
  end if;
  if (select count(*) from public.kiroku_inbox where inbox = v_inbox) >= 500 then
    raise exception 'inbox is full: open Kiroku on your phone to collect waiting links';
  end if;
  insert into public.kiroku_inbox (inbox, url, title, folder, tags, note)
  values (
    v_inbox,
    btrim(p_url),
    nullif(btrim(coalesce(p_title, '')), ''),
    nullif(btrim(coalesce(p_folder, '')), ''),
    coalesce((select array_agg(left(btrim(t), 60)) from unnest(coalesce(p_tags, '{}')) with ordinality as x(t, n)
              where btrim(t) <> '' and n <= 20), '{}'),
    nullif(btrim(coalesce(p_note, '')), '')
  )
  returning id into v_id;
  return v_id;
end $$;

-- Waiting links (called by the phone).
create or replace function public.kiroku_inbox_list(p_key text)
returns table (id uuid, url text, title text, folder text, tags text[], note text, created_at timestamptz)
language sql security definer
set search_path = pg_catalog, public
as $$
  select i.id, i.url, i.title, i.folder, i.tags, i.note, i.created_at
  from public.kiroku_inbox i
  where i.inbox = public.kiroku_inbox_hash(p_key)
  order by i.created_at;
$$;

-- Remove links the phone has saved (called by the phone).
create or replace function public.kiroku_inbox_ack(p_key text, p_ids uuid[])
returns integer language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare n integer;
begin
  delete from public.kiroku_inbox
  where inbox = public.kiroku_inbox_hash(p_key) and id = any(coalesce(p_ids, '{}'));
  get diagnostics n = row_count;
  return n;
end $$;

-- The phone shares its folder names; the web page reads them for suggestions.
create or replace function public.kiroku_inbox_set_folders(p_key text, p_folders text[])
returns void language sql security definer
set search_path = pg_catalog, public
as $$
  insert into public.kiroku_inbox_meta (inbox, folders, updated_at)
  values (public.kiroku_inbox_hash(p_key), coalesce(p_folders[1:200], '{}'), now())
  on conflict (inbox) do update set folders = excluded.folders, updated_at = now();
$$;

create or replace function public.kiroku_inbox_folders(p_key text)
returns text[] language sql security definer stable
set search_path = pg_catalog, public
as $$
  select coalesce((select folders from public.kiroku_inbox_meta
                   where inbox = public.kiroku_inbox_hash(p_key)), '{}');
$$;

revoke all on function public.kiroku_inbox_add(text, text, text, text, text[], text) from public;
revoke all on function public.kiroku_inbox_list(text)                              from public;
revoke all on function public.kiroku_inbox_ack(text, uuid[])                       from public;
revoke all on function public.kiroku_inbox_set_folders(text, text[])               from public;
revoke all on function public.kiroku_inbox_folders(text)                           from public;

grant execute on function public.kiroku_inbox_add(text, text, text, text, text[], text) to anon, authenticated;
grant execute on function public.kiroku_inbox_list(text)                              to anon, authenticated;
grant execute on function public.kiroku_inbox_ack(text, uuid[])                       to anon, authenticated;
grant execute on function public.kiroku_inbox_set_folders(text, text[])               to anon, authenticated;
grant execute on function public.kiroku_inbox_folders(text)                           to anon, authenticated;
