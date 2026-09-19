window.onerror = function(msg, url, lineNo, columnNo, error) { alert("JS Error: " + msg + " at " + lineNo + ":" + columnNo); };
/**
 * ============================================================================
 * MODULE: MAIN APPLICATION CONTROLLER
 * ============================================================================
 * Handles screen navigation, system bridge communication, haptics,
 * dashboard telemetry metrics, and the transactions ledger.
 */

// Core Application State
let activeTab = 'dashboard';
let currentLastSlipNo = 'GP-9485';
let cachedTransactions = [];
let ledgerFilter = 'ALL';

/**
 * Accessor for the native Android JavascriptInterface bridge.
 */
function bridge() {
  return window.AndroidBridge || null;
}

/**
 * Dispatches tactile haptic vibration via bridge or browser API.
 */
function vibrate(ms = 30) {
  if (bridge() && bridge().vibrate) {
    bridge().vibrate(ms);
  } else if (navigator.vibrate) {
    navigator.vibrate(ms);
  }
}

/**
 * Displays a short system toast message.
 */
function showToast(msg) {
  if (bridge() && bridge().showToast) {
    bridge().showToast(msg);
  } else {
    console.log("[Toast]", msg);
  }
}

/**
 * Switches the active viewport tab (Dashboard, POS, Dispatch, Thermal, Ledger).
 */
function switchTab(tabId) {
  vibrate(25);
  activeTab = tabId;

  document.querySelectorAll('.tab-page').forEach(page => {
    page.classList.add('hidden');
  });
  const targetPage = document.getElementById('tab-' + tabId);
  if (targetPage) targetPage.classList.remove('hidden');

  // Highlight bottom navigation bar buttons
  document.querySelectorAll('.nav-btn').forEach(btn => {
    const isSelected = btn.getAttribute('data-tab') === tabId;
    btn.className = isSelected
      ? 'nav-btn flex flex-col items-center justify-center gap-0.5 flex-1 text-primary'
      : 'nav-btn flex flex-col items-center justify-center gap-0.5 flex-1 text-on-surface-variant';
  });

  const subtitles = {
    'dashboard': 'Central Depot - Godown Overview',
    'pos': 'Quick Dispatch Terminal',
    'dispatch': 'Issue Gate Pass & Unload',
    'thermal': 'Thermal Print Spooler',
    'ledger': 'Stock Ledger & Audit Log'
  };
  const subEl = document.getElementById('topSubtitle');
  if (subEl && subtitles[tabId]) {
    subEl.textContent = subtitles[tabId];
  }

  // Toggle floating POS Cart bar visibility
  const cartBar = document.getElementById('posCartBottomBar');
  if (cartBar) {
    if (tabId === 'pos' && getTotalCartBags() > 0) {
      cartBar.classList.remove('hidden');
    } else {
      cartBar.classList.add('hidden');
    }
  }

  if (tabId === 'dispatch') {
    if (typeof renderDispatchItems === 'function') {
      if (typeof getTotalCartBags === 'function' && getTotalCartBags() > 0 && typeof dispatchCartItems !== 'undefined' && Object.keys(dispatchCartItems).length === 0) {
        loadCartIntoDispatch(posCart);
      } else {
        renderDispatchItems();
      }
    }
  } else if (tabId === 'thermal') {
    loadSlipPreview(currentLastSlipNo);
  } else if (tabId === 'ledger') {
    renderLedger();
  }
}

/**
 * Synchronizes telemetry, catalogs, parties, and history from local storage and Supabase.
 */
function refreshData() {
  vibrate(30);
  try {
    if (bridge()) {
      const telemetryStr = bridge().getDashboardTelemetry();
      const productsStr = bridge().getProducts();
      const partiesStr = bridge().getParties();
      const txStr = bridge().getTransactions();
      currentLastSlipNo = bridge().getLastSlipNo() || 'GP-9485';

      if (telemetryStr) updateDashboardTelemetry(JSON.parse(telemetryStr));
      if (productsStr) {
        cachedProducts = JSON.parse(productsStr);
        renderPosProducts();
        populateProductDropdowns();
        if (typeof renderDispatchItems === 'function') renderDispatchItems();
      }
      if (partiesStr) {
        cachedParties = JSON.parse(partiesStr);
        populatePartyDropdowns();
      }
      if (txStr) {
        cachedTransactions = JSON.parse(txStr);
        renderDashboardActivities();
        renderLedger();
      }
      refreshBluetoothPrinters();
      syncFromCloud();
    }
  } catch (e) {
    console.error("[App] Error refreshing data:", e);
  }
}

/**
 * Updates top metrics on Dashboard (Inventory stock, Inward, Dispatched, Slips).
 */
function updateDashboardTelemetry(t) {
  if (!t) return;
  const totalUnits = t.totalUnits !== undefined ? t.totalUnits : (t.totalStoredBags || 0);
  const activeProducts = t.activeProductsCount !== undefined ? t.activeProductsCount : (cachedProducts ? cachedProducts.filter(p => p.isActive !== false).length : 0);
  const inwardUnits = t.inwardDayUnits !== undefined ? t.inwardDayUnits : (t.inwardDayBags || 0);
  const dispatchedUnits = t.dispatchedUnits !== undefined ? t.dispatchedUnits : (t.dispatchedBags || 0);
  const slipsCount = t.pendingSlipsCount || 0;

  const totalEl = document.getElementById('metricTotalBags');
  const netMtEl = document.getElementById('metricNetMt');
  const inEl = document.getElementById('metricInwardBags');
  const outEl = document.getElementById('metricDispatchedBags');
  const pendEl = document.getElementById('metricPendingSlips');
  const slipEl = document.getElementById('dashLastSlipNo');

  if (totalEl) totalEl.textContent = Number(totalUnits).toLocaleString('en-IN');
  if (netMtEl) netMtEl.textContent = `${activeProducts} Products Active`;
  if (inEl) inEl.textContent = Number(inwardUnits).toLocaleString('en-IN');
  if (outEl) outEl.textContent = Number(dispatchedUnits).toLocaleString('en-IN');
  if (pendEl) pendEl.textContent = slipsCount;
  if (slipEl) slipEl.textContent = currentLastSlipNo;
}

/**
 * Renders recent activity stream on the Dashboard home page.
 */
function renderDashboardActivities() {
  const list = document.getElementById('dashActivityList');
  if (!list) return;
  list.innerHTML = '';
  const recent = cachedTransactions.slice(0, 4);

  if (recent.length === 0) {
    list.innerHTML = '<div class="text-[12px] text-on-surface-variant text-center py-2">No recent activity</div>';
    return;
  }

  recent.forEach(tx => {
    const isOut = tx.type === 'OUTWARD';
    const card = document.createElement('div');
    card.className = 'bg-surface-container-lowest rounded-xl p-2.5 flex items-center justify-between gap-2 shadow-sm border border-surface-container-high cursor-pointer active:scale-[0.99]';
    card.onclick = () => viewSlip(tx.slipNo);
    card.innerHTML = `
      <div class="flex items-center gap-2.5 min-w-0">
        <div class="w-9 h-9 rounded-lg ${isOut ? 'bg-primary-fixed text-primary' : 'bg-tertiary-fixed text-tertiary'} flex items-center justify-center shrink-0">
          <span class="material-symbols-outlined text-[20px]">${isOut ? 'local_shipping' : 'archive'}</span>
        </div>
        <div class="flex flex-col min-w-0">
          <div class="flex items-center gap-1.5">
            <span class="font-mono text-[12px] font-bold text-on-surface">${tx.slipNo}</span>
            <span class="text-[9px] font-bold uppercase px-1 rounded ${isOut ? 'bg-primary-fixed text-primary' : 'bg-tertiary-fixed text-tertiary'}">${isOut ? 'Dispatched' : 'Stock-In'}</span>
          </div>
          <span class="text-[11px] text-on-surface-variant truncate">${tx.totalBags} Units • ${tx.partyName}</span>
        </div>
      </div>
      <div class="text-right shrink-0">
        <span class="text-[10px] text-on-surface-variant">${tx.timeStr}</span>
        <span class="block text-[11px] font-bold text-primary">₹${Number(tx.totalAmount || 0).toLocaleString('en-IN')}</span>
      </div>
    `;
    list.appendChild(card);
  });
}

/**
 * Filters ledger records by transaction type (ALL, OUTWARD, INWARD).
 */
function filterLedgerType(type, btn) {
  vibrate(20);
  ledgerFilter = type;
  document.querySelectorAll('.ledger-pill').forEach(el => {
    el.className = 'ledger-pill px-3 py-1 rounded-full text-[11px] font-bold uppercase bg-surface-container-lowest text-on-surface-variant border border-surface-container-high shrink-0';
  });
  if (btn) {
    btn.className = 'ledger-pill px-3 py-1 rounded-full text-[11px] font-bold uppercase bg-primary-container text-on-primary shadow-sm shrink-0';
  }
  renderLedger();
}

/**
 * Renders complete ledger table with search filtering.
 */
function renderLedger() {
  const list = document.getElementById('ledgerTransactionsList');
  if (!list) return;
  list.innerHTML = '';

  const searchInput = document.getElementById('ledgerSearchInput');
  const search = (searchInput ? searchInput.value : '').toLowerCase().trim();

  const filtered = cachedTransactions.filter(t => {
    if (ledgerFilter !== 'ALL' && t.type !== ledgerFilter) return false;
    if (search) {
      const hay = `${t.slipNo} ${t.partyName} ${t.vehicleNo} ${t.destinationSite}`.toLowerCase();
      return hay.includes(search);
    }
    return true;
  });

  if (filtered.length === 0) {
    list.innerHTML = '<div class="text-[12px] text-on-surface-variant text-center py-6">No matching transactions found</div>';
    return;
  }

  filtered.forEach(t => {
    const isOut = t.type === 'OUTWARD';
    const card = document.createElement('div');
    card.className = 'bg-surface-container-lowest rounded-xl p-3 shadow-sm border border-surface-container-high space-y-1.5 cursor-pointer active:scale-[0.99]';
    card.onclick = () => viewSlip(t.slipNo);
    card.innerHTML = `
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="font-mono text-[13px] font-bold text-on-surface">${t.slipNo}</span>
          <span class="text-[9px] font-bold uppercase px-1.5 py-0.2 rounded ${isOut ? 'bg-primary-fixed text-primary' : 'bg-tertiary-fixed text-tertiary'}">${isOut ? 'Dispatched' : 'Stock In'}</span>
        </div>
        <span class="text-[11px] text-on-surface-variant">${t.dateStr} • ${t.timeStr}</span>
      </div>
      <div class="text-[12px] font-semibold text-on-surface truncate">
        ${t.partyName}
      </div>
      <div class="flex items-center justify-between text-[11px] text-on-surface-variant pt-1 border-t border-surface-container">
        <span>Vehicle: <b class="text-on-surface">${t.vehicleNo}</b></span>
        <span>Total: <b class="text-primary font-bold">${t.totalBags} Units</b> (₹${(Number(t.totalAmount) || 0).toLocaleString('en-IN')})</span>
      </div>
    `;
    list.appendChild(card);
  });
}

function captureCurrentScreen(tag = null) {
  vibrate(40);
  const name = tag || activeTab;
  if (bridge() && bridge().takeScreenshot) {
    bridge().takeScreenshot(name);
  } else {
    showToast("Bridge not ready");
  }
}

function openSystemBluetoothSettings() {
  vibrate(25);
  if (bridge()) {
    bridge().openBluetoothSettings();
  }
}

// Application Startup Lifecycle
window.addEventListener('DOMContentLoaded', () => {
  loadSavedPrintOptions();
  checkAuthOnStartup();
  
  // Auto-capture screenshot 2 seconds after boot
  setTimeout(() => {
    captureCurrentScreen('dashboard_auto');
  }, 2000);
});
