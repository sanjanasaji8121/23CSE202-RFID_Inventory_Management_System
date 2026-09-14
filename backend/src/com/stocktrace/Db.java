package com.stocktrace;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * All SQL lives here. Every method returns plain Maps/Lists so Json.write()
 * can serialize them directly — no ORM, no model classes, on purpose, so the
 * SQL itself stays easy to read for a course project.
 *
 * IMPORTANT — the schema (including triggers/views) is embedded below as an
 * array of individual statements rather than parsed from schema.sql, because
 * naively splitting a multi-statement SQL file on ";" breaks trigger bodies
 * (they contain their own internal semicolons between BEGIN/END). schema.sql
 * is kept alongside this file as the human-readable reference copy — the two
 * are meant to stay identical.
 */
public class Db {

    private final Connection conn;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Db(String path) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        initSchema();
        if (isEmpty("products")) seed();
    }

    // ---------- schema ----------

    private static final String[] SCHEMA = {
        "CREATE TABLE IF NOT EXISTS suppliers (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, contact TEXT, category TEXT, lead_time TEXT)",

        "CREATE TABLE IF NOT EXISTS products (" +
            "tag_id TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT, qty INTEGER NOT NULL DEFAULT 0, " +
            "reorder_point INTEGER NOT NULL DEFAULT 0, max_stock INTEGER NOT NULL DEFAULT 0, unit_price REAL NOT NULL DEFAULT 0)",

        "CREATE TABLE IF NOT EXISTS orders (" +
            "id TEXT PRIMARY KEY, supplier_id INTEGER NOT NULL REFERENCES suppliers(id), items TEXT, " +
            "status TEXT NOT NULL DEFAULT 'Pending' CHECK (status IN ('Pending','Shipped','Delivered','Cancelled')), placed_date TEXT NOT NULL)",

        "CREATE TABLE IF NOT EXISTS stock_movements (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, tag_id TEXT NOT NULL REFERENCES products(tag_id), " +
            "action TEXT NOT NULL CHECK (action IN ('IN','OUT')), qty INTEGER NOT NULL, reason TEXT, " +
            "created_at TEXT NOT NULL DEFAULT (datetime('now')))",

        "CREATE TABLE IF NOT EXISTS users (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, " +
            "role TEXT NOT NULL CHECK (role IN ('admin','manager','staff')))",

        "CREATE TABLE IF NOT EXISTS audit_log (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, actor TEXT NOT NULL, action TEXT NOT NULL, " +
            "created_at TEXT NOT NULL DEFAULT (datetime('now')))",

        "CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)",

        "CREATE TRIGGER IF NOT EXISTS trg_apply_stock_movement AFTER INSERT ON stock_movements BEGIN " +
            "UPDATE products SET qty = qty + (CASE WHEN NEW.action = 'IN' THEN NEW.qty ELSE -NEW.qty END) WHERE tag_id = NEW.tag_id; " +
            "INSERT INTO audit_log (actor, action) VALUES ('system', 'recorded ' || NEW.action || ' scan on ' || NEW.tag_id || ' (' || NEW.qty || ')'); " +
            "END",

        "CREATE TRIGGER IF NOT EXISTS trg_audit_new_product AFTER INSERT ON products BEGIN " +
            "INSERT INTO audit_log (actor, action) VALUES ('system', 'added product ' || NEW.name); " +
            "END",

        "CREATE TRIGGER IF NOT EXISTS trg_audit_order_status AFTER UPDATE OF status ON orders WHEN NEW.status <> OLD.status BEGIN " +
            "INSERT INTO audit_log (actor, action) VALUES ('system', 'order ' || NEW.id || ' status changed to ' || NEW.status); " +
            "END",

        "CREATE VIEW IF NOT EXISTS reorder_suggestions AS " +
            "SELECT tag_id, name, qty, reorder_point, max_stock, (max_stock - qty) AS suggested_order_qty " +
            "FROM products WHERE qty <= reorder_point",

        "CREATE VIEW IF NOT EXISTS stock_by_category AS " +
            "SELECT category, SUM(qty) AS total_qty FROM products GROUP BY category",

        "CREATE VIEW IF NOT EXISTS order_details AS " +
            "SELECT o.id, o.status, o.items, o.placed_date, s.name AS supplier_name, s.contact AS supplier_contact, s.lead_time " +
            "FROM orders o JOIN suppliers s ON o.supplier_id = s.id",
    };

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String sql : SCHEMA) st.execute(sql);
        }
    }

    private boolean isEmpty(String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM " + table)) {
            rs.next();
            return rs.getInt("c") == 0;
        }
    }

    // ---------- seed data ----------

    private void seed() throws SQLException {
        conn.setAutoCommit(false);
        try {
            insertSupplier("Robocraft Components", "orders@robocraft.in", "Motor Drivers & Sensors", "3\u20135 days");
            insertSupplier("MakerHub Electronics", "sales@makerhub.in", "Microcontrollers", "2\u20134 days");
            insertSupplier("PowerCell Batteries Pvt Ltd", "orders@powercell.in", "Batteries", "5\u20137 days");
            insertSupplier("ServoTech Motors", "info@servotech.in", "Motors", "4\u20136 days");

            // Baseline qty is pre-movement; the demo stock_movements below push each
            // product to its final displayed qty via the trigger, same as a real scan would.
            // Prices are in INR (\u20B9).
            insertProduct("4A1F2B3C", "L298N Motor Driver Module", "Motor Drivers", 19, 10, 40, 120.00);
            insertProduct("5B2E3C4D", "Arduino Uno R3", "Microcontrollers", 8, 15, 60, 650.00);
            insertProduct("6C3F4D5E", "ESP32 Dev Board (38-pin)", "Microcontrollers", 30, 12, 60, 450.00);
            insertProduct("7D4A5E6F", "12V 2200mAh Li-ion Battery Pack", "Batteries", 4, 20, 80, 850.00);
            insertProduct("8E5B6F7A", "TB6600 Stepper Motor Driver", "Motor Drivers", 12, 8, 30, 550.00);
            insertProduct("9F6C7A8B", "HC-SR04 Ultrasonic Sensor", "Sensors", 12, 10, 30, 60.00);
            insertProduct("A07D8B9C", "MPU6050 Gyro/Accelerometer Module", "Sensors", 27, 10, 40, 150.00);
            insertProduct("B18E9C0D", "NEMA17 Stepper Motor", "Motors", 13, 15, 60, 700.00);
            insertProduct("C29F0D1E", "18650 Li-ion Cell 2600mAh", "Batteries", 10, 10, 30, 180.00);
            insertProduct("D3A01E2F", "SG90 Micro Servo Motor", "Motors", 51, 20, 80, 120.00);

            seedMovement("4A1F2B3C", "IN", 5, "RFID scan", 4);
            seedMovement("5B2E3C4D", "OUT", 2, "RFID scan", 12);
            seedMovement("7D4A5E6F", "OUT", 1, "RFID scan", 18);
            seedMovement("8E5B6F7A", "IN", 6, "RFID scan", 35);
            seedMovement("9F6C7A8B", "OUT", 1, "RFID scan", 52);
            seedMovement("B18E9C0D", "IN", 10, "RFID scan", 70);
            seedMovement("C29F0D1E", "OUT", 2, "RFID scan", 130);
            seedMovement("6C3F4D5E", "IN", 12, "RFID scan", 26 * 60);

            insertOrder("ORD-1042", "Robocraft Components", "L298N Motor Driver x50, HC-SR04 Sensor x40", "Pending", "2026-07-14");
            insertOrder("ORD-1041", "MakerHub Electronics", "ESP32 Dev Board x25", "Shipped", "2026-07-10");
            insertOrder("ORD-1040", "ServoTech Motors", "NEMA17 Stepper Motor x30", "Delivered", "2026-07-02");
            insertOrder("ORD-1039", "PowerCell Batteries Pvt Ltd", "18650 Li-ion Cell x100", "Cancelled", "2026-06-28");

            insertUser("Priya Nair", "priya@store.com", "admin");
            insertUser("Alex Chen", "alex@store.com", "manager");
            insertUser("Sam Ortiz", "sam@store.com", "staff");
            insertUser("Jordan Blake", "jordan@store.com", "staff");

            putSetting("defaultReorderPercent", "25");
            putSetting("lowStockNotifications", "true");
            putSetting("orderStatusNotifications", "true");

            run("INSERT INTO audit_log (actor, action) VALUES (?, ?)", "Priya Nair", "added user Jordan Blake (staff)");
            run("INSERT INTO audit_log (actor, action) VALUES (?, ?)", "Alex Chen", "added supplier PowerCell Batteries Pvt Ltd");

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private void insertSupplier(String name, String contact, String category, String leadTime) throws SQLException {
        run("INSERT INTO suppliers (name, contact, category, lead_time) VALUES (?,?,?,?)", name, contact, category, leadTime);
    }

    private void insertProduct(String tagId, String name, String category, int qty, int reorder, int maxStock, double price) throws SQLException {
        run("INSERT INTO products (tag_id,name,category,qty,reorder_point,max_stock,unit_price) VALUES (?,?,?,?,?,?,?)",
            tagId, name, category, qty, reorder, maxStock, price);
    }

    private void seedMovement(String tagId, String action, int qty, String reason, int minutesAgo) throws SQLException {
        String ts = LocalDateTime.now().minusMinutes(minutesAgo).format(TS);
        run("INSERT INTO stock_movements (tag_id,action,qty,reason,created_at) VALUES (?,?,?,?,?)",
            tagId, action, qty, reason, ts);
    }

    private void insertOrder(String id, String supplierName, String items, String status, String date) throws SQLException {
        int supplierId = supplierIdByName(supplierName);
        run("INSERT INTO orders (id,supplier_id,items,status,placed_date) VALUES (?,?,?,?,?)", id, supplierId, items, status, date);
    }

    private void insertUser(String name, String email, String role) throws SQLException {
        run("INSERT INTO users (name,email,role) VALUES (?,?,?)", name, email, role);
    }

    private void putSetting(String key, String value) throws SQLException {
        run("INSERT OR REPLACE INTO settings (key,value) VALUES (?,?)", key, value);
    }

    private int supplierIdByName(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM suppliers WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
                throw new SQLException("No such supplier: " + name);
            }
        }
    }

    // ---------- generic helpers ----------

    private void run(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    private List<Map<String, Object>> query(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= cols; c++) {
                        Object v = rs.getObject(c);
                        row.put(meta.getColumnLabel(c), v);
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private Map<String, Object> queryOne(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---------- products ----------

    public List<Map<String, Object>> getProducts() throws SQLException {
        return query("SELECT * FROM products ORDER BY name");
    }

    public Map<String, Object> getProduct(String tagId) throws SQLException {
        return queryOne("SELECT * FROM products WHERE tag_id = ?", tagId);
    }

    public void addProduct(Map<String, Object> p) throws SQLException {
        run("INSERT INTO products (tag_id,name,category,qty,reorder_point,max_stock,unit_price) VALUES (?,?,?,?,?,?,?)",
            Json.str(p, "tagId"), Json.str(p, "name"), Json.str(p, "category"),
            Json.intVal(p, "qty"), Json.intVal(p, "reorder"), Json.intVal(p, "maxStock"), Json.dblVal(p, "unitPrice"));
    }

    public void updateProduct(String tagId, Map<String, Object> p) throws SQLException {
        run("UPDATE products SET name=?, category=?, qty=?, reorder_point=?, max_stock=?, unit_price=? WHERE tag_id=?",
            Json.str(p, "name"), Json.str(p, "category"), Json.intVal(p, "qty"),
            Json.intVal(p, "reorder"), Json.intVal(p, "maxStock"), Json.dblVal(p, "unitPrice"), tagId);
        run("INSERT INTO audit_log (actor, action) VALUES ('You', ?)", "edited product " + Json.str(p, "name"));
    }

    public void deleteProduct(String tagId) throws SQLException {
        Map<String, Object> p = getProduct(tagId);
        run("DELETE FROM products WHERE tag_id=?", tagId);
        if (p != null) run("INSERT INTO audit_log (actor, action) VALUES ('You', ?)", "deleted product " + p.get("name"));
    }

    /** Records an RFID scan (or a manual adjustment when reason is non-null) as one transaction. */
    public void recordMovement(String tagId, String action, int qty, String reason) throws SQLException {
        run("INSERT INTO stock_movements (tag_id, action, qty, reason) VALUES (?,?,?,?)", tagId, action, qty, reason);
    }

    public List<String> getCategories() throws SQLException {
        List<Map<String, Object>> rows = query("SELECT DISTINCT category FROM products ORDER BY category");
        List<String> cats = new ArrayList<>();
        for (Map<String, Object> r : rows) cats.add((String) r.get("category"));
        return cats;
    }

    public void reassignTag(String oldTagId, String newTagId) throws SQLException {
        String upper = newTagId.toUpperCase();
        run("UPDATE products SET tag_id=? WHERE tag_id=?", upper, oldTagId);
        Map<String, Object> p = getProduct(upper);
        run("INSERT INTO audit_log (actor, action) VALUES ('You', ?)",
            "registered new tag " + upper + " for " + (p == null ? oldTagId : p.get("name")));
    }

    // ---------- scans ----------

    public List<Map<String, Object>> getScans() throws SQLException {
        return query("SELECT sm.id, sm.tag_id, p.name AS product_name, sm.action, sm.qty, sm.reason, sm.created_at " +
            "FROM stock_movements sm JOIN products p ON sm.tag_id = p.tag_id " +
            "ORDER BY sm.created_at DESC, sm.id DESC LIMIT 200");
    }

    public List<Map<String, Object>> getScansForProduct(String tagId) throws SQLException {
        return query("SELECT * FROM stock_movements WHERE tag_id = ? ORDER BY created_at DESC, id DESC", tagId);
    }

    // ---------- orders ----------

    public List<Map<String, Object>> getOrders() throws SQLException {
        return query("SELECT * FROM order_details ORDER BY placed_date DESC");
    }

    public Map<String, Object> getOrder(String id) throws SQLException {
        return queryOne("SELECT * FROM order_details WHERE id = ?", id);
    }

    public List<Map<String, Object>> getOrdersForSupplier(String supplierName) throws SQLException {
        return query("SELECT * FROM order_details WHERE supplier_name = ? ORDER BY placed_date DESC", supplierName);
    }

    public String addOrder(Map<String, Object> o) throws SQLException {
        String supplierName = Json.str(o, "supplier");
        Integer supplierId = findSupplierId(supplierName);
        if (supplierId == null) {
            run("INSERT INTO suppliers (name) VALUES (?)", supplierName);
            supplierId = supplierIdByName(supplierName);
        }
        String id = "ORD-" + (1000 + new Random().nextInt(9000));
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        run("INSERT INTO orders (id, supplier_id, items, status, placed_date) VALUES (?,?,?,?,?)",
            id, supplierId, Json.str(o, "items"), "Pending", today);
        run("INSERT INTO audit_log (actor, action) VALUES ('You', ?)", "placed order " + id + " with " + supplierName);
        return id;
    }

    public void updateOrderStatus(String id, String status) throws SQLException {
        run("UPDATE orders SET status=? WHERE id=?", status, id);
    }

    private Integer findSupplierId(String name) throws SQLException {
        Map<String, Object> row = queryOne("SELECT id FROM suppliers WHERE name = ?", name);
        return row == null ? null : (Integer) row.get("id");
    }

    // ---------- suppliers ----------

    public List<Map<String, Object>> getSuppliers() throws SQLException {
        return query("SELECT * FROM suppliers ORDER BY name");
    }

    public Map<String, Object> getSupplier(String name) throws SQLException {
        return queryOne("SELECT * FROM suppliers WHERE name = ?", name);
    }

    public void addSupplier(Map<String, Object> s) throws SQLException {
        run("INSERT INTO suppliers (name, contact, category, lead_time) VALUES (?,?,?,?)",
            Json.str(s, "name"), Json.str(s, "contact"), Json.str(s, "category"), Json.str(s, "leadTime"));
        run("INSERT INTO audit_log (actor, action) VALUES ('You', ?)", "added supplier " + Json.str(s, "name"));
    }

    public void updateSupplier(String name, Map<String, Object> s) throws SQLException {
        run("UPDATE suppliers SET contact=?, category=?, lead_time=? WHERE name=?",
            Json.str(s, "contact"), Json.str(s, "category"), Json.str(s, "leadTime"), name);
        run("INSERT INTO audit_log (actor, action) VALUES ('You', ?)", "edited supplier " + name);
    }

    // ---------- users ----------

    public List<Map<String, Object>> getUsers() throws SQLException {
        return query("SELECT * FROM users ORDER BY name");
    }

    public void addUser(Map<String, Object> u) throws SQLException {
        run("INSERT INTO users (name, email, role) VALUES (?,?,?)", Json.str(u, "name"), Json.str(u, "email"), Json.str(u, "role"));
        run("INSERT INTO audit_log (actor, action) VALUES ('You', ?)", "added user " + Json.str(u, "name") + " (" + Json.str(u, "role") + ")");
    }

    // ---------- audit / settings ----------

    public List<Map<String, Object>> getAuditLog() throws SQLException {
        return query("SELECT * FROM audit_log ORDER BY created_at DESC, id DESC LIMIT 200");
    }

    public Map<String, Object> getSettings() throws SQLException {
        List<Map<String, Object>> rows = query("SELECT key, value FROM settings");
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String key = (String) r.get("key");
            String value = (String) r.get("value");
            if ("true".equals(value) || "false".equals(value)) out.put(key, Boolean.parseBoolean(value));
            else {
                try { out.put(key, Integer.parseInt(value)); }
                catch (NumberFormatException e) { out.put(key, value); }
            }
        }
        return out;
    }

    public void updateSettings(Map<String, Object> updates) throws SQLException {
        for (Map.Entry<String, Object> e : updates.entrySet()) {
            putSetting(e.getKey(), String.valueOf(e.getValue()));
        }
        run("INSERT INTO audit_log (actor, action) VALUES ('You', 'updated system settings')");
    }

    // ---------- dashboard / reports ----------

    public Map<String, Object> getDashboardSummary() throws SQLException {
        Map<String, Object> totalRow = queryOne("SELECT COUNT(*) AS c FROM products");
        Map<String, Object> lowRow = queryOne("SELECT COUNT(*) AS c FROM reorder_suggestions");
        Map<String, Object> scansRow = queryOne("SELECT COUNT(*) AS c FROM stock_movements WHERE created_at >= datetime('now','-1 day')");
        Map<String, Object> valueRow = queryOne("SELECT COALESCE(SUM(qty * unit_price),0) AS v FROM products");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalProducts", totalRow.get("c"));
        out.put("lowStock", lowRow.get("c"));
        out.put("scansToday", scansRow.get("c"));
        out.put("totalValue", ((Number) valueRow.get("v")).doubleValue());
        return out;
    }

    public List<Map<String, Object>> getReorderSuggestions() throws SQLException {
        return query("SELECT * FROM reorder_suggestions ORDER BY qty ASC");
    }

    public List<Map<String, Object>> getStockByCategory() throws SQLException {
        return query("SELECT * FROM stock_by_category ORDER BY category");
    }

    public List<Map<String, Object>> getMovementTrend() throws SQLException {
        return query(
            "SELECT date(created_at) AS day, " +
            "SUM(CASE WHEN action='IN' THEN qty ELSE qty END) AS units " +
            "FROM stock_movements WHERE created_at >= datetime('now','-7 day') " +
            "GROUP BY date(created_at) ORDER BY day");
    }
}
