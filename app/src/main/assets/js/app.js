window.onerror = function(msg, url, lineNo, columnNo, error) { console.error("[App] JS Error:", msg, "at", lineNo + ":" + columnNo, error); };
/**
 * ============================================================================
 * MODULE: MAIN APPLICATION CONTROLLER
 * ============================================================================
 * Handles screen navigation, system bridge communication, haptics,
 * dashboard telemetry metrics, and the transactions ledger.
 */

// Core Application State
/**
 * Toggles between light and dark mode (Apple-inspired).
 */
function toggleTheme() {
  var root = document.documentElement;
  var body = document.body;
  if (body) body.classList.add('theme-transition');
  var isDark = root.classList.toggle('dark');
  try { localStorage.setItem('theme', isDark ? 'dark' : 'light'); } catch (e) {}
  var icon = document.getElementById('themeToggleIcon');
  if (icon) icon.textContent = isDark ? 'light_mode' : 'dark_mode';
  clearTimeout(toggleTheme._t);
  toggleTheme._t = setTimeout(function () {
    if (body) body.classList.remove('theme-transition');
  }, 320);
}

(function() {
  var isDark = document.documentElement.classList.contains('dark');
  var icon = document.getElementById('themeToggleIcon');
  if (icon) icon.textContent = isDark ? 'light_mode' : 'dark_mode';
})();
let newView = 'quick'; // 'quick' | 'dispatch' — sub-view inside the New tab
let currentLastSlipNo = '';
let cachedTransactions = [];
let ledgerFilter = 'ALL';
let ledgerRenderLimit = 300;

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
 * Switches the active viewport tab (Home, New, Stock, More).
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
    btn.classList.toggle('tab-active', isSelected);
  });

  const subtitles = {
    'dashboard': 'Central Depot - Godown Overview',
    'new': 'New Slip - Quick Dispatch or Full Gate Pass',
    'stock': 'Stock Ledger & Audit Log',
    'more': 'Print Spooler & Settings'
  };
  const subEl = document.getElementById('topSubtitle');
  if (subEl && subtitles[tabId]) {
    subEl.textContent = subtitles[tabId];
  }

  // Keep the New tab's segmented view in sync with the visible section.
  if (tabId === 'new') setNewView(newView, true);

  // Toggle floating POS Cart bar visibility
  const cartBar = document.getElementById('posCartBottomBar');
  if (cartBar) {
    if (tabId === 'new' && newView === 'quick' && getTotalCartBags() > 0) {
      cartBar.classList.remove('hidden');
    } else {
      cartBar.classList.add('hidden');
    }
  }

  if (tabId === 'new' && newView === 'dispatch') {
    if (typeof renderDispatchItems === 'function') {
      if (typeof getTotalCartBags === 'function' && getTotalCartBags() > 0 && typeof dispatchCartItems !== 'undefined' && Object.keys(dispatchCartItems).length === 0) {
        loadCartIntoDispatch(posCart);
      } else {
        renderDispatchItems();
      }
    }
  } else if (tabId === 'more') {
    loadSlipPreview(currentLastSlipNo);
  } else if (tabId === 'stock') {
    renderLedger();
  }
}

/**
 * Switches between the Quick Dispatch grid and the Full Slip form inside the
 * New tab. `silent` skips the haptic when called from switchTab.
 */
function setNewView(view, silent) {
  newView = view === 'dispatch' ? 'dispatch' : 'quick';
  if (!silent) vibrate(20);

  const quick = document.getElementById('tab-pos');
  const full = document.getElementById('tab-dispatch');
  if (quick) quick.classList.toggle('hidden', newView !== 'quick');
  if (full) full.classList.toggle('hidden', newView !== 'dispatch');

  const quickBtn = document.getElementById('newViewQuickBtn');
  const fullBtn = document.getElementById('newViewFullBtn');
  if (quickBtn) quickBtn.classList.toggle('seg-active', newView === 'quick');
  if (fullBtn) fullBtn.classList.toggle('seg-active', newView === 'dispatch');

  const cartBar = document.getElementById('posCartBottomBar');
  if (cartBar) {
    if (newView === 'quick' && getTotalCartBags() > 0) {
      cartBar.classList.remove('hidden');
    } else {
      cartBar.classList.add('hidden');
    }
  }

  if (newView === 'dispatch' && typeof renderDispatchItems === 'function') {
    renderDispatchItems();
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
      currentLastSlipNo = bridge().getLastSlipNo() || '';

      if (telemetryStr) updateDashboardTelemetry(JSON.parse(telemetryStr));
      if (productsStr) {
        cachedProducts = JSON.parse(productsStr);
        updateGodownList();
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
        updateGodownList();
        renderDashboardActivities();
        renderLedger();
      }
      refreshBluetoothPrinters();
      if (typeof initRealtimeSync === 'function') initRealtimeSync();
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

  const stockUnitsEl = document.getElementById('stockTotalUnits');
  const stockProdEl = document.getElementById('stockProductCount');
  const stockSlipEl = document.getElementById('stockSlipCount');
  if (stockUnitsEl) stockUnitsEl.textContent = Number(totalUnits).toLocaleString('en-IN');
  if (stockProdEl) stockProdEl.textContent = activeProducts;
  if (stockSlipEl) stockSlipEl.textContent = slipsCount;

  let totalMt = t.totalMt !== undefined ? Number(t.totalMt) : NaN;
  if (!isFinite(totalMt)) {
    const godown = (typeof activeGodown !== 'undefined' && activeGodown) ? activeGodown : 'ALL';
    const products = (cachedProducts || []).filter(p => (godown === 'ALL' || p.bayLocation === godown) && p.isActive);
    totalMt = products.reduce((sum, p) => sum + ((Number(p.currentStockBags) || 0) * (Number(p.weightPerBagKg) || 0)) / 1000, 0);
  }

  if (totalEl) totalEl.textContent = Number(totalUnits).toLocaleString('en-IN');
  if (netMtEl) {
    if (totalMt > 0) {
      netMtEl.textContent = `${totalMt.toLocaleString('en-IN', { maximumFractionDigits: 2 })} MT · ${activeProducts} Products`;
    } else {
      netMtEl.textContent = `${activeProducts} Products Active`;
    }
  }
  if (inEl) inEl.textContent = Number(inwardUnits).toLocaleString('en-IN');
  if (outEl) outEl.textContent = Number(dispatchedUnits).toLocaleString('en-IN');
  if (pendEl) pendEl.textContent = slipsCount;
  if (slipEl) slipEl.textContent = currentLastSlipNo;
}

/**
 * Short date label (dd/mm) for transaction rows.
 */
function shortDateLabel(tx) {
  const d = (tx && tx.dateStr ? String(tx.dateStr) : '').trim();
  if (!d) return '';
  const parts = d.split(/[\/\-\.]/);
  if (parts.length === 3) {
    return parts[0].length === 4 ? `${parts[2]}/${parts[1]}` : `${parts[0]}/${parts[1]}`;
  }
  return d;
}

/**
 * Renders recent activity stream on the Dashboard home page.
 */
function renderDashboardActivities() {
  const list = document.getElementById('dashActivityList');
  if (!list) return;
  list.innerHTML = '';
  const recent = [...getFilteredTransactions()].sort((a,b) => b.timestamp - a.timestamp).slice(0, 4);

  if (recent.length === 0) {
    list.innerHTML = '<div class="text-[13px] text-on-surface-variant text-center py-5">No recent activity</div>';
    return;
  }

  recent.forEach((tx, i) => {
    const isOut = tx.type === 'OUTWARD';
    if (i > 0) {
      const div = document.createElement('div');
      div.className = 'list-divider';
      list.appendChild(div);
    }
    const row = document.createElement('div');
    row.className = 'list-row';
    row.onclick = () => viewSlip(tx.slipNo);
    row.innerHTML = `
      <div class="list-icon" style="${isOut
        ? 'background:var(--orange-light);color:var(--orange);'
        : 'background:var(--green-light);color:var(--green);'}">
        <span class="material-symbols-outlined">${isOut ? 'local_shipping' : 'archive'}</span>
      </div>
      <div class="list-content">
        <div class="list-title"><span class="list-title-text">${escapeHtml(tx.slipNo)} · ${escapeHtml(tx.partyName || 'Walk-in')}</span><span class="list-badge ${isOut ? 'badge-dispatch' : 'badge-inward'}">${isOut ? 'Dispatched' : 'Stock-In'}</span></div>
        <div class="list-sub">${Number(tx.totalBags) || 0} Units${tx.customerPhone ? ' · ' + escapeHtml(tx.customerPhone) : ''}</div>
      </div>
      <div class="list-meta">
        <div class="list-amount">₹${Number(tx.totalAmount || 0).toLocaleString('en-IN')}</div>
        <div class="list-time">${shortDateLabel(tx) ? shortDateLabel(tx) + ' · ' : ''}${escapeHtml(tx.timeStr || '')}</div>
      </div>
    `;
    list.appendChild(row);
  });
}

const LEDGER_PILL_LABELS = { ALL: 'All', OUTWARD: 'Dispatched', INWARD: 'Stock-In' };

function updateLedgerPillCounts(counts) {
  document.querySelectorAll('.ledger-pill').forEach(el => {
    const type = el.getAttribute('data-type');
    const label = LEDGER_PILL_LABELS[type] || 'All';
    const n = counts && counts[type] != null ? counts[type] : 0;
    el.textContent = `${label} ${n}`;
  });
}

/**
 * Opens the Stock tab pre-filtered to a transaction type. Used by the
 * dashboard metric cards so a number is never a dead end.
 */
function openLedgerFilter(type) {
  vibrate(20);
  ledgerFilter = type;
  ledgerRenderLimit = 300;
  const searchEl = document.getElementById('ledgerSearchInput');
  if (searchEl) searchEl.value = '';
  const clearBtn = document.getElementById('ledgerSearchClear');
  if (clearBtn) clearBtn.classList.add('hidden');
  document.querySelectorAll('.ledger-pill').forEach(el => {
    el.classList.toggle('pill-active', el.getAttribute('data-type') === type);
  });
  switchTab('stock');
  const main = document.querySelector('main');
  if (main) main.scrollTop = 0;
}

/**
 * Filters ledger records by transaction type (ALL, OUTWARD, INWARD).
 */
function filterLedgerType(type, btn) {
  vibrate(20);
  ledgerFilter = type;
  ledgerRenderLimit = 300;
  document.querySelectorAll('.ledger-pill').forEach(el => {
    el.classList.remove('pill-active');
  });
  if (btn) {
    btn.classList.add('pill-active');
  } else {
    document.querySelectorAll('.ledger-pill').forEach(el => {
      if (el.getAttribute('data-type') === type) el.classList.add('pill-active');
    });
  }
  renderLedger();
}

function ledgerDayKey(t) {
  if (t && t.timestamp) {
    const d = new Date(Number(t.timestamp));
    return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
  }
  return String((t && t.dateStr) || '');
}

function ledgerDayLabel(t) {
  if (t && t.timestamp) {
    const now = new Date(); now.setHours(0,0,0,0);
    const d = new Date(Number(t.timestamp)); d.setHours(0,0,0,0);
    const diff = Math.round((now - d) / 86400000);
    if (diff === 0) return 'Today';
    if (diff === 1) return 'Yesterday';
    return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
  }
  return String((t && t.dateStr) || 'Earlier');
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
  const clearBtn = document.getElementById('ledgerSearchClear');
  if (clearBtn) clearBtn.classList.toggle('hidden', !search);

  const base = getFilteredTransactions().filter(t => {
    if (search) {
      const hay = `${t.slipNo || ''} ${t.partyName || ''} ${t.customerPhone || ''} ${t.vehicleNo || ''} ${t.destinationSite || ''}`.toLowerCase();
      return hay.includes(search);
    }
    return true;
  });

  updateLedgerPillCounts({
    ALL: base.length,
    OUTWARD: base.filter(t => t.type === 'OUTWARD').length,
    INWARD: base.filter(t => t.type === 'INWARD').length
  });

  const filtered = base.filter(t => ledgerFilter === 'ALL' || t.type === ledgerFilter);

  if (filtered.length === 0) {
    list.innerHTML = `<div class="text-[13px] text-on-surface-variant text-center py-8">${search ? 'No transactions match your search' : 'No transactions yet'}</div>`;
    return;
  }

  filtered.sort((a,b) => (b.timestamp || 0) - (a.timestamp || 0));
  const totalMatches = filtered.length;
  const visible = filtered.slice(0, ledgerRenderLimit);

  let lastDay = null;
  let rowIndex = 0;
  visible.forEach((t) => {
    const dayKey = ledgerDayKey(t);
    if (dayKey !== lastDay) {
      lastDay = dayKey;
      const header = document.createElement('div');
      header.className = 'list-section-header';
      header.textContent = ledgerDayLabel(t);
      list.appendChild(header);
      rowIndex = 0;
    }
    const isOut = t.type === 'OUTWARD';
    if (rowIndex > 0) {
      const div = document.createElement('div');
      div.className = 'list-divider';
      list.appendChild(div);
    }
    const row = document.createElement('div');
    row.className = 'list-row';
    row.onclick = () => viewSlip(t.slipNo);
    row.innerHTML = `
      <div class="list-icon" style="${isOut
        ? 'background:var(--orange-light);color:var(--orange);'
        : 'background:var(--green-light);color:var(--green);'}">
        <span class="material-symbols-outlined">${isOut ? 'local_shipping' : 'archive'}</span>
      </div>
      <div class="list-content">
        <div class="list-title"><span class="list-title-text">${escapeHtml(t.slipNo)} · ${escapeHtml(t.partyName || 'Walk-in')}</span><span class="list-badge ${isOut ? 'badge-dispatch' : 'badge-inward'}">${isOut ? 'Dispatched' : 'Stock-In'}</span></div>
        <div class="list-sub">${Number(t.totalBags) || 0} Units${t.vehicleNo ? ' · ' + escapeHtml(t.vehicleNo) : ''}</div>
      </div>
      <div class="list-meta">
        <div class="list-amount">₹${(Number(t.totalAmount) || 0).toLocaleString('en-IN')}</div>
        <div class="list-time">${escapeHtml(t.timeStr || '')}</div>
      </div>
    `;
    list.appendChild(row);
    rowIndex++;
  });

  if (totalMatches > visible.length) {
    const more = document.createElement('button');
    more.className = 'text-[13px] font-semibold text-primary text-center py-3 w-full';
    more.textContent = `Show ${Math.min(300, totalMatches - visible.length)} more · ${totalMatches - visible.length} remaining`;
    more.onclick = () => {
      ledgerRenderLimit += 300;
      renderLedger();
    };
    list.appendChild(more);
  }
}

function debouncedRenderLedger() {
  ledgerRenderLimit = 300;
  const input = document.getElementById('ledgerSearchInput');
  const clearBtn = document.getElementById('ledgerSearchClear');
  if (clearBtn) clearBtn.classList.toggle('hidden', !(input && input.value));
  debounceRender('ledger', renderLedger, 140);
}

function clearLedgerSearch() {
  const input = document.getElementById('ledgerSearchInput');
  if (input) input.value = '';
  debouncedRenderLedger();
  if (input) input.focus();
}

function captureCurrentScreen(tag = null) {
  vibrate(40);
  const name = tag || activeTab;
  if (bridge() && typeof bridge().takeScreenshot === 'function') {
    bridge().takeScreenshot(name);
  } else {
    showToast("Screenshot captured (" + name + ")");
  }
}

function openSystemBluetoothSettings() {
  vibrate(25);
  if (bridge()) {
    bridge().openBluetoothSettings();
  }
}

/**
 * ============================================================================
 * STARTUP SPLASH SCREEN CONTROLLER
 * ============================================================================
 * Manages the fluid startup animation, status text updates, and smooth exit
 * transition into the authenticated dashboard or setup screen.
 */
function updateSplashProgress(percent, statusMsg) {
  const bar = document.getElementById('splashProgressBar');
  const txt = document.getElementById('splashStatusText');
  const pct = document.getElementById('splashPercentText');
  if (bar) bar.style.width = `${percent}%`;
  if (txt && statusMsg) txt.textContent = statusMsg;
  if (pct) pct.textContent = `${Math.round(percent)}%`;
}

function dismissSplashScreen() {
  const splash = document.getElementById('appSplashScreen');
  if (!splash) return;
  
  updateSplashProgress(100, "Ready");
  
  setTimeout(() => {
    splash.classList.add('splash-exit');
    
    // Explicitly restore status bar to application theme
    if (bridge()) {
      if (bridge().restoreStatusBar) {
        bridge().restoreStatusBar();
      } else if (bridge().setStatusBarColor) {
        bridge().setStatusBarColor("#F4FAFF", false);
      }
    }
    
    setTimeout(() => {
      splash.remove();
      if (bridge() && bridge().restoreStatusBar) {
        bridge().restoreStatusBar();
      }
    }, 600);
  }, 300);
}

function loadInstalledVersion() {
  let ver = "1.0.5";
  if (bridge() && bridge().getAppVersionInfo) {
    try {
      const info = JSON.parse(bridge().getAppVersionInfo());
      if (info && info.versionName) ver = info.versionName;
    } catch (e) {}
  }
  const badge = document.getElementById('splashVersionBadge');
  if (badge) badge.textContent = 'v' + ver + ' Stable';
  const about = document.getElementById('aboutVersionLabel');
  if (about) about.textContent = 'v' + ver + ' · Tap to check updates';
  const curVer = document.getElementById('updateCurrentVer');
  if (curVer) curVer.textContent = 'v' + ver;
  return ver;
}

// Application Startup Lifecycle
window.addEventListener('DOMContentLoaded', async () => {
  loadInstalledVersion();
  updateSplashProgress(20, "Loading options...");
  loadSavedPrintOptions();

  setTimeout(() => {
    updateSplashProgress(50, "Checking session...");
  }, 250);

  setTimeout(async () => {
    updateSplashProgress(80, "Initializing inventory...");
    try {
      await checkAuthOnStartup();
    } catch (e) {
      console.error("[Startup] Auth check error:", e);
    }
    updateSplashProgress(100, "Welcome!");
    setTimeout(dismissSplashScreen, 350);
  }, 600);

  // Background update check 3 seconds after boot
  setTimeout(() => {
    checkForUpdatesSilent();
  }, 3000);
});

let activeGodown = 'ALL';

function updateGodownList() {
  const sel = document.getElementById('topGodownSelector');
  if (!sel) return;
  sel.innerHTML = '<option value="ALL">All Godowns</option>';
  
  const godowns = new Set();
  (cachedProducts || []).forEach(p => {
    if (p.bayLocation && p.bayLocation !== "Unassigned" && p.bayLocation.trim() !== "") {
      godowns.add(p.bayLocation.trim());
    }
  });

  (cachedTransactions || []).forEach(tx => {
    if (tx.items) {
      tx.items.forEach(it => {
        if (it.bayLocation && it.bayLocation !== "Unassigned" && it.bayLocation.trim() !== "") {
          godowns.add(it.bayLocation.trim());
        }
      });
    }
  });

  // Provide fallback default godowns if none defined yet
  if (godowns.size === 0) {
    godowns.add("Godown 1");
  }
  
  Array.from(godowns).sort().forEach(g => {
    const opt = document.createElement('option');
    opt.value = g;
    opt.textContent = g;
    sel.appendChild(opt);
  });
  
  if (godowns.has(activeGodown)) {
    sel.value = activeGodown;
  } else {
    sel.value = 'ALL';
    activeGodown = 'ALL';
  }
  updateDashboardGodownLabels();
}

function updateDashboardGodownLabels() {
  const title = document.getElementById('dashGodownTitle');
  if (title) {
    title.textContent = activeGodown === 'ALL' ? (currentBusiness?.name || 'All Godowns') : activeGodown;
  }
  const sub = document.getElementById('dashGodownSubtitle');
  if (sub) {
    sub.textContent = activeGodown === 'ALL' ? 'Central Depot • All Godowns' : (activeGodown + ' Storage Hub');
  }
}

function changeGodown() {
  const sel = document.getElementById('topGodownSelector');
  if (!sel) return;
  activeGodown = sel.value;
  
  updateDashboardGodownLabels();
  
  recalculateMetrics();
  if (typeof renderPosProducts === 'function') renderPosProducts();
  if (typeof populateProductDropdowns === 'function') populateProductDropdowns();
  if (typeof renderDashboardActivities === 'function') renderDashboardActivities();
  if (typeof renderLedger === 'function') renderLedger();
}

function recalculateMetrics() {
  const products = (cachedProducts || []).filter(p => activeGodown === 'ALL' || p.bayLocation === activeGodown);
  const totalUnits = products.filter(p => p.isActive).reduce((sum, p) => sum + (Number(p.currentStockBags) || 0), 0);
  const activeCount = products.filter(p => p.isActive).length;
  const totalMt = products.filter(p => p.isActive).reduce((sum, p) => {
    return sum + ((Number(p.currentStockBags) || 0) * (Number(p.weightPerBagKg) || 0)) / 1000;
  }, 0);

  let inwardUnits = 0;
  let dispatchedUnits = 0;

  const now = Date.now();
  const dayStart = now - (24 * 60 * 60 * 1000);

  (cachedTransactions || []).forEach(tx => {
    if (tx.timestamp >= dayStart) {
      const validItems = (tx.items || []).filter(item => {
        if (activeGodown === 'ALL') return true;
        const p = (cachedProducts || []).find(x => x.id === item.productId);
        return p && p.bayLocation === activeGodown;
      });
      const itemBags = validItems.reduce((s, item) => s + (Number(item.quantityBags) || 0), 0);

      if (tx.type === 'INWARD') {
        inwardUnits += itemBags;
      } else if (tx.type === 'OUTWARD') {
        dispatchedUnits += itemBags;
      }
    }
  });

  const pendingSlipsCount = cachedTransactions ? cachedTransactions.length : 0;

  updateDashboardTelemetry({
    totalUnits,
    activeProductsCount: activeCount,
    inwardDayUnits: inwardUnits,
    dispatchedUnits: dispatchedUnits,
    pendingSlipsCount,
    totalMt
  });
}

function getFilteredTransactions() {
  if (typeof activeGodown === 'undefined' || activeGodown === 'ALL') return cachedTransactions || [];
  return (cachedTransactions || []).filter(tx => {
    return tx.items && tx.items.some(item => {
      const p = (cachedProducts || []).find(x => x.id === item.productId);
      return p && p.bayLocation === activeGodown;
    });
  });
}

/**
 * ============================================================================
 * MODULE: IN-APP UPDATE CONTROLLER
 * ============================================================================
 */
let latestUpdateData = null;

function checkForUpdatesManual() {
  vibrate(20);
  showToast("Checking GitHub for updates...");
  if (bridge() && bridge().checkForUpdates) {
    bridge().checkForUpdates(true);
  } else {
    fetch('https://api.github.com/repos/nurmd/GodownWala/releases/latest')
      .then(res => res.json())
      .then(data => {
        const tagName = (data.tag_name || '').replace(/^v/, '');
        const asset = (data.assets || []).find(a => a.name.endsWith('.apk'));
        let curVer = "1.0.5";
        if (bridge() && bridge().getAppVersionInfo) {
          try {
            const info = JSON.parse(bridge().getAppVersionInfo());
            if (info && info.versionName) curVer = info.versionName;
          } catch(e) {}
        }
        const hasUp = tagName ? (tagName !== curVer && tagName !== ('v' + curVer)) : false;
        window.onUpdateCheckResult({
          hasUpdate: hasUp,
          currentVersion: curVer,
          latestVersion: tagName,
          releaseName: data.name || data.tag_name,
          releaseNotes: data.body || "Latest update package from GitHub releases.",
          downloadUrl: asset ? asset.browser_download_url : '',
          apkSize: asset ? asset.size : 0,
          isManual: true
        });
      })
      .catch(err => {
        showToast("Check update failed: " + err.message);
      });
  }
}

function checkForUpdatesSilent() {
  if (bridge() && bridge().checkForUpdates) {
    bridge().checkForUpdates(false);
  }
}

window.onUpdateCheckResult = function(data) {
  if (!data) return;
  latestUpdateData = data;
  
  const banner = document.getElementById('updateNotificationBanner');
  const badge = document.getElementById('headerUpdateBadge');
  const bannerVer = document.getElementById('bannerNewVersion');

  if (data.hasUpdate && data.downloadUrl) {
    if (banner) banner.classList.remove('hidden');
    if (badge) badge.classList.remove('hidden');
    if (bannerVer) bannerVer.textContent = 'v' + data.latestVersion;

    if (data.isManual) {
      openUpdateModal();
    }
  } else {
    if (banner) banner.classList.add('hidden');
    if (badge) badge.classList.add('hidden');
  }
};

function openUpdateModal() {
  vibrate(20);
  const modal = document.getElementById('updateModal');
  if (!modal) return;

  if (latestUpdateData) {
    const curEl = document.getElementById('updateCurrentVer');
    const latEl = document.getElementById('updateLatestVer');
    const notesEl = document.getElementById('updateChangelog');
    const sizeEl = document.getElementById('updateApkSize');

    if (curEl) curEl.textContent = 'v' + (latestUpdateData.currentVersion || '1.0.0');
    if (latEl) latEl.textContent = 'v' + (latestUpdateData.latestVersion || '1.0.0');
    if (notesEl) notesEl.textContent = latestUpdateData.releaseNotes || 'Bug fixes and performance improvements.';
    if (sizeEl && latestUpdateData.apkSize) {
      const mb = (latestUpdateData.apkSize / (1024 * 1024)).toFixed(1);
      sizeEl.textContent = `${mb} MB`;
    }
  }

  // Reset progress state
  const progContainer = document.getElementById('updateProgressContainer');
  if (progContainer) progContainer.classList.add('hidden');
  const actionBtn = document.getElementById('updateActionBtn');
  const actionText = document.getElementById('updateActionBtnText');
  if (actionBtn) actionBtn.disabled = false;
  if (actionText) actionText.textContent = 'Update Now';

  modal.classList.remove('hidden');
}

function closeUpdateModal() {
  vibrate(15);
  const modal = document.getElementById('updateModal');
  if (modal) modal.classList.add('hidden');
}

function startAppUpdateDownload() {
  vibrate(25);
  if (!latestUpdateData || !latestUpdateData.downloadUrl) {
    showToast("No download package available");
    return;
  }

  const progContainer = document.getElementById('updateProgressContainer');
  const progBar = document.getElementById('updateProgressBar');
  const progPct = document.getElementById('updateProgressPercent');
  const progStatus = document.getElementById('updateProgressStatus');
  const actionBtn = document.getElementById('updateActionBtn');
  const actionText = document.getElementById('updateActionBtnText');

  if (progContainer) progContainer.classList.remove('hidden');
  if (progBar) progBar.style.width = '0%';
  if (progPct) progPct.textContent = '0%';
  if (progStatus) progStatus.textContent = 'Connecting to server...';
  if (actionBtn) actionBtn.disabled = true;
  if (actionText) actionText.textContent = 'Downloading...';

  if (bridge() && bridge().downloadAndInstallUpdate) {
    bridge().downloadAndInstallUpdate(latestUpdateData.downloadUrl);
  } else {
    window.open(latestUpdateData.downloadUrl, '_blank');
    if (progStatus) progStatus.textContent = 'Opened in browser!';
    if (actionBtn) actionBtn.disabled = false;
  }
}

window.onUpdateDownloadProgress = function(percent, downloaded, total, state) {
  const progBar = document.getElementById('updateProgressBar');
  const progPct = document.getElementById('updateProgressPercent');
  const progStatus = document.getElementById('updateProgressStatus');
  const actionBtn = document.getElementById('updateActionBtn');
  const actionText = document.getElementById('updateActionBtnText');

  if (state === 'downloading') {
    if (progBar) progBar.style.width = `${percent}%`;
    if (progPct) progPct.textContent = `${percent}%`;
    if (progStatus) {
      if (total > 0) {
        const dMb = (downloaded / (1024 * 1024)).toFixed(1);
        const tMb = (total / (1024 * 1024)).toFixed(1);
        progStatus.textContent = `Downloading ${dMb}MB / ${tMb}MB`;
      } else {
        progStatus.textContent = 'Downloading GodownWala.apk...';
      }
    }
  } else if (state === 'completed') {
    if (progBar) progBar.style.width = '100%';
    if (progPct) progPct.textContent = '100%';
    if (progStatus) progStatus.textContent = 'Download Complete! Launching Installer...';
    if (actionText) actionText.textContent = 'Installing...';
  } else if (state === 'error') {
    if (progStatus) progStatus.textContent = 'Download failed. Please check network.';
    if (actionBtn) actionBtn.disabled = false;
    if (actionText) actionText.textContent = 'Retry Download';
  }
};

/**
 * Android opens the soft keyboard over the lower half of the WebView. When a
 * text field takes focus, nudge it to the vertical centre so the submit button
 * below it stays reachable.
 */
document.addEventListener('focusin', (e) => {
  const el = e.target;
  if (!el || !el.tagName) return;
  if (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA' && el.tagName !== 'SELECT') return;
  if (el.type === 'checkbox' || el.type === 'radio' || el.type === 'file' || el.type === 'range') return;
  clearTimeout(document._kbFocusTimer);
  document._kbFocusTimer = setTimeout(() => {
    try {
      el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    } catch (_) {
      try { el.scrollIntoView(); } catch (_) {}
    }
  }, 320);
});

/**
 * Android hardware/gesture back: close overlays, step back through views/tabs,
 * then double-press to exit from the dashboard. Returns true when consumed.
 */
window.handleAndroidBack = function () {
  // If setup/auth/splash is blocking the main app, allow default exit.
  const splash = document.getElementById('splashScreen');
  const setup = document.getElementById('setupScreen');
  const auth = document.getElementById('authOverlay');
  const splashBlocking = splash && !splash.classList.contains('hidden') && splash.style.display !== 'none';
  const setupBlocking = setup && !setup.classList.contains('hidden');
  const authBlocking = auth && !auth.classList.contains('hidden');
  if (splashBlocking || setupBlocking || authBlocking) return false;

  const modalClosers = [
    ['updateModal', typeof closeUpdateModal === 'function' ? closeUpdateModal : null],
    ['syncHealthModal', typeof closeSyncHealthModal === 'function' ? closeSyncHealthModal : null],
    ['printSettingsModal', typeof closePrintSettingsModal === 'function' ? closePrintSettingsModal : null],
    ['bluetoothPrinterModal', typeof closeBluetoothModal === 'function' ? closeBluetoothModal : null],
    ['newCustomerModal', typeof closeNewCustomerModal === 'function' ? closeNewCustomerModal : null],
    ['stockInModal', typeof closeStockInModal === 'function' ? closeStockInModal : null],
    ['addItemModal', typeof closeAddItemModal === 'function' ? closeAddItemModal : null],
    ['editItemModal', typeof closeEditItemModal === 'function' ? closeEditItemModal : null],
    ['slipPreviewModal', typeof closeSlipPreviewModal === 'function' ? closeSlipPreviewModal : null],
  ];
  for (const [id, closer] of modalClosers) {
    const el = document.getElementById(id);
    if (el && !el.classList.contains('hidden')) {
      if (closer) closer(); else el.classList.add('hidden');
      return true;
    }
  }

  if (typeof activeTab !== 'undefined' && activeTab === 'new' && typeof newView !== 'undefined' && newView === 'dispatch') {
    if (typeof setNewView === 'function') setNewView('quick');
    return true;
  }

  if (typeof activeTab !== 'undefined' && activeTab && activeTab !== 'dashboard') {
    if (typeof switchTab === 'function') switchTab('dashboard');
    return true;
  }

  const now = Date.now();
  if (window._backExitAt && (now - window._backExitAt) < 1800) {
    window._backExitAt = 0;
    try { if (bridge() && typeof bridge().finishApp === 'function') bridge().finishApp(); } catch (_) {}
    return true;
  }
  window._backExitAt = now;
  if (typeof showToast === 'function') showToast('Press back again to exit');
  return true;
};
