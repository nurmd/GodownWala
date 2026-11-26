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

/* ---------------------------------------------------------------------------
   SLIP PREVIEW — two rendering styles:
     receipt : paper receipt, data-driven, print-accurate content (default)
     paper   : exact ESC/POS text on a paper card at true column width
   --------------------------------------------------------------------------- */
let slipPreviewStyle = (function () {
  try { return localStorage.getItem('slipPreviewStyle') === 'paper' ? 'paper' : 'receipt'; } catch (e) { return 'receipt'; }
})();

function ensureSlipPreviewCss() {
  if (document.getElementById('slipPreviewProtoCss')) return;
  const css = `
.slip-chip{font-size:10px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;padding:5px 11px;border-radius:999px;background:var(--surface-tertiary);color:var(--text-secondary);transition:background .15s ease,color .15s ease;}
html.dark .slip-chip,body.dark .slip-chip{background:rgba(255,255,255,.10);color:rgba(235,235,245,.72);}
.slip-chip-on{background:var(--accent)!important;color:#fff!important;}
#slipPreviewContent{background:var(--bg);}
.slip-stage{display:flex;justify-content:center;align-items:flex-start;min-height:100%;padding:18px 6px 26px;box-sizing:border-box;}
.slip-paper{position:relative;margin:auto;width:var(--paper-w,420px);max-width:100%;background:var(--paper-bg);color:var(--ink);border-radius:10px;padding:20px 18px 22px;font-family:var(--font);font-size:12px;line-height:1.45;box-shadow:0 1px 1px rgba(0,0,0,.10),0 8px 24px rgba(0,0,0,.16),0 24px 60px rgba(0,0,0,.10);}
.slip-paper::before,.slip-paper::after{content:"";position:absolute;left:0;right:0;height:7px;pointer-events:none;background-image:radial-gradient(circle at 5px 3.5px,transparent 0 3.5px,var(--paper-bg) 3.6px);background-size:10px 7px;background-repeat:repeat-x;}
.slip-paper::before{top:-7px;}
.slip-paper::after{bottom:-7px;}
.slip-paper{--paper-bg:#FDFCFA;--ink:#111114;--ink-2:rgba(20,20,25,.58);--rule:rgba(20,20,25,.16);}
html.dark .slip-paper,body.dark .slip-paper{--paper-bg:#151518;--ink:#F5F5F7;--ink-2:rgba(235,235,245,.55);--rule:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.10);box-shadow:0 1px 1px rgba(0,0,0,.6),0 10px 30px rgba(0,0,0,.55);}
.slip-brand{text-align:center;font-size:15px;font-weight:800;letter-spacing:.09em;line-height:1.2;}
.slip-sub{text-align:center;font-size:9.5px;color:var(--ink-2);letter-spacing:.11em;margin-top:3px;text-transform:uppercase;}
.slip-doc{text-align:center;margin-top:13px;font-size:10px;font-weight:800;letter-spacing:.16em;color:var(--accent);text-transform:uppercase;}
.slip-meta{display:grid;grid-template-columns:1fr 1fr;gap:8px 14px;margin-top:14px;}
.slip-meta>div{display:flex;flex-direction:column;gap:1px;min-width:0;}
.slip-meta span{font-size:8.5px;letter-spacing:.11em;text-transform:uppercase;color:var(--ink-2);}
.slip-meta b{font-size:11.5px;font-weight:600;font-variant-numeric:tabular-nums;overflow:hidden;text-overflow:ellipsis;}
.slip-hr{height:1px;background:var(--rule);margin:14px 0;}
.slip-hr-dash{height:0;border-top:1px dashed var(--rule);background:none;}
.slip-sec{font-size:8.5px;font-weight:800;letter-spacing:.14em;text-transform:uppercase;color:var(--ink-2);margin-bottom:7px;}
.slip-kv{display:flex;justify-content:space-between;gap:14px;padding:2.5px 0;font-size:11.5px;line-height:1.4;}
.slip-kv span{color:var(--ink-2);flex:0 0 auto;}
.slip-kv b{font-weight:600;text-align:right;word-break:break-word;min-width:0;}
.slip-thead,.slip-irow{display:grid;grid-template-columns:1fr 38px 58px;gap:8px;align-items:baseline;}
.slip-thead{font-size:8px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:var(--ink-2);padding-bottom:6px;border-bottom:1px solid var(--rule);}
.slip-thead span:nth-child(2),.slip-thead span:nth-child(3){text-align:right;}
.slip-irow{padding:7px 0;border-bottom:1px dashed var(--rule);}
.slip-irow:last-child{border-bottom:none;}
.slip-iname{font-size:12px;font-weight:600;line-height:1.3;word-break:break-word;}
.slip-isub{font-size:9px;color:var(--ink-2);margin-top:2px;}
.slip-iqty,.slip-iwt{text-align:right;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:11.5px;font-variant-numeric:tabular-nums;}
.slip-total{display:flex;justify-content:space-between;padding:3px 0;font-size:11.5px;}
.slip-total span{color:var(--ink-2);}
.slip-total b{font-weight:600;font-variant-numeric:tabular-nums;}
.slip-grand{display:flex;justify-content:space-between;align-items:baseline;margin-top:10px;padding-top:10px;border-top:1px solid var(--rule);}
.slip-grand span{font-size:9.5px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:var(--ink-2);}
.slip-grand b{font-size:19px;font-weight:800;font-variant-numeric:tabular-nums;letter-spacing:-.02em;}
.slip-sign{display:flex;justify-content:space-between;gap:18px;margin-top:26px;font-size:9px;color:var(--ink-2);text-align:center;}
.slip-sign>div{flex:1;border-top:1px solid var(--rule);padding-top:6px;}
.slip-foot{text-align:center;font-size:8.5px;letter-spacing:.11em;color:var(--ink-2);margin-top:16px;text-transform:uppercase;}
.slip-pre{margin:0;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12.2px;line-height:1.5;white-space:pre;overflow-x:auto;color:var(--ink);}
.slip-note{padding:10px 12px;border-radius:10px;background:var(--surface-secondary);color:var(--text-secondary);font-size:11px;text-align:center;}
`;
  const el = document.createElement('style');
  el.id = 'slipPreviewProtoCss';
  el.textContent = css;
  document.head.appendChild(el);
}

function slipRawText(slipNo) {
  if (!bridge()) return '';
  const optJson = JSON.stringify(activePrintOptions);
  try {
    if (bridge().getSlipTextWithOptions) {
      return bridge().getSlipTextWithOptions(slipNo, currentPaperWidth, optJson) || '';
    }
    return bridge().getSlipText(slipNo, currentPaperWidth) || '';
  } catch (e) {
    console.error(e);
    return '';
  }
}

function slipMoney(n) {
  const v = Number(n) || 0;
  return '\u20B9' + v.toLocaleString('en-IN', { maximumFractionDigits: 2 });
}

function renderReceiptPreview(tx) {
  const esc = escapeHtml;
  const isOut = tx.type === 'OUTWARD';
  const biz = (typeof currentBusiness !== 'undefined' && currentBusiness) ? currentBusiness : {};
  const bizName = String(biz.name || 'GODOWN MANAGER');
  const gstin = biz.gstin && String(biz.gstin).toUpperCase() !== 'UNREGISTERED' ? String(biz.gstin) : '';
  const items = Array.isArray(tx.items) ? tx.items : [];

  const itemRows = items.length ? items.map(it => {
    const name = it.productName || it.name || 'Item';
    const qty = Number(it.quantityBags != null ? it.quantityBags : (it.qty || it.bags || 0));
    const mt = Number(it.metricTons != null ? it.metricTons : 0);
    const sub = [it.bayLocation, it.batchNo ? 'Batch ' + it.batchNo : ''].filter(Boolean).join(' \u00B7 ');
    return `<div class="slip-irow">
        <div><div class="slip-iname">${esc(String(name))}</div>${sub ? `<div class="slip-isub">${esc(sub)}</div>` : ''}</div>
        <div class="slip-iqty">${qty}</div>
        <div class="slip-iwt">${mt ? mt.toFixed(2) : '\u2014'}</div>
      </div>`;
  }).join('') : `<div class="slip-irow"><div class="slip-iname" style="color:var(--ink-2)">No items on this slip</div></div>`;

  const kv = (label, value) => {
    const v = value == null ? '' : String(value).trim();
    if (!v || v === '-' || v === '\u2014') return '';
    return `<div class="slip-kv"><span>${esc(label)}</span><b>${esc(v)}</b></div>`;
  };

  const partyRows = [
    kv(isOut ? 'Customer' : 'Vendor', tx.partyName || 'Walk-in'),
    kv('Phone', tx.customerPhone),
    kv('Destination', tx.destinationSite)
  ].filter(Boolean).join('');

  const transportRows = [
    kv('Vehicle', tx.vehicleNo),
    kv('Driver', tx.driverName),
    kv('Driver Phone', tx.driverPhone),
    kv('Challan', tx.challanNo),
    kv('E-Way Bill', tx.ewbNo)
  ].filter(Boolean).join('');

  const bags = Number(tx.totalBags || 0);
  const mt = Number(tx.totalMetricTons != null ? tx.totalMetricTons : (tx.totalMt || 0));
  const amount = Number(tx.totalAmount || 0);
  const operator = tx.dispatchedBy && tx.dispatchedBy !== '-' ? tx.dispatchedBy : 'Admin';
  const paperW = currentPaperWidth === 58 ? 340 : 430;

  return `<div class="slip-stage">
    <div class="slip-paper" style="--paper-w:${paperW}px">
      <div class="slip-brand">${esc(bizName.toUpperCase())}</div>
      <div class="slip-sub">Central Depot</div>
      ${gstin ? `<div class="slip-sub">GSTIN ${esc(gstin)}</div>` : ''}
      <div class="slip-doc">Gate Pass \u00B7 ${isOut ? 'Outward' : 'Stock-In'}</div>

      <div class="slip-meta">
        <div><span>Slip No</span><b>${esc(tx.slipNo || '\u2014')}</b></div>
        <div><span>Date</span><b>${esc(tx.dateStr || '\u2014')}</b></div>
        <div><span>Time</span><b>${esc(tx.timeStr || '\u2014')}</b></div>
        <div><span>Operator</span><b>${esc(operator)}</b></div>
      </div>

      <div class="slip-hr"></div>
      ${partyRows ? `<div class="slip-sec">Party</div>${partyRows}` : ''}
      ${transportRows ? `<div class="slip-sec" style="margin-top:12px">Transport</div>${transportRows}` : ''}

      <div class="slip-hr"></div>
      <div class="slip-thead"><span>Item</span><span>Qty</span><span>Wt</span></div>
      ${itemRows}

      <div class="slip-hr"></div>
      <div class="slip-total"><span>Total quantity</span><b>${bags} units</b></div>
      <div class="slip-total"><span>Total net weight</span><b>${mt.toFixed(2)} MT</b></div>
      <div class="slip-grand"><span>Total value</span><b>${slipMoney(amount)}</b></div>

      <div class="slip-sign"><div>Driver Signature</div><div>Gate Officer</div></div>
      <div class="slip-foot">Computer generated slip</div>
    </div>
  </div>`;
}

function renderPaperPreview(raw) {
  const paperW = currentPaperWidth === 58 ? 340 : 430;
  const cols = currentPaperWidth === 58 ? 32 : 48;
  const fs = Math.min(13, (paperW - 38) / (cols * 0.6)).toFixed(2);
  return `<div class="slip-stage">
    <div class="slip-paper" style="--paper-w:${paperW}px">
      <pre class="slip-pre" style="font-size:${fs}px">${escapeHtml(raw || 'No data')}</pre>
      <div class="slip-foot">${currentPaperWidth} mm \u00B7 ${cols} columns</div>
    </div>
  </div>`;
}

/**
 * Slip for preview. The JS cache is authoritative once synced, but a slip
 * created seconds ago is not in it yet; the local database always has it.
 */
function findSlipTx(slipNo) {
  const list = (typeof cachedTransactions !== 'undefined' && Array.isArray(cachedTransactions))
    ? cachedTransactions : [];
  let tx = list.find(t => t && t.slipNo === slipNo);
  if (tx) return tx;

  try {
    if (bridge() && bridge().getTransactions) {
      const all = JSON.parse(bridge().getTransactions() || '[]');
      if (Array.isArray(all)) {
        tx = all.find(t => t && t.slipNo === slipNo);
        if (tx) cachedTransactions = list.filter(t => t && t.slipNo !== slipNo).concat([tx]);
      }
    }
  } catch (e) {
    console.warn('[Slip] local lookup failed', e);
  }
  return tx || null;
}

function renderSlipPreviewBody(slipNo) {
  const previewEl = document.getElementById('slipPreviewContent');
  if (!previewEl) return;
  ensureSlipPreviewCss();

  const tx = findSlipTx(slipNo);
  const raw = slipRawText(slipNo);

  if (slipPreviewStyle === 'paper') {
    previewEl.innerHTML = renderPaperPreview(raw);
  } else {
    previewEl.innerHTML = tx
      ? renderReceiptPreview(tx)
      : `<div class="slip-note">Slip details not available.</div>`;
  }
}

function syncSlipStyleChips() {
  document.querySelectorAll('#slipPreviewStyleSwitch [data-style]').forEach(b => {
    b.classList.toggle('slip-chip-on', b.dataset.style === slipPreviewStyle);
  });
}

function setSlipPreviewStyle(style) {
  if (['receipt', 'paper'].indexOf(style) === -1) return;
  slipPreviewStyle = style;
  try { localStorage.setItem('slipPreviewStyle', style); } catch (e) {}
  syncSlipStyleChips();
  if (currentLastSlipNo) renderSlipPreviewBody(currentLastSlipNo);
}

function viewSlip(slipNo) {
  currentLastSlipNo = slipNo;
  const modal = document.getElementById('slipPreviewModal');
  const header = document.getElementById('slipPreviewHeader');
  const editBtn = document.getElementById('slipPreviewEditBtn');
  const printBtn = document.getElementById('slipPreviewPrintBtn');

  if (header) header.textContent = 'Slip: ' + slipNo;

  // Show the dialog first; a rendering error must never swallow it.
  if (modal) modal.classList.remove('hidden');

  try { renderSlipPreviewBody(slipNo); } catch (e) { console.error('[Slip] preview render failed', e); }
  try { syncSlipStyleChips(); } catch (e) { console.error('[Slip] chip sync failed', e); }

  if (editBtn) {
    const tx = findSlipTx(slipNo);
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
