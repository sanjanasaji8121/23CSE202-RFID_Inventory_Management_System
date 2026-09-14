# StockTrace — frontend

This is the multi-page UI for the RFID-based inventory management system.
**It's now wired up to a real Java + SQLite backend** (see `../backend`) —
`data.js` calls the API instead of using in-memory mock arrays. The old mock
version is kept as `data.mock.js` for reference / in case you want to go back
to pure UI work without the backend running.

**To actually use this, start the backend first** — see `../backend/README.md`.
Once it's running, open **http://localhost:8080** (not this folder directly;
the Java server serves these files itself, so opening `login.html` straight
from disk will fail on every API call with a CORS/fetch error).

## Pages

| Page | Purpose | Who sees it |
|---|---|---|
| `login.html` | Entry point — pick a role to "sign in" as | everyone |
| `dashboard.html` | Stats, recent scans, low-stock summary | staff, manager, admin |
| `inventory.html` | Product list — search, filter, add/edit/delete | staff, manager, admin |
| `product-detail.html` | Single product: scan history, manual stock adjustment, printable tag | staff, manager, admin |
| `scan-log.html` | RFID scan feed, filters, register a new tag, simulate a scan | staff, manager, admin |
| `orders.html` | Purchase orders — filter, create | manager, admin |
| `order-detail.html` | Single order: status timeline, advance/cancel | manager, admin |
| `suppliers.html` | Supplier list — add/edit | manager, admin |
| `supplier-detail.html` | Single supplier: info + order history | manager, admin |
| `reports.html` | Stock-by-category and movement charts, CSV export | manager, admin |
| `admin.html` | Users, system settings, audit log (tabbed) | admin |

## How the code is organized

- `styles.css` — every page's styling
- `data.js` — **now a thin fetch() wrapper around the backend API**, mapping
  the database's field names (`tag_id`, `created_at`, ...) to the camelCase
  shape the UI layer expects (`tagId`, `time`, ...). Every function keeps the
  exact same name/signature it had when it was mock data, so nothing else had
  to change when the backend was added.
- `data.mock.js` — the original in-memory version, unused now but kept for
  reference or offline UI-only work.
- `app.js` — shared UI logic used on every page: role-based nav visibility,
  notifications dropdown, generic modal/tab helpers, formatting helpers, CSV
  export.
- Each page has its own inline `<script>` block with only the logic specific
  to that page.

## Role handling

Still a `?role=` query param carried on every link rather than a real login —
that part hasn't changed. See the backend README for what a real Java-session
based login would look like; the frontend doesn't need to change much for it
(the login buttons already point at `dashboard.html`, they'd just stop needing
`?role=` once the backend sets a session on its own login endpoint).

## Live RFID scanning (Web Serial API)

`scan-log.html` can now talk directly to an Arduino over USB from the browser
— no Java-side serial listener needed. `serial.js` (loaded on that page only)
wraps the Web Serial API; the Arduino sketch is in `../arduino/rfid_reader.ino`.

- **Chrome/Edge only.** `navigator.serial` doesn't exist in Firefox/Safari —
  the "Connect reader" button disables itself automatically if unsupported.
- **Requires a click.** Browsers won't let a page silently connect to a
  serial device; the first click opens the OS device picker.
- **Scoped to that page.** Navigating away from `scan-log.html` tears down
  the connection (it's a real page load, not a SPA route change). Clicking
  "Connect reader" again is instant, no repeat permission prompt.
- **Mode toggle stands in for the RFID tag not knowing IN vs OUT.** A raw
  scan is just an ID — the "Stock In / Stock Out" dropdown next to Connect
  is what tells the app which direction to apply, one scan = one unit.
- Debounced client-side (2s per tag) so a tag sitting in range for a second
  doesn't fire a dozen duplicate scans.

Unknown tags (not yet in the product table) pop the "Register tag" modal
with the scanned ID pre-filled instead of silently failing.

## Current architecture, end to end

```
Arduino (rfid_reader.ino) --USB serial--> serial.js --> data.js --> Java API --> SQLite
                                              |
                                     scan-log.html UI
```

`recordScan()` in `data.js` is the single choke point every scan source goes
through — Simulate Scan button, live Web Serial reads, and (if you build it
later) a Java-side serial listener would all call the exact same function.

## State now actually persists

Unlike the old mock version, edits made on one page **do** show up when you
navigate to another — that's the whole point of the backend swap. Refreshing
resets nothing except what you'd expect a real page refresh to reset.

