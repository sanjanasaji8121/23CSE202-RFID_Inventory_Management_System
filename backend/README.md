# StockTrace backend

A plain Java HTTP server (no Spring, no Maven — just the JDK) backed by SQLite.
It serves the REST API the frontend's `data.js` calls, AND serves the frontend
itself as static files, so there's one process, one port, and no CORS to deal
with.

## Requirements

## Sample data

First run seeds 10 robotics components across 5 categories, all priced in \u20B9:

| Tag ID | Product | Category | \u20B9 |
|---|---|---|---|
| 4A1F2B3C | L298N Motor Driver Module | Motor Drivers | 120 |
| 5B2E3C4D | Arduino Uno R3 | Microcontrollers | 650 |
| 6C3F4D5E | ESP32 Dev Board (38-pin) | Microcontrollers | 450 |
| 7D4A5E6F | 12V 2200mAh Li-ion Battery Pack | Batteries | 850 |
| 8E5B6F7A | TB6600 Stepper Motor Driver | Motor Drivers | 550 |
| 9F6C7A8B | HC-SR04 Ultrasonic Sensor | Sensors | 60 |
| A07D8B9C | MPU6050 Gyro/Accelerometer Module | Sensors | 150 |
| B18E9C0D | NEMA17 Stepper Motor | Motors | 700 |
| C29F0D1E | 18650 Li-ion Cell 2600mAh | Batteries | 180 |
| D3A01E2F | SG90 Micro Servo Motor | Motors | 120 |

Plus 4 suppliers (Robocraft Components, MakerHub Electronics, PowerCell
Batteries Pvt Ltd, ServoTech Motors) and 4 sample orders against them. All of
this lives in `Db.java`'s `seed()` method — edit it directly and delete
`stocktrace.db` to reseed with your own data.

`arduino/rfid_reader_simulator.ino` cycles through the first 5 of these tag
IDs every 4 seconds, no reader hardware required — useful for testing the
whole pipeline (trigger, audit log, dashboard) before your MFRC522 is wired up.

- JDK 17+ (built/tested on 21)
- Nothing else — the only dependency, the SQLite JDBC driver, is already
  vendored in `lib/sqlite-jdbc.jar`

## Run it

From this `backend/` folder:

```bash
java -cp "out:lib/sqlite-jdbc.jar" com.stocktrace.Server
```

(Windows: use `out;lib/sqlite-jdbc.jar` — semicolon instead of colon.)

Then open **http://localhost:8080** — that's `login.html`, served by this same
Java process. Pick a role to get to the dashboard.

First run creates `stocktrace.db` (SQLite file, in this folder) and seeds it
with the same sample data the mock frontend used to have. Delete that file to
reset back to the seed data at any time.

To use a different port or database file:

```bash
java -cp "out:lib/sqlite-jdbc.jar" -Ddb.path=mydata.db com.stocktrace.Server 9000
```

## If you change the Java source

The compiled classes in `out/` are already built. If you edit anything in
`src/com/stocktrace/`, recompile before running:

```bash
javac -d out -cp "lib/sqlite-jdbc.jar" src/com/stocktrace/*.java
```

## What's actually in here

- **`schema.sql`** — the reference copy of the schema: tables, foreign keys,
  three triggers, and three views. Read this first if you want to see the
  "database complexity" piece your abstract talks about, in one place.
  (`Db.java` embeds the same statements directly — see the comment at the top
  of that file for why it's not just parsed from this .sql file.)
- **`src/com/stocktrace/Json.java`** — a ~150-line JSON reader/writer. Written
  by hand instead of pulling in Jackson/Gson, since the payloads here are
  simple and it keeps the dependency list at just the JDBC driver.
- **`src/com/stocktrace/Db.java`** — every SQL statement the app runs. No ORM,
  on purpose, so the queries stay readable.
- **`src/com/stocktrace/Server.java`** — routes each `/api/...` request to a
  `Db` method and serves everything else as a static file from `../site`.

## Where the triggers and views actually show up

- `POST /api/scans` — and `POST /api/products/:tagId/adjust` for manual
  adjustments — just **insert one row** into `stock_movements`. The
  `trg_apply_stock_movement` trigger is what actually changes `products.qty`
  and writes an `audit_log` row. The Java code never touches `qty` directly.
- `reorder_suggestions` is a view standing in for what would be a stored
  procedure/function on MySQL or PostgreSQL (SQLite has no stored procedures).
  `GET /api/reports/reorder-suggestions` just selects from it.
- `order_details` is a view joining `orders` to `suppliers` — every order
  endpoint reads from this view instead of duplicating that join everywhere.

## Porting off SQLite later

If your course wants a "real" client-server database (MySQL/PostgreSQL)
rather than a local file:

1. Swap the JDBC URL in `Db.java`'s constructor for a MySQL/Postgres one, and
   swap `lib/sqlite-jdbc.jar` for the matching driver jar.
2. `AUTOINCREMENT` → `AUTO_INCREMENT` (MySQL) or `SERIAL` (Postgres).
3. Turn `reorder_suggestions` into an actual `CREATE PROCEDURE` /
   `CREATE FUNCTION` if your assignment specifically wants a stored procedure
   rather than a view.
4. Everything else (`Db.java`'s query methods, `Server.java`'s routes) should
   work unchanged — that's the point of keeping SQL centralized in `Db.java`.

## Connecting the real RFID/Arduino hardware

**Already done, browser-side** — see `../site/serial.js` and
`../arduino/rfid_reader.ino`. The Web Serial API lets `scan-log.html` talk to
the Arduino directly from the browser tab and call `POST /api/scans` itself;
this backend doesn't need to know or care where a scan came from.

If you'd rather have the Java side own the serial connection instead (e.g. to
support Firefox/Safari, or run scanning without a browser tab open at all),
the alternative is a small Java class using a library like `jSerialComm` that
listens on the port and calls `Db.recordMovement(tagId, "IN", qty, "RFID
scan")` directly — same trigger, same effect, just a different place the read
comes from. Not currently built since the Web Serial approach covers the
Chrome/Edge case with less code, but the swap is contained to "who calls
recordMovement," nothing else changes.
