/* ============================================================
   SHARED UI HELPERS — loaded on every page via <script src="app.js">
   ------------------------------------------------------------
   Handles the stuff common to all pages: role-based nav visibility,
   the notifications dropdown, generic modal/tab wiring, and small
   formatting helpers. Each page's own inline <script> block calls
   into these, plus the data-layer functions in data.js.

   ROLE HANDLING: there's no backend and no browser storage in this
   prototype, so the "logged in" role is carried as a ?role= query
   param on every link instead of a real session. Swap this out for
   server-side auth (Java session / JWT) once the backend exists —
   see README.md.
============================================================ */

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const ROLE_LABELS = { admin: 'Admin', manager: 'Manager', staff: 'Staff' };

function getRole() {
  const params = new URLSearchParams(window.location.search);
  const role = params.get('role');
  return ['admin', 'manager', 'staff'].includes(role) ? role : 'manager';
}

function withRole(href) {
  const url = new URL(href, window.location.href);
  url.searchParams.set('role', getRole());
  return url.pathname.split('/').pop() + url.search;
}

function initShell() {
  const role = getRole();

  $$('.nav-item[data-roles]').forEach(item => {
    const allowed = item.dataset.roles.split(',');
    item.hidden = !allowed.includes(role);
  });

  // Propagate role on every internal link present at load time (static nav links).
  // Links injected later by a page's own render code include &role= themselves —
  // see any page that links to a *-detail.html page.
  $$('a[href$=".html"]').forEach(a => {
    const href = a.getAttribute('href');
    if (href && !href.startsWith('http')) a.setAttribute('href', withRole(href));
  });

  const current = window.location.pathname.split('/').pop() || 'dashboard.html';
  $$('.nav-item').forEach(item => {
    const target = (item.getAttribute('href') || '').split('?')[0];
    item.classList.toggle('active', target === current);
  });

  const pill = $('#rolePill');
  if (pill) pill.querySelector('.role-pill-name').textContent = ROLE_LABELS[role];

  setupSidebarToggle();
  setupNotifications();
}

function setupSidebarToggle() {
  const toggle = $('#sidebarToggle');
  const sidebar = $('#sidebar');
  const scrim = $('#sidebarScrim');
  toggle?.addEventListener('click', () => { sidebar.classList.toggle('open'); scrim.classList.toggle('open'); });
  scrim?.addEventListener('click', () => { sidebar.classList.remove('open'); scrim.classList.remove('open'); });
}

async function setupNotifications() {
  const btn = $('#notifBtn');
  const dropdown = $('#notifDropdown');
  const countEl = $('#notifCount');
  if (!btn || !dropdown) return;

  const products = await getProducts();
  const lowStock = products.filter(p => p.qty <= p.reorder);

  countEl.textContent = lowStock.length;
  countEl.style.display = lowStock.length ? 'flex' : 'none';

  dropdown.innerHTML = lowStock.length
    ? lowStock.map(p => `<div class="notif-item"><strong>${p.name}</strong> is at ${p.qty} units (reorder at ${p.reorder})</div>`).join('')
    : `<div class="notif-item">No alerts right now.</div>`;

  btn.addEventListener('click', () => {
    dropdown.classList.toggle('open');
    if (dropdown.classList.contains('open')) countEl.style.display = 'none'; // mark as read on open
  });
  document.addEventListener('click', e => {
    if (!btn.contains(e.target) && !dropdown.contains(e.target)) dropdown.classList.remove('open');
  });
}

function wireModal(overlayId, openBtnId, cancelBtnId) {
  const overlay = $('#' + overlayId);
  const openBtn = openBtnId ? $('#' + openBtnId) : null;
  const cancelBtn = $('#' + cancelBtnId);
  openBtn?.addEventListener('click', () => overlay.classList.add('open'));
  cancelBtn?.addEventListener('click', () => overlay.classList.remove('open'));
  overlay?.addEventListener('click', e => { if (e.target === overlay) overlay.classList.remove('open'); });
  return overlay;
}
function openModal(overlayId) { $('#' + overlayId)?.classList.add('open'); }
function closeModal(overlayId) { $('#' + overlayId)?.classList.remove('open'); }

function setupTabs() {
  $$('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      $$('.tab-btn').forEach(b => b.classList.toggle('active', b === btn));
      $$('.tab-panel').forEach(p => p.classList.toggle('active', p.id === 'tab-' + btn.dataset.tab));
    });
  });
}

function timeAgo(ts) {
  const diff = Math.max(0, Date.now() - ts);
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}
function money(n) { return '\u20B9' + n.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 }); }
function emptyState(msg) { return `<div class="empty-state">${msg}</div>`; }

function statusBadge(p) {
  if (p.qty === 0) return `<span class="badge danger">Out of stock</span>`;
  if (p.qty <= p.reorder) return `<span class="badge warning">Low stock</span>`;
  return `<span class="badge success">In stock</span>`;
}
function orderStatusBadge(status) {
  const map = { Pending: 'warning', Shipped: 'info', Delivered: 'success', Cancelled: 'danger' };
  return `<span class="badge ${map[status] || 'neutral'}">${status}</span>`;
}
function roleBadge(role) {
  return `<span class="badge role-badge ${role}">${ROLE_LABELS[role] || role}</span>`;
}

function showToast(msg, type = 'default') {
  let toast = $('#toast');
  if (!toast) { toast = document.createElement('div'); toast.id = 'toast'; toast.className = 'toast'; document.body.appendChild(toast); }
  toast.textContent = msg;
  toast.className = 'toast show' + (type === 'error' ? ' error' : '');
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => toast.classList.remove('show'), type === 'error' ? 5000 : 2400);
}

/** Standard failure handler for form submits / actions: shows the backend's
 *  actual error message instead of failing silently. Every await-based form
 *  handler in this project should catch into this. */
function showError(err) {
  console.error(err);
  showToast(err.message || 'Something went wrong \u2014 check the console for details.', 'error');
}

function exportCSV(filename, rows) {
  if (!rows.length) return;
  const headers = Object.keys(rows[0]);
  const csv = [headers.join(',')]
    .concat(rows.map(r => headers.map(h => `"${String(r[h]).replace(/"/g, '""')}"`).join(',')))
    .join('\n');
  const blob = new Blob([csv], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

document.addEventListener('DOMContentLoaded', initShell);
