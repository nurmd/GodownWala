# GodownWala POS - Development, Architecture & Release Rules

## 1. Supabase Backend & Persistence Invariants
- **Strict Schema Compliance**: The Supabase `transactions` table has a fixed schema (`id`, `business_id`, `slip_no`, `type`, `timestamp`, `date_str`, `time_str`, `party_name`, `vehicle_no`, `driver_name`, `driver_phone`, `challan_no`, `ewb_no`, `destination_site`, `total_bags`, `total_metric_tons`, `total_amount`, `dispatched_by`, `items`). Never include undeclared columns (e.g., `customer_phone`) in insert or update payloads. Doing so triggers PostgREST error `PGRST204` and rejects the write.
- **Party & Phone Comma Encoding**:
  - Save customer phone numbers for dispatches appended to `party_name`:
    `party_name: "${partyName}, ${customerPhone}"`
  - Parse on retrieval by looking for trailing comma and digits (`lastIndexOf(',')` and `\d{4,}` check), keeping UI labels, dropdowns, and printed slips clean.
- **Non-Destructive Local State Sync**:
  - In `bridge().setTransactions(...)`, never overwrite the local repository cache with cloud records wholesale. Always merge:
    ```kotlin
    val currentLocal = repository.getCurrentTransactions()
    val incomingSlipNos = list.map { it.slipNo }.toSet()
    val localOnly = currentLocal.filter { it.slipNo !in incomingSlipNos }
    repository.setTransactions(localOnly + list)
    ```

## 2. Inventory & Stock Calculations
- **Product-Specific Weight**: Compute metric tons dynamically using each product's configured `weightPerBagKg`:
  `mt = (bags * (p.weightPerBagKg ?: 50.0)) / 1000.0`.
- **Slip Edit Delta Tracking**: When updating an existing dispatch slip, compute item quantity deltas (`delta = newQty - oldQty`) so stock is only adjusted by the difference. Never re-deduct total quantities when only transport, driver, or site fields are edited.

## 3. Warehouse (Godown) Scoping
- Filter transactions, catalog tiles, and dashboard metrics by `activeGodown` matching `product.bayLocation`.
- Dashboard titles and telemetry cards must dynamically reflect the active Godown name rather than static placeholders (e.g., "Bay 2, Central Logistics").

## 4. Bluetooth Thermal Printing (ESC/POS)
- **Slip Typography**: Use standard ESC/POS bold commands (`ESC E 1` / `ESC G 1`) for prominent item names and quantities. Avoid double-height font flags (`ESC ! 0x10`) which cause stretching or text distortion on receipt printers.
- **Buffer & Compatibility**: Retain a 4.5s stream flush delay for 58mm printers (SR588) to prevent truncated slips. Omit hardware auto-cut commands (`GS V 66 0`) on 58mm profiles.
- **Failover Logic**: Direct test print buttons must disable failover (`allowFailover = false`) to accurately diagnose offline devices; slip printing should fail over seamlessly to online paired printers.

## 5. Synchronized Release Versioning Checklist
When releasing or bumping versions (e.g., `1.0.4` / code `5`), update all 5 files in lockstep:
1. `app/src/main/AndroidManifest.xml`: `android:versionCode` and `android:versionName`.
2. `app/build.gradle.kts`: fallback `vCode` and `vName`.
3. `build_apk.sh`: fallback version code and name flags.
4. `app/src/main/assets/index.html`: `splashVersionBadge`, `bannerNewVersion`, `updateCurrentVer`, `updateLatestVer`.
5. `app/src/main/assets/js/app.js`: `loadInstalledVersion` and `checkForUpdatesManual`.

## 6. Repository Cleanliness
- Keep raw design mockups, asset exports, and `stitch_design` folders out of version control.
