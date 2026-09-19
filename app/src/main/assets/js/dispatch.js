/**
 * ============================================================================
 * MODULE: DISPATCH & STOCK-IN OPERATIONS
 * ============================================================================
 * Manages Outward Gate Passes, Inward Stock-In receipts, and Customer/Party creation.
 */

// Global Parties / Customers State
let cachedParties = [];

/**
 * Handles party selector dropdown change.
 * If '+ Add New Customer' is selected, triggers modal.
 */
function handlePartyChange(select) {
  if (select.value === '__NEW__') {
    document.getElementById('newCustomerTargetSelect').value = select.id;
    document.getElementById('newCustomerName').value = '';
    document.getElementById('newCustomerPhone').value = '';
    document.getElementById('newCustomerModal').classList.remove('hidden');
  } else {
    if (select.id === 'dispatchPartySelect') {
      const selectedOpt = select.options[select.selectedIndex];
      const phoneInput = document.getElementById('dispatchPhone');
      if (phoneInput) {
        if (selectedOpt && selectedOpt.dataset.phone) {
          phoneInput.value = selectedOpt.dataset.phone;
        } else {
          const party = cachedParties.find(p => p.name === select.value);
          if (party) phoneInput.value = party.phone;
        }
      }
    }
  }
}

function closeNewCustomerModal() {
  document.getElementById('newCustomerModal').classList.add('hidden');
  const targetId = document.getElementById('newCustomerTargetSelect').value;
  if (targetId) {
    document.getElementById(targetId).value = 'Direct Walk-in Contractor';
  }
}

/**
 * Registers a new customer and syncs directly to Supabase parties table.
 */
async function submitNewCustomer() {
  const name = document.getElementById('newCustomerName').value.trim();
  const phone = document.getElementById('newCustomerPhone').value.trim();
  const targetId = document.getElementById('newCustomerTargetSelect').value;
  
  if (!name) {
    alert("Name is required");
    return;
  }
  
  const newId = 'party_' + Date.now();
  try {
    if (currentBusiness && currentBusiness.id) {
      await sFetch(`parties`, {
        method: 'POST',
        body: JSON.stringify({
          id: newId,
          business_id: currentBusiness.id,
          type: 'customer',
          name: name,
          phone: phone,
          is_active: true
        })
      });
    }
  } catch (e) {
    console.error("[Dispatch] Cloud party creation failed:", e);
  }
  
  const newParty = { id: newId, name: name, phone: phone };
  cachedParties.push(newParty);
  populatePartyDropdowns();
  
  const targetSelect = document.getElementById(targetId);
  if (targetSelect) {
    targetSelect.value = name;
    if (targetId === 'dispatchPartySelect') {
      const phoneInput = document.getElementById('dispatchPhone');
      if (phoneInput) phoneInput.value = phone;
    }
  }
  
  document.getElementById('newCustomerModal').classList.add('hidden');
}

// Dispatch Items State (transferred from POS cart or added in Dispatch tab)
let dispatchCartItems = {};

/**
 * Populates customer/party dropdowns across POS and Detailed Dispatch.
 */
function populatePartyDropdowns() {
  const posSelect = document.getElementById('posPartySelect');
  const dispatchSelect = document.getElementById('dispatchPartySelect');
  if (!dispatchSelect) return;

  if (posSelect) posSelect.innerHTML = '';
  dispatchSelect.innerHTML = '';

  const walkIn = document.createElement('option');
  walkIn.value = 'Direct Walk-in Contractor';
  walkIn.textContent = 'Direct Walk-in Contractor [CASH/UPI]';
  if (posSelect) posSelect.appendChild(walkIn.cloneNode(true));
  dispatchSelect.appendChild(walkIn.cloneNode(true));

  cachedParties.forEach(p => {
    if (p.name === 'Direct Walk-in Contractor') return;
    const opt = document.createElement('option');
    opt.value = p.name;
    opt.textContent = p.phone ? `${p.name} [${p.phone}]` : p.name;
    if (posSelect) posSelect.appendChild(opt.cloneNode(true));
    dispatchSelect.appendChild(opt);
  });

  const newOpt = document.createElement('option');
  newOpt.value = '__NEW__';
  newOpt.textContent = '+ Add New Customer...';
  if (posSelect) posSelect.appendChild(newOpt.cloneNode(true));
  dispatchSelect.appendChild(newOpt);
}

/**
 * Loads cart items from POS into the Dispatch tab.
 */
function loadCartIntoDispatch(cart) {
  dispatchCartItems = {};
  if (cart) {
    for (const [pId, qty] of Object.entries(cart)) {
      if (qty > 0) {
        dispatchCartItems[pId] = qty;
      }
    }
  }
  renderDispatchItems();
}

/**
 * Renders the interactive items list in the Dispatch tab.
 */
function renderDispatchItems() {
  const container = document.getElementById('dispatchItemsList');
  if (!container) return;
  container.innerHTML = '';

  const pIds = Object.keys(dispatchCartItems);
  const badge = document.getElementById('dispatchItemCountBadge');
  if (badge) {
    badge.textContent = `${pIds.length} ${pIds.length === 1 ? 'item' : 'items'}`;
  }

  if (pIds.length === 0) {
    container.innerHTML = `
      <div class="text-center py-5 px-3 text-on-surface-variant bg-surface-container-low/40 rounded-xl border border-dashed border-surface-container-high space-y-1">
        <span class="material-symbols-outlined text-[32px] text-on-surface-variant/50">inventory</span>
        <p class="text-[12px] font-bold text-on-surface">No products selected for dispatch</p>
        <p class="text-[10px] text-on-surface-variant">Select products from POS terminal or pick below to add</p>
      </div>
    `;
    updateDispatchTotals();
    return;
  }

  pIds.forEach(pId => {
    const qty = dispatchCartItems[pId];
    const p = cachedProducts.find(x => x.id === pId);
    const name = p ? p.name : "Product";
    const unit = p ? (p.unit || 'Units') : 'Units';
    const rate = p ? p.defaultRatePerBag : 0;
    const stock = p ? p.currentStockBags : 0;
    const subtotal = qty * rate;

    const row = document.createElement('div');
    row.className = 'bg-surface-container-low border border-surface-container-high rounded-xl p-2.5 flex items-center justify-between gap-2 shadow-xs';
    row.innerHTML = `
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-1.5 flex-wrap">
          <h4 class="text-[12px] font-bold text-on-surface leading-tight truncate">${name}</h4>
          <span class="text-[9px] font-bold px-1.5 py-0.2 rounded bg-surface-container-high text-on-surface-variant">Stock: ${stock}</span>
        </div>
        <div class="text-[11px] text-on-surface-variant font-medium mt-0.5">
          ₹${rate.toLocaleString('en-IN')}/${unit.toLowerCase()} • <span class="text-primary font-bold">₹${subtotal.toLocaleString('en-IN')}</span>
        </div>
      </div>
      <div class="flex items-center gap-1.5 shrink-0">
        <div class="flex items-center bg-surface-container-lowest rounded-lg border border-surface-container-high p-0.5">
          <button type="button" onclick="stepDispatchItem('${pId}', -1)" class="w-6 h-6 rounded bg-surface-container text-on-surface font-bold text-[14px] flex items-center justify-center active:scale-90">-</button>
          <input type="number" min="1" max="${stock}" value="${qty}" onchange="updateDispatchItemQty('${pId}', this.value)" class="w-12 text-center text-[12px] font-bold bg-transparent border-0 p-0 focus:ring-0 text-on-surface"/>
          <button type="button" onclick="stepDispatchItem('${pId}', 1)" class="w-6 h-6 rounded bg-surface-container text-on-surface font-bold text-[14px] flex items-center justify-center active:scale-90">+</button>
        </div>
        <button type="button" onclick="removeDispatchItem('${pId}')" class="w-7 h-7 rounded-lg text-error hover:bg-error-container/20 flex items-center justify-center active:scale-90" title="Remove item">
          <span class="material-symbols-outlined text-[18px]">delete</span>
        </button>
      </div>
    `;
    container.appendChild(row);
  });

  updateDispatchTotals();
}

/**
 * Increments or decrements an item quantity in the dispatch cart.
 */
function stepDispatchItem(productId, delta) {
  vibrate(20);
  const cur = dispatchCartItems[productId] || 0;
  const p = cachedProducts.find(x => x.id === productId);
  const stock = p ? p.currentStockBags : 999999;
  const next = cur + delta;

  if (next <= 0) {
    removeDispatchItem(productId);
    return;
  }
  if (next > stock) {
    showToast(`Only ${stock} ${p ? (p.unit || 'units') : 'units'} in stock`);
    return;
  }

  dispatchCartItems[productId] = next;
  posCart[productId] = next;
  updatePosCartBar();
  renderDispatchItems();
}

/**
 * Updates an item's quantity directly from the numeric input.
 */
function updateDispatchItemQty(productId, value) {
  const p = cachedProducts.find(x => x.id === productId);
  const stock = p ? p.currentStockBags : 999999;
  let qty = parseInt(value, 10);

  if (isNaN(qty) || qty <= 0) {
    removeDispatchItem(productId);
    return;
  }
  if (qty > stock) {
    showToast(`Max stock available is ${stock}`);
    qty = stock;
  }

  dispatchCartItems[productId] = qty;
  posCart[productId] = qty;
  updatePosCartBar();
  renderDispatchItems();
}

/**
 * Removes an item completely from the dispatch cart.
 */
function removeDispatchItem(productId) {
  vibrate(25);
  delete dispatchCartItems[productId];
  delete posCart[productId];

  const badge = document.getElementById(`posBadge-${productId}`);
  if (badge) badge.classList.add('hidden');

  updatePosCartBar();
  renderDispatchItems();
}

/**
 * Adds one unit of the selected product from the dispatch dropdown.
 */
function addDispatchItemFromSelect() {
  const select = document.getElementById('dispatchAddProductSelect');
  if (!select || !select.value) return;
  const pId = select.value;
  const p = cachedProducts.find(x => x.id === pId);
  if (!p) return;

  if (p.currentStockBags <= 0) {
    showToast("Item is out of stock");
    return;
  }

  const cur = dispatchCartItems[pId] || 0;
  if (cur >= p.currentStockBags) {
    showToast(`Cannot add: only ${p.currentStockBags} available`);
    return;
  }

  dispatchCartItems[pId] = cur + 1;
  posCart[pId] = dispatchCartItems[pId];
  updatePosCartBar();
  renderDispatchItems();
  showToast(`Added ${p.name} to Dispatch`);
}

/**
 * Calculates and updates summary totals for the dispatch form.
 */
function updateDispatchTotals() {
  let totalUnits = 0;
  let totalAmount = 0;

  for (const [pId, qty] of Object.entries(dispatchCartItems)) {
    totalUnits += qty;
    const p = cachedProducts.find(x => x.id === pId);
    if (p) {
      totalAmount += qty * p.defaultRatePerBag;
    }
  }

  const uEl = document.getElementById('dispatchTotalUnits');
  const aEl = document.getElementById('dispatchTotalAmount');
  if (uEl) uEl.textContent = totalUnits.toLocaleString('en-IN');
  if (aEl) aEl.textContent = totalAmount.toLocaleString('en-IN');
}

let editingSlipNo = null;

function clearDispatchForm() {
  editingSlipNo = null;
  const header = document.querySelector('#tab-dispatch h2');
  if (header) header.textContent = 'Issue Gate Pass / Dispatch';
  
  const btn = document.querySelector('#tab-dispatch button[onclick="submitFullDispatch()"]');
  if (btn) btn.innerHTML = '<span class="material-symbols-outlined">receipt_long</span><span>Issue Gate Pass & Print Slip</span>';
  
  const formIds = ['dispatchVehicle', 'dispatchSite', 'dispatchDriver', 'dispatchPhone', 'dispatchChallan', 'dispatchEwb'];
  formIds.forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
  
  dispatchCartItems = {};
  renderDispatchItems();
}

function openEditDispatch(slipNo) {
  const tx = cachedTransactions.find(t => t.slipNo === slipNo);
  if (!tx || tx.type !== 'OUTWARD') {
    alert("Cannot edit this slip.");
    return;
  }
  
  editingSlipNo = slipNo;
  
  const header = document.querySelector('#tab-dispatch h2');
  if (header) header.textContent = 'Edit Gate Pass: ' + slipNo;
  
  const btn = document.querySelector('#tab-dispatch button[onclick="submitFullDispatch()"]');
  if (btn) btn.innerHTML = '<span class="material-symbols-outlined">save</span><span>Save Edit & Print</span>';
  
  const partySelect = document.getElementById('dispatchPartySelect');
  if (partySelect) {
    if (Array.from(partySelect.options).some(opt => opt.value === tx.partyName)) {
      partySelect.value = tx.partyName;
    } else {
      const opt = document.createElement('option');
      opt.value = tx.partyName;
      opt.textContent = tx.partyName;
      partySelect.appendChild(opt);
      partySelect.value = tx.partyName;
    }
  }
  
  document.getElementById('dispatchVehicle').value = tx.vehicleNo || '';
  document.getElementById('dispatchSite').value = tx.destinationSite || '';
  document.getElementById('dispatchDriver').value = tx.driverName || '';
  document.getElementById('dispatchPhone').value = tx.driverPhone || '';
  document.getElementById('dispatchChallan').value = tx.challanNo || '';
  document.getElementById('dispatchEwb').value = tx.ewbNo || '';
  
  dispatchCartItems = {};
  if (tx.items) {
    tx.items.forEach(it => {
      dispatchCartItems[it.productId] = it.quantityBags || it.qty || 0;
    });
  }
  
  renderDispatchItems();
  switchTab('dispatch');
}

/**
 * Submits comprehensive dispatch with vehicle, driver, site, and multi-item list.
 */
function submitFullDispatch() {
  vibrate(40);
  const partySelect = document.getElementById('dispatchPartySelect');
  const party = partySelect ? partySelect.value : "Direct Walk-in Contractor";
  const vehicle = document.getElementById('dispatchVehicle').value.trim();
  const site = document.getElementById('dispatchSite').value.trim();
  const driver = document.getElementById('dispatchDriver').value.trim();
  const phone = document.getElementById('dispatchPhone').value.trim();
  const challan = document.getElementById('dispatchChallan').value.trim();
  const ewb = document.getElementById('dispatchEwb').value.trim();

  const pIds = Object.keys(dispatchCartItems);
  if (pIds.length === 0) {
    alert("Please add at least one item to dispatch");
    return;
  }

  const items = [];
  const detailedItems = [];
  let totalBags = 0;
  let totalMt = 0;
  let totalAmount = 0;

  for (const [pId, qty] of Object.entries(dispatchCartItems)) {
    if (qty <= 0) continue;
    items.push({ productId: pId, bags: qty });
    totalBags += qty;

    const p = cachedProducts.find(x => x.id === pId);
    const mt = p ? (p.weightPerBagKg * qty) / 1000.0 : 0;
    const amt = p ? qty * p.defaultRatePerBag : 0;
    totalMt += mt;
    totalAmount += amt;

    detailedItems.push({
      productId: pId,
      productName: p ? p.name : "Product",
      quantityBags: qty,
      metricTons: mt,
      ratePerBag: p ? p.defaultRatePerBag : 0
    });
  }

  if (items.length === 0) {
    alert("Please enter a valid quantity for items");
    return;
  }

  if (bridge()) {
    let resStr;
    if (editingSlipNo) {
      if (bridge().updateDispatch) {
        resStr = bridge().updateDispatch(editingSlipNo, party, vehicle, driver, phone, site, challan, ewb, JSON.stringify(items));
      } else {
        alert("Update dispatch not supported in this version.");
        return;
      }
    } else {
      resStr = bridge().recordDispatch(party, vehicle, driver, phone, site, challan, ewb, JSON.stringify(items));
    }
    
    const res = JSON.parse(resStr);
    if (res.success) {
      currentLastSlipNo = res.slipNo;

      cloudRecordTx('OUTWARD', res.slipNo, party, vehicle, driver, phone, challan, ewb, site, detailedItems, { bags: totalBags, mt: totalMt, amount: totalAmount }, editingSlipNo ? true : false);

      // Clear both dispatch and POS carts
      dispatchCartItems = {};
      posCart = {};
      renderPosProducts();
      updatePosCartBar();
      clearDispatchForm();

      syncFromCloud();
      
      // Automatically return to the Ledger tab so the user doesn't feel stuck on a blank form
      switchTab('ledger');
      
      viewSlip(res.slipNo);
      showToast(editingSlipNo ? `Gate Pass ${res.slipNo} Updated!` : `Gate Pass ${res.slipNo} Created!`);
    } else {
      alert(res.error || "Dispatch failed");
    }
  }
}

/**
 * Modal handlers for Inward Stock-In.
 */
function openStockInModal() {
  vibrate(25);
  document.getElementById('stockInModal').classList.remove('hidden');
}

function closeStockInModal() {
  vibrate(25);
  document.getElementById('stockInModal').classList.add('hidden');
}

/**
 * Submits an Inward Stock-In transaction to add physical bags into inventory.
 */
function submitStockIn() {
  vibrate(40);
  const pId = document.getElementById('stockInProductSelect').value;
  const bags = parseInt(document.getElementById('stockInBags').value, 10) || 0;
  const bay = document.getElementById('stockInBay').value.trim();
  const batch = document.getElementById('stockInBatch').value.trim();

  if (bags <= 0) {
    alert("Stock in quantity must be greater than zero");
    return;
  }

  if (bridge()) {
    const resStr = bridge().recordStockIn(pId, bags, batch, bay);
    const res = JSON.parse(resStr);
    if (res.success) {
      const p = cachedProducts.find(x => x.id === pId);
      const mt = p ? (p.weightPerBagKg * bags) / 1000.0 : 0;
      const amt = p ? bags * p.defaultRatePerBag : 0;
      const items = [{
        productId: pId,
        productName: p ? p.name : "Product",
        quantityBags: bags,
        metricTons: mt,
        ratePerBag: p ? p.defaultRatePerBag : 0
      }];
      
      cloudRecordTx('INWARD', res.slipNo, "Factory", "-", "-", "-", "-", "-", bay, items, { bags: bags, mt: mt, amount: amt });
      
      closeStockInModal();
      syncFromCloud();
      showToast(`Stock In +${bags} ${p ? (p.unit || 'units') : 'units'} recorded!`);
    } else {
      alert(res.error || "Stock in failed");
    }
  }
}
