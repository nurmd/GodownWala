            } catch (e: Exception) {
                response.put("success", false)
                response.put("error", e.message ?: "Print failed")
            }
            return response.toString()
        }

        @JavascriptInterface
        fun getBluetoothStatus(): String {
            val root = JSONObject()
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val isSupported = adapter != null
                val isEnabled = adapter?.isEnabled == true
                val hasPerm = hasBluetoothPermission()
                root.put("supported", isSupported)
                root.put("enabled", isEnabled)
                root.put("hasPermission", hasPerm)
                root.put("isScanning", isScanning)

                val activeObj = JSONObject()
                activeObj.put("name", getSavedPrinterName())
                activeObj.put("address", getSavedPrinterAddress())
                root.put("activePrinter", activeObj)
            } catch (e: Exception) {
                root.put("error", e.message ?: "Unknown error")
            }
            return root.toString()
        }

        @JavascriptInterface
        fun requestBluetoothPermission(): String {
            runOnUiThread {
                requestBluetoothPermissions()
            }
            return "requested"
        }

        @JavascriptInterface
        fun getPairedPrinters(): String {
            val array = JSONArray()
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return array.toString()
                if (!hasBluetoothPermission()) {
                    return array.toString()
                }
                val bonded = try { adapter.bondedDevices } catch (_: SecurityException) { null } ?: return array.toString()
                val activeAddr = getSavedPrinterAddress()
                for (dev in bonded) {
                    val obj = JSONObject()
                    val name = try { dev.name ?: "Unknown Device" } catch (_: SecurityException) { "Device" }
                    val addr = dev.address ?: ""
                    obj.put("name", name)
                    obj.put("address", addr)
                    obj.put("bonded", true)
                    obj.put("isSelected", addr.isNotBlank() && addr.equals(activeAddr, ignoreCase = true))

                    val lower = name.lowercase(Locale.ROOT)
                    val isLikelyPrinter = lower.contains("printer") || lower.contains("pos") ||
                            lower.contains("rp") || lower.contains("thermal") ||
                            lower.contains("mpt") || lower.contains("slip") || lower.contains("bt")
                    obj.put("isPrinter", isLikelyPrinter)
                    array.put(obj)
                }
            } catch (_: Exception) {}
            return array.toString()
        }

        @JavascriptInterface
        fun startBluetoothScan(): String {
            val res = JSONObject()
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    res.put("success", false)
                    res.put("error", "Bluetooth not supported")
                    return res.toString()
                }
                if (!adapter.isEnabled) {
                    res.put("success", false)
                    res.put("error", "Bluetooth is disabled. Please turn on Bluetooth in settings.")
                    return res.toString()
                }
                if (!hasScanPermission()) {
                    runOnUiThread { requestBluetoothPermissions() }
                    res.put("success", false)
                    res.put("error", "Bluetooth scan permission required")
                    return res.toString()
                }

                discoveredDevices.clear()
                try {
                    if (adapter.isDiscovering) adapter.cancelDiscovery()
                } catch (_: Exception) {}

                val started = adapter.startDiscovery()
                isScanning = started
                res.put("success", started)
            } catch (e: Exception) {
                res.put("success", false)
                res.put("error", e.message ?: "Scan failed")
            }
            return res.toString()
        }

        @JavascriptInterface
        fun stopBluetoothScan(): String {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter?.isDiscovering == true) {
                    adapter.cancelDiscovery()
                }
                isScanning = false
            } catch (_: Exception) {}
            return "stopped"
        }

        @JavascriptInterface
        fun getDiscoveredPrinters(): String {
            val array = JSONArray()
            val activeAddr = getSavedPrinterAddress()
            for ((addr, name) in discoveredDevices) {
                val obj = JSONObject()
                obj.put("name", name)
                obj.put("address", addr)
                obj.put("bonded", false)
                obj.put("isSelected", addr.equals(activeAddr, ignoreCase = true))
                val lower = name.lowercase(Locale.ROOT)
                val isLikelyPrinter = lower.contains("printer") || lower.contains("pos") ||
                        lower.contains("rp") || lower.contains("thermal") ||
                        lower.contains("mpt") || lower.contains("slip") || lower.contains("bt")
                obj.put("isPrinter", isLikelyPrinter)
                array.put(obj)
            }
            return array.toString()
