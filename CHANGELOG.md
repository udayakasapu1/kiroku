# Kiroku changelog

Every version's APK is on the [Releases page](https://github.com/udayakasapu1/kiroku/releases).
Each one installs over the previous one and keeps your data (all builds share one signing key).

## 0.8.0 (2026-10-22): all your links on the web page

**Needs the Supabase setup script run again** (it upgrades the inbox; nothing is lost).

- The web page now shows your **whole library**, like the app: folders with link counts, search
  (titles, links, descriptions, notes, tags, contacts), pinned links first, preview images, and archived links behind "Show archived".
- **Edit any link on the web**, including ones saved on the phone. Changes reach the phone on its
  next sync, and only the fields you changed are updated there.
- Adding a link that's already in Kiroku shows "Already in Kiroku" with a button to edit that link instead.
- The page shows when your phone last synced. The phone now also syncs when you leave the app,
  besides when you open it and about every 15 minutes.
- New switch in the app (⋮ → Web inbox): **Show all my links on the web page**, on by default.
  Turning it off removes the phone's copy from Supabase; links saved on the web stay listed there.
  Attached files and notes stay on the phone.
- The save form now opens with **＋ Save a link** (or the bookmark button) instead of always being shown.
- Fixed: the same link saved twice on the web before the phone synced could stop syncing.

## 0.7.1 (2026-10-19): titles sync both ways with the web inbox

**Needs the Supabase setup script run again** (it upgrades the inbox; nothing is lost).

- The web page now shows the phone's current version of each link: titles the phone fetched,
  and titles, notes, tags, folders or contacts you changed on the phone.
- Editing a link on the web changes only the fields you touched, so changes made on the phone stay.
- Links you delete on the phone also leave the web list.
- Saving a link from the web that's already on the phone: a title typed on the web replaces a
  fetched title, but never one you typed on the phone. Tags and contacts are added, and a note is appended.
- Clearing a title fetches the page's own title again.
- Placeholder titles are recognised more precisely: "Just a moment…", "Please enable JavaScript"
  and similar are ignored, but real titles like "Error codes for BMS" are kept.
- Automated tests run on every build, 32 so far. They cover titles, the database upgrade, and
  web ↔ phone sync against the real inbox script.

## 0.7.0 (2026-10-19): contacts and editing on the web; titles you type are kept

**Needs the Supabase setup script run again.**

Web inbox:
- Contacts (name, email, phone, facility address) can be added on the web page, like in the app.
- After you click Save, the form stays open on that link ("Save changes" / "Done"), so you can add
  anything you missed. The bookmark popup no longer closes on its own.
- "Recently saved here" lists links saved from the browser for 60 days, each with **Edit**, even after
  they've reached the phone. Edits follow on the phone's next check. A link can be removed from the
  web only while it's still waiting for the phone.

App:
- **Title field when saving a link** ("Title (optional)"; leave empty to use the page's title).
- **A title you type is kept.** Fetching or retrying page details no longer replaces it.
- The title on a link's page now looks editable (underline and pencil icon), with a hint when the page gave no title.
- Placeholder page titles ("Just a moment…", "Enable JavaScript…") are ignored instead of saved.
- Database version 5 (remembers which titles were typed by hand); updating keeps all data.

## 0.6.0 (2026-10-19): web inbox, save links from a laptop

- New **Web inbox** (⋮ → Web inbox): save links from any browser at
  https://udayakasapu1.github.io/kiroku/. They wait in your own Supabase project until Kiroku collects
  them, when you open the app and about every 15 minutes.
- Web page: link, title, folder (your phone's folders are suggested), tags and note, plus a
  **Save to Kiroku** bookmark button that saves the page you're on.
- Connect a browser by sharing a setup link from the app. "Disconnect all browsers" makes a new key.
- Security: the inbox tables are locked. Everything goes through functions that need your private
  inbox key, and only a hash of that key is stored.
- One-time setup: run `supabase/kiroku_inbox.sql` in Supabase, then enter the project URL and anon key in the app.

## 0.5.0 (2026-10-19): contacts on links

- **Contacts** section on a link's page and in the save-a-link sheet, with several contacts per link:
  name, email, phone, facility address.
- Contact cards have email, call and show-on-map buttons. Tap a card to edit or delete it.
- Search also finds links by contact name, email, phone or address.
- Backups include contacts.
- Database version 4; updating keeps all data.

## 0.4.0 (2026-10-18): tags when saving, file attachments

- **Tags right in the save-a-link sheet** (Enter or comma adds one).
- **Attach files** from the Files app to links and notes. Tap a file to open it in a viewer app;
  removing one asks first. Files are copied into Kiroku, and deleting a link or note removes its files.
- Backups don't include attached files yet.
- Database version 3; updating keeps all data.

## 0.3.2 (2026-10-18): edit a link's address, no duplicate folders

- **Edit link** (⋮ on a link's page, also offered when details can't be fetched). Changing the address
  fetches the page details again, and an address already saved as another link is refused.
- Folder names are unique (case and spacing ignored): creating, renaming, first-launch setup and
  backup import reuse an existing folder with the same name.
- Existing duplicate folders are merged on start, and their links move into one folder.

## 0.3.1 (2026-10-18): lighter folders, final logo

- Folder tiles are smaller (three per row), in a lighter shade, with thin, faint line drawings.
- Logo: the single tanzaku with the 60-unit strip, on a white app-icon tile, with a white-strip version in dark mode.

## 0.3.0 (2026-10-18): the base version, first build on GitHub

An offline-first link and note keeper for Android 12+. No account and no server: everything lives on the phone.

- **Save links** by sharing from Chrome or any app (Share → Save to Kiroku), or by hand with the
  **Link** button. A link is saved immediately, even offline.
- **Page details in the background**: title, description, preview image and site name are fetched
  whenever the phone is online, with automatic retries. Pull down in a folder, or use ⋮ → Retry, to try again.
- **Folders you define**: first-launch setup (pick suggestions or type your own), then ⋮ → Manage folders
  to add, rename, change icon, re-order or delete. A deleted folder's links go to Unsorted.
- **Two spaces**: tap the logo to switch between **Links** (in folders) and **Notes**, a separate
  Keep-style board where you write, pin, search and archive. The app reopens in the last space you used.
- **On each link**: editable title, note, tags, folder, pin, share, open in browser, archive (with Undo) and delete.
- **Search** across all links or all notes; archived items are behind the archive icon.
- **Shared text with no link** can be saved as a note instead.
- **Backup**: export and import as JSON (folders, links, notes). Imports from the earlier Gurthu app work too.
- Sky and slate theme, light and dark. Built automatically on GitHub, with the APK published as a release.

## Before GitHub (zip builds)

- **Gurthu 0.1**: the first offline link saver, with six fixed categories (OEM, Tier 1, Aftermarket,
  Software, Tools, Org), a notes field and the sky/slate palette.
- **Kiroku 0.2**: renamed to Kiroku (new app id, installs next to Gurthu), links always saved with
  archiving in the ⋮ menu, notes split into their own space, and the tanzaku logo.
