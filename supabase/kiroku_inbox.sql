-- =============================================================================
-- Kiroku web inbox  (version 2: contacts, editing after saving)
-- Links added from a browser wait here until the Kiroku app on the phone picks
-- them up. Saved links stay listed (and editable) on the web page for 60 days;
-- edits made there are copied to the phone on its next check.
--
-- Run this in Supabase: Dashboard -> SQL Editor -> New query -> paste everything
-- -> Run. Safe to run again, and it upgrades an inbox made with version 1.
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
-- Version 2 columns.
alter table public.kiroku_inbox add column if not exists contacts          jsonb not null default '[]';
alter table public.kiroku_inbox add column if not exists version           integer not null default 1;
alter table public.kiroku_inbox add column if not exists delivered_version integer;   -- version the phone has
alter table public.kiroku_inbox add column if not exists delivered         jsonb;     -- what the phone got then
alter table public.kiroku_inbox add column if not exists link_id           text;      -- the link's id on the phone
alter table public.kiroku_inbox add column if not exists updated_at        timestamptz not null default now();
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

-- Version 1 functions whose signature changed.
drop function if exists public.kiroku_inbox_add(text, text, text, text, text[], text);
drop function if exists public.kiroku_inbox_list(text);

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

-- Helpers that tidy what the web page sends.
create or replace function public.kiroku_inbox_text(p text, p_max integer)
returns text language sql immutable
as $$ select nullif(left(btrim(coalesce(p, '')), p_max), '') $$;

create or replace function public.kiroku_inbox_clean_tags(p text[])
returns text[] language sql immutable
as $$
  select coalesce(array_agg(left(btrim(t), 60) order by n), '{}')
  from unnest(coalesce(p, '{}')) with ordinality as x(t, n)
  where btrim(t) <> '' and n <= 20
$$;

-- Contacts: [{id, name, email, phone, address}], at most 20, blank ones dropped.
create or replace function public.kiroku_inbox_clean_contacts(p jsonb)
returns jsonb language sql volatile
set search_path = pg_catalog, public
as $$
  select coalesce(jsonb_agg(c order by n), '[]'::jsonb)
  from (
    select n, jsonb_build_object(
      'id',      coalesce(public.kiroku_inbox_text(e->>'id', 80), 'w-' || gen_random_uuid()::text),
      'name',    coalesce(public.kiroku_inbox_text(e->>'name', 200), ''),
      'email',   coalesce(public.kiroku_inbox_text(e->>'email', 200), ''),
      'phone',   coalesce(public.kiroku_inbox_text(e->>'phone', 60), ''),
      'address', coalesce(public.kiroku_inbox_text(e->>'address', 1000), '')
    ) as c
    from jsonb_array_elements(case when jsonb_typeof(p) = 'array' then p else '[]'::jsonb end)
         with ordinality as x(e, n)
    where jsonb_typeof(e) = 'object' and n <= 20
  ) s
  where c->>'name' <> '' or c->>'email' <> '' or c->>'phone' <> '' or c->>'address' <> ''
$$;

-- Add a link (web page).
create or replace function public.kiroku_inbox_add(
  p_key      text,
  p_url      text,
  p_title    text   default null,
  p_folder   text   default null,
  p_tags     text[] default '{}',
  p_note     text   default null,
  p_contacts jsonb  default '[]'
) returns uuid language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare
  v_inbox text := public.kiroku_inbox_hash(p_key);
  v_id    uuid;
begin
  if public.kiroku_inbox_text(p_url, 4000) is null then
    raise exception 'url is required';
  end if;
  -- Saved links are listed on the web page for 60 days.
  delete from public.kiroku_inbox
  where inbox = v_inbox and delivered_version = version and created_at < now() - interval '60 days';
  if (select count(*) from public.kiroku_inbox
      where inbox = v_inbox and delivered_version is distinct from version) >= 500 then
    raise exception 'inbox is full: open Kiroku on your phone to collect waiting links';
  end if;
  insert into public.kiroku_inbox (inbox, url, title, folder, tags, note, contacts)
  values (v_inbox, public.kiroku_inbox_text(p_url, 4000), public.kiroku_inbox_text(p_title, 500),
          public.kiroku_inbox_text(p_folder, 80), public.kiroku_inbox_clean_tags(p_tags),
          public.kiroku_inbox_text(p_note, 4000), public.kiroku_inbox_clean_contacts(p_contacts))
  returning id into v_id;
  return v_id;
end $$;

-- Change a link (web page). Works before and after the phone has it.
create or replace function public.kiroku_inbox_update(
  p_key      text,
  p_id       uuid,
  p_url      text,
  p_title    text   default null,
  p_folder   text   default null,
  p_tags     text[] default '{}',
  p_note     text   default null,
  p_contacts jsonb  default '[]'
) returns integer language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare v_version integer;
begin
  if public.kiroku_inbox_text(p_url, 4000) is null then
    raise exception 'url is required';
  end if;
  update public.kiroku_inbox set
    url = public.kiroku_inbox_text(p_url, 4000),
    title = public.kiroku_inbox_text(p_title, 500),
    folder = public.kiroku_inbox_text(p_folder, 80),
    tags = public.kiroku_inbox_clean_tags(p_tags),
    note = public.kiroku_inbox_text(p_note, 4000),
    contacts = public.kiroku_inbox_clean_contacts(p_contacts),
    version = version + 1,
    updated_at = now()
  where inbox = public.kiroku_inbox_hash(p_key) and id = p_id
  returning version into v_version;
  if v_version is null then
    raise exception 'that link is no longer in the web inbox';
  end if;
  return v_version;
end $$;

-- Remove a link that hasn't reached the phone yet (web page).
create or replace function public.kiroku_inbox_remove(p_key text, p_id uuid)
returns void language plpgsql security definer
set search_path = pg_catalog, public
as $$
begin
  if exists (select 1 from public.kiroku_inbox
             where inbox = public.kiroku_inbox_hash(p_key) and id = p_id and link_id is not null) then
    raise exception 'this link is already on your phone; delete it in the Kiroku app';
  end if;
  delete from public.kiroku_inbox where inbox = public.kiroku_inbox_hash(p_key) and id = p_id;
end $$;

-- Recent links for the web page: waiting ones and ones already on the phone.
create or replace function public.kiroku_inbox_recent(p_key text)
returns table (id uuid, url text, title text, folder text, tags text[], note text, contacts jsonb,
               on_phone boolean, pending boolean, created_at timestamptz, updated_at timestamptz)
language sql security definer stable
set search_path = pg_catalog, public
as $$
  select i.id, i.url, i.title, i.folder, i.tags, i.note, i.contacts,
         i.link_id is not null or i.delivered_version is not null,
         i.delivered_version is distinct from i.version,
         i.created_at, i.updated_at
  from public.kiroku_inbox i
  where i.inbox = public.kiroku_inbox_hash(p_key)
  order by i.created_at desc
  limit 100;
$$;

-- New or changed links (phone). "delivered" is what the phone got last time, so it
-- only changes the fields that were edited on the web since.
create or replace function public.kiroku_inbox_list(p_key text)
returns table (id uuid, url text, title text, folder text, tags text[], note text, contacts jsonb,
               version integer, link_id text, delivered jsonb, created_at timestamptz)
language sql security definer stable
set search_path = pg_catalog, public
as $$
  select i.id, i.url, i.title, i.folder, i.tags, i.note, i.contacts,
         i.version, i.link_id, i.delivered, i.created_at
  from public.kiroku_inbox i
  where i.inbox = public.kiroku_inbox_hash(p_key)
    and i.delivered_version is distinct from i.version
  order by i.created_at;
$$;

-- The phone reports what it saved: [{id, version, link_id, snapshot}].
create or replace function public.kiroku_inbox_done(p_key text, p_items jsonb)
returns integer language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare n integer;
begin
  update public.kiroku_inbox i set
    delivered_version = greatest(coalesce(i.delivered_version, 0), (x->>'version')::integer),
    link_id = coalesce(x->>'link_id', i.link_id),
    delivered = coalesce(x->'snapshot', i.delivered)
  from jsonb_array_elements(case when jsonb_typeof(p_items) = 'array' then p_items else '[]'::jsonb end) as x
  where i.inbox = public.kiroku_inbox_hash(p_key)
    and i.id = (x->>'id')::uuid
    and (x->>'version')::integer >= coalesce(i.delivered_version, 0);
  get diagnostics n = row_count;
  return n;
end $$;

-- Version 1 phones (Kiroku 0.6.0): mark links as collected instead of deleting them.
create or replace function public.kiroku_inbox_ack(p_key text, p_ids uuid[])
returns integer language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare n integer;
begin
  update public.kiroku_inbox set delivered_version = version
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

revoke all on function public.kiroku_inbox_add(text, text, text, text, text[], text, jsonb)          from public;
revoke all on function public.kiroku_inbox_update(text, uuid, text, text, text, text[], text, jsonb) from public;
revoke all on function public.kiroku_inbox_remove(text, uuid)                                       from public;
revoke all on function public.kiroku_inbox_recent(text)                                             from public;
revoke all on function public.kiroku_inbox_list(text)                                               from public;
revoke all on function public.kiroku_inbox_done(text, jsonb)                                        from public;
revoke all on function public.kiroku_inbox_ack(text, uuid[])                                        from public;
revoke all on function public.kiroku_inbox_set_folders(text, text[])                                from public;
revoke all on function public.kiroku_inbox_folders(text)                                            from public;

grant execute on function public.kiroku_inbox_add(text, text, text, text, text[], text, jsonb)          to anon, authenticated;
grant execute on function public.kiroku_inbox_update(text, uuid, text, text, text, text[], text, jsonb) to anon, authenticated;
grant execute on function public.kiroku_inbox_remove(text, uuid)                                       to anon, authenticated;
grant execute on function public.kiroku_inbox_recent(text)                                             to anon, authenticated;
grant execute on function public.kiroku_inbox_list(text)                                               to anon, authenticated;
grant execute on function public.kiroku_inbox_done(text, jsonb)                                        to anon, authenticated;
grant execute on function public.kiroku_inbox_ack(text, uuid[])                                        to anon, authenticated;
grant execute on function public.kiroku_inbox_set_folders(text, text[])                                to anon, authenticated;
grant execute on function public.kiroku_inbox_folders(text)                                            to anon, authenticated;
