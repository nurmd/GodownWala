Created At: 2026-09-17T11:47:13Z
Completed At: 2026-09-17T11:47:13Z
File Path: `file:///data/data/com.termux/files/home/bhpos/app/src/main/java/com/example/bhpos/MainActivity.kt`
Total Lines: 1005
Total Bytes: 41871
Showing lines 1 to 800
The following code has been modified to include a line number before every line, in the format: <line_number>: <original_line>. Please note that any changes targeting the original code should remove the line number, colon, and leading space.
package com.example.bhpos

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.bhpos.data.repository.CementStockRepositoryImpl
import com.example.bhpos.domain.model.StockTransaction
import com.example.bhpos.domain.model.StockTransactionItem
import com.example.bhpos.domain.model.TransactionType
import com.example.bhpos.printer.EscPosSlipGenerator
import com.example.bhpos.printer.PrintOptions
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private val repository = CementStockRepositoryImpl.instance
    private var vibrator: Vibrator? = null

    private val discoveredDevices = ConcurrentHashMap<String, String>()
    private var isScanning = false

    companion object {
        private const val PREFS_NAME = \"bhpos_bluetooth_prefs\"
        private const val KEY_PRINTER_NAME = \"active_printer_name\"
        private const val KEY_PRINTER_ADDR = \"active_printer_address\"
        private const val KEY_PRINT_OPTIONS = \"active_print_options\"
        private const val PERM_REQUEST_CODE = 2001
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        val name = try {
                            device.name ?: \"Unnamed Device\"
                        } catch (_: SecurityException) {
                            \"Bluetooth Device\"
                        }
                        val addr = device.address ?: \"\"
                        if (addr.isNotBlank()) {
                            discoveredDevices[addr] = name
                            runOnUiThread {
                                val script = \"window.onBluetoothDeviceDiscovered && window.onBluetoothDeviceDiscovered(${JSONObject.quote(name)}, ${JSONObject.quote(addr)});\"
                                webView.evaluateJavascript(script, null)
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning = false
                    runOnUiThread {
                        webView.evaluateJavascript(\"window.onBluetoothScanFinished && window.onBluetoothScanFinished();\", null)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(discoveryReceiver, filter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            window.statusBarColor = android.graphics.Color.parseColor(\"#F4FAFF\")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress(\"DEPRECATION\")
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        val rootLayout = android.widget.FrameLayout(this)
        rootLayout.setBackgroundColor(android.graphics.Color.parseColor(\"#F4FAFF\"))

        val sbHeight = getStatusBarHeight()
        rootLayout.setPadding(0, sbHeight, 0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            rootLayout.setOnApplyWindowInsetsListener { view, insets ->
                val top = insets.systemWindowInsetTop.coerceAtLeast(sbHeight)
                view.setPadding(0, top, 0, 0)
                insets.consumeSystemWindowInsets()
            }
        }

        webView = WebView(this)
        rootLayout.addView(
            webView,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(rootLayout)

        setupWebView()
        webView.loadUrl(\"file:///android_asset/index.html\")
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier(\"status_bar_height\", \"dimen\", \"android\")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
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

        webView.addJavascriptInterface(AndroidBridge(), \"AndroidBridge\")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(discoveryReceiver)
        } catch (_: Exception) {}
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        webView.evaluateJavascript(\"window.onBluetoothPermissionsResult && window.onBluetoothPermissionsResult($allGranted);\", null)
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(\"android.permission.BLUETOOTH_CONNECT\") == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(\"android.permission.BLUETOOTH_SCAN\") == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestBluetoothPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            perms.add(\"android.permission.BLUETOOTH_CONNECT\")
            perms.add(\"android.permission.BLUETOOTH_SCAN\")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            perms.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (perms.isNotEmpty()) {
            requestPermissions(perms.toTypedArray(), PERM_REQUEST_CODE)
        }
    }

    fun getSavedPrinterName(): String {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_PRINTER_NAME, \"POS-80C Mobile Thermal\") ?: \"POS-80C Mobile Thermal\"
    }

    fun getSavedPrinterAddress(): String {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_PRINTER_ADDR, \"\") ?: \"\"
    }

    fun saveActivePrinter(name: String, address: String) {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_PRINTER_NAME, name).putString(KEY_PRINTER_ADDR, address).apply()
    }

    fun getSavedPrintOptions(): String {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_PRINT_OPTIONS, \"{}\") ?: \"{}\"
    }

    fun savePrintOptions(json: String) {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_PRINT_OPTIONS, json).apply()
    }


    inner class AndroidBridge {

        private val dateFormat = SimpleDateFormat(\"dd-MMM-yyyy\", Locale.ENGLISH)
        private val timeFormat = SimpleDateFormat(\"hh:mm a\", Locale.ENGLISH)

        @JavascriptInterface
        fun getDashboardTelemetry(): String {
            return try {
                val products = repository.getCurrentProducts()
                val totalBags = products.sumOf { it.currentStockBags }
                val totalMt = (totalBags * 50.0) / 1000.0

                val root = JSONObject()
                root.put(\"totalStoredBags\", totalBags)
                root.put(\"totalMetricTons\", totalMt)
                root.put(\"inwardDayBags\", 1200)
                root.put(\"inwardDayMt\", 60.0)
                root.put(\"dispatchedBags\", 850)
                root.put(\"dispatchedMt\", 42.5)
                root.put(\"pendingSlipsCount\", 1)
                root.put(\"netTallyBags\", 350)

                val brandDist = JSONArray()
                for (p in products) {
                    val bObj = JSONObject()
                    val pct = if (totalBags > 0) (p.currentStockBags.toDouble() / totalBags.toDouble()) * 100.0 else 0.0
                    bObj.put(\"brandName\", p.brandName)
                    bObj.put(\"productName\", p.name)
                    bObj.put(\"percentage\", pct)
                    bObj.put(\"bags\", p.currentStockBags)
                    brandDist.put(bObj)
                }
                root.put(\"brandDistribution\", brandDist)

                root.toString()
            } catch (e: Exception) {
                \"{}\"
            }
        }

        @JavascriptInterface
        fun getProducts(): String {
            return try {
                val list = repository.getCurrentProducts()
                val array = JSONArray()
                for (p in list) {
                    val obj = JSONObject()
                    obj.put(\"id\", p.id)
                    obj.put(\"brandName\", p.brandName)
                    obj.put(\"name\", p.name)
                    obj.put(\"grade\", p.grade)
                    obj.put(\"category\", p.category)
                    obj.put(\"weightPerBagKg\", p.weightPerBagKg)
                    obj.put(\"defaultRatePerBag\", p.defaultRatePerBag)
                    obj.put(\"bayLocation\", p.bayLocation)
                    obj.put(\"currentStockBags\", p.currentStockBags)
                    obj.put(\"batchNo\", p.batchNo)
                    array.put(obj)
                }
                array.toString()
            } catch (e: Exception) {
                \"[]\"
            }
        }

        @JavascriptInterface
        fun getParties(): String {
            return try {
                val list = repository.getParties()
                val array = JSONArray()
                for (p in list) {
                    val obj = JSONObject()
                    obj.put(\"id\", p.id)
                    obj.put(\"name\", p.name)
                    obj.put(\"gstin\", p.gstin)
                    obj.put(\"phone\", p.phone)
                    obj.put(\"defaultDestination\", p.defaultDestination)
                    obj.put(\"accountNo\", p.accountNo)
                    array.put(obj)
                }
                array.toString()
            } catch (e: Exception) {
                \"[]\"
            }
        }

        @JavascriptInterface
        fun getTransactions(): String {
            return try {
                val list = repository.getCurrentTransactions()
                val array = JSONArray()
                for (t in list) {
                    val obj = JSONObject()
                    obj.put(\"id\", t.id)
                    obj.put(\"slipNo\", t.slipNo)
                    obj.put(\"type\", t.type.name)
                    obj.put(\"timestamp\", t.timestamp)
                    obj.put(\"dateStr\", dateFormat.format(Date(t.timestamp)))
                    obj.put(\"timeStr\", timeFormat.format(Date(t.timestamp)))
                    obj.put(\"partyName\", t.partyName)
                    obj.put(\"vehicleNo\", t.vehicleNo)
                    obj.put(\"driverName\", t.driverName)
                    obj.put(\"driverPhone\", t.driverPhone)
                    obj.put(\"challanNo\", t.challanNo)
                    obj.put(\"ewbNo\", t.ewbNo)
                    obj.put(\"destinationSite\", t.destinationSite)
                    obj.put(\"totalBags\", t.totalBags)
                    obj.put(\"totalMetricTons\", t.totalMetricTons)
                    obj.put(\"totalAmount\", t.totalAmount)

                    val itemsArray = JSONArray()
                    for (item in t.items) {
                        val itemObj = JSONObject()
                        itemObj.put(\"productId\", item.productId)
                        itemObj.put(\"productName\", item.productName)
                        itemObj.put(\"quantityBags\", item.quantityBags)
                        itemObj.put(\"metricTons\", item.metricTons)
                        itemObj.put(\"ratePerBag\", item.ratePerBag)
                        itemObj.put(\"batchNo\", item.batchNo)
                        itemObj.put(\"bayLocation\", item.bayLocation)
                        itemsArray.put(itemObj)
                    }
                    obj.put(\"items\", itemsArray)
                    array.put(obj)
                }
                array.toString()
            } catch (e: Exception) {
                \"[]\"
            }
        }

        @JavascriptInterface
        fun recordQuickDispatch(productId: String, bags: Int, partyName: String): String {
            val response = JSONObject()
            try {
                if (bags <= 0) {
                    response.put(\"success\", false)
                    response.put(\"error\", \"Quantity must be greater than zero\")
                    return response.toString()
                }

                val products = repository.getCurrentProducts()
                val product = products.find { it.id == productId }
                if (product == null) {
                    response.put(\"success\", false)
                    response.put(\"error\", \"Product not found\")
                    return response.toString()
                }

                if (product.currentStockBags < bags) {
                    response.put(\"success\", false)
                    response.put(\"error\", \"Insufficient stock: only ${product.currentStockBags} bags available\")
                    return response.toString()
                }

                val slipNo = \"GP-${System.currentTimeMillis() % 100000}\"
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
                    id = \"tx_${System.currentTimeMillis()}\",
                    slipNo = slipNo,
                    type = TransactionType.OUTWARD,
                    timestamp = System.currentTimeMillis(),
                    partyName = if (partyName.isNotBlank()) partyName else \"Direct Walk-in Contractor\",
                    vehicleNo = \"MH-12-QZ-4891\",
                    driverName = \"Express Dispatch\",
                    driverPhone = \"+91 98220-44102\",
                    challanNo = \"DC-${System.currentTimeMillis() % 10000}\",
                    ewbNo = \"EWB-${System.currentTimeMillis() % 1000000}\",
                    destinationSite = \"Central Industrial Area Bay 2\",
                    totalBags = bags,
                    totalMetricTons = mt,
                    totalAmount = amount,
                    items = listOf(item)
                )

                val result = runBlocking { repository.recordDispatch(tx) }
                if (result.isSuccess) {
                    response.put(\"success\", true)
                    response.put(\"slipNo\", slipNo)
                    vibrate(40)
                } else {
                    response.put(\"success\", false)
                    response.put(\"error\", result.exceptionOrNull()?.message ?: \"Dispatch failed\")
                }
            } catch (e: Exception) {
                response.put(\"success\", false)
                response.put(\"error\", e.message ?: \"Unknown error\")
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
                    response.put(\"success\", false)
                    response.put(\"error\", \"No items added to dispatch\")
                    return response.toString()
                }

                val products = repository.getCurrentProducts()
                val txItems = mutableListOf<StockTransactionItem>()
                var totalBags = 0
                var totalMt = 0.0
                var totalAmount = 0.0

                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val pId = itemObj.getString(\"productId\")
                    val bags = itemObj.getInt(\"bags\")
                    if (bags <= 0) continue

                    val p = products.find { it.id == pId }
                    if (p == null) {
                        response.put(\"success\", false)
                        response.put(\"error\", \"Product $pId not found\")
                        return response.toString()
                    }

                    if (p.currentStockBags < bags) {
                        response.put(\"success\", false)
                        response.put(\"error\", \"Insufficient stock for ${p.name}: Available ${p.currentStockBags}, Requested $bags\")
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
                    response.put(\"success\", false)
                    response.put(\"error\", \"Total quantity must be greater than zero\")
                    return response.toString()
                }

                val slipNo = \"GP-${System.currentTimeMillis() % 100000}\"
                val tx = StockTransaction(
                    id = \"tx_${System.currentTimeMillis()}\",
                    slipNo = slipNo,
                    type = TransactionType.OUTWARD,
                    timestamp = System.currentTimeMillis(),
                    partyName = if (partyName.isNotBlank()) partyName else \"Direct Walk-in Contractor\",
                    vehicleNo = if (vehicleNo.isNotBlank()) vehicleNo else \"MH-12-QZ-4891\",
                    driverName = if (driverName.isNotBlank()) driverName else \"Depot Driver\",
                    driverPhone = if (driverPhone.isNotBlank()) driverPhone else \"-\",
                    challanNo = if (challanNo.isNotBlank()) challanNo else \"DC-${System.currentTimeMillis() % 10000}\",
                    ewbNo = if (ewbNo.isNotBlank()) ewbNo else \"-\",
                    destinationSite = if (site.isNotBlank()) site else \"Site Delivery\",
                    totalBags = totalBags,
                    totalMetricTons = totalMt,
                    totalAmount = totalAmount,
                    items = txItems
                )

                val result = runBlocking { repository.recordDispatch(tx) }
                if (result.isSuccess) {
                    response.put(\"success\", true)
                    response.put(\"slipNo\", slipNo)
                    vibrate(40)
                } else {
                    response.put(\"success\", false)
                    response.put(\"error\", result.exceptionOrNull()?.message ?: \"Dispatch failed\")
                }
            } catch (e: Exception) {
                response.put(\"success\", false)
                response.put(\"error\", e.message ?: \"Unknown error\")
            }
            return response.toString()
        }

        @JavascriptInterface
        fun recordStockIn(productId: String, bags: Int, batchNo: String, bayLocation: String): String {
            val response = JSONObject()
            try {
                if (bags <= 0) {
                    response.put(\"success\", false)
                    response.put(\"error\", \"Stock-in bags must be positive\")
                    return response.toString()
                }

                val result = runBlocking { repository.recordStockIn(productId, bags, batchNo, bayLocation) }
                if (result.isSuccess) {
                    response.put(\"success\", true)
                    vibrate(40)
                } else {
                    response.put(\"success\", false)
                    response.put(\"error\", result.exceptionOrNull()?.message ?: \"Stock in failed\")
                }
            } catch (e: Exception) {
                response.put(\"success\", false)
                response.put(\"error\", e.message ?: \"Unknown error\")
            }
            return response.toString()
        }

        @JavascriptInterface
        fun getPrintOptions(): String {
            return this@MainActivity.getSavedPrintOptions()
        }

        @JavascriptInterface
        fun savePrintOptions(optionsJson: String): String {
            this@MainActivity.savePrintOptions(optionsJson)
            return \"ok\"
        }

        @JavascriptInterface
        fun getSlipText(slipNo: String, paperWidthMm: Int): String {
            return getSlipTextWithOptions(slipNo, paperWidthMm, this@MainActivity.getSavedPrintOptions())
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
                    val opts = PrintOptions.fromJson(optionsJson)
                    EscPosSlipGenerator.generatePreviewText(tx, if (paperWidthMm == 58) 58 else 80, opts)
                } else {
                    \"No transaction available.\"
                }
            } catch (e: Exception) {
                \"Error generating slip: ${e.message}\"
            }
        }

        @JavascriptInterface
        fun printSlip(slipNo: String, paperWidthMm: Int): String {
            return printSlipWithOptions(slipNo, paperWidthMm, this@MainActivity.getSavedPrintOptions())
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
                    response.put(\"success\", false)
                    response.put(\"error\", \"Transaction not found for slip $slipNo\")
                    return response.toString()
                }

                if (optionsJson.isNotBlank() && optionsJson != \"{}\") {
                    this@MainActivity.savePrintOptions(optionsJson)
                }

                showToast(\"Printing slip ${tx.slipNo}...\")
                val opts = PrintOptions.fromJson(optionsJson)
                val escBytes = EscPosSlipGenerator.generateEscPosBytes(tx, if (paperWidthMm == 58) 58 else 80, opts)
                val (printedBt, statusMsg) = tryBluetoothPrint(escBytes)

                vibrate(100)
                if (printedBt) {
                    showToast(\"Thermal Slip printed via Bluetooth!\")
                } else {
                    showToast(\"Slip ${tx.slipNo} spooled ($statusMsg)\")
                }

                response.put(\"success\", true)
                response.put(\"printedViaBluetooth\", printedBt)
                response.put(\"message\", statusMsg)
                response.put(\"slipNo\", tx.slipNo)
            } catch (e: Exception) {
                response.put(\"success\", false)
                response.put(\"error\", e.message ?: \"Print failed\")
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
                root.put(\"supported\", isSupported)
                root.put(\"enabled\", isEnabled)
                root.put(\"hasPermission\", hasPerm)
                root.put(\"isScanning\", isScanning)

                val activeObj = JSONObject()
                activeObj.put(\"name\", getSavedPrinterName())
                activeObj.put(\"address\", getSavedPrinterAddress())
                root.put(\"activePrinter\", activeObj)
            } catch (e: Exception) {
                root.put(\"error\", e.message ?: \"Unknown error\")
            }
            return root.toString()
        }

        @JavascriptInterface
        fun requestBluetoothPermission(): String {
            runOnUiThread {
                requestBluetoothPermissions()
            }
            return \"requested\"
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
                    val name = try { dev.name ?: \"Unknown Device\" } catch (_: SecurityException) { \"Device\" }
                    val addr = dev.address ?: \"\"
                    obj.put(\"name\", name)
                    obj.put(\"address\", addr)
                    obj.put(\"bonded\", true)
                    obj.put(\"isSelected\", addr.isNotBlank() && addr.equals(activeAddr, ignoreCase = true))

                    val lower = name.lowercase(Locale.ROOT)
                    val isLikelyPrinter = lower.contains(\"printer\") || lower.contains(\"pos\") ||
                            lower.contains(\"rp\") || lower.contains(\"thermal\") ||
                            lower.contains(\"mpt\") || lower.contains(\"slip\") || lower.contains(\"bt\")
                    obj.put(\"isPrinter\", isLikelyPrinter)
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
                    res.put(\"success\", false)
                    res.put(\"error\", \"Bluetooth not supported\")
                    return res.toString()
                }
                if (!adapter.isEnabled) {
                    res.put(\"success\", false)
                    res.put(\"error\", \"Bluetooth is disabled. Please turn on Bluetooth in settings.\")
                    return res.toString()
                }
                if (!hasScanPermission()) {
                    runOnUiThread { requestBluetoothPermissions() }
                    res.put(\"success\", false)
                    res.put(\"error\", \"Bluetooth scan permission required\")
                    return res.toString()
                }

                discoveredDevices.clear()
                try {
                    if (adapter.isDiscovering) adapter.cancelDiscovery()
                } catch (_: Exception) {}

                val started = adapter.startDiscovery()
                isScanning = started
                res.put(\"success\", started)
            } catch (e: Exception) {
                res.put(\"success\", false)
                res.put(\"error\", e.message ?: \"Scan failed\")
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
            return \"stopped\"
        }

        @JavascriptInterface
        fun getDiscoveredPrinters(): String {
            val array = JSONArray()
            val activeAddr = getSavedPrinterAddress()
            for ((addr, name) in discoveredDevices) {
                val obj = JSONObject()
                obj.put(\"name\", name)
                obj.put(\"address\", addr)
                obj.put(\"bonded\", false)
                obj.put(\"isSelected\", addr.equals(activeAddr, ignoreCase = true))
                val lower = name.lowercase(Locale.ROOT)
                val isLikelyPrinter = lower.contains(\"printer\") || lower.contains(\"pos\") ||
                        lower.contains(\"rp\") || lower.contains(\"thermal\") ||
                        lower.contains(\"mpt\") || lower.contains(\"slip\") || lower.contains(\"bt\")
                obj.put(\"isPrinter\", isLikelyPrinter)
                array.put(obj)
            }
            return array.toString()
        }

        @JavascriptInterface
        fun selectPrinter(name: String, address: String): String {
            saveActivePrinter(name, address)
            showToast(\"Selected Printer: $name\")
            vibrate(30)
The above content does NOT show the entire file contents. If you need to view any lines of the file which were not shown to complete your task, call this tool again to view those lines.

