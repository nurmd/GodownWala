package com.example.bhpos

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.bhpos.data.repository.CementStockRepositoryImpl
import com.example.bhpos.domain.model.CementProduct
import com.example.bhpos.domain.model.StockTransaction
import com.example.bhpos.domain.model.StockTransactionItem
import com.example.bhpos.domain.model.TransactionType
import com.example.bhpos.printer.EscPosSlipGenerator
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private val repository = CementStockRepositoryImpl.instance
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        webView = WebView(this)
        
        val container = android.widget.FrameLayout(this)
        container.fitsSystemWindows = true
        container.addView(webView)
        
        setContentView(container)

        setupWebView()
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private val discoveredDevices = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var isScanning = false

    companion object {
        private const val PREFS_NAME = "bhpos_bluetooth_prefs"
        private const val KEY_PRINTER_NAME = "active_printer_name"
        private const val KEY_PRINTER_ADDR = "active_printer_address"
        private const val KEY_PRINT_OPTIONS = "active_print_options"
        private const val PERM_REQUEST_CODE = 2001
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission("android.permission.BLUETOOTH_SCAN") == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestBluetoothPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            perms.add("android.permission.BLUETOOTH_CONNECT")
            perms.add("android.permission.BLUETOOTH_SCAN")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            perms.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (perms.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(perms.toTypedArray(), PERM_REQUEST_CODE)
            }
        }
    }

    fun getSavedPrinterName(): String {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_PRINTER_NAME, "POS-80C Mobile Thermal") ?: "POS-80C Mobile Thermal"
    }

    fun getSavedPrinterAddress(): String {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_PRINTER_ADDR, "") ?: ""
    }

    fun saveActivePrinter(name: String, address: String) {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_PRINTER_NAME, name).putString(KEY_PRINTER_ADDR, address).apply()
    }

    fun getSavedPrintOptions(): String {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_PRINT_OPTIONS, "{}") ?: "{}"
    }

    fun savePrintOptions(json: String) {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_PRINT_OPTIONS, json).apply()
    }

    inner class AndroidBridge {

        private val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

        @JavascriptInterface
        fun getDashboardTelemetry(): String {
            return try {
                val products = repository.getCurrentProducts()
                val totalBags = products.sumOf { it.currentStockBags }
                val totalMt = (totalBags * 50.0) / 1000.0

                val root = JSONObject()
                root.put("totalStoredBags", totalBags)
                root.put("totalMetricTons", totalMt)
                root.put("inwardDayBags", 1200)
                root.put("inwardDayMt", 60.0)
                root.put("dispatchedBags", 850)
                root.put("dispatchedMt", 42.5)
                root.put("pendingSlipsCount", 1)
                root.put("netTallyBags", 350)

                // Brand distribution removed

                root.toString()
            } catch (e: Exception) {
                "{}"
            }
        }

        @JavascriptInterface
        fun getProducts(): String {
            return try {
                val list = repository.getCurrentProducts()
                val array = JSONArray()
                for (p in list) {
                    val obj = JSONObject()
                    obj.put("id", p.id)
                    obj.put("name", p.name)
                    obj.put("grade", p.grade)
                    obj.put("weightPerBagKg", p.weightPerBagKg)
                    obj.put("defaultRatePerBag", p.defaultRatePerBag)
                    obj.put("bayLocation", p.bayLocation)
                    obj.put("currentStockBags", p.currentStockBags)
                    obj.put("batchNo", p.batchNo)
                    array.put(obj)
                }
                array.toString()
            } catch (e: Exception) {
                "[]"
            }
        }

        @JavascriptInterface
        fun getParties(): String {
            return try {
                val list = repository.getParties()
                val array = JSONArray()
                for (p in list) {
                    val obj = JSONObject()
                    obj.put("id", p.id)
                    obj.put("name", p.name)
                    obj.put("gstin", p.gstin)
                    obj.put("phone", p.phone)
                    obj.put("defaultDestination", p.defaultDestination)
                    obj.put("accountNo", p.accountNo)
                    array.put(obj)
                }
                array.toString()
            } catch (e: Exception) {
                "[]"
            }
        }

        @JavascriptInterface
        fun getTransactions(): String {
            return try {
                val list = repository.getCurrentTransactions()
                val array = JSONArray()
                for (t in list) {
                    val obj = JSONObject()
                    obj.put("id", t.id)
                    obj.put("slipNo", t.slipNo)
                    obj.put("type", t.type.name)
                    obj.put("timestamp", t.timestamp)
                    obj.put("dateStr", dateFormat.format(Date(t.timestamp)))
                    obj.put("timeStr", timeFormat.format(Date(t.timestamp)))
                    obj.put("partyName", t.partyName)
                    obj.put("vehicleNo", t.vehicleNo)
                    obj.put("driverName", t.driverName)
                    obj.put("driverPhone", t.driverPhone)
                    obj.put("challanNo", t.challanNo)
                    obj.put("ewbNo", t.ewbNo)
                    obj.put("destinationSite", t.destinationSite)
                    obj.put("totalBags", t.totalBags)
                    obj.put("totalMetricTons", t.totalMetricTons)
                    obj.put("totalAmount", t.totalAmount)

                    val itemsArray = JSONArray()
                    for (item in t.items) {
                        val itemObj = JSONObject()
                        itemObj.put("productId", item.productId)
                        itemObj.put("productName", item.productName)
                        itemObj.put("quantityBags", item.quantityBags)
                        itemObj.put("metricTons", item.metricTons)
                        itemObj.put("ratePerBag", item.ratePerBag)
                        itemObj.put("batchNo", item.batchNo)
                        itemObj.put("bayLocation", item.bayLocation)
                        itemsArray.put(itemObj)
                    }
                    obj.put("items", itemsArray)
                    array.put(obj)
                }
                array.toString()
            } catch (e: Exception) {
                "[]"
            }
        }

        @JavascriptInterface
        fun recordQuickDispatch(productId: String, bags: Int, partyName: String): String {
            val response = JSONObject()
            try {
                if (bags <= 0) {
                    response.put("success", false)
                    response.put("error", "Quantity must be greater than zero")
                    return response.toString()
                }

                val products = repository.getCurrentProducts()
                val product = products.find { it.id == productId }
                if (product == null) {
                    response.put("success", false)
                    response.put("error", "Product not found")
                    return response.toString()
                }

                if (product.currentStockBags < bags) {
                    response.put("success", false)
                    response.put("error", "Insufficient stock: only ${product.currentStockBags} bags available")
                    return response.toString()
                }

                val slipNo = "GP-${System.currentTimeMillis() % 100000}"
                val mt = (bags * product.weightPerBagKg) / 1000.0
                val amount = bags * product.defaultRatePerBag

                val item = StockTransactionItem(
                    productId = product.id,
                    productName = product.name,
                    quantityBags = bags,
                    metricTons = mt,
                    ratePerBag = product.defaultRatePerBag,
                    batchNo = product.batchNo,
                    bayLocation = product.bayLocation
                )

                val tx = StockTransaction(
                    id = "tx_${System.currentTimeMillis()}",
                    slipNo = slipNo,
                    type = TransactionType.OUTWARD,
                    timestamp = System.currentTimeMillis(),
                    partyName = if (partyName.isNotBlank()) partyName else "Direct Walk-in Contractor",
                    vehicleNo = "MH-12-QZ-4891",
                    driverName = "Express Dispatch",
                    driverPhone = "+91 98220-44102",
                    challanNo = "DC-${System.currentTimeMillis() % 10000}",
                    ewbNo = "EWB-${System.currentTimeMillis() % 1000000}",
                    destinationSite = "Central Industrial Area Bay 2",
                    totalBags = bags,
                    totalMetricTons = mt,
                    totalAmount = amount,
                    items = listOf(item)
                )

                val result = runBlocking { repository.recordDispatch(tx) }
                if (result.isSuccess) {
                    response.put("success", true)
                    response.put("slipNo", slipNo)
                    vibrate(40)
                } else {
                    response.put("success", false)
                    response.put("error", result.exceptionOrNull()?.message ?: "Dispatch failed")
                }
            } catch (e: Exception) {
                response.put("success", false)
                response.put("error", e.message ?: "Unknown error")
            }
            return response.toString()
        }

        @JavascriptInterface
        fun recordDispatch(
            partyName: String,
            vehicleNo: String,
            driverName: String,
            driverPhone: String,
            site: String,
            challanNo: String,
            ewbNo: String,
            itemsJson: String
        ): String {
            val response = JSONObject()
            try {
                val itemsArray = JSONArray(itemsJson)
                if (itemsArray.length() == 0) {
                    response.put("success", false)
                    response.put("error", "No items added to dispatch")
                    return response.toString()
                }

                val products = repository.getCurrentProducts()
                val txItems = mutableListOf<StockTransactionItem>()
                var totalBags = 0
                var totalMt = 0.0
                var totalAmount = 0.0

                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val pId = itemObj.getString("productId")
                    val bags = itemObj.getInt("bags")
                    if (bags <= 0) continue

                    val p = products.find { it.id == pId }
                    if (p == null) {
                        response.put("success", false)
                        response.put("error", "Product $pId not found")
                        return response.toString()
                    }

                    if (p.currentStockBags < bags) {
                        response.put("success", false)
                        response.put("error", "Insufficient stock for ${p.name}: Available ${p.currentStockBags}, Requested $bags")
                        return response.toString()
                    }

                    val lineMt = (bags * p.weightPerBagKg) / 1000.0
                    val lineAmount = bags * p.defaultRatePerBag

                    totalBags += bags
                    totalMt += lineMt
                    totalAmount += lineAmount

                    txItems.add(
                        StockTransactionItem(
                            productId = p.id,
                            productName = p.name,
                            quantityBags = bags,
                            metricTons = lineMt,
                            ratePerBag = p.defaultRatePerBag,
                            batchNo = p.batchNo,
                            bayLocation = p.bayLocation
                        )
                    )
                }

                if (txItems.isEmpty()) {
                    response.put("success", false)
                    response.put("error", "Total quantity must be greater than zero")
                    return response.toString()
                }

                val slipNo = "GP-${System.currentTimeMillis() % 100000}"
                val tx = StockTransaction(
                    id = "tx_${System.currentTimeMillis()}",
                    slipNo = slipNo,
                    type = TransactionType.OUTWARD,
                    timestamp = System.currentTimeMillis(),
                    partyName = if (partyName.isNotBlank()) partyName else "Direct Walk-in Contractor",
                    vehicleNo = if (vehicleNo.isNotBlank()) vehicleNo else "MH-12-QZ-4891",
                    driverName = if (driverName.isNotBlank()) driverName else "Depot Driver",
                    driverPhone = if (driverPhone.isNotBlank()) driverPhone else "-",
                    challanNo = if (challanNo.isNotBlank()) challanNo else "DC-${System.currentTimeMillis() % 10000}",
                    ewbNo = if (ewbNo.isNotBlank()) ewbNo else "-",
                    destinationSite = if (site.isNotBlank()) site else "Site Delivery",
                    totalBags = totalBags,
                    totalMetricTons = totalMt,
                    totalAmount = totalAmount,
                    items = txItems
                )

                val result = runBlocking { repository.recordDispatch(tx) }
                if (result.isSuccess) {
                    response.put("success", true)
                    response.put("slipNo", slipNo)
                    vibrate(40)
                } else {
                    response.put("success", false)
                    response.put("error", result.exceptionOrNull()?.message ?: "Dispatch failed")
                }
            } catch (e: Exception) {
                response.put("success", false)
                response.put("error", e.message ?: "Unknown error")
            }
            return response.toString()
        }

        @JavascriptInterface
        fun recordStockIn(productId: String, bags: Int, batchNo: String, bayLocation: String): String {
            val response = JSONObject()
            try {
                if (bags <= 0) {
                    response.put("success", false)
                    response.put("error", "Stock-in bags must be positive")
                    return response.toString()
                }

                val result = runBlocking { repository.recordStockIn(productId, bags, batchNo, bayLocation) }
                if (result.isSuccess) {
                    response.put("success", true)
                    vibrate(40)
                } else {
                    response.put("success", false)
                    response.put("error", result.exceptionOrNull()?.message ?: "Stock in failed")
                }
            } catch (e: Exception) {
                response.put("success", false)
                response.put("error", e.message ?: "Unknown error")
            }
            return response.toString()
        }

        @JavascriptInterface
        fun addProduct(jsonStr: String): String {
            val response = JSONObject()
            try {
                val obj = JSONObject(jsonStr)
                val name = obj.getString("name")
                val defaultRatePerBag = obj.getDouble("defaultRatePerBag")
                val currentStockBags = obj.getInt("currentStockBags")
                
                val newId = name.lowercase().replace("\\s+".toRegex(), "_") + "_" + System.currentTimeMillis()
                
                val newProduct = CementProduct(
                    id = newId,
                    name = name,
                    grade = "Custom",
                    weightPerBagKg = 50.0,
                    defaultRatePerBag = defaultRatePerBag,
                    bayLocation = "Unassigned",
                    currentStockBags = currentStockBags,
                    batchNo = "NEW-BATCH",
                    imageUrl = null
                )
                val result = runBlocking { repository.addProduct(newProduct) }
                if (result.isSuccess) {
                    response.put("success", true)
                } else {
                    response.put("success", false)
                    response.put("error", result.exceptionOrNull()?.message ?: "Add failed")
                }
            } catch (e: Exception) {
                response.put("success", false)
                response.put("error", e.message ?: "Unknown error")
            }
            return response.toString()
        }

        @JavascriptInterface
        fun updateProduct(jsonStr: String): String {
            val response = JSONObject()
            try {
                val obj = JSONObject(jsonStr)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val rate = obj.getDouble("defaultRatePerBag")
                val imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.getString("imageUrl").takeIf { it.isNotBlank() } else null
                
                val products = repository.getCurrentProducts()
                val existing = products.find { it.id == id }
                if (existing != null) {
                    val updated = existing.copy(
                        name = name,
                        defaultRatePerBag = rate,
                        imageUrl = imageUrl ?: existing.imageUrl,
                        isActive = if (obj.has("isActive")) obj.getBoolean("isActive") else existing.isActive
                    )
                    val result = runBlocking { repository.updateProduct(updated) }
                    if (result.isSuccess) {
                        response.put("success", true)
                    } else {
                        response.put("success", false)
                        response.put("error", result.exceptionOrNull()?.message ?: "Update failed")
                    }
                } else {
                    response.put("success", false)
                    response.put("error", "Product not found")
                }
            } catch (e: Exception) {
                response.put("success", false)
                response.put("error", e.message ?: "Unknown error")
            }
            return response.toString()
        }

        @JavascriptInterface
        fun getSlipText(slipNo: String, paperWidthMm: Int): String {
            return try {
                val tx = runBlocking {
                    if (slipNo.isNotBlank()) {
                        repository.getTransactionBySlipNo(slipNo) ?: repository.getLastSlip()
                    } else {
                        repository.getLastSlip()
                    }
                }
                if (tx != null) {
                    EscPosSlipGenerator.generatePreviewText(tx, if (paperWidthMm == 58) 58 else 80)
                } else {
                    "No transaction available."
                }
            } catch (e: Exception) {
                "Error generating slip: ${e.message}"
            }
        }
        @JavascriptInterface
        fun getSlipTextWithOptions(slipNo: String, paperWidthMm: Int, optionsJson: String): String {
            return try {
                val tx = runBlocking {
                    if (slipNo.isNotBlank()) {
                        repository.getTransactionBySlipNo(slipNo) ?: repository.getLastSlip()
                    } else {
                        repository.getLastSlip()
                    }
                }
                if (tx != null) {
                    val opts = com.example.bhpos.printer.PrintOptions.fromJson(optionsJson)
                    EscPosSlipGenerator.generatePreviewText(tx, if (paperWidthMm == 58) 58 else 80, opts)
                } else {
                    "No transaction available."
                }
            } catch (e: Exception) {
                "Error generating slip: ${e.message}"
            }
        }

        @JavascriptInterface
        fun savePrintOptions(jsonStr: String) {
            // Save logic if needed
        }

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

        @JavascriptInterface
        fun printSlip(slipNo: String, paperWidthMm: Int): String {
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

                val escBytes = EscPosSlipGenerator.generateEscPosBytes(tx, if (paperWidthMm == 58) 58 else 80)
                val printedBt = tryBluetoothPrint(escBytes)

                vibrate(100)
                showToast(if (printedBt) "Thermal Slip printed via Bluetooth!" else "Slip ${tx.slipNo} dispatched to thermal spooler")

                response.put("success", true)
                response.put("printedViaBluetooth", printedBt)
                response.put("slipNo", tx.slipNo)
            } catch (e: Exception) {
                response.put("success", false)
                response.put("error", e.message ?: "Print failed")
            }
            return response.toString()
        }

        @JavascriptInterface
        fun getLastSlipNo(): String {
            return try {
                val tx = runBlocking { repository.getLastSlip() }
                tx?.slipNo ?: "GP-9485"
            } catch (e: Exception) {
                "GP-9485"
            }
        }

        @JavascriptInterface
        fun vibrate(durationMs: Long) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs.coerceAtLeast(15), VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs.coerceAtLeast(15))
                }
            } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        private fun tryBluetoothPrint(bytes: ByteArray): Boolean {
            return try {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return false
                if (!adapter.isEnabled) return false
                
                var printer: android.bluetooth.BluetoothDevice? = null
                val savedAddr = getSavedPrinterAddress()
                
                if (savedAddr.isNotBlank()) {
                    try {
                        printer = adapter.getRemoteDevice(savedAddr)
                    } catch (e: Exception) {}
                }
                
                if (printer == null) {
                    val bonded = adapter.bondedDevices ?: return false
                    printer = bonded.firstOrNull { d ->
                        val name = (d.name ?: "").lowercase(Locale.ROOT)
                        name.contains("printer") || name.contains("pos") || name.contains("rp") || name.contains("thermal") || name.contains("bt")
                    } ?: bonded.firstOrNull() ?: return false
                }

                val uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val socket = printer.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                val os = socket.outputStream
                os.write(bytes)
                os.flush()
                socket.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

