/* ============================================================
   DATA LAYER — shared across every page via <script src="data.js">
   ------------------------------------------------------------
   Mock data + placeholder functions. Pages only ever call the
   functions below, never the MOCK_* arrays directly, so wiring up
   the real Java backend later means editing this file only.
   See README.md for the suggested REST endpoints.

   NOTE ON STATE: because this is a static prototype with no
   backend and no browser storage in use, each page load re-reads
   these arrays fresh. Edits you make on one page won't be visible
   after you navigate to another page — that's expected here, and
   goes away once real API calls replace these functions.

   STALE DATA WARNING: this file's sample items (hardware-store
   products) were NOT updated when the live backend's seed data
   was switched to robotics components — this file is unused by
   any page and kept only as a structural reference, so it wasn't
   worth updating. See backend/src/.../Db.java's seed() method for
   the current, actually-used sample data.
============================================================ */

let MOCK_PRODUCTS = [
  { tagId: 'A3F19C82', name: 'Cordless Drill 18V', category: 'Power Tools', qty: 24, reorder: 10, maxStock: 40, unitPrice: 89.99 },
  { tagId: 'B7E20D14', name: 'Safety Goggles', category: 'Safety Gear', qty: 6, reorder: 15, maxStock: 60, unitPrice: 7.50 },
  { tagId: 'C912E4F0', name: 'Claw Hammer 16oz', category: 'Hand Tools', qty: 42, reorder: 12, maxStock: 60, unitPrice: 18.25 },
  { tagId: 'D45A1B77', name: 'Wood Screws 1in (Box of 100)', category: 'Fasteners', qty: 3, reorder: 20, maxStock: 80, unitPrice: 5.99 },
  { tagId: 'E8F30C29', name: 'Interior Latex Paint White 1gal', category: 'Paint & Supplies', qty: 18, reorder: 8, maxStock: 30, unitPrice: 32.00 },
  { tagId: 'F1C82A93', name: 'Extension Cord 25ft', category: 'Electrical', qty: 11, reorder: 10, maxStock: 30, unitPrice: 14.75 },
  { tagId: 'A9D74E11', name: 'Adjustable Wrench Set', category: 'Hand Tools', qty: 27, reorder: 10, maxStock: 40, unitPrice: 24.50 },
  { tagId: 'B22F8C05', name: 'Work Gloves (Pair)', category: 'Safety Gear', qty: 33, reorder: 15, maxStock: 60, unitPrice: 9.99 },
  { tagId: 'C6A19D48', name: 'Circular Saw Blade 7.25in', category: 'Power Tools', qty: 8, reorder: 10, maxStock: 30, unitPrice: 12.30 },
  { tagId: 'D317E902', name: 'Duct Tape Roll', category: 'Hardware', qty: 51, reorder: 20, maxStock: 80, unitPrice: 4.25 },
];

let MOCK_SCANS = [
  { tagId: 'A3F19C82', productName: 'Cordless Drill 18V', action: 'IN', qty: 5, time: Date.now() - 1000 * 60 * 4, reason: 'RFID scan' },
  { tagId: 'B7E20D14', productName: 'Safety Goggles', action: 'OUT', qty: 2, time: Date.now() - 1000 * 60 * 12, reason: 'RFID scan' },
  { tagId: 'D45A1B77', productName: 'Wood Screws 1in (Box of 100)', action: 'OUT', qty: 1, time: Date.now() - 1000 * 60 * 18, reason: 'RFID scan' },
  { tagId: 'E8F30C29', productName: 'Interior Latex Paint White 1gal', action: 'IN', qty: 6, time: Date.now() - 1000 * 60 * 35, reason: 'RFID scan' },
  { tagId: 'F1C82A93', productName: 'Extension Cord 25ft', action: 'OUT', qty: 1, time: Date.now() - 1000 * 60 * 52, reason: 'RFID scan' },
  { tagId: 'B22F8C05', productName: 'Work Gloves (Pair)', action: 'IN', qty: 10, time: Date.now() - 1000 * 60 * 70, reason: 'RFID scan' },
  { tagId: 'C6A19D48', productName: 'Circular Saw Blade 7.25in', action: 'OUT', qty: 2, time: Date.now() - 1000 * 60 * 130, reason: 'RFID scan' },
  { tagId: 'C912E4F0', productName: 'Claw Hammer 16oz', action: 'IN', qty: 12, time: Date.now() - 1000 * 60 * 60 * 26, reason: 'RFID scan' },
];

let MOCK_ORDERS = [
  { id: 'ORD-1042', supplier: 'Coastal Hardware Distributors', items: 'Wood Screws x50 boxes, Safety Goggles x40', status: 'Pending', date: '2026-07-14' },
  { id: 'ORD-1041', supplier: 'BrightSpark Electrical Supply', items: 'Extension Cord x25', status: 'Shipped', date: '2026-07-10' },
  { id: 'ORD-1040', supplier: 'ProTool Wholesale', items: 'Circular Saw Blade x30', status: 'Delivered', date: '2026-07-02' },
  { id: 'ORD-1039', supplier: 'ColorCraft Paint Co.', items: 'Interior Latex Paint White x12', status: 'Cancelled', date: '2026-06-28' },
];

let MOCK_SUPPLIERS = [
  { name: 'Coastal Hardware Distributors', contact: 'orders@coastalhw.com', leadTime: '5–7 days', category: 'Fasteners & Safety' },
  { name: 'BrightSpark Electrical Supply', contact: 'sales@brightspark.co', leadTime: '3–4 days', category: 'Electrical' },
  { name: 'ProTool Wholesale', contact: 'wholesale@protool.com', leadTime: '6–10 days', category: 'Power & Hand Tools' },
  { name: 'ColorCraft Paint Co.', contact: 'orders@colorcraft.com', leadTime: '2–3 days', category: 'Paint & Supplies' },
];

let MOCK_USERS = [
  { name: 'Priya Nair', email: 'priya@store.com', role: 'admin' },
  { name: 'Alex Chen', email: 'alex@store.com', role: 'manager' },
  { name: 'Sam Ortiz', email: 'sam@store.com', role: 'staff' },
  { name: 'Jordan Blake', email: 'jordan@store.com', role: 'staff' },
];

let MOCK_AUDIT = [
  { actor: 'Alex Chen', action: 'updated order ORD-1041 to Shipped', time: Date.now() - 1000 * 60 * 40 },
  { actor: 'Sam Ortiz', action: 'recorded RFID scan on Cordless Drill 18V (+5)', time: Date.now() - 1000 * 60 * 55 },
  { actor: 'Priya Nair', action: 'added user Jordan Blake (staff)', time: Date.now() - 1000 * 60 * 60 * 5 },
  { actor: 'Alex Chen', action: 'added supplier ColorCraft Paint Co.', time: Date.now() - 1000 * 60 * 60 * 22 },
  { actor: 'Sam Ortiz', action: 'manual adjustment on Wood Screws — cycle count correction (-2)', time: Date.now() - 1000 * 60 * 60 * 30 },
];

let MOCK_SETTINGS = {
  defaultReorderPercent: 25,
  lowStockNotifications: true,
  orderStatusNotifications: true,
};

function logAudit(actor, action) {
  MOCK_AUDIT.unshift({ actor, action, time: Date.now() });
}

async function getProducts() { return [...MOCK_PRODUCTS]; }
async function getProduct(tagId) { return MOCK_PRODUCTS.find(p => p.tagId === tagId) || null; }
async function addProduct(product) { MOCK_PRODUCTS.push(product); logAudit('You', `added product ${product.name}`); return product; }
async function updateProduct(tagId, updates) {
  const p = MOCK_PRODUCTS.find(x => x.tagId === tagId);
  if (p) { Object.assign(p, updates); logAudit('You', `edited product ${p.name}`); }
  return p;
}
async function deleteProduct(tagId) {
  const p = MOCK_PRODUCTS.find(x => x.tagId === tagId);
  MOCK_PRODUCTS = MOCK_PRODUCTS.filter(x => x.tagId !== tagId);
  if (p) logAudit('You', `deleted product ${p.name}`);
  return true;
}
async function adjustProductQty(tagId, delta, reason) {
  const product = MOCK_PRODUCTS.find(p => p.tagId === tagId);
  if (product) {
    product.qty = Math.max(0, product.qty + delta);
    if (reason) logAudit('You', `manual adjustment on ${product.name} — ${reason} (${delta > 0 ? '+' : ''}${delta})`);
  }
  return product;
}
async function getCategories() { return [...new Set(MOCK_PRODUCTS.map(p => p.category))].sort(); }
async function reassignTag(productTagId, newTagId) {
  const p = MOCK_PRODUCTS.find(x => x.tagId === productTagId);
  if (p) { p.tagId = newTagId.toUpperCase(); logAudit('You', `registered new tag ${p.tagId} for ${p.name}`); }
  return p;
}

async function getScans() { return [...MOCK_SCANS].sort((a, b) => b.time - a.time); }
async function getScansForProduct(tagId) { return (await getScans()).filter(s => s.tagId === tagId); }
async function recordScan(scan) { MOCK_SCANS.unshift(scan); return scan; }

async function getOrders() { return [...MOCK_ORDERS].reverse(); }
async function getOrder(id) { return MOCK_ORDERS.find(o => o.id === id) || null; }
async function addOrder(order) { MOCK_ORDERS.push(order); logAudit('You', `placed order ${order.id} with ${order.supplier}`); return order; }
async function updateOrderStatus(id, status) {
  const o = MOCK_ORDERS.find(x => x.id === id);
  if (o) { o.status = status; logAudit('You', `updated order ${id} to ${status}`); }
  return o;
}

async function getSuppliers() { return [...MOCK_SUPPLIERS]; }
async function getSupplier(name) { return MOCK_SUPPLIERS.find(s => s.name === name) || null; }
async function addSupplier(supplier) { MOCK_SUPPLIERS.push(supplier); logAudit('You', `added supplier ${supplier.name}`); return supplier; }
async function updateSupplier(name, updates) {
  const s = MOCK_SUPPLIERS.find(x => x.name === name);
  if (s) { Object.assign(s, updates); logAudit('You', `edited supplier ${name}`); }
  return s;
}
async function getOrdersForSupplier(name) { return (await getOrders()).filter(o => o.supplier === name); }

async function getUsers() { return [...MOCK_USERS]; }
async function addUser(user) { MOCK_USERS.push(user); logAudit('You', `added user ${user.name} (${user.role})`); return user; }

async function getAuditLog() { return [...MOCK_AUDIT].sort((a, b) => b.time - a.time); }
async function getSettings() { return { ...MOCK_SETTINGS }; }
async function updateSettings(updates) { Object.assign(MOCK_SETTINGS, updates); logAudit('You', 'updated system settings'); return MOCK_SETTINGS; }

async function getDashboardSummary() {
  const products = await getProducts();
  const scans = await getScans();
  const oneDayAgo = Date.now() - 1000 * 60 * 60 * 24;
  return {
    totalProducts: products.length,
    lowStock: products.filter(p => p.qty <= p.reorder).length,
    scansToday: scans.filter(s => s.time >= oneDayAgo).length,
    totalValue: products.reduce((sum, p) => sum + p.qty * p.unitPrice, 0),
  };
}

async function getStockByCategory() {
  const products = await getProducts();
  const map = {};
  products.forEach(p => { map[p.category] = (map[p.category] || 0) + p.qty; });
  return Object.entries(map).map(([category, qty]) => ({ category, qty }));
}

async function getMovementTrend() {
  const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  const pattern = [12, 18, 9, 22, 15, 7, 11];
  return days.map((d, i) => ({ day: d, units: pattern[i] }));
}
