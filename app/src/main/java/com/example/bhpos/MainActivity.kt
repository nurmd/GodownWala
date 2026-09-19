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
import com.example.bhpos.domain.model.Product
import com.example.bhpos.domain.model.StockTransaction
import com.example.bhpos.domain.model.StockTransactionItem
import com.example.bhpos.domain.model.TransactionType
import com.example.bhpos.printer.BluetoothPrinterManager
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
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Handles incoming test intents and JS evaluation commands from ADB or test scripts.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val js = intent.getStringExtra("eval_js")
        if (!js.isNullOrBlank()) {
            runOnUiThread {
                webView.evaluateJavascript(js, null)
            }
        }

        val testPrint = intent.getBooleanExtra("test_print", false)
        if (testPrint) {
            val name = intent.getStringExtra("printer_name") ?: ""
            val addr = intent.getStringExtra("printer_addr") ?: ""
            val width = intent.getIntExtra("paper_width", 58)
            Thread {
                android.util.Log.d("BH_INTENT_PRINT", "Triggering test print: name='$name', addr='$addr', width=$width")
                val res = AndroidBridge().testPrintPrinter(name, addr, width)
                android.util.Log.d("BH_INTENT_PRINT", "Result: $res")
            }.start()
        }
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

        try {
            WebView.setWebContentsDebuggingEnabled(true)
        } catch (_: Exception) {}

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                android.util.Log.d("BH_CONSOLE", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }
        }

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
        private const val KEY_CLOUD_URL = "cloud_sync_url"
        private const val KEY_CURRENT_USER = "current_user_json"
        private const val PERM_REQUEST_CODE = 2001
    }

    fun getCloudUrl(): String {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CLOUD_URL, "") ?: ""
    }

    fun saveCloudUrl(url: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CLOUD_URL, url).apply()
    }

    fun getCurrentUser(): String {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CURRENT_USER, "") ?: ""
    }

    fun saveCurrentUser(json: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CURRENT_USER, json).apply()
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
        return sp.getString(KEY_PRINTER_NAME, "MPT-III") ?: "MPT-III"
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
        private fun getCurrentUserName(): String {
            val userStr = this@MainActivity.getCurrentUser()
            return if (userStr.isNotBlank()) {
                try { JSONObject(userStr).getString("name") } catch (e: Exception) { "Unknown" }
            } else {
                "Unknown"
            }
        }
        @JavascriptInterface
        fun getCloudUrl(): String {
            return this@MainActivity.getCloudUrl()
        }

        @JavascriptInterface
        fun saveCloudUrl(url: String) {
            this@MainActivity.saveCloudUrl(url)
        }

        @JavascriptInterface
        fun getCurrentUser(): String {
            return this@MainActivity.getCurrentUser()
        }

        @JavascriptInterface
        fun saveCurrentUser(json: String) {
            this@MainActivity.saveCurrentUser(json)
        }

        @JavascriptInterface
        fun syncCloudData(payload: String, callbackId: String) {
            val urlStr = this@MainActivity.getCloudUrl()
            if (urlStr.isBlank()) {
                runOnUiThread {
                    webView.evaluateJavascript("if(window.cloudSyncCallback) window.cloudSyncCallback('$callbackId', false, 'No URL set');", null)
                }
                return
            }

            Thread {
                try {
                    val url = java.net.URL(urlStr)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; utf-8")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.doOutput = true

                    conn.outputStream.use { os ->
                        val input = payload.toByteArray(Charsets.UTF_8)
                        os.write(input, 0, input.size)
                    }

                    val code = conn.responseCode
                    if (code in 200..299) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        runOnUiThread {
                            webView.evaluateJavascript("if(window.cloudSyncCallback) window.cloudSyncCallback('$callbackId', true, ${JSONObject.quote(response)});", null)
                        }
                    } else {
                        val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                        runOnUiThread {
                            webView.evaluateJavascript("if(window.cloudSyncCallback) window.cloudSyncCallback('$callbackId', false, ${JSONObject.quote(error)});", null)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        webView.evaluateJavascript("if(window.cloudSyncCallback) window.cloudSyncCallback('$callbackId', false, ${JSONObject.quote(e.message ?: "Network error")});", null)
                    }
                }
            }.start()
        }

        private val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

        @JavascriptInterface
        fun getDashboardTelemetry(): String {
            return try {
                val products = repository.getCurrentProducts()
                val totalUnits = products.filter { it.isActive }.sumOf { it.currentStockBags }
                val activeCount = products.count { it.isActive }
                val totalMt = (totalUnits * 50.0) / 1000.0

                val txs = repository.getCurrentTransactions()
                var inwardUnits = 0
                var dispatchedUnits = 0
                val now = System.currentTimeMillis()
                val dayStart = now - (24 * 60 * 60 * 1000)
                for (tx in txs) {
                    if (tx.timestamp >= dayStart) {
                        if (tx.type == com.example.bhpos.domain.model.TransactionType.INWARD) {
                            inwardUnits += tx.totalBags
                        } else if (tx.type == com.example.bhpos.domain.model.TransactionType.OUTWARD) {
                            dispatchedUnits += tx.totalBags
                        }
                    }
                }
                if (inwardUnits == 0 && txs.isEmpty()) inwardUnits = 1200
                if (dispatchedUnits == 0 && txs.isEmpty()) dispatchedUnits = 850

                val root = JSONObject()
                root.put("totalStoredBags", totalUnits)
                root.put("totalUnits", totalUnits)
                root.put("activeProductsCount", activeCount)
                root.put("totalMetricTons", totalMt)
                root.put("inwardDayBags", inwardUnits)
                root.put("inwardDayUnits", inwardUnits)
                root.put("inwardDayMt", (inwardUnits * 50.0) / 1000.0)
                root.put("dispatchedBags", dispatchedUnits)
                root.put("dispatchedUnits", dispatchedUnits)
                root.put("dispatchedMt", (dispatchedUnits * 50.0) / 1000.0)
                root.put("pendingSlipsCount", if (txs.isNotEmpty()) txs.count { it.type == com.example.bhpos.domain.model.TransactionType.OUTWARD } else 1)
                root.put("netTallyBags", inwardUnits - dispatchedUnits)

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
                    obj.put("unit", p.unit)
                    obj.put("weightPerBagKg", p.weightPerBagKg)
                    obj.put("defaultRatePerBag", p.defaultRatePerBag)
                    obj.put("bayLocation", p.bayLocation)
                    obj.put("currentStockBags", p.currentStockBags)
                    obj.put("batchNo", p.batchNo)
                    obj.put("isActive", p.isActive)
                    if (p.imageUrl != null) {
                        obj.put("imageUrl", p.imageUrl)
                    }
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
        fun setProducts(jsonStr: String): String {
            return try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<Product>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        Product(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            grade = obj.optString("grade", "Standard"),
                            weightPerBagKg = obj.optDouble("weightPerBagKg", 50.0),
                            defaultRatePerBag = obj.optDouble("defaultRatePerBag", 0.0),
                            bayLocation = obj.optString("bayLocation", "Unassigned"),
                            currentStockBags = obj.optInt("currentStockBags", 0),
                            batchNo = obj.optString("batchNo", "-"),
                            imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.getString("imageUrl").takeIf { it.isNotBlank() } else null,
                            isActive = obj.optBoolean("isActive", true),
                            unit = obj.optString("unit", obj.optString("grade", "Units"))
                        )
                    )
                }
                repository.setProducts(list)
                "ok"
            } catch (e: Exception) {
                "error: ${e.message}"
            }
        }

        @JavascriptInterface
        fun setParties(jsonStr: String): String {
            return try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<com.example.bhpos.domain.model.Party>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        com.example.bhpos.domain.model.Party(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            gstin = obj.optString("gstin", "UNREGISTERED"),
                            phone = obj.optString("phone", "-"),
                            defaultDestination = obj.optString("defaultDestination", "Site Delivery"),
                            accountNo = obj.optString("accountNo", "ACC-000")
                        )
                    )
                }
                repository.setParties(list)
                "ok"
            } catch (e: Exception) {
                "error: ${e.message}"
            }
        }

        @JavascriptInterface
        fun setTransactions(jsonStr: String): String {
            return try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<StockTransaction>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    
                    val itemsArray = obj.optJSONArray("items") ?: JSONArray()
                    val txItems = mutableListOf<StockTransactionItem>()
                    for (j in 0 until itemsArray.length()) {
                        val itemObj = itemsArray.getJSONObject(j)
                        txItems.add(
                            StockTransactionItem(
                                productId = itemObj.optString("productId", ""),
                                productName = itemObj.optString("productName", ""),
                                quantityBags = itemObj.optInt("quantityBags", itemObj.optInt("bags", 0)),
                                metricTons = itemObj.optDouble("metricTons", 0.0),
                                ratePerBag = itemObj.optDouble("ratePerBag", 0.0),
                                batchNo = itemObj.optString("batchNo", ""),
                                bayLocation = itemObj.optString("bayLocation", "")
                            )
                        )
                    }

                    list.add(
                        StockTransaction(
                            id = obj.getString("id"),
                            slipNo = obj.getString("slipNo"),
                            type = try { TransactionType.valueOf(obj.getString("type")) } catch (e: Exception) { TransactionType.OUTWARD },
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            partyName = obj.optString("partyName", "Direct Walk-in Contractor"),
                            vehicleNo = obj.optString("vehicleNo", ""),
                            driverName = obj.optString("driverName", ""),
                            driverPhone = obj.optString("driverPhone", ""),
                            challanNo = obj.optString("challanNo", ""),
                            ewbNo = obj.optString("ewbNo", ""),
                            destinationSite = obj.optString("destinationSite", ""),
                            totalBags = obj.optInt("totalBags", 0),
                            totalMetricTons = obj.optDouble("totalMetricTons", 0.0),
                            totalAmount = obj.optDouble("totalAmount", 0.0),
                            items = txItems,
                            dispatchedBy = obj.optString("dispatchedBy", "Admin")
                        )
                    )
                }
                repository.setTransactions(list)
                "ok"
            } catch (e: Exception) {
                e.printStackTrace()
                "error: ${e.message}"
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
                var product = products.find { it.id == productId }
                if (product == null) {
                    product = products.find { it.name.equals(productId, ignoreCase = true) }
                }
                if (product == null) {
                    val fallback = Product(
                        id = productId,
                        name = "Product $productId",
                        grade = "Standard",
                        weightPerBagKg = 50.0,
                        defaultRatePerBag = 0.0,
                        bayLocation = "Unassigned",
                        currentStockBags = bags.coerceAtLeast(100),
                        batchNo = "AUTO",
                        imageUrl = null
                    )
                    runBlocking { repository.addProduct(fallback) }
                    product = fallback
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
                    items = listOf(item),
                    dispatchedBy = getCurrentUserName()
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
        fun updateDispatch(
            slipNo: String,
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

                    var p = products.find { it.id == pId }
                    if (p == null) p = products.find { it.name.equals(pId, ignoreCase = true) }
                    if (p == null) {
                        val fallback = Product(
                            id = pId, name = "Product $pId", grade = "Standard", weightPerBagKg = 50.0,
                            defaultRatePerBag = 0.0, bayLocation = "Unassigned", currentStockBags = bags.coerceAtLeast(100),
                            batchNo = "AUTO", imageUrl = null
                        )
                        runBlocking { repository.addProduct(fallback) }
                        p = fallback
                    }

                    val lineMt = (bags * p.weightPerBagKg) / 1000.0
                    val lineAmount = bags * p.defaultRatePerBag

                    totalBags += bags
                    totalMt += lineMt
                    totalAmount += lineAmount

                    txItems.add(
                        StockTransactionItem(
                            productId = p.id, productName = p.name, quantityBags = bags,
                            metricTons = lineMt, ratePerBag = p.defaultRatePerBag, batchNo = p.batchNo, bayLocation = p.bayLocation
                        )
                    )
                }

                if (txItems.isEmpty()) {
                    response.put("success", false)
                    response.put("error", "Total quantity must be greater than zero")
                    return response.toString()
                }

                val existingTx = runBlocking { repository.getTransactionBySlipNo(slipNo) }
                if (existingTx == null) {
                    response.put("success", false)
                    response.put("error", "Slip not found")
                    return response.toString()
                }

                val tx = existingTx.copy(
                    partyName = if (partyName.isNotBlank()) partyName else "Direct Walk-in Contractor",
                    vehicleNo = if (vehicleNo.isNotBlank()) vehicleNo else "MH-12-QZ-4891",
                    driverName = if (driverName.isNotBlank()) driverName else "Depot Driver",
                    driverPhone = if (driverPhone.isNotBlank()) driverPhone else "-",
                    challanNo = if (challanNo.isNotBlank()) challanNo else existingTx.challanNo,
                    ewbNo = if (ewbNo.isNotBlank()) ewbNo else "-",
                    destinationSite = if (site.isNotBlank()) site else "Site Delivery",
                    totalBags = totalBags,
                    totalMetricTons = totalMt,
                    totalAmount = totalAmount,
                    items = txItems
                )

                val result = runBlocking { repository.updateDispatch(tx) }
                if (result.isSuccess) {
                    response.put("success", true)
                    response.put("slipNo", tx.slipNo)
                } else {
                    response.put("success", false)
                    response.put("error", result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                response.put("success", false)
                response.put("error", e.message)
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

                    var p = products.find { it.id == pId }
                    if (p == null) {
                        p = products.find { it.name.equals(pId, ignoreCase = true) }
                    }
                    if (p == null) {
                        val fallback = Product(
                            id = pId,
                            name = "Product $pId",
                            grade = "Standard",
                            weightPerBagKg = 50.0,
                            defaultRatePerBag = 0.0,
                            bayLocation = "Unassigned",
                            currentStockBags = bags.coerceAtLeast(100),
                            batchNo = "AUTO",
                            imageUrl = null
                        )
                        runBlocking { repository.addProduct(fallback) }
                        p = fallback
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
                    items = txItems,
                    dispatchedBy = getCurrentUserName()
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
                val id = if (obj.has("id") && obj.getString("id").isNotBlank()) {
                    obj.getString("id")
                } else {
                    val name = obj.getString("name")
                    name.lowercase().replace("\\s+".toRegex(), "_") + "_" + System.currentTimeMillis()
                }
                val name = obj.getString("name")
                val defaultRatePerBag = obj.optDouble("defaultRatePerBag", 0.0)
                val currentStockBags = obj.optInt("currentStockBags", 0)
                val weight = obj.optDouble("weightPerBagKg", 50.0)
                val bay = obj.optString("bayLocation", "Unassigned")
                val batch = obj.optString("batchNo", "NEW")
                val imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.getString("imageUrl").takeIf { it.isNotBlank() } else null
                val isActive = obj.optBoolean("isActive", true)
                val unit = obj.optString("unit", obj.optString("grade", "Units"))
                
                val newProduct = Product(
                    id = id,
                    name = name,
                    grade = unit,
                    weightPerBagKg = weight,
                    defaultRatePerBag = defaultRatePerBag,
                    bayLocation = bay,
                    currentStockBags = currentStockBags,
                    batchNo = batch,
                    imageUrl = imageUrl,
                    isActive = isActive,
                    unit = unit
                )
                val result = runBlocking { repository.addProduct(newProduct) }
                if (result.isSuccess) {
                    response.put("success", true)
                    response.put("id", id)
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
                val unit = if (obj.has("unit")) obj.getString("unit") else null
                
                val products = repository.getCurrentProducts()
                val existing = products.find { it.id == id }
                if (existing != null) {
                    val finalUnit = unit ?: existing.unit
                    val updated = existing.copy(
                        name = name,
                        defaultRatePerBag = rate,
                        imageUrl = imageUrl ?: existing.imageUrl,
                        isActive = if (obj.has("isActive")) obj.getBoolean("isActive") else existing.isActive,
                        unit = finalUnit,
                        grade = finalUnit
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

                    val isLikelyPrinter = BluetoothPrinterManager.isLikelyPrinter(dev)
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

        /**
         * Sends an ESC/POS self-test receipt to a targeted or default Bluetooth printer.
         *
         * Futureproofing rules:
         * 1. Uses [EscPosSlipGenerator.generateTestSlipBytes] for valid ESC/POS commands (feed & cut).
         * 2. Always targets [address] if passed; otherwise falls back to the saved printer in SharedPreferences.
         * 3. Automatically persists newly verified printer connections to [saveActivePrinter].
         */
        @JavascriptInterface
        fun testPrintPrinter(name: String, address: String, paperWidthMm: Int): String {
            val response = JSONObject()
            try {
                val isSpecificTarget = address.isNotBlank()
                val targetAddr = if (isSpecificTarget) address else getSavedPrinterAddress()
                val targetName = if (isSpecificTarget) name else getSavedPrinterName()
                val width = if (paperWidthMm == 58) 58 else 80
                val allowFailover = !isSpecificTarget

                val result = BluetoothPrinterManager.print(
                    targetAddress = targetAddr,
                    fallbackAddress = getSavedPrinterAddress(),
                    allowFailover = allowFailover,
                    payloadSupplier = { connectedDevice, isFailover ->
                        val devName = connectedDevice.name ?: targetName
                        val devAddr = connectedDevice.address ?: targetAddr
                        com.example.bhpos.printer.EscPosSlipGenerator.generateTestSlipBytes(devName, devAddr, width, isFailover)
                    }
                )
                
                vibrate(60)
                if (result.success && !result.deviceAddress.isNullOrBlank()) {
                    saveActivePrinter(result.deviceName ?: targetName, result.deviceAddress)
                    if (result.isFailover) {
                        runOnUiThread {
                            val newName = result.deviceName ?: "Thermal Printer"
                            showToast("Primary printer offline. Routed to $newName!")
                            val script = "window.onActivePrinterChanged && window.onActivePrinterChanged(${JSONObject.quote(newName)}, ${JSONObject.quote(result.deviceAddress)}, true);"
                            webView.evaluateJavascript(script, null)
                        }
                    }
                } else if (!result.success) {
                    runOnUiThread {
                        val errMsg = result.errorMessage ?: "Printer $targetName is offline."
                        showToast(errMsg)
                    }
                }
                
                response.put("success", result.success)
                val msg = when {
                    result.success && result.isFailover -> "Sent to ${result.deviceName} (active online printer)!"
                    result.success -> "Test slip printed via Bluetooth on ${result.deviceName ?: targetName}!"
                    else -> result.errorMessage ?: "Could not connect to $targetName. Ensure it is turned on and paired."
                }
                response.put("message", msg)
                response.put("isFailover", result.isFailover)
                response.put("printedDevice", result.deviceName ?: "")
            } catch (e: Exception) {
                response.put("success", false)
                response.put("message", e.message ?: "Test print failed")
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
                val result = tryBluetoothPrint(escBytes)
                val printedBt = result.success

                vibrate(100)
                if (printedBt) {
                    if (result.isFailover) {
                        showToast("Printed via Bluetooth on ${result.deviceName}!")
                    } else {
                        showToast("Thermal Slip printed via Bluetooth!")
                    }
                } else {
                    showToast("Slip ${tx.slipNo} spooled (${result.errorMessage ?: "Printer offline"})")
                }

                response.put("success", true)
                response.put("printedViaBluetooth", printedBt)
                response.put("printedDevice", result.deviceName ?: "")
                response.put("isFailover", result.isFailover)
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
                val result = tryBluetoothPrint(escBytes)
                val printedBt = result.success

                vibrate(100)
                showToast(if (printedBt) "Thermal Slip printed via Bluetooth on ${result.deviceName ?: "printer"}!" else "Slip ${tx.slipNo} dispatched to thermal spooler")

                response.put("success", true)
                response.put("printedViaBluetooth", printedBt)
                response.put("printedDevice", result.deviceName ?: "")
                response.put("isFailover", result.isFailover)
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

        /**
         * ============================================================================
         * THERMAL BLUETOOTH PRINT EXECUTION WITH OFFLINE FAILOVER
         * ============================================================================
         * Delegates raw ESC/POS byte streaming to [BluetoothPrinterManager.print].
         * Automatically falls back to secondary active thermal printers (e.g. MPT-III)
         * when the primary configured printer (e.g. SR588) is offline.
         */
        private fun tryBluetoothPrint(bytes: ByteArray, targetAddress: String? = null): BluetoothPrinterManager.PrintResult {
            val result = BluetoothPrinterManager.print(bytes, targetAddress, getSavedPrinterAddress())
            if (result.success) {
                if (result.isFailover && !result.deviceAddress.isNullOrBlank()) {
                    val newName = result.deviceName ?: "Thermal Printer"
                    saveActivePrinter(newName, result.deviceAddress)
                    runOnUiThread {
                        showToast("Primary printer offline. Routed to $newName!")
                        val script = "window.onActivePrinterChanged && window.onActivePrinterChanged(${JSONObject.quote(newName)}, ${JSONObject.quote(result.deviceAddress)}, true);"
                        webView.evaluateJavascript(script, null)
                    }
                }
            } else {
                runOnUiThread {
                    showToast(result.errorMessage ?: "Printer offline. Check power & pairing.")
                    val script = "window.onPrinterOffline && window.onPrinterOffline(${JSONObject.quote(getSavedPrinterName())}, ${JSONObject.quote(getSavedPrinterAddress())});"
                    webView.evaluateJavascript(script, null)
                }
            }
            return result
        }
    }
}

