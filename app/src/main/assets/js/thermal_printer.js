/**
 * ============================================================================
 * MODULE: THERMAL PRINTER & BLUETOOTH INTEGRATION
 * ============================================================================
 * Manages ESC/POS receipt generation, print options/presets, paper width (58mm/80mm),
 * Bluetooth device scanning, pairing, and live test printing.
 */

// Printer & Formatting State
let activePrinterName = "MPT-III";
let activePrinterAddress = "";
let currentPaperWidth = 80;
let isScanningPrinters = false;
let discoveredPrintersMap = {};

// Slip Customization Options
let activePrintOptions = {
  showHeader: true,
  showSlipMeta: true,
  showCustomer: true,
  showSite: true,
  showTransport: true,
  showChallanEwb: true,
  showBatchBay: true,
  showAmount: true,
  showQrVerification: true,
  showSignatures: true
};

/**
 * Loads customized print preferences from local storage or native bridge.
 */
function loadSavedPrintOptions() {
  try {
    if (bridge() && bridge().getPrintOptions) {
      const saved = bridge().getPrintOptions();
      if (saved && saved !== '{}') {
        activePrintOptions = Object.assign({}, activePrintOptions, JSON.parse(saved));
        syncPrintOptionCheckboxes();
        return;
      }
    }
    const local = localStorage.getItem('bhpos_print_options');
    if (local) {
      activePrintOptions = Object.assign({}, activePrintOptions, JSON.parse(local));
    }
  } catch (e) {
    console.warn("[Printer] Could not load print options:", e);
  }
  syncPrintOptionCheckboxes();
}

/**
 * Persists customized print options to SharedPreferences and localStorage.
 */
function savePrintOptionsToStorage() {
  const jsonStr = JSON.stringify(activePrintOptions);
  try {
    localStorage.setItem('bhpos_print_options', jsonStr);
    if (bridge() && bridge().savePrintOptions) {
      bridge().savePrintOptions(jsonStr);
    }
  } catch (e) {
    console.warn("[Printer] Could not save print options:", e);
  }
}

function syncPrintOptionCheckboxes() {
  for (const key in activePrintOptions) {
    const cb = document.getElementById('opt_' + key);
    if (cb) {
      cb.checked = !!activePrintOptions[key];
    }
  }
}

function onPrintOptionChanged(key, checked) {
  activePrintOptions[key] = !!checked;
  savePrintOptionsToStorage();
  loadSlipPreview(currentLastSlipNo);
}

/**
 * Applies predefined receipt layouts (All, Driver Pass, Minimal).
 */
function applyPrintPreset(preset) {
  vibrate(25);
  if (preset === 'all') {
    for (const k in activePrintOptions) activePrintOptions[k] = true;
  } else if (preset === 'driver_pass') {
    activePrintOptions.showHeader = true;
    activePrintOptions.showSlipMeta = true;
    activePrintOptions.showCustomer = true;
    activePrintOptions.showSite = true;
    activePrintOptions.showTransport = true;
    activePrintOptions.showChallanEwb = true;
    activePrintOptions.showBatchBay = false;
    activePrintOptions.showAmount = false; // Conceal billing details from driver
    activePrintOptions.showQrVerification = true;
    activePrintOptions.showSignatures = true;
  } else if (preset === 'minimal') {
    activePrintOptions.showHeader = false;
    activePrintOptions.showSlipMeta = true;
    activePrintOptions.showCustomer = true;
    activePrintOptions.showSite = false;
    activePrintOptions.showTransport = false;
    activePrintOptions.showChallanEwb = false;
    activePrintOptions.showBatchBay = false;
    activePrintOptions.showAmount = false;
    activePrintOptions.showQrVerification = false;
    activePrintOptions.showSignatures = false;
  }
  syncPrintOptionCheckboxes();
  savePrintOptionsToStorage();
  loadSlipPreview(currentLastSlipNo);
  showToast(`Preset: ${preset.replace('_', ' ').toUpperCase()}`);
}

function openPrintSettingsModal() {
  vibrate(25);
  syncPrintOptionCheckboxes();
  const modal = document.getElementById('printSettingsModal');
  if (modal) modal.classList.remove('hidden');
}

function closePrintSettingsModal() {
  vibrate(20);
  const modal = document.getElementById('printSettingsModal');
  if (modal) modal.classList.add('hidden');
  loadSlipPreview(currentLastSlipNo);
}

/**
 * Generates and loads the monospace thermal receipt preview text.
 */
function loadSlipPreview(slipNo) {
  if (bridge()) {
    const optJson = JSON.stringify(activePrintOptions);
    let text = '';
    try {
      if (bridge().getSlipTextWithOptions) {
        text = bridge().getSlipTextWithOptions(slipNo || currentLastSlipNo, currentPaperWidth, optJson);
      } else {
        text = bridge().getSlipText(slipNo || currentLastSlipNo, currentPaperWidth);
      }
    } catch(e) {
      console.error(e);
      text = "Preview Error";
    }
    const previewEl = document.getElementById('thermalSlipContent');
    if (previewEl) previewEl.textContent = text || "";
  }
}

/**
 * Switches between 58mm (32 chars) and 80mm (48 chars) thermal formats.
 * Persists the preferred width for the active printer.
 */
function setPaperWidth(width, savePreference = true) {
  vibrate(25);
  currentPaperWidth = width;
  const b58 = document.getElementById('btnPaper58');
  const b80 = document.getElementById('btnPaper80');

  if (width === 58) {
    if (b58) b58.className = 'flex-1 py-1 rounded-md text-center font-bold transition-all bg-surface-container-lowest text-primary shadow-sm';
    if (b80) b80.className = 'flex-1 py-1 rounded-md text-center font-bold transition-all text-on-surface-variant';
  } else {
    if (b80) b80.className = 'flex-1 py-1 rounded-md text-center font-bold transition-all bg-surface-container-lowest text-primary shadow-sm';
    if (b58) b58.className = 'flex-1 py-1 rounded-md text-center font-bold transition-all text-on-surface-variant';
  }

  if (savePreference && bridge() && bridge().savePrinterPaperWidth) {
    if (activePrinterAddress) {
      bridge().savePrinterPaperWidth(activePrinterAddress, width);
    }
  }

  loadSlipPreview(currentLastSlipNo);
}

/**
 * Triggers native thermal printing over Bluetooth.
 */
function triggerPrintSlip() {
  vibrate(60);
  const cutBar = document.getElementById('cutLineBar');
  if (cutBar) {
    cutBar.classList.remove('hidden');
    setTimeout(() => cutBar.classList.add('hidden'), 1800);
  }

  if (bridge()) {
    const optJson = JSON.stringify(activePrintOptions);
    let resStr = '';
    if (bridge().printSlipWithOptions) {
      resStr = bridge().printSlipWithOptions(currentLastSlipNo, currentPaperWidth, optJson);
    } else {
      resStr = bridge().printSlip(currentLastSlipNo, currentPaperWidth);
    }
    const res = JSON.parse(resStr);
    if (!res.success) {
      showToast(res.error || "Print failed");
    }
  }
}

function copySlipToClipboard() {
  vibrate(25);
  const el = document.getElementById('thermalSlipContent');
  const text = el ? el.textContent : '';
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text);
    showToast("Slip copied to clipboard");
  } else {
    showToast("Copied");
  }
}

function renderSlipDetails(tx) {
  const isOut = tx.type === 'OUTWARD';
  const typeLabel = isOut ? 'Dispatched' : 'Stock-In';
  const typeClass = isOut ? 'badge-dispatch' : 'badge-inward';
  const dateLine = [tx.dateStr, tx.timeStr].filter(Boolean).join(' · ');

  const items = Array.isArray(tx.items) ? tx.items : [];
  const itemsHtml = items.length ? items.map(it => {
    const name = it.productName || it.name || 'Item';
    const qty = Number(it.quantityBags || it.qty || it.bags || 0);
    const rate = Number(it.ratePerBag || it.rate || 0);
    const amt = Number(it.amount != null ? it.amount : (qty * rate));
    return `
      <div class="flex items-start justify-between gap-3 py-2.5">
        <div class="min-w-0 flex-1">
          <div class="text-[14px] font-semibold text-on-surface truncate">${escapeHtml(String(name))}</div>
          <div class="text-[12px] text-on-surface-variant mt-0.5">${qty.toLocaleString('en-IN')} × ₹${rate.toLocaleString('en-IN')}</div>
        </div>
        <div class="text-[14px] font-bold text-on-surface shrink-0">₹${amt.toLocaleString('en-IN')}</div>
      </div>`;
  }).join('<div class="h-px bg-outline-variant/40"></div>') : `<div class="text-[13px] text-on-surface-variant py-3 text-center">No item details</div>`;

  const row = (label, value) => {
    if (value == null || value === '' || value === '-') return '';
    return `<div class="flex justify-between gap-3 py-1.5"><span class="text-[12px] text-on-surface-variant shrink-0">${escapeHtml(label)}</span><span class="text-[13px] font-medium text-on-surface text-right break-words">${escapeHtml(String(value))}</span></div>`;
  };

  const partyBlock = [
    row(isOut ? 'Customer' : 'Vendor', tx.partyName || 'Walk-in'),
    row('Phone', tx.customerPhone || ''),
    row('Destination', tx.destinationSite || ''),
  ].filter(Boolean).join('');

  const transportBlock = [
    row('Vehicle', tx.vehicleNo || ''),
    row('Driver', tx.driverName || ''),
    row('Driver Phone', tx.driverPhone || ''),
    row('Challan', tx.challanNo || ''),
    row('E-Way Bill', tx.ewbNo || tx.ewayBillNo || ''),
  ].filter(Boolean).join('');

  const totalBags = Number(tx.totalBags || 0);
  const totalAmt = Number(tx.totalAmount || 0);
  const totalMt = Number(tx.totalMt || tx.totalNetMt || 0);

  return `
    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <span class="list-badge ${typeClass}">${typeLabel}</span>
        <span class="text-[12px] text-on-surface-variant">${escapeHtml(dateLine)}</span>
      </div>

      ${partyBlock ? `<div class="card" style="padding:12px 14px;">${partyBlock}</div>` : ''}

      <div>
        <div class="text-[11px] font-bold uppercase tracking-wide text-on-surface-variant mb-1.5 px-1">Items</div>
        <div class="card" style="padding:4px 14px;">${itemsHtml}</div>
      </div>

      <div class="card" style="padding:12px 14px;">
        <div class="flex justify-between py-1"><span class="text-[13px] text-on-surface-variant">Total Units</span><span class="text-[14px] font-bold text-on-surface">${totalBags.toLocaleString('en-IN')}</span></div>
        ${totalMt > 0 ? `<div class="flex justify-between py-1"><span class="text-[13px] text-on-surface-variant">Net Weight</span><span class="text-[14px] font-bold text-on-surface">${totalMt.toLocaleString('en-IN', {maximumFractionDigits:2})} MT</span></div>` : ''}
        <div class="flex justify-between py-1 border-t border-outline-variant/40 mt-1 pt-2"><span class="text-[13px] font-semibold text-on-surface">Total Amount</span><span class="text-[16px] font-bold text-primary">₹${totalAmt.toLocaleString('en-IN')}</span></div>
      </div>

      ${transportBlock ? `<div><div class="text-[11px] font-bold uppercase tracking-wide text-on-surface-variant mb-1.5 px-1">Transport &amp; References</div><div class="card" style="padding:12px 14px;">${transportBlock}</div></div>` : ''}
    </div>
  `;
}

function viewSlip(slipNo) {
  currentLastSlipNo = slipNo;
  const modal = document.getElementById('slipPreviewModal');
  const previewEl = document.getElementById('slipPreviewContent');
  const header = document.getElementById('slipPreviewHeader');
  const editBtn = document.getElementById('slipPreviewEditBtn');
  const printBtn = document.getElementById('slipPreviewPrintBtn');

  if (header) header.textContent = 'Slip · ' + slipNo;

  const tx = (typeof cachedTransactions !== 'undefined' ? cachedTransactions : []).find(t => t.slipNo === slipNo);

  if (previewEl) {
    if (tx) {
      previewEl.innerHTML = renderSlipDetails(tx);
    } else {
      previewEl.innerHTML = `<div class="text-center py-10 text-on-surface-variant text-[14px]">Slip details not available.</div>`;
    }
  }

  if (editBtn) {
    const canEdit = !!(tx && tx.type === 'OUTWARD');
    editBtn.classList.toggle('hidden', !canEdit);
    editBtn.onclick = () => {
      closeSlipPreviewModal();
      if (typeof openEditDispatch === 'function') openEditDispatch(slipNo);
    };
  }

  if (printBtn) {
    printBtn.onclick = () => {
      vibrate(30);
      triggerPrintSlip();
    };
  }

  if (modal) modal.classList.remove('hidden');
}

function closeSlipPreviewModal() {
  const modal = document.getElementById('slipPreviewModal');
  if (modal) modal.classList.add('hidden');
}

/**
 * Updates UI cards and header badges with active printer info.
 */
function updatePrinterDisplayUI(name, address, paperWidthMm) {
  activePrinterName = name || "POS-80C Mobile Thermal";
  activePrinterAddress = address || "";
  
  // Resolve remembered paper width for this printer and apply silently to preview toggle
  let width = paperWidthMm;
  if (!width && bridge() && bridge().getPrinterPaperWidth) {
    try {
      width = bridge().getPrinterPaperWidth(activePrinterName, activePrinterAddress);
    } catch(e) {}
  }
  if (width === 58 || width === 80) {
    setPaperWidth(width, false);
  }

  const shortName = activePrinterName.length > 14 ? activePrinterName.slice(0, 12) + ".." : activePrinterName;
  
  const hName = document.getElementById('headerBtName');
  if (hName) hName.textContent = shortName;
  
  const dLabel = document.getElementById('dashPrinterLabel');
  if (dLabel) dLabel.textContent = `ESC/POS Spooler • ${shortName} (Ready)`;
  
  const tName = document.getElementById('thermalCardPrinterName');
  if (tName) tName.textContent = activePrinterName;
  const tMac = document.getElementById('thermalCardPrinterMac');
  if (tMac) tMac.textContent = activePrinterAddress ? `MAC: ${activePrinterAddress} • ESC/POS` : "ESC/POS Standard • Direct Link";

  const mName = document.getElementById('btActiveName');
  if (mName) mName.textContent = activePrinterName;
  const mAddr = document.getElementById('btActiveAddress');
  if (mAddr) mAddr.textContent = activePrinterAddress ? `MAC: ${activePrinterAddress}` : "Default Thermal Spooler Direct Link";
}

function openBluetoothModal() {
  vibrate(25);
  document.getElementById('bluetoothPrinterModal').classList.remove('hidden');
  refreshBluetoothPrinters();
}

function closeBluetoothModal() {
  vibrate(20);
  document.getElementById('bluetoothPrinterModal').classList.add('hidden');
  if (isScanningPrinters) {
    stopBluetoothScan();
  }
}

/**
 * Queries adapter state and bonded devices from native bridge.
 */
function refreshBluetoothPrinters() {
  if (!bridge()) return;

  try {
    const statusStr = bridge().getBluetoothStatus();
    const status = JSON.parse(statusStr);
    const warnBox = document.getElementById('btWarningBox');
    const warnMsg = document.getElementById('btWarningMsg');

    if (!status.enabled) {
      if (warnBox) warnBox.classList.remove('hidden');
      if (warnMsg) warnMsg.textContent = "Bluetooth is turned off";
    } else if (!status.hasPermission) {
      if (warnBox) warnBox.classList.remove('hidden');
      if (warnMsg) warnMsg.textContent = "Bluetooth permissions required";
      bridge().requestBluetoothPermission();
    } else {
      if (warnBox) warnBox.classList.add('hidden');
    }

    if (status.activePrinter) {
      updatePrinterDisplayUI(status.activePrinter.name, status.activePrinter.address);
    }

    loadPairedPrinters();
  } catch (e) {
    console.error("[Printer] Error refreshing bluetooth printers:", e);
  }
}

/**
 * Loads paired devices from Android system settings.
 */
function loadPairedPrinters() {
  const list = document.getElementById('pairedPrintersList');
  const badge = document.getElementById('pairedCountBadge');
  if (!list) return;

  try {
    const pairedStr = bridge().getPairedPrinters();
    const paired = JSON.parse(pairedStr);
    if (badge) badge.textContent = `${paired.length} paired`;

    if (paired.length === 0) {
      list.innerHTML = `
        <div class="p-3 bg-surface-container-lowest rounded-xl border border-surface-container-high text-center space-y-1">
          <p class="text-[12px] font-bold text-on-surface">No Paired Devices Found</p>
          <p class="text-[11px] text-on-surface-variant">Turn on your Bluetooth printer and pair it with PIN 0000 or 1234 in Android Settings.</p>
          <button onclick="openSystemBluetoothSettings()" class="mt-1 px-3 py-1 bg-primary text-on-primary text-[11px] font-bold rounded-lg uppercase">Open Android Bluetooth</button>
        </div>
      `;
      return;
    }

    list.innerHTML = '';
    paired.forEach(dev => {
      const isCurrent = dev.isSelected || (activePrinterAddress && dev.address === activePrinterAddress);
      const item = document.createElement('div');
      item.className = `p-2.5 rounded-xl border transition-all ${isCurrent ? 'bg-primary/5 border-primary shadow-xs' : 'bg-surface-container-lowest border-surface-container-high'}`;
      item.innerHTML = `
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 min-w-0 pr-2">
            <div class="w-8 h-8 rounded-lg ${isCurrent ? 'bg-primary text-on-primary' : 'bg-surface-container text-on-surface-variant'} flex items-center justify-center shrink-0">
              <span class="material-symbols-outlined text-[18px]">${dev.isPrinter ? 'print' : 'bluetooth'}</span>
            </div>
            <div class="min-w-0">
              <div class="flex items-center gap-1.5">
                <span class="text-[13px] font-bold text-on-surface truncate">${escapeHtml(dev.name)}</span>
                ${dev.isPrinter ? '<span class="px-1 py-0.2 bg-tertiary-fixed text-on-tertiary-fixed text-[8px] font-bold uppercase rounded">Thermal</span>' : ''}
                ${isCurrent ? '<span class="px-1.5 py-0.2 bg-primary text-on-primary text-[8px] font-bold uppercase rounded">Active</span>' : ''}
              </div>
              <p class="text-[10px] font-mono text-on-surface-variant truncate"></p>
            </div>
          </div>
          <div class="flex items-center gap-1 shrink-0">
            ${!isCurrent ? `
              <button data-action="select" class="px-2.5 py-1 bg-primary text-on-primary font-bold text-[11px] rounded-lg uppercase tracking-wide active:scale-95 shadow-xs">
                Select
              </button>
            ` : ''}
            <button data-action="test" class="p-1 rounded-lg bg-surface-container text-on-surface-variant active:scale-95" title="Test Print">
              <span class="material-symbols-outlined text-[16px]">receipt</span>
            </button>
          </div>
        </div>
      `;
      // Set untrusted values via DOM APIs (safe against attribute injection)
      item.querySelector('p.font-mono').textContent = dev.address;
      item.querySelectorAll('button[data-action]').forEach(btn => {
        btn.addEventListener('click', () => {
          if (btn.dataset.action === 'select') {
            selectPrinter(dev.name, dev.address);
          } else {
            testPrintSpecific(dev.name, dev.address);
          }
        });
      });
      list.appendChild(item);
    });
  } catch (e) {
    list.innerHTML = `<div class="text-[11px] text-error text-center py-2">Error loading devices: ${escapeHtml(e && e.message ? e.message : e)}</div>`;
  }
}

function toggleBluetoothScan() {
  vibrate(30);
  if (isScanningPrinters) {
    stopBluetoothScan();
  } else {
    startBluetoothScan();
  }
}

function startBluetoothScan() {
  if (!bridge()) return;
  discoveredPrintersMap = {};
  const resStr = bridge().startBluetoothScan();
  const res = JSON.parse(resStr);
  if (res.success) {
    isScanningPrinters = true;
    updateScanButtonUI(true);
    const list = document.getElementById('discoveredPrintersList');
    if (list) {
      list.innerHTML = `
        <div class="text-[11px] text-primary text-center py-3 bg-primary/5 rounded-xl border border-primary/20 flex items-center justify-center gap-2">
          <span class="w-2 h-2 rounded-full bg-primary animate-ping"></span>
          <span>Scanning for nearby Bluetooth devices...</span>
        </div>
      `;
    }
  } else {
    showToast(res.error || "Scan could not start");
  }
}

function stopBluetoothScan() {
  if (bridge()) {
    bridge().stopBluetoothScan();
  }
  isScanningPrinters = false;
  updateScanButtonUI(false);
}

function updateScanButtonUI(scanning) {
  const btn = document.getElementById('btnToggleScan');
  const label = document.getElementById('btnScanLabel');
  const icon = document.getElementById('scanRadarIcon');
  if (scanning) {
    if (btn) btn.className = 'px-2.5 py-1 rounded-lg bg-error text-on-error font-bold text-[11px] flex items-center gap-1 active:scale-95 shadow-xs';
    if (label) label.textContent = 'Stop';
    if (icon) icon.className = 'material-symbols-outlined text-[15px] text-primary animate-spin';
  } else {
    if (btn) btn.className = 'px-2.5 py-1 rounded-lg bg-surface-container text-primary font-bold text-[11px] flex items-center gap-1 active:scale-95 border border-surface-container-high';
    if (label) label.textContent = 'Scan';
    if (icon) icon.className = 'material-symbols-outlined text-[15px] text-primary';
  }
}

/**
 * Native callback listeners for asynchronous Bluetooth events.
 */
window.onBluetoothDeviceDiscovered = function(name, address) {
  discoveredPrintersMap[address] = name;
  renderDiscoveredPrinters();
};

window.onBluetoothScanFinished = function() {
  isScanningPrinters = false;
  updateScanButtonUI(false);
  renderDiscoveredPrinters();
};

window.onBluetoothPermissionsResult = function(granted) {
  if (granted) {
    showToast("Bluetooth permissions granted");
    refreshBluetoothPrinters();
  } else {
    showToast("Bluetooth permissions denied");
  }
};

/**
 * Triggered by native host when active printer changes or auto-fails over from an offline printer.
 */
window.onActivePrinterChanged = function(name, address, isFailover, paperWidthMm) {
  updatePrinterDisplayUI(name, address, paperWidthMm);
  loadPairedPrinters();
  if (isFailover) {
    showToast(`Offline printer bypassed. Switched to ${name} (${paperWidthMm || 80}mm)!`);
    const statusLabel = document.getElementById('dashPrinterLabel');
    if (statusLabel) {
      statusLabel.textContent = `ESC/POS Spooler • ${name} [${paperWidthMm || 80}mm] (Active)`;
    }
  }
};

/**
 * Triggered when native bridge detects printer paper size (58mm or 80mm).
 */
window.onPrinterSizeDetected = function(width, name, address) {
  if (width === 58 || width === 80) {
    setPaperWidth(width, false);
    updatePrinterDisplayUI(name, address, width);
    showToast(`Thermal format auto-detected: ${width}mm!`);
  }
};

/**
 * Triggered by native host when all target printers are offline.
 */
window.onPrinterOffline = function(name, address) {
  const tMac = document.getElementById('thermalCardPrinterMac');
  if (tMac) {
    tMac.textContent = '';
    const status = document.createElement('span');
    status.className = 'text-error font-bold';
    status.textContent = '● Offline';
    tMac.appendChild(status);
    tMac.appendChild(document.createTextNode(` • ${name || address}`));
  }
  const dLabel = document.getElementById('dashPrinterLabel');
  if (dLabel) {
    dLabel.textContent = `ESC/POS Spooler • ${name} (Offline)`;
  }
};

function renderDiscoveredPrinters() {
  const list = document.getElementById('discoveredPrintersList');
  if (!list) return;

  const addrs = Object.keys(discoveredPrintersMap);
  if (addrs.length === 0) {
    list.innerHTML = `
      <div class="text-[11px] text-on-surface-variant text-center py-3 bg-surface-container-lowest rounded-xl border border-surface-container-high border-dashed">
        No new devices found nearby. Ensure printer is in pairing mode.
      </div>
    `;
    return;
  }

  list.innerHTML = '';
  addrs.forEach(addr => {
    const name = discoveredPrintersMap[addr];
    const is58 = name.toLowerCase().includes('58') || name.toLowerCase().includes('sr');
    const widthTag = is58 ? '58mm' : '80mm';
    const item = document.createElement('div');
    item.className = 'p-2.5 rounded-xl bg-surface-container-lowest border border-surface-container-high flex items-center justify-between shadow-xs';
    item.innerHTML = `
      <div class="flex items-center gap-2 min-w-0 pr-2">
        <div class="w-8 h-8 rounded-lg bg-surface-container text-on-surface-variant flex items-center justify-center shrink-0">
          <span class="material-symbols-outlined text-[18px]">bluetooth_searching</span>
        </div>
        <div class="min-w-0">
          <span class="text-[13px] font-bold text-on-surface truncate block">${escapeHtml(name)}</span>
          <span class="text-[10px] font-mono text-on-surface-variant"></span>
        </div>
      </div>
      <div class="flex items-center gap-1 shrink-0">
        <button data-action="pair" class="px-2.5 py-1 bg-tertiary text-on-tertiary font-bold text-[11px] rounded-lg uppercase tracking-wide active:scale-95 shadow-xs flex items-center gap-1">
          <span class="material-symbols-outlined text-[14px]">link</span>
          <span>Pair</span>
        </button>
      </div>
    `;
    item.querySelector('span.font-mono').textContent = addr;
    item.querySelector('button[data-action="pair"]').addEventListener('click', () => {
      pairAndSelectDevice(name, addr);
    });
    list.appendChild(item);
  });
}

function selectPrinter(name, address) {
  if (bridge()) {
    const resStr = bridge().selectPrinter(name, address);
    const res = JSON.parse(resStr);
    if (res.success) {
      updatePrinterDisplayUI(name, address, res.paperWidthMm);
      loadPairedPrinters();
    }
  }
}

function pairAndSelectDevice(name, address) {
  if (bridge()) {
    bridge().pairBluetoothDevice(address);
    bridge().selectPrinter(name, address);
    updatePrinterDisplayUI(name, address);
    setTimeout(refreshBluetoothPrinters, 2000);
  }
}

/**
 * Sends a self-test printout to the currently active printer (with intelligent failover if offline).
 */
function testPrintCurrent() {
  vibrate(40);
  if (bridge()) {
    showToast(`Sending test slip to ${activePrinterName}...`);
    const resStr = bridge().testPrintPrinter(activePrinterName, "", currentPaperWidth);
    const res = JSON.parse(resStr);
    if (res.success) {
      showToast(res.message || "Test print sent!");
      if (res.paperWidthMm) {
        updatePrinterDisplayUI(res.printedDevice || activePrinterName, activePrinterAddress, res.paperWidthMm);
        loadPairedPrinters();
      }
    } else {
      showToast(res.message || "Test print failed");
    }
  }
}

/**
 * Sends a self-test printout to a targeted device MAC address.
 */
function testPrintSpecific(name, address) {
  vibrate(40);
  if (bridge()) {
    showToast(`Testing ${name}...`);
    const resStr = bridge().testPrintPrinter(name, address, currentPaperWidth);
    const res = JSON.parse(resStr);
    if (res.success) {
      showToast(res.message || "Test print sent!");
      if (res.paperWidthMm) {
        updatePrinterDisplayUI(res.printedDevice || name, address, res.paperWidthMm);
        loadPairedPrinters();
      }
    } else {
      showToast(res.message || "Test print failed");
    }
  }
}
