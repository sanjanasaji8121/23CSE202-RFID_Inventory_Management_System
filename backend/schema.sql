-- StockTrace schema
-- SQLite for local/dev use (zero setup). The Java app embeds this same schema
-- programmatically (see Db.java) so it can execute triggers reliably — SQLite's
-- JDBC driver doesn't split multi-statement scripts safely when a trigger body
-- contains its own semicolons, so this file is the human-readable reference copy.
--
-- Porting to MySQL/PostgreSQL for production: swap AUTOINCREMENT for
-- AUTO_INCREMENT / SERIAL, and turn the two SQLite VIEWs that stand in for
-- "stored procedures" (reorder_suggestions, in particular) into real stored
-- procedures/functions if your course wants that distinction — see README.

CREATE TABLE IF NOT EXISTS suppliers (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  name      TEXT NOT NULL UNIQUE,
  contact   TEXT,
  category  TEXT,
  lead_time TEXT
);

CREATE TABLE IF NOT EXISTS products (
  tag_id         TEXT PRIMARY KEY,
  name           TEXT NOT NULL,
  category       TEXT,
  qty            INTEGER NOT NULL DEFAULT 0,
  reorder_point  INTEGER NOT NULL DEFAULT 0,
  max_stock      INTEGER NOT NULL DEFAULT 0,
  unit_price     REAL NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS orders (
  id           TEXT PRIMARY KEY,
  supplier_id  INTEGER NOT NULL REFERENCES suppliers(id),
  items        TEXT,
  status       TEXT NOT NULL DEFAULT 'Pending' CHECK (status IN ('Pending','Shipped','Delivered','Cancelled')),
  placed_date  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_movements (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  tag_id      TEXT NOT NULL REFERENCES products(tag_id),
  action      TEXT NOT NULL CHECK (action IN ('IN','OUT')),
  qty         INTEGER NOT NULL,
  reason      TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS users (
  id     INTEGER PRIMARY KEY AUTOINCREMENT,
  name   TEXT NOT NULL,
  email  TEXT NOT NULL UNIQUE,
  role   TEXT NOT NULL CHECK (role IN ('admin','manager','staff'))
);

CREATE TABLE IF NOT EXISTS audit_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  actor       TEXT NOT NULL,
  action      TEXT NOT NULL,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS settings (
  key    TEXT PRIMARY KEY,
  value  TEXT
);

-- Triggers: this is the "database complexity beyond simple read/write" part.
-- An RFID scan is just an INSERT into stock_movements — the trigger below is
-- what actually adjusts the live stock count, so the application code never
-- has to (and can't forget to, or get it wrong in two places).
CREATE TRIGGER IF NOT EXISTS trg_apply_stock_movement
AFTER INSERT ON stock_movements
BEGIN
  UPDATE products
  SET qty = qty + (CASE WHEN NEW.action = 'IN' THEN NEW.qty ELSE -NEW.qty END)
  WHERE tag_id = NEW.tag_id;

  INSERT INTO audit_log (actor, action)
  VALUES ('system', 'recorded ' || NEW.action || ' scan on ' || NEW.tag_id || ' (' || NEW.qty || ')');
END;

CREATE TRIGGER IF NOT EXISTS trg_audit_new_product
AFTER INSERT ON products
BEGIN
  INSERT INTO audit_log (actor, action) VALUES ('system', 'added product ' || NEW.name);
END;

CREATE TRIGGER IF NOT EXISTS trg_audit_order_status
AFTER UPDATE OF status ON orders
WHEN NEW.status <> OLD.status
BEGIN
  INSERT INTO audit_log (actor, action)
  VALUES ('system', 'order ' || NEW.id || ' status changed to ' || NEW.status);
END;

-- Views: joins + the reorder-suggestion logic that would be a stored
-- procedure/function on MySQL or PostgreSQL (SQLite has no stored procedures).
CREATE VIEW IF NOT EXISTS reorder_suggestions AS
SELECT tag_id, name, qty, reorder_point, max_stock, (max_stock - qty) AS suggested_order_qty
FROM products
WHERE qty <= reorder_point;

CREATE VIEW IF NOT EXISTS stock_by_category AS
SELECT category, SUM(qty) AS total_qty
FROM products
GROUP BY category;

CREATE VIEW IF NOT EXISTS order_details AS
SELECT o.id, o.status, o.items, o.placed_date,
       s.name AS supplier_name, s.contact AS supplier_contact, s.lead_time
FROM orders o
JOIN suppliers s ON o.supplier_id = s.id;
