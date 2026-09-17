        @JavascriptInterface
        fun getBluetoothStatus(): String {
            val root = JSONObject()
            try {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                val isSupported = adapter != null
                val isEnabled = adapter?.isEnabled == true
                // Note: we just assume permission for simplicity in this mock unless actually requested
                val hasPerm = if (Build.VERSION.SDK_INT >= 31) checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED else true
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
                val perms = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= 31) {
                    perms.add("android.permission.BLUETOOTH_CONNECT")
                    perms.add("android.permission.BLUETOOTH_SCAN")
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    perms.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (perms.isNotEmpty()) {
                    requestPermissions(perms.toTypedArray(), 2001)
                }
            }
            return "requested"
        }

        @JavascriptInterface
        fun getPairedPrinters(): String {
            val array = JSONArray()
            try {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return array.toString()
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

                    val lower = name.lowercase(java.util.Locale.ROOT)
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
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    res.put("success", false)
                    res.put("error", "Bluetooth not supported")
                    return res.toString()
                }
                if (!adapter.isEnabled) {
                    res.put("success", false)
                    res.put("error", "Bluetooth is disabled.")
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
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
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
                val lower = name.lowercase(java.util.Locale.ROOT)
                val isLikelyPrinter = lower.contains("printer") || lower.contains("pos") ||
                        lower.contains("rp") || lower.contains("thermal") ||
                        lower.contains("mpt") || lower.contains("slip") || lower.contains("bt")
                obj.put("isPrinter", isLikelyPrinter)
                array.put(obj)
            }
            return array.toString()
        }

        @JavascriptInterface
        fun selectPrinter(name: String, address: String): String {
            saveActivePrinter(name, address)
            showToast("Selected Printer: $name")
            vibrate(30)
            return """{"success": true}"""
        }

        @JavascriptInterface
        fun openBluetoothSettings() {
            startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        @JavascriptInterface
        fun pairBluetoothDevice(address: String) {
            try {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                val device = adapter?.getRemoteDevice(address)
                device?.createBond()
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun testPrintPrinter(name: String, address: String, paperWidthMm: Int): String {
            val response = JSONObject()
            try {
                val bytes = byteArrayOf(0x1B, 0x40) + "Thermal Test Print\nPrinter: $name\n".toByteArray() + byteArrayOf(0x0A, 0x0A, 0x0A)
                val printed = tryBluetoothPrint(bytes)
                response.put("success", printed)
                response.put("message", if (printed) "Test print sent" else "Test print failed")
            } catch (e: Exception) {
                response.put("success", false)
                response.put("message", e.message)
            }
            return response.toString()
        }

        @JavascriptInterface
        fun printSlipWithOptions(slipNo: String, paperWidthMm: Int, optionsJson: String): String {
            val response = JSONObject()
            try {
                val tx = runBlocking {
                    if (slipNo.isNotBlank()) {
                        repository.getTransactionBySlipNo(slipNo) ?: repository.getLastSlip()
                    } else {
                        repository.getLastSlip()
                    }
                }
                if (tx == null) {
                    response.put("success", false)
                    response.put("error", "Transaction not found for slip $slipNo")
                    return response.toString()
                }

                if (optionsJson.isNotBlank() && optionsJson != "{}") {
                    this@MainActivity.savePrintOptions(optionsJson)
                }

                showToast("Printing slip ${tx.slipNo}...")
                val opts = com.example.bhpos.printer.PrintOptions.fromJson(optionsJson)
                val escBytes = com.example.bhpos.printer.EscPosSlipGenerator.generateEscPosBytes(tx, if (paperWidthMm == 58) 58 else 80, opts)
                val printedBt = tryBluetoothPrint(escBytes)

                vibrate(100)
                if (printedBt) {
                    showToast("Thermal Slip printed via Bluetooth!")
                } else {
                    showToast("Slip ${tx.slipNo} spooled")
                }

                response.put("success", true)
                response.put("printedViaBluetooth", printedBt)
                response.put("slipNo", tx.slipNo)
            } catch (e: Exception) {
                response.put("success", false)
                response.put("error", e.message ?: "Print failed")
            }
            return response.toString()
        }
