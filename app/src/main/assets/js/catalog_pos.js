/**
 * ============================================================================
 * MODULE: CATALOG & POINT OF SALE (POS)
 * ============================================================================
 * Manages product tiles, cart quantities, item add/edit lifecycle,
 * and optimistic UI updates for instantaneous feedback.
 */

// Catalog & POS State
let cachedProducts = [];
let posCart = {}; // productId -> quantity
let editModeItemId = null;
let lastToggleTime = 0;

/**
 * Toggles edit mode for a specific product card.
 * Triggered by long-press (600ms) or right-click.
 */
function toggleItemEditMode(id) {
  const now = Date.now();
  if (now - lastToggleTime < 300) return; // Debounce double triggers
  lastToggleTime = now;
  
  if (editModeItemId === id) {
    editModeItemId = null;
  } else {
    editModeItemId = id;
    vibrate(50);
  }
  renderPosProducts();
}

/**
 * Renders product grid on the Quick Dispatch / POS tab.
 * Includes "+ Add Product" tile at the end of the list.
 */
function renderPosProducts() {
  const grid = document.getElementById('posProductsGrid');
  if (!grid) return;
  grid.innerHTML = '';

  cachedProducts.filter(p => typeof activeGodown === "undefined" || activeGodown === "ALL" || p.bayLocation === activeGodown).forEach(p => {
    const inCart = posCart[p.id] || 0;
    const opacity = p.isActive === false ? 'opacity-50 grayscale' : '';
    const tile = document.createElement('div');
    tile.className = `bg-surface-container-lowest rounded-xl p-3 border border-surface-container-high shadow-sm flex flex-col justify-between select-none relative ${opacity}`;
    
    // Long-press detection for opening item editor
    let pressTimer;
    tile.addEventListener('touchstart', (e) => {
      pressTimer = setTimeout(() => {
        toggleItemEditMode(p.id);
      }, 600);
    });
    tile.addEventListener('touchend', () => clearTimeout(pressTimer));
    tile.addEventListener('touchmove', () => clearTimeout(pressTimer));
    tile.oncontextmenu = (e) => {
      e.preventDefault();
      toggleItemEditMode(p.id);
    };

    if (editModeItemId === p.id) {
      tile.innerHTML = `
        <div class="flex-1 flex flex-col items-center justify-center cursor-pointer min-h-[120px]" onclick="openEditItemModal('${p.id}')">
          <span class="material-symbols-outlined text-[48px] text-primary">edit</span>
          <span class="text-[12px] font-bold text-primary mt-2 uppercase">EDIT ITEM</span>
        </div>
      `;
    } else {
      const imgHtml = (p.imageUrl && p.imageUrl.trim() !== "") 
        ? `<div class="relative w-full h-24 mb-2 rounded-lg overflow-hidden border border-surface-container bg-surface-container-low shrink-0">
             <img src="${p.imageUrl}" class="w-full h-full object-cover" />
             <span class="${inCart > 0 ? '' : 'hidden'} absolute top-1.5 right-1.5 px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-primary text-on-primary shadow-sm" id="posBadge-${p.id}">${inCart} in cart</span>
           </div>`
        : `<div class="flex items-start justify-end min-h-[16px] mb-1">
             <span class="${inCart > 0 ? '' : 'hidden'} px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-primary text-on-primary shadow-sm" id="posBadge-${p.id}">${inCart} in cart</span>
           </div>`;
        
      tile.innerHTML = `
        <div>
          ${imgHtml}
          <div>
            <h4 class="text-[14px] font-bold text-on-surface line-clamp-1 leading-snug">${p.name}</h4>
            <div class="flex items-baseline justify-between mt-1">
              <div class="flex items-baseline gap-1">
                <span class="text-[18px] font-black text-on-surface tracking-tight">${p.currentStockBags}</span>
                <span class="text-[11px] font-semibold text-on-surface-variant">${p.unit || 'Units'}</span>
              </div>
              <span class="text-[11px] font-bold text-primary">₹${p.defaultRatePerBag}<span class="text-[9px] font-normal text-on-surface-variant">/${(p.unit || 'Unit').toLowerCase()}</span></span>
            </div>
          </div>
        </div>
        <div class="mt-2 pt-2 border-t border-surface-container grid grid-cols-3 gap-1">
          <button onclick="addPosItem('${p.id}', 1)" class="h-8 rounded bg-surface-container text-on-surface text-[11px] font-bold active:scale-90 transition-transform flex items-center justify-center">+1</button>
          <button onclick="addPosItem('${p.id}', 5)" class="h-8 rounded bg-surface-container text-on-surface text-[11px] font-bold active:scale-90 transition-transform flex items-center justify-center">+5</button>
          <button onclick="addPosItem('${p.id}', 10)" class="h-8 rounded bg-primary-fixed text-on-primary-fixed text-[11px] font-bold active:scale-90 transition-transform flex items-center justify-center">+10</button>
        </div>
      `;
    }
    grid.appendChild(tile);
  });

  // "+ Add Product" quick action tile
  const addTile = document.createElement('div');
  addTile.className = 'bg-surface-container-lowest rounded-xl p-3 border border-surface-container-high border-dashed shadow-sm flex flex-col justify-center items-center select-none relative cursor-pointer min-h-[140px] active:scale-95 transition-transform';
  addTile.onclick = () => openAddItemModal();
  addTile.innerHTML = `
    <span class="material-symbols-outlined text-[48px] text-primary/50">add</span>
    <span class="text-[12px] font-bold text-primary/70 mt-2 uppercase tracking-wide">Add Product</span>
  `;
  grid.appendChild(addTile);
}

/**
 * Increments or decrements bag quantity for a product in the POS cart.
 */
function addPosItem(productId, delta) {
  vibrate(35);
  const cur = posCart[productId] || 0;
  const product = cachedProducts.find(p => p.id === productId);
  if (!product) return;

  const next = Math.max(0, cur + delta);
  if (next > product.currentStockBags) {
    showToast(`Cannot add: only ${product.currentStockBags} ${product.unit || 'units'} in stock`);
    return;
  }

  if (next === 0) {
    delete posCart[productId];
  } else {
    posCart[productId] = next;
  }

  // Update badge UI
  const badge = document.getElementById(`posBadge-${productId}`);
  if (badge) {
    if (next > 0) {
      badge.textContent = `${next} in cart`;
      badge.classList.remove('hidden');
    } else {
      badge.classList.add('hidden');
    }
  }

  updatePosCartBar();
}

/**
 * Returns total count of bags currently added to cart.
 */
function getTotalCartBags() {
  return Object.values(posCart).reduce((a, b) => a + b, 0);
}

/**
 * Refreshes bottom cart metrics (Bags, Metric Tons, Total Value).
 */
function updatePosCartBar() {
  const totalBags = getTotalCartBags();
  let totalAmount = 0;
  let totalMt = 0;

  for (const [pId, qty] of Object.entries(posCart)) {
    const p = cachedProducts.find(x => x.id === pId);
    if (p) {
      totalAmount += qty * p.defaultRatePerBag;
      totalMt += (qty * p.weightPerBagKg) / 1000.0;
    }
  }

  const bagsEl = document.getElementById('posCartTotalBags');
  const mtEl = document.getElementById('posCartTotalMt');
  const amtEl = document.getElementById('posCartTotalAmount');
  const bar = document.getElementById('posCartBottomBar');

  if (bagsEl) bagsEl.textContent = totalBags;
  if (mtEl) mtEl.textContent = totalMt.toFixed(2);
  if (amtEl) amtEl.textContent = totalAmount.toLocaleString('en-IN');

  if (bar) {
    if (activeTab === 'pos' && totalBags > 0) {
      bar.classList.remove('hidden');
    } else {
      bar.classList.add('hidden');
    }
  }
}

/**
 * Forwards current POS cart items to the Dispatch tab for review and customer assignment.
 */
function forwardPosToDispatch() {
  vibrate(40);
  const totalBags = getTotalCartBags();
  if (totalBags === 0) {
    showToast("Cart is empty. Please select items first.");
    return;
  }

  if (typeof loadCartIntoDispatch === 'function') {
    loadCartIntoDispatch(posCart);
  }
  switchTab('dispatch');
  showToast(`${totalBags} item(s) forwarded to Dispatch`);
}

/**
 * Checks out the current POS cart (forwards to Dispatch).
 */
function checkoutPosCart() {
  forwardPosToDispatch();
}

/**
 * Handles client-side photo picking and canvas compression.
 * Resizes image down to max 350x350 and encodes as JPEG Data URL (~10-20KB),
 * allowing direct storage in products.image_url and instant real-time sync across devices.
 */
function handleProductImageUpload(fileInput, hiddenInputId, previewImgId, placeholderId) {
  if (!fileInput.files || !fileInput.files[0]) return;
  const file = fileInput.files[0];
  const reader = new FileReader();

  reader.onload = function(e) {
    const img = new Image();
    img.onload = function() {
      const maxDim = 350;
      let w = img.width;
      let h = img.height;
      if (w > h) {
        if (w > maxDim) {
          h = Math.round((h * maxDim) / w);
          w = maxDim;
        }
      } else {
        if (h > maxDim) {
          w = Math.round((w * maxDim) / h);
          h = maxDim;
        }
      }

      const canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, w, h);

      // Quality 0.7 gives clear 350px image under 15-20 KB
      const compressedDataUrl = canvas.toDataURL('image/jpeg', 0.7);

      const hiddenInput = document.getElementById(hiddenInputId);
      if (hiddenInput) hiddenInput.value = compressedDataUrl;

      const previewImg = document.getElementById(previewImgId);
      const placeholder = document.getElementById(placeholderId);
      const removeBtn = document.getElementById(hiddenInputId.replace('Image', 'ImageRemoveBtn'));

      if (previewImg) {
        previewImg.src = compressedDataUrl;
        previewImg.classList.remove('hidden');
      }
      if (placeholder) placeholder.classList.add('hidden');
      if (removeBtn) removeBtn.classList.remove('hidden');
    };
    img.src = e.target.result;
  };
  reader.readAsDataURL(file);
}

function clearProductImage(hiddenInputId, previewImgId, placeholderId) {
  const hiddenInput = document.getElementById(hiddenInputId);
  if (hiddenInput) hiddenInput.value = '';

  const previewImg = document.getElementById(previewImgId);
  const placeholder = document.getElementById(placeholderId);
  const removeBtn = document.getElementById(hiddenInputId.replace('Image', 'ImageRemoveBtn'));
  const fileInput = document.getElementById(hiddenInputId.replace('Image', 'FileInput'));

  if (fileInput) fileInput.value = '';
  if (previewImg) {
    previewImg.src = '';
    previewImg.classList.add('hidden');
  }
  if (placeholder) placeholder.classList.remove('hidden');
  if (removeBtn) removeBtn.classList.add('hidden');
}

/**
 * Opens modal to create a new product.
 */
function openAddItemModal() {
  const gInput = document.getElementById("addItemGodown"); if(gInput) gInput.value = (typeof activeGodown !== "undefined" && activeGodown !== "ALL") ? activeGodown : "Godown 1";
  document.getElementById('addItemName').value = '';
  document.getElementById('addItemRate').value = '';
  document.getElementById('addItemStock').value = '0';
  const unitSelect = document.getElementById('addItemUnit');
  if (unitSelect) unitSelect.value = 'Units';
  clearProductImage('addItemImage', 'addItemImagePreview', 'addItemImagePlaceholder');
  document.getElementById('addItemModal').classList.remove('hidden');
}

function closeAddItemModal() {
  document.getElementById('addItemModal').classList.add('hidden');
}

/**
 * Saves a newly added product.
 * Features 0ms optimistic UI rendering with asynchronous cloud sync.
 */
async function saveNewItem() {
  vibrate(40);
  const name = document.getElementById('addItemName').value.trim();
  const rate = parseFloat(document.getElementById('addItemRate').value);
  const stock = parseInt(document.getElementById('addItemStock').value) || 0;
  const unitSelect = document.getElementById('addItemUnit');
  const unit = unitSelect ? unitSelect.value : 'Units';
  const image = document.getElementById('addItemImage') ? document.getElementById('addItemImage').value.trim() : '';

  if (!name || isNaN(rate)) {
    alert("Please fill required fields properly.");
    return;
  }

  const id = 'prod_' + Date.now();
  const newProduct = {
    id: id,
    name: name,
    grade: unit,
    unit: unit,
    weightPerBagKg: 50,
    defaultRatePerBag: rate,
    bayLocation: document.getElementById("addItemGodown") ? document.getElementById("addItemGodown").value.trim() || "Godown 1" : "Godown 1",
    currentStockBags: stock,
    batchNo: "NEW",
    imageUrl: image,
    isActive: true
  };

  // 1. Optimistic local update: show item immediately on screen
  cachedProducts.push(newProduct);
  renderPosProducts();
  populateProductDropdowns();
  if (typeof updateGodownList === "function") updateGodownList();
  closeAddItemModal();
  showToast("Item added!");

  // 2. Persist to local repository cache
  if (bridge()) {
    try {
      bridge().addProduct(JSON.stringify(newProduct));
    } catch (e) {
      console.error("[Catalog] Bridge addProduct error:", e);
    }
  }

  // 3. Asynchronously sync to Supabase database
  try {
    if (currentBusiness && currentBusiness.id) {
      await sFetch(`products`, {
        method: 'POST',
        body: JSON.stringify({
          id: id,
          business_id: currentBusiness.id,
          name: name, 
          grade: unit,
          default_rate_per_bag: rate,
          current_stock_bags: stock,
          image_url: image,
          is_active: true
        })
      });
    }
  } catch (e) {
    console.error("[Catalog] Cloud save failed:", e);
  }
}

/**
 * Opens modal to edit an existing product's title, price, image, or active status.
 */
function openEditItemModal(id) {
  vibrate(25);
  const product = cachedProducts.find(p => p.id === id);
  if (!product) return;
  document.getElementById('editItemId').value = product.id;
  document.getElementById('editItemName').value = product.name;
  document.getElementById('editItemRate').value = product.defaultRatePerBag;
  
  const imgVal = product.imageUrl || '';
  const imgInput = document.getElementById('editItemImage');
  if (imgInput) imgInput.value = imgVal;

  const previewImg = document.getElementById('editItemImagePreview');
  const placeholder = document.getElementById('editItemImagePlaceholder');
  const removeBtn = document.getElementById('editItemImageRemoveBtn');
  const fileInput = document.getElementById('editItemFileInput');
  if (fileInput) fileInput.value = '';

  if (imgVal && imgVal.trim() !== '') {
    if (previewImg) {
      previewImg.src = imgVal;
      previewImg.classList.remove('hidden');
    }
    if (placeholder) placeholder.classList.add('hidden');
    if (removeBtn) removeBtn.classList.remove('hidden');
  } else {
    if (previewImg) {
      previewImg.src = '';
      previewImg.classList.add('hidden');
    }
    if (placeholder) placeholder.classList.remove('hidden');
    if (removeBtn) removeBtn.classList.add('hidden');
  }

  const unitSelect = document.getElementById('editItemUnit');
  if (unitSelect) unitSelect.value = product.unit || product.grade || 'Units';
  
  const isAct = product.isActive !== false;
  document.getElementById('editItemId').dataset.active = isAct;
  updateEditToggleBtn(isAct);

  document.getElementById('editItemModal').classList.remove('hidden');
}

function updateEditToggleBtn(isActive) {
  const btn = document.getElementById('editItemToggleStatusBtn');
  const text = document.getElementById('editItemToggleStatusText');
  const icon = document.getElementById('editItemToggleStatusIcon');
  if (!btn || !text || !icon) return;

  if (isActive) {
    btn.className = "bg-error text-on-error py-2.5 rounded-xl font-extrabold shadow uppercase tracking-wider text-[13px] active:scale-95 transition-transform flex items-center justify-center gap-1";
    icon.textContent = "block";
    text.textContent = "DISABLE";
  } else {
    btn.className = "bg-primary-container text-on-primary-container py-2.5 rounded-xl font-extrabold shadow uppercase tracking-wider text-[13px] active:scale-95 transition-transform flex items-center justify-center gap-1";
    icon.textContent = "check_circle";
    text.textContent = "ENABLE";
  }
}

function toggleEditItemStatus() {
  vibrate(40);
  const input = document.getElementById('editItemId');
  const current = input.dataset.active === 'true';
  const next = !current;
  input.dataset.active = next;
  updateEditToggleBtn(next);
}

function closeEditItemModal() {
  vibrate(25);
  document.getElementById('editItemModal').classList.add('hidden');
  editModeItemId = null;
  renderPosProducts();
}

/**
 * Saves edits to an existing product with instant optimistic feedback.
 */
async function saveEditItem() {
  vibrate(40);
  const id = document.getElementById('editItemId').value;
  const name = document.getElementById('editItemName').value.trim();
  const rate = parseFloat(document.getElementById('editItemRate').value);
  const image = document.getElementById('editItemImage').value.trim();
  const unitSelect = document.getElementById('editItemUnit');
  const unit = unitSelect ? unitSelect.value : 'Units';

  if (!name || isNaN(rate)) {
    alert("Please fill required fields.");
    return;
  }
  
  const isActive = document.getElementById('editItemId').dataset.active === 'true';

  // 1. Optimistic update
  const pIdx = cachedProducts.findIndex(p => p.id === id);
  if (pIdx !== -1) {
    cachedProducts[pIdx].name = name;
    cachedProducts[pIdx].defaultRatePerBag = rate;
    cachedProducts[pIdx].imageUrl = image;
    cachedProducts[pIdx].isActive = isActive;
    cachedProducts[pIdx].unit = unit;
    cachedProducts[pIdx].grade = unit;
    renderPosProducts();
    populateProductDropdowns();
    if (typeof updateGodownList === "function") updateGodownList();
  }
  closeEditItemModal();
  showToast("Item updated!");

  // 2. Persist to bridge
  if (bridge()) {
    try {
      const obj = { id: id, name: name, defaultRatePerBag: rate, imageUrl: image, isActive: isActive, unit: unit };
      bridge().updateProduct(JSON.stringify(obj));
    } catch (e) {
      console.error("[Catalog] Bridge updateProduct error:", e);
    }
  }

  // 3. Sync to Supabase
  try {
    if (currentBusiness && currentBusiness.id) {
      await sFetch(`products?id=eq.${id}&business_id=eq.${currentBusiness.id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          name: name,
          grade: unit,
          default_rate_per_bag: rate,
          image_url: image,
          is_active: isActive
        })
      });
    }
  } catch (e) {
    console.error("[Catalog] Cloud edit failed:", e);
  }
}

/**
 * Populates product select dropdowns across Dispatch and Stock-In forms.
 */
function populateProductDropdowns() {
  const dAddSelect = document.getElementById('dispatchAddProductSelect');
  const dSelect = document.getElementById('dispatchProductSelect');
  const sSelect = document.getElementById('stockInProductSelect');

  if (dAddSelect) {
    dAddSelect.innerHTML = '';
    cachedProducts.filter(p => typeof activeGodown === "undefined" || activeGodown === "ALL" || p.bayLocation === activeGodown).forEach(p => {
      if (p.isActive === false) return;
      const opt = document.createElement('option');
      opt.value = p.id;
      const unitStr = (p.unit || 'unit').toLowerCase();
      opt.textContent = `${p.name} - ₹${p.defaultRatePerBag}/${unitStr} (Avail: ${p.currentStockBags})`;
      dAddSelect.appendChild(opt);
    });
  }

  if (dSelect) {
    dSelect.innerHTML = '';
    cachedProducts.filter(p => typeof activeGodown === "undefined" || activeGodown === "ALL" || p.bayLocation === activeGodown).forEach(p => {
      if (p.isActive === false) return;
      const opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = `${p.name} (${p.currentStockBags} ${p.unit || 'units'} available)`;
      dSelect.appendChild(opt);
    });
  }

  if (sSelect) {
    sSelect.innerHTML = '';
    cachedProducts.filter(p => typeof activeGodown === "undefined" || activeGodown === "ALL" || p.bayLocation === activeGodown).forEach(p => {
      if (p.isActive === false) return;
      const opt2 = document.createElement('option');
      opt2.value = p.id;
      opt2.textContent = `${p.name} (${p.bayLocation || "Default"})`;
      sSelect.appendChild(opt2);
    });
  }
}
