package com.example.expirytracker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ProductStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE)

    fun getAll(): List<Product> {
        val raw = prefs.getString("products", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<Product>()

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Product(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    batchDateMillis = o.optLong("batchDateMillis", System.currentTimeMillis()),
                    qtyInitial = o.getInt("qtyInitial"),
                    qtyCurrent = o.getInt("qtyCurrent"),
                    expiresAtMillis = o.getLong("expiresAtMillis"),
                    status = o.optString("status", "ACTIVE")
                )
            )
        }
        return out.sortedBy { it.expiresAtMillis }
    }

    fun getActive(): List<Product> =
        getAll().filter { it.status == "ACTIVE" && it.qtyCurrent > 0 }
            .sortedBy { it.expiresAtMillis }

    fun getHistory(): List<Product> =
        getAll().filter { it.status != "ACTIVE" || it.qtyCurrent <= 0 }
            .sortedByDescending { it.batchDateMillis }

    fun upsert(p: Product) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == p.id }
        if (idx >= 0) list[idx] = p else list.add(p)
        saveAll(list)
    }

    fun getById(id: Long): Product? = getAll().firstOrNull { it.id == id }

    fun updateQty(productId: Long, newQty: Int, newStatus: String? = null) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == productId }
        if (idx < 0) return

        val old = list[idx]
        val status = newStatus ?: old.status
        list[idx] = old.copy(qtyCurrent = newQty, status = status)
        saveAll(list)
    }

    fun deleteById(id: Long) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list.removeAt(idx)
            saveAll(list)
        }
    }

    fun setStatusAndQty(id: Long, newQty: Int, status: String) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val old = list[idx]
        list[idx] = old.copy(qtyCurrent = newQty, status = status)
        saveAll(list)
    }

    private fun saveAll(list: List<Product>) {
        val arr = JSONArray()
        list.forEach { p ->
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("batchDateMillis", p.batchDateMillis)
            o.put("qtyInitial", p.qtyInitial)
            o.put("qtyCurrent", p.qtyCurrent)
            o.put("expiresAtMillis", p.expiresAtMillis)
            o.put("status", p.status)
            arr.put(o)
        }
        prefs.edit().putString("products", arr.toString()).apply()
    }
}
