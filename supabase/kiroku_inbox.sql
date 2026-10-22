-- =============================================================================
-- Kiroku web inbox  (version 4: all your links on the web)
-- Links added from a browser wait here until the Kiroku app on the phone picks
-- them up. The phone also keeps a copy of all its links here (unless that is
-- turned off in the app), so the web page can show and edit your whole library:
-- edits made on the web are copied to the phone on its next check, and the phone
-- sends back its own current version (fetched titles, edits made on the phone).
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
-- Version 4 columns: the rest of what the app shows for a link.
alter table public.kiroku_inbox add column if not exists origin      text not null default 'web';  -- 'web' or 'phone'
alter table public.kiroku_inbox add column if not exists description text;
alter table public.kiroku_inbox add column if not exists image_url   text;
alter table public.kiroku_inbox add column if not exists site_name   text;
alter table public.kiroku_inbox add column if not exists archived    boolean not null default false;
alter table public.kiroku_inbox add column if not exists pinned      boolean not null default false;
alter table public.kiroku_inbox add column if not exists saved_ms    bigint;   -- when it was saved on the phone
create index if not exists kiroku_inbox_inbox_idx on public.kiroku_inbox (inbox, created_at);
-- One row per phone link (older versions could leave two after saving a link twice).
delete from public.kiroku_inbox a using public.kiroku_inbox b
where a.inbox = b.inbox and a.link_id = b.link_id and a.link_id is not null and a.id <> b.id
  and (a.updated_at, a.id) < (b.updated_at, b.id);
create unique index if not exists kiroku_inbox_link_idx on public.kiroku_inbox (inbox, link_id) where link_id is not null;

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
drop function if exists public.kiroku_inbox_update(text, uuid, text, text, text, text[], text, jsonb);
drop function if exists public.kiroku_inbox_recent(text);
drop function if exists public.kiroku_inbox_collected(text);

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

-- Snapshot of a row in the shape both sides compare.
create or replace function public.kiroku_inbox_snapshot(i public.kiroku_inbox)
returns jsonb language sql immutable
as $$
  select jsonb_build_object('url', i.url, 'title', i.title, 'folder', i.folder,
                            'tags', to_jsonb(i.tags), 'note', i.note, 'contacts', i.contacts)
$$;

-- What the app shows besides the editable fields (sent by the phone, shown on the web).
create or replace function public.kiroku_inbox_extras(i public.kiroku_inbox)
returns jsonb language sql immutable
as $$
  select jsonb_build_object('description', i.description, 'image_url', i.image_url, 'site_name', i.site_name,
                            'archived', i.archived, 'pinned', i.pinned, 'saved_ms', i.saved_ms)
$$;

-- Change a link (web page). Works before and after the phone has it. p_changes holds
-- only the fields that were changed: {url, title, folder, tags, note, contacts}.
create or replace function public.kiroku_inbox_update(p_key text, p_id uuid, p_changes jsonb)
returns integer language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare v_version integer;
begin
  if p_changes ? 'url' and public.kiroku_inbox_text(p_changes->>'url', 4000) is null then
    raise exception 'url is required';
  end if;
  update public.kiroku_inbox set
    url      = case when p_changes ? 'url'      then public.kiroku_inbox_text(p_changes->>'url', 4000) else url end,
    title    = case when p_changes ? 'title'    then public.kiroku_inbox_text(p_changes->>'title', 500) else title end,
    folder   = case when p_changes ? 'folder'   then public.kiroku_inbox_text(p_changes->>'folder', 80) else folder end,
    tags     = case when p_changes ? 'tags'
                    then public.kiroku_inbox_clean_tags(array(select jsonb_array_elements_text(
                           case when jsonb_typeof(p_changes->'tags') = 'array' then p_changes->'tags' else '[]'::jsonb end)))
                    else tags end,
    note     = case when p_changes ? 'note'     then public.kiroku_inbox_text(p_changes->>'note', 4000) else note end,
    contacts = case when p_changes ? 'contacts' then public.kiroku_inbox_clean_contacts(p_changes->'contacts') else contacts end,
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

-- All links for the web page: ones saved here, and the phone's copy of its library.
create or replace function public.kiroku_inbox_recent(p_key text)
returns table (id uuid, url text, title text, folder text, tags text[], note text, contacts jsonb,
               on_phone boolean, pending boolean, origin text, description text, image_url text,
               site_name text, archived boolean, pinned boolean, saved_ms bigint,
               created_at timestamptz, updated_at timestamptz)
language sql security definer stable
set search_path = pg_catalog, public
as $$
  select i.id, i.url, i.title, i.folder, i.tags, i.note, i.contacts,
         i.link_id is not null or i.delivered_version is not null,
         i.delivered_version is distinct from i.version,
         i.origin, i.description, i.image_url, i.site_name, i.archived, i.pinned,
         coalesce(i.saved_ms, (extract(epoch from i.created_at) * 1000)::bigint),
         i.created_at, i.updated_at
  from public.kiroku_inbox i
  where i.inbox = public.kiroku_inbox_hash(p_key)
  order by coalesce(i.saved_ms, (extract(epoch from i.created_at) * 1000)::bigint) desc
  limit 10000;
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
declare
  v_inbox text := public.kiroku_inbox_hash(p_key);
  x jsonb;
  n integer := 0;
  c integer;
begin
  -- One item at a time: a link saved twice ends up as one link on the phone, so the
  -- older row for that link is removed before this one takes the link over.
  for x in select * from jsonb_array_elements(case when jsonb_typeof(p_items) = 'array' then p_items else '[]'::jsonb end)
  loop
    if x->>'link_id' is not null then
      delete from public.kiroku_inbox i
      where i.inbox = v_inbox and i.link_id = x->>'link_id' and i.id <> (x->>'id')::uuid;
    end if;
    update public.kiroku_inbox i set
      delivered_version = greatest(coalesce(i.delivered_version, 0), (x->>'version')::integer),
      link_id = coalesce(x->>'link_id', i.link_id),
      delivered = coalesce(x->'snapshot', i.delivered)
    where i.inbox = v_inbox
      and i.id = (x->>'id')::uuid
      and (x->>'version')::integer >= coalesce(i.delivered_version, 0);
    get diagnostics c = row_count;
    n := n + c;
  end loop;
  return n;
end $$;

-- Every row that belongs to a phone link, with what the web page shows for it (phone).
-- "pending" rows have a web edit the phone hasn't collected yet.
create or replace function public.kiroku_inbox_collected(p_key text)
returns table (id uuid, version integer, link_id text, pending boolean, delivered jsonb, extras jsonb)
language sql security definer stable
set search_path = pg_catalog, public
as $$
  select i.id, i.version, i.link_id, i.delivered_version is distinct from i.version,
         public.kiroku_inbox_snapshot(i), public.kiroku_inbox_extras(i)
  from public.kiroku_inbox i
  where i.inbox = public.kiroku_inbox_hash(p_key) and i.link_id is not null;
$$;

-- The phone's current version of links it has: [{id, version, url, title, folder, tags,
-- note, contacts}]. Skipped for a link with a web edit the phone hasn't collected yet.
create or replace function public.kiroku_inbox_refresh(p_key text, p_items jsonb)
returns integer language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare n integer;
begin
  update public.kiroku_inbox i set
    url      = coalesce(public.kiroku_inbox_text(x->>'url', 4000), i.url),
    title    = public.kiroku_inbox_text(x->>'title', 500),
    folder   = public.kiroku_inbox_text(x->>'folder', 80),
    tags     = public.kiroku_inbox_clean_tags(array(select jsonb_array_elements_text(
                 case when jsonb_typeof(x->'tags') = 'array' then x->'tags' else '[]'::jsonb end))),
    note     = public.kiroku_inbox_text(x->>'note', 4000),
    contacts = public.kiroku_inbox_clean_contacts(x->'contacts'),
    description = public.kiroku_inbox_text(x->>'description', 2000),
    image_url   = public.kiroku_inbox_text(x->>'image_url', 2000),
    site_name   = public.kiroku_inbox_text(x->>'site_name', 200),
    archived    = coalesce((x->>'archived')::boolean, false),
    pinned      = coalesce((x->>'pinned')::boolean, false),
    saved_ms    = coalesce((x->>'saved_ms')::bigint, i.saved_ms)
  from jsonb_array_elements(case when jsonb_typeof(p_items) = 'array' then p_items else '[]'::jsonb end) as x
  where i.inbox = public.kiroku_inbox_hash(p_key)
    and i.id = (x->>'id')::uuid
    and i.version = (x->>'version')::integer
    and i.delivered_version = i.version;
  update public.kiroku_inbox i set delivered = public.kiroku_inbox_snapshot(i)
  from jsonb_array_elements(case when jsonb_typeof(p_items) = 'array' then p_items else '[]'::jsonb end) as x
  where i.inbox = public.kiroku_inbox_hash(p_key)
    and i.id = (x->>'id')::uuid
    and i.version = (x->>'version')::integer
    and i.delivered_version = i.version;
  get diagnostics n = row_count;
  return n;
end $$;

-- Links deleted on the phone leave the web list too: [{id, version}].
create or replace function public.kiroku_inbox_forget(p_key text, p_items jsonb)
returns integer language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare n integer;
begin
  delete from public.kiroku_inbox i
  using jsonb_array_elements(case when jsonb_typeof(p_items) = 'array' then p_items else '[]'::jsonb end) as x
  where i.inbox = public.kiroku_inbox_hash(p_key)
    and i.id = (x->>'id')::uuid
    and i.version = (x->>'version')::integer
    and i.delivered_version = i.version;
  get diagnostics n = row_count;
  return n;
end $$;

-- The phone adds links the web page doesn't have yet (its library copy):
-- [{link_id, url, title, folder, tags, note, contacts, description, image_url, site_name,
--   archived, pinned, saved_ms}].
create or replace function public.kiroku_inbox_publish(p_key text, p_items jsonb)
returns integer language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare
  v_inbox text := public.kiroku_inbox_hash(p_key);
  n integer;
begin
  insert into public.kiroku_inbox (inbox, origin, link_id, version, delivered_version, url, title, folder, tags,
                                   note, contacts, description, image_url, site_name, archived, pinned, saved_ms)
  select v_inbox, 'phone', left(x->>'link_id', 80), 1, 1,
         public.kiroku_inbox_text(x->>'url', 4000), public.kiroku_inbox_text(x->>'title', 500),
         public.kiroku_inbox_text(x->>'folder', 80),
         public.kiroku_inbox_clean_tags(array(select jsonb_array_elements_text(
           case when jsonb_typeof(x->'tags') = 'array' then x->'tags' else '[]'::jsonb end))),
         public.kiroku_inbox_text(x->>'note', 4000), public.kiroku_inbox_clean_contacts(x->'contacts'),
         public.kiroku_inbox_text(x->>'description', 2000), public.kiroku_inbox_text(x->>'image_url', 2000),
         public.kiroku_inbox_text(x->>'site_name', 200), coalesce((x->>'archived')::boolean, false),
         coalesce((x->>'pinned')::boolean, false), (x->>'saved_ms')::bigint
  from jsonb_array_elements(case when jsonb_typeof(p_items) = 'array' then p_items else '[]'::jsonb end) as x
  where public.kiroku_inbox_text(x->>'link_id', 80) is not null
    and public.kiroku_inbox_text(x->>'url', 4000) is not null
  on conflict (inbox, link_id) where link_id is not null do nothing;
  get diagnostics n = row_count;
  update public.kiroku_inbox i set delivered = public.kiroku_inbox_snapshot(i)
  where i.inbox = v_inbox and i.origin = 'phone' and i.delivered is null;
  return n;
end $$;

-- "Show all my links on the web" turned off: remove the phone's library copy
-- (links saved on the web page stay listed).
create or replace function public.kiroku_inbox_unpublish(p_key text)
returns integer language plpgsql security definer
set search_path = pg_catalog, public
as $$
declare n integer;
begin
  delete from public.kiroku_inbox
  where inbox = public.kiroku_inbox_hash(p_key) and origin = 'phone'
    and delivered_version is not distinct from version;
  get diagnostics n = row_count;
  return n;
end $$;

-- Folders (in the phone's order) and when the phone last checked in (web page).
create or replace function public.kiroku_inbox_info(p_key text)
returns jsonb language sql security definer stable
set search_path = pg_catalog, public
as $$
  select jsonb_build_object(
    'folders', coalesce(m.folders, '{}'),
    'synced_at', m.updated_at)
  from (select public.kiroku_inbox_hash(p_key) as h) k
  left join public.kiroku_inbox_meta m on m.inbox = k.h;
$$;

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
revoke all on function public.kiroku_inbox_update(text, uuid, jsonb)                                 from public;
revoke all on function public.kiroku_inbox_collected(text)                                          from public;
revoke all on function public.kiroku_inbox_refresh(text, jsonb)                                     from public;
revoke all on function public.kiroku_inbox_forget(text, jsonb)                                      from public;
revoke all on function public.kiroku_inbox_remove(text, uuid)                                       from public;
revoke all on function public.kiroku_inbox_recent(text)                                             from public;
revoke all on function public.kiroku_inbox_list(text)                                               from public;
revoke all on function public.kiroku_inbox_done(text, jsonb)                                        from public;
revoke all on function public.kiroku_inbox_ack(text, uuid[])                                        from public;
revoke all on function public.kiroku_inbox_set_folders(text, text[])                                from public;
revoke all on function public.kiroku_inbox_publish(text, jsonb)                                      from public;
revoke all on function public.kiroku_inbox_unpublish(text)                                           from public;
revoke all on function public.kiroku_inbox_info(text)                                                from public;
revoke all on function public.kiroku_inbox_folders(text)                                            from public;

grant execute on function public.kiroku_inbox_add(text, text, text, text, text[], text, jsonb)          to anon, authenticated;
grant execute on function public.kiroku_inbox_update(text, uuid, jsonb)                                 to anon, authenticated;
grant execute on function public.kiroku_inbox_collected(text)                                          to anon, authenticated;
grant execute on function public.kiroku_inbox_refresh(text, jsonb)                                     to anon, authenticated;
grant execute on function public.kiroku_inbox_forget(text, jsonb)                                      to anon, authenticated;
grant execute on function public.kiroku_inbox_remove(text, uuid)                                       to anon, authenticated;
grant execute on function public.kiroku_inbox_recent(text)                                             to anon, authenticated;
grant execute on function public.kiroku_inbox_list(text)                                               to anon, authenticated;
grant execute on function public.kiroku_inbox_done(text, jsonb)                                        to anon, authenticated;
grant execute on function public.kiroku_inbox_ack(text, uuid[])                                        to anon, authenticated;
grant execute on function public.kiroku_inbox_set_folders(text, text[])                                to anon, authenticated;
grant execute on function public.kiroku_inbox_publish(text, jsonb)                                      to anon, authenticated;
grant execute on function public.kiroku_inbox_unpublish(text)                                           to anon, authenticated;
grant execute on function public.kiroku_inbox_info(text)                                                to anon, authenticated;
grant execute on function public.kiroku_inbox_folders(text)                                            to anon, authenticated;
