/**
 * ============================================================================
 * MODULE: CLOUD SYNCHRONIZATION (SUPABASE)
 * ============================================================================
 * Manages two-way data replication between local device state and Supabase.
 * Enforces safe fallback defaults to prevent rendering or calculation crashes.
 */

/**
 * Records a transaction (OUTWARD dispatch or INWARD stock-in) to Supabase.
 * Fails gracefully without interrupting the user if offline.
 */
async function cloudRecordTx(type, slipNo, partyName, vehicleNo, driverName, driverPhone, challanNo, ewbNo, destinationSite, items, totals, isUpdate = false) {
  if (!currentBusiness || !currentBusiness.id) return;

  const id = 'tx_' + Date.now();
  const ts = Date.now();
  const dateStr = new Date().toLocaleDateString();
  const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  
  const payload = {
    business_id: currentBusiness.id,
    slip_no: slipNo,
    type: type,
    party_name: partyName,
    vehicle_no: vehicleNo,
    driver_name: driverName,
    driver_phone: driverPhone,
    challan_no: challanNo,
    ewb_no: ewbNo,
    destination_site: destinationSite,
    total_bags: totals.bags || 0,
    total_metric_tons: totals.mt || 0,
    total_amount: totals.amount || 0,
    dispatched_by: currentUser ? currentUser.name : "Admin",
    items: items
  };
  
  if (!isUpdate) {
    payload.id = id;
    payload.timestamp = ts;
    payload.date_str = dateStr;
    payload.time_str = timeStr;
  }
  
  try {
    if (isUpdate) {
      await sFetch(`transactions?slip_no=eq.${slipNo}`, { method: 'PATCH', body: JSON.stringify(payload) });
    } else {
      await sFetch(`transactions`, { method: 'POST', body: JSON.stringify(payload) });
    }
  } catch (e) {
    console.error("[CloudSync] Transaction sync failed (offline):", e);
  }
  
  return payload;
}

/**
 * Pulls products, transactions, and parties for the active business tenant from Supabase.
 * Invariants:
 * 1. Always checks Array.isArray() before assignment.
 * 2. Provides strict fallbacks (e.g. weightPerBagKg = 50, totalMetricTons = 0) to avoid NaN / toFixed crashes.
 */
async function syncFromCloud() {
  if (!currentBusiness || !currentBusiness.id) return;

  try {
    const [cloudProducts, cloudTx, cloudParties] = await Promise.all([
      sFetch(`products?business_id=eq.${currentBusiness.id}&select=*`),
      sFetch(`transactions?business_id=eq.${currentBusiness.id}&select=*`),
      sFetch(`parties?business_id=eq.${currentBusiness.id}&select=*`)
    ]);

    // 1. Sync Products Catalog
    if (Array.isArray(cloudProducts)) {
      cachedProducts = cloudProducts.map(p => ({
        id: p.id,
        name: p.name,
        grade: p.grade || "Standard",
        unit: p.unit || p.grade || "Units",
        weightPerBagKg: Number(p.weight_per_bag_kg) || 50,
        defaultRatePerBag: Number(p.default_rate_per_bag) || 0,
        bayLocation: p.bay_location || "Unassigned",
        currentStockBags: Number(p.current_stock_bags) || 0,
        batchNo: p.batch_no || "-",
        imageUrl: p.image_url || "",
        isActive: p.is_active !== false
      }));
      renderPosProducts();
      populateProductDropdowns();
      if (typeof updateGodownList === "function") updateGodownList();
      if (typeof recalculateMetrics === "function") recalculateMetrics();
      if (bridge() && bridge().setProducts) {
        bridge().setProducts(JSON.stringify(cachedProducts));
      }
    }

    // 2. Sync Transactions & History
    if (Array.isArray(cloudTx)) {
      cachedTransactions = cloudTx.map(t => ({
        id: t.id,
        slipNo: t.slip_no,
        type: t.type,
        timestamp: Number(t.timestamp) || Date.now(),
        timeStr: t.time_str || "",
        dateStr: t.date_str || "",
        partyName: t.party_name || "Direct Walk-in Contractor",
        vehicleNo: t.vehicle_no || "-",
        driverName: t.driver_name || "-",
        driverPhone: t.driver_phone || "-",
        challanNo: t.challan_no || "-",
        ewbNo: t.ewb_no || "-",
        destinationSite: t.destination_site || "-",
        totalBags: Number(t.total_bags) || 0,
        totalMetricTons: Number(t.total_metric_tons) || 0,
        totalAmount: Number(t.total_amount) || 0,
        dispatchedBy: t.dispatched_by || "Admin",
        items: Array.isArray(t.items) ? t.items : []
      }));
      if (bridge() && bridge().setTransactions) {
        bridge().setTransactions(JSON.stringify(cachedTransactions));
      }
      renderDashboardActivities();
      renderLedger();
    }

    // 3. Sync Parties (Customers/Vendors)
    if (Array.isArray(cloudParties)) {
      cachedParties = cloudParties.map(p => ({
        id: p.id,
        name: p.name,
        phone: p.phone || "",
        gstin: p.gstin || "UNREGISTERED",
        defaultDestination: p.default_destination || "",
        accountNo: p.account_no || ""
      }));
      populatePartyDropdowns();
      if (bridge() && bridge().setParties) {
        bridge().setParties(JSON.stringify(cachedParties));
      }
    }
  } catch (e) {
    console.error("[CloudSync] Background sync encountered an error:", e);
  }
}

/**
 * Triggered manually by the user from the Ledger UI.
 * Provides visual feedback during the synchronization process.
 */
async function forceCloudSync() {
  const syncBtnIcon = document.querySelector('button[onclick="forceCloudSync()"] span');
  if (syncBtnIcon) {
    syncBtnIcon.classList.add('animate-spin');
  }
  
  showToast("Syncing with cloud...");
  await syncFromCloud();
  showToast("Cloud sync complete!");
  
  if (syncBtnIcon) {
    syncBtnIcon.classList.remove('animate-spin');
  }
}

async function deleteCurrentSlip() {
  if (!currentLastSlipNo) return;
  const slipNo = currentLastSlipNo;
  
  if (!confirm(`Are you sure you want to completely trash slip ${slipNo}?\n\nThis will permanently delete the transaction and automatically return/deduct the stock from your inventory.`)) {
    return;
  }
  
  const txIndex = cachedTransactions.findIndex(t => t.slipNo === slipNo);
  if (txIndex === -1) {
    showToast("Slip not found in local memory");
    return;
  }
  
  const tx = cachedTransactions[txIndex];
  
  // 1. Revert inventory
  try {
    if (Array.isArray(tx.items)) {
      for (const item of tx.items) {
        const pIndex = cachedProducts.findIndex(p => p.id === item.productId);
        if (pIndex !== -1) {
          const p = cachedProducts[pIndex];
          let newBags = p.currentStockBags;
          const qty = Number(item.quantityBags) || Number(item.bags) || Number(item.qty) || 0;
          
          if (tx.type === 'OUTWARD') {
            // Revert dispatch -> ADD stock back
            newBags += qty;
          } else if (tx.type === 'INWARD') {
            // Revert stock-in -> SUBTRACT stock
            newBags -= qty;
          }
          
          // Safety bounds
          if (newBags < 0) newBags = 0;
          
          // Update locally
          p.currentStockBags = newBags;
          p.currentStockMt = (newBags * (p.weightPerBagKg || 50)) / 1000;
          
          // Sync product to Cloud
          if (currentBusiness && currentBusiness.id) {
            await sFetch(`products?id=eq.${p.id}&business_id=eq.${currentBusiness.id}`, {
              method: 'PATCH',
              body: JSON.stringify({ current_stock_bags: newBags })
            });
          }
          
          // Sync product to Bridge
          if (bridge() && bridge().updateProduct) {
            bridge().updateProduct(JSON.stringify(p));
          }
        }
      }
    }
  } catch (e) {
    console.error("Error reverting stock:", e);
    showToast("Error reverting stock. Inventory may be out of sync.");
  }
  
  // 2. Delete transaction from Cloud
  try {
    if (currentBusiness && currentBusiness.id) {
      await sFetch(`transactions?slip_no=eq.${slipNo}&business_id=eq.${currentBusiness.id}`, {
        method: 'DELETE'
      });
    }
  } catch (e) {
    console.error("Error deleting slip from cloud:", e);
  }
  
  // 3. Remove locally
  cachedTransactions.splice(txIndex, 1);
  
  // 4. Update UI
  if (typeof renderDashboardActivities === 'function') renderDashboardActivities();
  if (typeof renderLedger === 'function') renderLedger();
  if (typeof recalculateMetrics === 'function') recalculateMetrics();
  if (typeof renderPosProducts === 'function') renderPosProducts();
  if (typeof populateProductDropdowns === 'function') populateProductDropdowns();
  
  if (typeof closeSlipPreviewModal === 'function') closeSlipPreviewModal();
  
  showToast(`Slip ${slipNo} trashed successfully!`);
}

// --- REAL-TIME PUSH SYNC ---
let realtimeClient = null;
let realtimeChannel = null;

function initRealtimeSync() {
  if (!window.supabase) {
    console.log("Supabase not loaded yet for realtime sync.");
    return;
  }
  if (!currentBusiness || !currentBusiness.id) return;
  
  if (!realtimeClient) {
    realtimeClient = supabase.createClient(SUPABASE_URL, SUPABASE_KEY);
  }

  if (realtimeChannel) {
    realtimeClient.removeChannel(realtimeChannel);
  }
  
  realtimeChannel = realtimeClient.channel(`room_${currentBusiness.id}`)
    .on(
      'postgres_changes',
      { event: '*', schema: 'public', table: 'transactions', filter: `business_id=eq.${currentBusiness.id}` },
      (payload) => {
        console.log("Realtime push received for transactions!", payload);
        setTimeout(syncFromCloud, 600);
      }
    )
    .on(
      'postgres_changes',
      { event: '*', schema: 'public', table: 'products', filter: `business_id=eq.${currentBusiness.id}` },
      (payload) => {
        console.log("Realtime push received for products!", payload);
        setTimeout(syncFromCloud, 600);
      }
    )
    .subscribe((status, err) => {
      if (status === 'SUBSCRIBED') {
        console.log("Subscribed to Realtime push updates for business:", currentBusiness.id);
      } else if (status === 'CHANNEL_ERROR') {
        console.error("Realtime push channel error:", err);
      }
    });
}
