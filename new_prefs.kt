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
