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
