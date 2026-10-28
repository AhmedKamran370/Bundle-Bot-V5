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
data class Rec(val number:String,val packageName:String,val bank:String,val price:Int,val time:String,val epoch:Long)
data class Proof(val number:String,val packageName:String,val price:Int,val time:String,val path:String)
data class MatchResult(val pkg: Package?, val needsConfirmation: Boolean, val suggestion: String)

object Store {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("bb", 0) }

    var alarmActive: Boolean
        get() = sp.getBoolean("alarm", false)
        set(v) { sp.edit().putBoolean("alarm", v).apply() }
    
    var pendingTask: String
        get() = sp.getString("ptask", "")!!
        set(v) { sp.edit().putString("ptask", v).apply() }
    
    var pickRecording = false
    var pendingUploadNetwork: String = "Jazz" // Remembers which SIM you are uploading
    
    var recordFilter: String
        get() = sp.getString("rf", "Today")!!
        set(v) { sp.edit().putString("rf", v).apply() }

    var easypaisaPin: String
        get() = sp.getString("epin", "")!!
        set(v) { sp.edit().putString("epin", v).apply() }

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
        val list = packages().filter { it.network.equals(network, true) || network.isEmpty() }

        if (gb > 0) {
            val gbMatches = list.filter { it.gb == gb && (days == 0 || it.days == days) }
            if (gbMatches.isNotEmpty()) {
                if (price > 0) {
                    val closest = gbMatches.minByOrNull { abs(it.price - price) }
                    if (closest != null && abs(closest.price - price) <= 150) {
                        return MatchResult(closest, false, "")
                    }
                }
                return MatchResult(gbMatches.first(), false, "")
            }
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

    fun banks(): List<Bank> = (JSONArray(sp.getString("bk", "[]"))).let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Bank(o.getString("n"), o.getString("a"), o.getInt("b"), o.getInt("l"))
        }
    }
    fun upsertBank(b: Bank, old: Bank?) {
        val list = banks().toMutableList()
        if (old != null) list.remove(old)
        list.add(b)
        val arr = JSONArray()
        for (x in list) arr.put(JSONObject().apply {
            put("n", x.name); put("a", x.account); put("b", x.balance); put("l", x.lowAt)
        })
        sp.edit().putString("bk", arr.toString()).apply()
    }
    fun deleteBank(b: Bank) {
        val list = banks().filter { it != b }
        val arr = JSONArray()
        for (x in list) arr.put(JSONObject().apply {
            put("n", x.name); put("a", x.account); put("b", x.balance); put("l", x.lowAt)
        })
        sp.edit().putString("bk", arr.toString()).apply()
    }
    fun lowFundsPolicy(): String = sp.getString("lf", "Switch to next bank")!!
    fun setLowFundsPolicy(v: String) { sp.edit().putString("lf", v).apply() }

    fun groups(): List<Group> = (JSONArray(sp.getString("gr", "[]"))).let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Group(o.getString("n"), o.getBoolean("w"), o.getString("c"))
        }
    }
    fun addGroup(name: String) {
        val list = groups().toMutableList()
        if (list.any { it.name == name }) return
        list.add(Group(name, true, "PKR"))
        saveGroups(list)
    }
    fun setGroupWatched(name: String, watched: Boolean) {
        saveGroups(groups().map { if (it.name == name) it.copy(watched = watched) else it })
    }
    fun setGroupCurrency(name: String, cur: String) {
        saveGroups(groups().map { if (it.name == name) it.copy(currency = cur) else it })
    }
    private fun saveGroups(list: List<Group>) {
        val arr = JSONArray()
        for (g in list) arr.put(JSONObject().apply {
            put("n", g.name); put("w", g.watched); put("c", g.currency)
        })
        sp.edit().putString("gr", arr.toString()).apply()
    }
    fun groupFor(name: String): Group? = groups().firstOrNull { it.name.equals(name, true) }

    fun records(): List<Rec> = (JSONArray(sp.getString("rc", "[]"))).let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Rec(o.getString("num"), o.getString("pk"), o.getString("bk"),
                o.getInt("pr"), o.getString("t"), o.getLong("e"))
        }
    }
    fun addRecord(r: Rec) {
        val list = records().toMutableList()
        list.add(r)
        val arr = JSONArray()
        for (x in list) arr.put(JSONObject().apply {
            put("num", x.number); put("pk", x.packageName); put("bk", x.bank)
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
        "030" to "Jazz", "031" to "Zong", "032" to "Warid", "033" to "Ufone",
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