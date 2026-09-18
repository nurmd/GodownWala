        private fun getCurrentUserName(): String {
            val userStr = this@MainActivity.getCurrentUser()
            return if (userStr.isNotBlank()) {
                try { JSONObject(userStr).getString("name") } catch (e: Exception) { "Unknown" }
            } else {
                "Unknown"
            }
        }
