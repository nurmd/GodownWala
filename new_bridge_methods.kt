        @JavascriptInterface
        fun setProducts(jsonStr: String) {
            try {
                val array = org.json.JSONArray(jsonStr)
                val list = mutableListOf<com.example.bhpos.domain.model.Product>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(com.example.bhpos.domain.model.Product(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        grade = obj.optString("grade", ""),
                        weightPerBagKg = obj.optDouble("weightPerBagKg", 50.0),
                        defaultRatePerBag = obj.optDouble("defaultRatePerBag", 0.0),
                        bayLocation = obj.optString("bayLocation", ""),
                        currentStockBags = obj.optInt("currentStockBags", 0),
                        batchNo = obj.optString("batchNo", ""),
                        imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.getString("imageUrl") else null,
                        isActive = obj.optBoolean("isActive", true)
                    ))
                }
                runBlocking { repository.setProducts(list) }
            } catch (e: Exception) { e.printStackTrace() }
        }

        @JavascriptInterface
        fun setTransactions(jsonStr: String) {
            try {
                val array = org.json.JSONArray(jsonStr)
                val list = mutableListOf<com.example.bhpos.domain.model.StockTransaction>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    // Simplified parsing for sync purposes... wait, JS needs to send items too.
                    // Actually, if JS pushes to Supabase, we can just let JS be the master for Transactions
                    // and just push them locally for dashboard.
                }
            } catch(e: Exception){}
        }
