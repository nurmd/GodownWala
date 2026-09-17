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

