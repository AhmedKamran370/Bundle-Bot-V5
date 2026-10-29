package com.bundlebot

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.abs

data class Package(val name:String,val network:String,val section:String,val gb:Int,val days:Int,val price:Int)
data class Bank(val name:String,val account:String,val balance:Int,val lowAt:Int)
data class Group(val name:String,val watched:Boolean,val currency:String)
data class Rec(val id:String, val number:String,val packageName:String,val bank:String,val price:Int,val time:String,val epoch:Long)
data class PendingRec(val id:String, val number:String,val packageName:String,val price:Int,val time:String,val epoch:Long)
data class Proof(val number:String,val packageName:String,val price:Int,val time:String,val path:String)
data class MatchResult(val pkg: Package?, val needsConfirmation: Boolean, val suggestion: String)

object Store {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("bb", 0) }

    var alarmActive: Boolean
        get() = sp.getBoolean("alarm", false)
        set(v) { sp.edit().putBoolean("alarm", v).apply() }
    
    var pendingUploadNetwork: String = "Jazz"
    var recordFilter: String
        get() = sp.getString("rf", "Today")!!
        set(v) { sp.edit().putString("rf", v).apply() }

    // Prevent spam replies to the same failed package
    fun getLastFail(key: String): Long = sp.getLong("fail_$key", 0)
    fun setLastFail(key: String, time: Long) { sp.edit().putLong("fail_$key", time).apply() }

    // Custom Replies
    fun getCustomReplies(): Map<String, String> {
        val s = sp.getString("custom_replies", "{}")!!
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(s)
            for (key in json.keys()) map[key] = json.getString(key)
        } catch (_: Exception) {}
        return map
    }
    fun addCustomReply(trigger: String, reply: String) {
        val map = getCustomReplies().toMutableMap()
        map[trigger.lowercase()] = reply
        val json = JSONObject()
        for ((k, v) in map) json.put(k, v)
        sp.edit().putString("custom_replies", json.toString()).apply()
    }
    fun clearCustomReplies() { sp.edit().putString("custom_replies", "{}").apply() }

    fun clearPackages() { sp.edit().putString("pk", "[]").apply() }

    fun packages(): List<Package> = (JSONArray(sp.getString("pk", "[]"))).let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Package(o.getString("n"), o.getString("net"), o.getString("sec"),
                o.getInt("gb"), o.getInt("d"), o.getInt("p"))
        }
    }
    
    fun upsertPackage(p: Package, old: Package?) {
        val list = packages().toMutableList()
        if (old != null) list.remove(old)
        list.add(p)
        save(list)
    }
    fun deletePackage(p: Package) { save(packages().filter { it != p }) }
    
    private fun save(list: List<Package>) {
        val arr = JSONArray()
        for (p in list) arr.put(JSONObject().apply {
            put("n", p.name); put("net", p.network); put("sec", p.section)
            put("gb", p.gb); put("d", p.days); put("p", p.price)
        })
        sp.edit().putString("pk", arr.toString()).apply()
    }

    fun matchPackage(network: String, gb: Int, days: Int, name: String, price: Int): MatchResult {
        val list = if (network == "unknown" || network.isEmpty()) packages() 
                   else packages().filter { it.network.equals(network, true) }

        if (gb > 0) {
            val gbMatches = list.filter { it.gb == gb && (days == 0 || it.days == days) }
            if (gbMatches.isNotEmpty()) {
                if (price > 0) {
                    val closest = gbMatches.minByOrNull { abs(it.price - price) }
                    if (closest != null && abs(closest.price - price) <= 150) return MatchResult(closest, false, "")
                }
                return MatchResult(gbMatches.first(), false, "")
            }
            val looseGbMatch = list.firstOrNull { it.gb == gb }
            if (looseGbMatch != null) return MatchResult(looseGbMatch, false, "")
        }

        if (price > 0) {
            val priceMatches = list.filter { abs(it.price - price) <= 150 }
            if (priceMatches.isNotEmpty()) return MatchResult(priceMatches.first(), false, "")
        }

        if (name.isNotBlank()) {
            val keywordMatches = list.filter {
                it.name.contains(name, true) || it.section.contains(name, true) ||
                (name.contains("imo", true) && (it.name.contains("social", true) || it.name.contains("imo", true)))
            }
            if (keywordMatches.isNotEmpty()) {
                val highestGbPkg = keywordMatches.maxByOrNull { it.gb } ?: keywordMatches.first()
                return MatchResult(highestGbPkg, true, "") 
            }
        }
        return MatchResult(null, false, "")
    }

    // PENDING RECORDS
    fun pendingRecords(): List<PendingRec> = (JSONArray(sp.getString("pend", "[]"))).let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PendingRec(o.getString("id"), o.getString("num"), o.getString("pk"), o.getInt("pr"), o.getString("t"), o.getLong("e"))
        }
    }
    fun addPendingRecord(r: PendingRec) {
        val list = pendingRecords().toMutableList()
        list.add(r)
        savePending(list)
    }
    fun resolvePendingRecord(id: String, isDone: Boolean) {
        val list = pendingRecords()
        val item = list.find { it.id == id } ?: return
        if (isDone) {
            addRecord(Rec(item.id, item.number, item.packageName, "WhatsApp", item.price, item.time, item.epoch))
        }
        savePending(list.filter { it.id != id })
    }
    private fun savePending(list: List<PendingRec>) {
        val arr = JSONArray()
        for (p in list) arr.put(JSONObject().apply {
            put("id", p.id); put("num", p.number); put("pk", p.packageName)
            put("pr", p.price); put("t", p.time); put("e", p.epoch)
        })
        sp.edit().putString("pend", arr.toString()).apply()
    }

    // CONFIRMED RECORDS
    fun records(): List<Rec> = (JSONArray(sp.getString("rc", "[]"))).let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Rec(o.getString("id"), o.getString("num"), o.getString("pk"), o.getString("bk"), o.getInt("pr"), o.getString("t"), o.getLong("e"))
        }
    }
    fun addRecord(r: Rec) {
        val list = records().toMutableList()
        list.add(r)
        val arr = JSONArray()
        for (x in list) arr.put(JSONObject().apply {
            put("id", x.id); put("num", x.number); put("pk", x.packageName); put("bk", x.bank)
            put("pr", x.price); put("t", x.time); put("e", x.epoch)
        })
        sp.edit().putString("rc", arr.toString()).apply()
    }
    fun filteredRecords(filter: String): List<Rec> {
        val all = records()
        val cal = Calendar.getInstance()
        return when (filter) {
            "Today" -> all.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.epoch }
                c.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
            }
            "This week" -> all.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.epoch }
                c.get(Calendar.WEEK_OF_YEAR) == cal.get(Calendar.WEEK_OF_YEAR) && c.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
            }
            "This month" -> all.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.epoch }
                c.get(Calendar.MONTH) == cal.get(Calendar.MONTH) && c.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
            }
            "This year" -> all.filter {
                Calendar.getInstance().apply { timeInMillis = it.epoch }.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
            }
            else -> all
        }
    }
    fun todayCount() = filteredRecords("Today").size
    fun todayTotal() = filteredRecords("Today").sumOf { it.price }

    fun proofs(): List<Proof> = (JSONArray(sp.getString("pf", "[]"))).let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Proof(o.getString("num"), o.getString("pk"), o.getInt("pr"), o.getString("t"), o.getString("p"))
        }
    }
    fun addProof(p: Proof) {
        val list = proofs().toMutableList()
        list.add(p)
        val arr = JSONArray()
        for (x in list) arr.put(JSONObject().apply {
            put("num", x.number); put("pk", x.packageName); put("pr", x.price)
            put("t", x.time); put("p", x.path)
        })
        sp.edit().putString("pf", arr.toString()).apply()
    }

    fun messages(): List<String> = sp.getString("ms", "")!!.lines().filter { it.isNotBlank() }
    fun addMessage(m: String) {
        val list = messages().toMutableList()
        list.add(m)
        sp.edit().putString("ms", list.takeLast(80).joinToString("\n")).apply()
    }
    fun rawNotifs(): List<String> = sp.getString("raw", "")!!.lines().filter { it.isNotBlank() }
    fun addRaw(r: String) {
        val list = rawNotifs().toMutableList()
        list.add(r)
        sp.edit().putString("raw", list.takeLast(60).joinToString("\n")).apply()
    }

    fun source(): String = sp.getString("src", "both")!!
    fun setSource(v: String) { sp.edit().putString("src", v).apply() }
    fun aiKey(): String = sp.getString("ai", "")!!
    fun setAiKey(v: String) { sp.edit().putString("ai", v).apply() }
    fun currency(): String = sp.getString("cur", "PKR")!!
    fun setCurrency(v: String) { sp.edit().putString("cur", v).apply() }
    fun convert(pkr: Int): Int {
        return when (currency()) {
            "USD" -> (pkr / 280.0).toInt()
            "AED" -> (pkr / 76.0).toInt()
            "SAR" -> (pkr / 74.0).toInt()
            "INR" -> (pkr / 3.3).toInt()
            "GBP" -> (pkr / 355.0).toInt()
            else -> pkr
        }
    }
    
    fun prefixMap(): Map<String, String> = mapOf(
        "030" to "Jazz", "031" to "Zong", "032" to "Jazz", "033" to "Ufone",
        "034" to "Telenor", "037" to "Jazz", "0370" to "Zong", "038" to "Ufone", "039" to "Zong"
    )
    fun networkFor(number: String): String {
        val digits = number.filter { it.isDigit() }
        val p4 = digits.take(4)
        val p3 = digits.take(3)
        return prefixMap()[p4] ?: prefixMap()[p3] ?: "unknown"
    }

    fun screenRecordingDone(): Boolean = sp.getBoolean("rec", false)
    fun setScreenRecordingDone() { sp.edit().putBoolean("rec", true).apply() }
}