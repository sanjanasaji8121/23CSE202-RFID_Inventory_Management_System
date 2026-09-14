/* ============================================================
   DATA LAYER — now backed by the real Java/SQLite API
   ------------------------------------------------------------
   This replaces the old mock-array version (kept alongside as
   data.mock.js for reference). Every function keeps the exact
   same name and signature as before, so nothing in app.js or any
   page's inline <script> needed to change — this file is the
   only thing that was swapped.

   It talks to the backend in /backend (see /backend/README.md),
   which must be running for this to work: from /backend, run
     java -cp "out:lib/sqlite-jdbc.jar" com.stocktrace.Server
   and open http://localhost:8080 (the Java server serves this
   frontend itself, so there's no separate "start a web server"
   step and no CORS issue).
============================================================ */

async function api(method, path, body) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Request failed: ${method} ${path}`);
  return data;
}

function parseSqliteTime(s) {
  // SQLite's datetime('now') is UTC "YYYY-MM-DD HH:MM:SS" — reparse as ISO/UTC.
  return new Date(s.replace(' ', 'T') + 'Z').getTime();
}
function weekdayLabel(dateStr) {
  const d = new Date(dateStr + 'T00:00:00Z');
  return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getUTCDay()];
}

/* ---------- field-name adapters ----------
   The API returns SQL-shaped rows (tag_id, product_name, created_at, ...).
   The UI layer expects the old mock's camelCase shape (tagId, productName,
   time as epoch ms, ...). These map one to the other so nothing above this
   file needs to know the database's column names. */

function mapProduct(p) {
  return { tagId: p.tag_id, name: p.name, category: p.category, qty: p.qty, reorder: p.reorder_point, maxStock: p.max_stock, unitPrice: p.unit_price };
}
function mapScan(s) {
  return { tagId: s.tag_id, productName: s.product_name, action: s.action, qty: s.qty, time: parseSqliteTime(s.created_at) };
}
function mapOrder(o) {
  return { id: o.id, supplier: o.supplier_name, items: o.items, status: o.status, date: o.placed_date };
}
function mapSupplier(s) {
  return { name: s.name, contact: s.contact, category: s.category, leadTime: s.lead_time };
}
function mapAudit(a) {
  return { actor: a.actor, action: a.action, time: parseSqliteTime(a.created_at) };
}

/* ---------- products ---------- */

async function getProducts() { return (await api('GET', '/api/products')).map(mapProduct); }
async function getProduct(tagId) {
  const p = await api('GET', `/api/products/${encodeURIComponent(tagId)}`);
  return p ? mapProduct(p) : null;
}
async function addProduct(product) {
  await api('POST', '/api/products', product);
  return product;
}
async function updateProduct(tagId, updates) {
  await api('PATCH', `/api/products/${encodeURIComponent(tagId)}`, updates);
  return updates;
}
async function deleteProduct(tagId) {
  await api('DELETE', `/api/products/${encodeURIComponent(tagId)}`);
  return true;
}
async function adjustProductQty(tagId, delta, reason) {
  // Manual adjustments (product-detail.html) pass a reason and should hit the
  // backend directly. The "simulate scan" flow on dashboard/scan-log calls
  // this WITHOUT a reason immediately before recordScan() — in that case this
  // is a deliberate no-op, because POST /api/scans (called by recordScan)
  // already adjusts stock via the database trigger. Doing both would double
  // -apply the change.
  if (reason) await api('POST', `/api/products/${encodeURIComponent(tagId)}/adjust`, { delta, reason });
}
async function getCategories() { return api('GET', '/api/categories'); }
async function reassignTag(productTagId, newTagId) {
  await api('POST', `/api/products/${encodeURIComponent(productTagId)}/reassign-tag`, { newTagId });
}

/* ---------- scans ---------- */

async function getScans() { return (await api('GET', '/api/scans')).map(mapScan); }
async function getScansForProduct(tagId) { return (await api('GET', `/api/products/${encodeURIComponent(tagId)}/scans`)).map(mapScan); }
async function recordScan(scan) {
  await api('POST', '/api/scans', { tagId: scan.tagId, action: scan.action, qty: scan.qty, reason: 'RFID scan' });
  return scan;
}

/* ---------- orders ---------- */

async function getOrders() { return (await api('GET', '/api/orders')).map(mapOrder); }
async function getOrder(id) {
  const o = await api('GET', `/api/orders/${encodeURIComponent(id)}`);
  return o ? mapOrder(o) : null;
}
async function addOrder(order) {
  const res = await api('POST', '/api/orders', { supplier: order.supplier, items: order.items });
  return { id: res.id, supplier: order.supplier, items: order.items, status: 'Pending', date: new Date().toISOString().slice(0, 10) };
}
async function updateOrderStatus(id, status) { await api('PATCH', `/api/orders/${encodeURIComponent(id)}`, { status }); }

/* ---------- suppliers ---------- */

async function getSuppliers() { return (await api('GET', '/api/suppliers')).map(mapSupplier); }
async function getSupplier(name) {
  const s = await api('GET', `/api/suppliers/${encodeURIComponent(name)}`);
  return s ? mapSupplier(s) : null;
}
async function addSupplier(supplier) { await api('POST', '/api/suppliers', supplier); return supplier; }
async function updateSupplier(name, updates) { await api('PATCH', `/api/suppliers/${encodeURIComponent(name)}`, updates); return updates; }
async function getOrdersForSupplier(name) { return (await api('GET', `/api/suppliers/${encodeURIComponent(name)}/orders`)).map(mapOrder); }

/* ---------- users ---------- */

async function getUsers() { return api('GET', '/api/users'); }
async function addUser(user) { await api('POST', '/api/users', user); return user; }

/* ---------- audit / settings ---------- */

async function getAuditLog() { return (await api('GET', '/api/audit')).map(mapAudit); }
async function getSettings() { return api('GET', '/api/settings'); }
async function updateSettings(updates) { await api('PATCH', '/api/settings', updates); return updates; }

/* ---------- dashboard / reports ---------- */

async function getDashboardSummary() { return api('GET', '/api/dashboard/summary'); }
async function getStockByCategory() {
  return (await api('GET', '/api/reports/stock-by-category')).map(r => ({ category: r.category, qty: r.total_qty }));
}
async function getMovementTrend() {
  return (await api('GET', '/api/reports/movement-trend')).map(r => ({ day: weekdayLabel(r.day), units: r.units }));
}
