/**
 * ============================================================================
 * MODULE: BACKGROUND SYNC QUEUE (local-first writes)
 * ============================================================================
 * All mutations are written to the local Room database first and turned into
 * queued cloud tasks here. The queue drains in the background with retries;
 * failures surface through a health modal instead of blocking the UI.
 */

const SYNC_QUEUE_KEY = 'gw_sync_queue_v1';
const SYNC_MAX_ATTEMPTS = 6;

let syncQueue = loadSyncQueue();
let syncProcessing = false;
let syncListeners = [];
let syncTimer = null;

function loadSyncQueue() {
  try { return JSON.parse(localStorage.getItem(SYNC_QUEUE_KEY) || '[]'); }
  catch (e) { return []; }
}
function saveSyncQueue() {
  try { localStorage.setItem(SYNC_QUEUE_KEY, JSON.stringify(syncQueue)); } catch (e) {}
}

function syncBackoff(n) { return Math.min(30000, 1000 * Math.pow(2, n)); }

function isConflictMsg(msg) {
  const m = (msg || '').toLowerCase();
  return m.includes('duplicate') || m.includes('conflict') || m.includes('already exists') ||
         m.includes('409') || m.includes('unique') || m.includes('violates');
}

function syncHealth() {
  const pending = syncQueue.filter(t => t.status === 'pending' || t.status === 'running').length;
  const failed = syncQueue.filter(t => t.status === 'failed').length;
  const conflicts = syncQueue.filter(t => t.status === 'conflict').length;
  return { total: syncQueue.length, pending, failed, conflicts, items: syncQueue.slice() };
}

function notifySyncListeners() {
  const h = syncHealth();
  syncListeners.forEach(fn => { try { fn(h); } catch (e) {} });
}

function enqueueSync(task) {
  const entry = {
    id: task.id || (task.type + '_' + Date.now() + '_' + Math.random().toString(36).slice(2)),
    type: task.type,
    operation: task.operation || 'upsert',
    payload: task.payload || null,
    attempts: 0,
    maxAttempts: task.maxAttempts || SYNC_MAX_ATTEMPTS,
    status: 'pending',
    lastError: null,
    createdAt: Date.now(),
    nextRunAt: Date.now(),
  };
  syncQueue.push(entry);
  saveSyncQueue();
  notifySyncListeners();
  scheduleSync();
  return entry.id;
}

// ---- cloud writers ----
async function cloudWriteProduct(p) {
  if (!currentBusiness || !currentBusiness.id) throw new Error('No business');
  await sFetch('products', {
    method: 'POST',
    headers: { 'Prefer': 'resolution=merge-duplicates,return=minimal' },
    body: JSON.stringify({
      id: p.id, business_id: currentBusiness.id, name: p.name,
      grade: p.unit || p.grade || 'Units', unit: p.unit || 'Units',
      weight_per_bag_kg: p.weightPerBagKg, default_rate_per_bag: p.defaultRatePerBag,
      bay_location: p.bayLocation, current_stock_bags: p.currentStockBags,
      batch_no: p.batchNo, image_url: p.imageUrl, is_active: p.isActive !== false
    })
  });
}

async function cloudWriteParty(p) {
  if (!currentBusiness || !currentBusiness.id) throw new Error('No business');
  await sFetch('parties', {
    method: 'POST',
    headers: { 'Prefer': 'resolution=merge-duplicates,return=minimal' },
    body: JSON.stringify({
      id: p.id, business_id: currentBusiness.id, type: p.type || 'customer',
      name: p.name, phone: p.phone, gstin: p.gstin,
      default_destination: p.defaultDestination, account_no: p.accountNo,
      is_active: p.isActive !== false
    })
  });
}

async function cloudWriteTransaction(task) {
  if (!currentBusiness || !currentBusiness.id) throw new Error('No business');
  const p = task.payload;
  if (task.operation === 'delete') {
    await sFetch(`transactions?slip_no=eq.${encodeURIComponent(p.slipNo)}&business_id=eq.${currentBusiness.id}`, { method: 'DELETE' });
    return;
  }
  const method = (task.operation === 'update') ? 'PATCH' : 'POST';
  const path = (task.operation === 'update')
    ? `transactions?slip_no=eq.${encodeURIComponent(p.slip_no)}&business_id=eq.${currentBusiness.id}`
    : 'transactions';
  const headers = (task.operation === 'update') ? {} : { 'Prefer': 'resolution=merge-duplicates,return=minimal' };
  await sFetch(path, { method, headers, body: JSON.stringify(p) });
}

async function cloudPatchStock(p) {
  if (!currentBusiness || !currentBusiness.id) throw new Error('No business');
  await sFetch(`products?id=eq.${encodeURIComponent(p.id)}&business_id=eq.${currentBusiness.id}`, {
    method: 'PATCH', body: JSON.stringify({ current_stock_bags: p.currentStockBags })
  });
}

async function runSyncTask(task) {
  switch (task.type) {
    case 'product': await cloudWriteProduct(task.payload); break;
    case 'party': await cloudWriteParty(task.payload); break;
    case 'transaction': await cloudWriteTransaction(task); break;
    case 'stock': await cloudPatchStock(task.payload); break;
    default: throw new Error('Unknown sync task type: ' + task.type);
  }
}

async function processSyncQueue() {
  if (syncProcessing) return;
  if (!navigator.onLine) return;
  syncProcessing = true;
  try {
    const now = Date.now();
    const runnable = syncQueue.filter(t =>
      (t.status === 'pending' || t.status === 'failed') && t.nextRunAt <= now
    );
    for (const t of runnable) {
      t.status = 'running';
      t.attempts++;
      saveSyncQueue();
      notifySyncListeners();
      try {
        await runSyncTask(t);
        t.status = 'done';
        t.lastError = null;
      } catch (e) {
        const msg = (e && e.message) ? e.message : String(e);
        t.lastError = msg;
        if (isConflictMsg(msg)) {
          t.status = 'conflict';
        } else if (t.attempts >= t.maxAttempts) {
          t.status = 'failed';
        } else {
          t.status = 'pending';
          t.nextRunAt = Date.now() + syncBackoff(t.attempts);
        }
      }
      saveSyncQueue();
      notifySyncListeners();
    }
    // prune done entries older than 1 hour
    const cutoff = Date.now() - 3600000;
    syncQueue = syncQueue.filter(t => t.status !== 'done' || t.createdAt > cutoff);
    saveSyncQueue();
    notifySyncListeners();
  } finally {
    syncProcessing = false;
  }
}

function scheduleSync() {
  if (syncTimer) return;
  syncTimer = setTimeout(() => { syncTimer = null; processSyncQueue(); }, 250);
}

function retrySyncTask(id) {
  const t = syncQueue.find(x => x.id === id);
  if (t) {
    t.status = 'pending';
    t.attempts = 0;
    t.lastError = null;
    t.nextRunAt = Date.now();
    saveSyncQueue();
    notifySyncListeners();
    processSyncQueue();
  }
}

function clearSyncTask(id) {
  syncQueue = syncQueue.filter(t => t.id !== id);
  saveSyncQueue();
  notifySyncListeners();
}

function retryAllFailedSync() {
  syncQueue.forEach(t => {
    if (t.status === 'failed' || t.status === 'conflict') {
      t.status = 'pending';
      t.attempts = 0;
      t.lastError = null;
      t.nextRunAt = Date.now();
    }
  });
  saveSyncQueue();
  notifySyncListeners();
  processSyncQueue();
}

function clearDoneSync() {
  syncQueue = syncQueue.filter(t => t.status !== 'done');
  saveSyncQueue();
  notifySyncListeners();
}

window.addEventListener('online', () => processSyncQueue());
setInterval(() => {
  if (syncQueue.some(t => t.status === 'pending' || t.status === 'failed')) processSyncQueue();
}, 30000);

// ---- health modal ----
function openSyncHealthModal() {
  const modal = document.getElementById('syncHealthModal');
  if (!modal) return;
  renderSyncHealth();
  modal.classList.remove('hidden');
}

function closeSyncHealthModal() {
  const modal = document.getElementById('syncHealthModal');
  if (modal) modal.classList.add('hidden');
}

function renderSyncHealth() {
  const h = syncHealth();
  const summary = document.getElementById('syncHealthSummary');
  if (summary) {
    summary.textContent = `${h.pending} pending · ${h.failed} failed · ${h.conflicts} conflicts`;
  }
  const list = document.getElementById('syncHealthList');
  if (!list) return;
  if (h.items.length === 0) {
    list.innerHTML = '<div class="text-center py-6 text-on-surface-variant text-[13px]">All writes are synced. 🎉</div>';
    return;
  }
  list.innerHTML = h.items.map(t => {
    const statusColor = t.status === 'done' ? 'text-green-400' :
      t.status === 'conflict' ? 'text-amber-400' :
      t.status === 'failed' ? 'text-red-400' : 'text-on-surface-variant';
    const label = `${t.type.toUpperCase()} · ${t.operation}`;
    const err = t.lastError ? `<div class="text-[10px] text-red-300 mt-1 break-words">${escapeHtml(t.lastError)}</div>` : '';
    const actions = (t.status === 'failed' || t.status === 'conflict' || t.status === 'pending')
      ? `<div class="flex gap-1.5 mt-2">
           <button onclick="retrySyncTask('${t.id}')" class="px-2.5 py-1 rounded-lg bg-primary text-on-primary text-[10px] font-black uppercase tracking-wide">Retry</button>
           <button onclick="clearSyncTask('${t.id}')" class="px-2.5 py-1 rounded-lg bg-surface-container-high text-on-surface-variant text-[10px] font-black uppercase tracking-wide">Dismiss</button>
         </div>` : '';
    return `<div class="bg-surface-container rounded-xl p-3 border border-outline-variant">
      <div class="flex items-center justify-between gap-2">
        <span class="text-[12px] font-bold text-on-surface truncate">${escapeHtml(label)}</span>
        <span class="text-[10px] font-black uppercase tracking-wide ${statusColor}">${t.status}</span>
      </div>
      <div class="text-[10px] text-on-surface-variant mt-0.5">attempts: ${t.attempts}/${t.maxAttempts} · ${new Date(t.createdAt).toLocaleTimeString()}</div>
      ${err}${actions}
    </div>`;
  }).join('');
}

function updateSyncStatusIndicator() {
  const h = syncHealth();
  const el = document.getElementById('syncStatusBtn');
  const dot = document.getElementById('syncStatusDot');
  if (!el) return;
  let color = 'bg-green-500';
  let label = 'Synced';
  if (h.conflicts > 0) { color = 'bg-amber-500'; label = 'Conflicts'; }
  else if (h.failed > 0) { color = 'bg-red-500'; label = 'Failed'; }
  else if (h.pending > 0) { color = 'bg-blue-400'; label = 'Pending'; }
  if (dot) dot.className = `w-2 h-2 rounded-full ${color} ${h.pending > 0 ? 'animate-pulse' : ''}`;
  el.setAttribute('title', `Cloud sync: ${label} (${h.pending} pending, ${h.failed} failed, ${h.conflicts} conflicts)`);
  const lbl = document.getElementById('syncStatusLabel');
  if (lbl) lbl.textContent = label;
}

// register default listener
syncListeners.push(updateSyncStatusIndicator);
// initial render
setTimeout(updateSyncStatusIndicator, 0);

// auto-process on load
setTimeout(processSyncQueue, 500);
