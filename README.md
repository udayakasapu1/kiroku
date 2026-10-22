# Kiroku (記録, "record")

An offline-first Android app for saving web links and notes, sorted into folders.
Share a page from Chrome to **Save to Kiroku**, pick a folder, and it's stored on the phone straight away.
The page's title, description and preview image are fetched in the background whenever the phone is online.

- Kotlin, Jetpack Compose, Material 3 (sky/slate theme, light and dark)
- Minimum Android 12 (API 31)
- No account, no server: everything lives on the phone


What changed in each version: [CHANGELOG.md](CHANGELOG.md).

## Two spaces: Links and Notes

Tap the Kiroku logo (top left) to switch between **Links** and **Notes**. The app reopens in the space you used last.

- **Links** are sorted into folders. The home screen shows your folders; open one to see its links.
- **Notes** are a separate Keep-style board: write, pin, search, archive. Notes don't use folders.

## Folders are yours

On first launch Kiroku asks which folders you want (pick suggestions or type your own; you can also skip).
Later: ⋮ → **Manage folders** to add, rename, change icon, re-order or delete (a deleted folder's links
move to **Unsorted**). You can also create a folder straight from the save-a-link sheet or a link's page.

All folders share one look: light sky-blue in light mode, slate with sky accents in dark mode. Each folder's
icon also picks its background pattern (gears for manufacturing, circuit for code, and so on).
Icons and patterns: `ui/components/FolderStyle.kt`.

**Updating from an earlier Kiroku:** your six folders (OEM, Tier 1, Aftermarket, Software, Tools, Org) are
kept as ordinary, editable folders, and links stay where they were. Notes lose their folder, since notes no longer use folders.

## Run it on your phone

1. Open this `kiroku` folder in Android Studio (**File → Open**) and wait for the Gradle sync.
2. Plug in the phone (USB debugging on), pick it in the device dropdown, press **Run ▶**.

Kiroku has a new app id (`com.kiroku.app`), so it installs **next to** Gurthu rather than replacing it.
To bring your Gurthu links across: in Gurthu, ⋮ → Export backup; in Kiroku, ⋮ → Import backup;
then uninstall Gurthu.

## Using it

| Action | How |
| --- | --- |
| Save from any app | Share → **Save to Kiroku** → pick a folder → Save |
| Save a link by hand | **Link** button (inside a folder, that folder is pre-selected) |
| Write a note | Switch to **Notes** (tap the logo), tap **Note**, type, press back. Saves as you type; empty notes are discarded |
| Shared text with no link | The sheet offers **Save as a note instead** |
| Browse | Links home shows your folders; open one to see its links |
| Search | The search box searches all links (Links space) or all notes (Notes space) |
| Move a link to another folder | Open the link and pick a different folder chip |
| Archive | Open the item → ⋮ → **Archive** (Undo appears briefly). Archived items are behind the archive icon in each space |
| Retry details | Pull down inside a folder, or ⋮ → Retry fetching details |
| Titles | Type one in the save-a-link sheet, or tap the title on a link's page. A title you type is kept; fetching the page never replaces it. Clear it to get the page's own title back |
| Tags | Type them right in the save-a-link sheet (Enter or comma adds one), or later on the link's page |
| Contacts | **Add contact** on a link's page or while saving it: name, email, phone, facility address. Tap the icons to email, call or open the address in Maps. Search finds links by contact too |
| Attach files | **Attach file** on a link's page, in a note, or while saving a link. Opens your Files app; files are copied into Kiroku. Tap a file to open it |
| Your links on a laptop | ⋮ → **Web inbox** (one-time setup below). The web page shows all your links (folders, search) and lets you add and edit them |
| Backup | ⋮ → Export backup / Import backup (JSON; folders, links and notes; **attached files are not included**) |

## Web inbox (save links from a laptop)

The phone stays the only place your links live. The web page only drops new links into a small
inbox in your own Supabase project, and Kiroku picks them up when you open the app and about every
15 minutes after that.

One-time setup:

1. **Supabase:** open SQL Editor → New query, paste [`supabase/kiroku_inbox.sql`](supabase/kiroku_inbox.sql), Run.
   The tables are locked; the page and the app can only reach them through functions that need your inbox key.
2. **GitHub Pages:** repo Settings → Pages → Source: **GitHub Actions**. Then Actions → "Publish web inbox" → Run workflow.
   The page is at https://udayakasapu1.github.io/kiroku/
3. **Phone:** ⋮ → Web inbox → paste the Project URL and anon public key (Supabase → Project Settings → API) → Connect.
4. **Laptop:** tap **Share link** in the app, send it to the laptop, open it once. Drag the "Save to Kiroku" button to the bookmarks bar.

Links saved on the web page stay listed there for 60 days under "Recently saved here", with contacts, and
can still be edited. Edits reach the phone on its next check and change only the fields you edited on the web.
The phone sends its current version back on each check, so titles it fetched and edits made on the phone show
on the web page too. Links deleted on the phone leave the web list.
After you click Save, the form stays open on that link, so anything you missed can be added right away.

After updating Kiroku, run the setup script once more (it upgrades the inbox, nothing is lost).

The setup link holds your inbox key, so keep it private. "Disconnect all browsers" in the app makes a new key.

## How it works

```
Share / Add link ──► Room database (saved immediately, works offline)
                        │  fetch_state = PENDING
                        ▼
                 WorkManager job (runs when online, survives restarts, retries with backoff)
                        │  OkHttp downloads the page, Jsoup reads og:/twitter:/meta tags
                        ▼
                 Room row updated (title, description, image, site) → UI updates live
```

```
app/src/main/java/com/kiroku/app/
├── MainActivity.kt          Receives Share intents (cold start and while running)
├── KirokuApp.kt             App + simple dependency container
├── AppPrefs.kt              First-launch flag, last-used space
├── data/                    Room entities (links, notes, folders), DAOs, repositories, migration
├── fetch/                   MetadataFetcher (OkHttp + Jsoup) and FetchWorker
├── backup/                  JSON export/import
├── util/UrlUtils.kt         Finds links in shared text; normalises URLs for duplicates
└── ui/
    ├── home/                Links home: folder grid + search
    ├── folder/              One folder's links (or the links Archive)
    ├── folders/             Manage folders
    ├── onboarding/          First-launch folder setup
    ├── detail/              Link detail
    ├── notes/               Notes home (and notes Archive)
    ├── note/                Note editor
    ├── add/                 App-wide "save a link" sheet (also used by Share)
    └── components/          Folder designs, cards, shared widgets
```

**Database:** links are in `knowledge_items` with the same column names as the Engineering KB Supabase table
(`category` holds the id of a row in `categories`, or null for Unsorted).
Notes are in a separate `notes` table. Database version 4 (migrations keep all data). Contacts: `contacts` table (included in backups). Attachments: `attachments` table + copies in the app's private `files/attachments/`. Every item's status is `saved` or `archived`.

## Limits to know

- **No JavaScript rendering.** Pages that only build their content in the browser may return just the domain.
  The link is still saved; edit the title by hand.
- **Data is on this phone.** Use *Export backup* before resetting or changing phones.
- Release builds are signed with the debug key for easy sideloading.
