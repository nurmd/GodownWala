    async function syncFromCloud() {
      if (!currentBusiness) return;
      try {
        const [cloudProducts, cloudTx, cloudParties] = await Promise.all([
          sFetch(`products?business_id=eq.${currentBusiness.id}&select=*`),
          sFetch(`transactions?business_id=eq.${currentBusiness.id}&select=*`),
          sFetch(`parties?business_id=eq.${currentBusiness.id}&select=*`)
        ]);

        if (cloudProducts && !cloudProducts.error) {
          cachedProducts = cloudProducts.map(p => ({
            id: p.id, name: p.name, grade: p.grade, weightPerBagKg: p.weight_per_bag_kg,
            defaultRatePerBag: p.default_rate_per_bag, bayLocation: p.bay_location,
            currentStockBags: p.current_stock_bags, batchNo: p.batch_no,
            imageUrl: p.image_url, isActive: p.is_active
          }));
          renderPosProducts();
          populateProductDropdowns();
        }

        if (cloudTx && !cloudTx.error) {
          cachedTransactions = cloudTx.map(t => ({
             id: t.id, slipNo: t.slip_no, type: t.type, timestamp: t.timestamp,
             partyName: t.party_name, vehicleNo: t.vehicle_no, driverName: t.driver_name,
             driverPhone: t.driver_phone, challanNo: t.challan_no, ewbNo: t.ewb_no,
             destinationSite: t.destination_site, totalBags: t.total_bags,
             totalMetricTons: t.total_metric_tons, totalAmount: t.total_amount,
             dispatchedBy: t.dispatched_by, items: t.items
          }));
          renderDashboardActivities();
          renderLedger();
        }

        if (cloudParties && !cloudParties.error) {
          cachedParties = cloudParties.map(p => ({
             id: p.id, name: p.name, phone: p.phone, gstin: p.gstin,
             defaultDestination: p.default_destination, accountNo: p.account_no
          }));
          populatePartyDropdowns();
        }
      } catch (e) {
        console.error("Cloud sync failed", e);
      }
    }
