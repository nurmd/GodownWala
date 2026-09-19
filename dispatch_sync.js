    async function cloudRecordTx(type, slipNo, partyName, vehicleNo, driverName, driverPhone, challanNo, ewbNo, destinationSite, items, totals) {
      if(!currentBusiness) return;
      const id = 'tx_' + Date.now();
      const ts = Date.now();
      const dateStr = new Date().toLocaleDateString();
      const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      
      const payload = {
        id: id,
        business_id: currentBusiness.id,
        slip_no: slipNo,
        type: type,
        timestamp: ts,
        date_str: dateStr,
        time_str: timeStr,
        party_name: partyName,
        vehicle_no: vehicleNo,
        driver_name: driverName,
        driver_phone: driverPhone,
        challan_no: challanNo,
        ewb_no: ewbNo,
        destination_site: destinationSite,
        total_bags: totals.bags,
        total_metric_tons: totals.mt,
        total_amount: totals.amount,
        dispatched_by: currentUser ? currentUser.name : "Admin",
        items: items
      };
      
      try {
        await sFetch(`transactions`, { method: 'POST', body: JSON.stringify(payload) });
      } catch(e) { console.error("Tx sync failed", e); }
      
      return payload;
    }
