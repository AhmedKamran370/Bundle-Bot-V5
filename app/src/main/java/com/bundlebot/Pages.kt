package com.bundlebot

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*

object Pages {
    private const val BG = "#0F172A"; private const val CARD = "#1E293B"
    private const val TEXT = "#F1F5F9"; private const val MUTED = "#94A3B8"
    private const val ACCENT = "#22C55E"; private const val WARN = "#EF4444"

    private fun tv(a: Activity, s: String, c: String = TEXT, sz: Float = 14f) = TextView(a).apply {
        text = s; setTextColor(Color.parseColor(c)); textSize = sz; setPadding(0, 10, 0, 10)
    }
    private fun col(a: Activity) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(30, 30, 30, 30); setBackgroundColor(Color.parseColor(BG))
    }
    private fun card(a: Activity, v: View) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(25, 20, 25, 20); setBackgroundColor(Color.parseColor(CARD))
        addView(v); setPadding(25, 20, 25, 20)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 15 }
    }
    private fun wrap(v: View) = ScrollView(v.context).apply { addView(v) }
    private fun btn(a: Activity, t: String, f: () -> Unit) = Button(a).apply {
        text = t; setOnClickListener { f() }; setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor(ACCENT))
    }

    fun home(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "Welcome", TEXT, 24f))
        c.addView(tv(a, "Today: ${Store.todayCount()} sales • ${Store.todayTotal()} PKR", MUTED))
        if (Store.alarmActive) {
            val al = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            al.addView(tv(a, "⚠ ERROR — manual action needed", WARN, 18f))
            al.addView(btn(a, "Stop alarm") {
                Alarm.stop(a); Store.alarmActive = false; (a as MainActivity).rebuild()
            })
            c.addView(card(a, al))
        }
        val perms = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        perms.addView(tv(a, "Permissions", ACCENT, 16f))
        perms.addView(btn(a, "WhatsApp message access") { a.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        perms.addView(btn(a, "Screen control (Easypaisa)") { a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        perms.addView(btn(a, "Disable battery saver") {
            try { a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
        })
        c.addView(card(a, perms))
        return wrap(c)
    }

    fun messages(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "Live Messages", TEXT, 24f))
        val msgs = Store.messages()
        if (msgs.isEmpty()) c.addView(tv(a, "Nothing yet.", MUTED))
        for (m in msgs.takeLast(50)) {
            val isIn = m.startsWith("IN:")
            val box = TextView(a).apply {
                text = m.substringAfter(":", m).trim(); setTextColor(Color.WHITE)
                setPadding(20, 15, 20, 15); textSize = 14f
                setBackgroundColor(Color.parseColor(if (isIn) "#334155" else "#166534"))
            }
            c.addView(box)
            c.addView(View(a).apply { layoutParams = LinearLayout.LayoutParams(-1, 6) })
        }
        return wrap(c)
    }

    fun packages(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "Packages", TEXT, 24f))
        c.addView(btn(a, "Upload list (txt, csv, docx)") { Store.pickRecording = false; pickFile(a) })
        c.addView(btn(a, "Add package manually") { editPackage(a, null) })
        val groups = Store.packages().groupBy { it.network }
        if (groups.isEmpty()) c.addView(tv(a, "No packages uploaded yet.", MUTED))
        for ((net, list) in groups) {
            c.addView(tv(a, net, ACCENT, 18f))
            for ((sec, items) in list.groupBy { it.section }) {
                c.addView(tv(a, sec, MUTED, 14f))
                for (p in items) {
                    val row = LinearLayout(a).apply {
                        orientation = LinearLayout.HORIZONTAL; setPadding(15, 12, 15, 12)
                        setBackgroundColor(Color.parseColor(CARD))
                        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6 }
                    }
                    val info = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
                    info.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    info.addView(tv(a, p.name, TEXT))
                    info.addView(tv(a, "${p.gb} GB • ${p.days} days • ${p.price} PKR", MUTED, 12f))
                    row.addView(info)
                    row.addView(btn(a, "Edit") { editPackage(a, p) })
                    c.addView(row)
                }
            }
        }
        return wrap(c)
    }

    private fun editPackage(a: Activity, p: Package?) {
        val ll = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        val name = EditText(a).apply { hint = "Name"; setText(p?.name ?: "") }
        val net = EditText(a).apply { hint = "Network (Jazz/Zong/Telenor/Ufone)"; setText(p?.network ?: "") }
        val sec = EditText(a).apply { hint = "Section (Weekly/Monthly call/etc)"; setText(p?.section ?: "") }
        val gb = EditText(a).apply { hint = "GB"; inputType = InputType.TYPE_CLASS_NUMBER; setText(p?.gb?.toString() ?: "") }
        val days = EditText(a).apply { hint = "Days"; inputType = InputType.TYPE_CLASS_NUMBER; setText(p?.days?.toString() ?: "") }
        val price = EditText(a).apply { hint = "Price"; inputType = InputType.TYPE_CLASS_NUMBER; setText(p?.price?.toString() ?: "") }
        listOf(name, net, sec, gb, days, price).forEach { ll.addView(it) }
        val d = AlertDialog.Builder(a).setView(ll)
            .setPositiveButton("Save") { _, _ ->
                Store.upsertPackage(
                    Package(name.text.toString(), net.text.toString(), sec.text.toString(),
                        gb.text.toString().toIntOrNull() ?: 0,
                        days.text.toString().toIntOrNull() ?: 0,
                        price.text.toString().toIntOrNull() ?: 0), p)
                (a as MainActivity).rebuild()
            }
            .setNegativeButton("Cancel", null)
        if (p != null) d.setNeutralButton("Delete") { _, _ -> Store.deletePackage(p); (a as MainActivity).rebuild() }
        d.show()
    }

    fun records(a: Activity): View {
        val c = col(a)
        val hdr = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        hdr.addView(tv(a, "Records", TEXT, 24f), LinearLayout.LayoutParams(0, -2, 1f))
        hdr.addView(btn(a, Store.recordFilter) {
            val opts = arrayOf("Today", "This week", "This month", "This year")
            AlertDialog.Builder(a).setItems(opts) { _, i ->
                Store.recordFilter = opts[i]; (a as MainActivity).rebuild()
            }.show()
        })
        c.addView(hdr)
        c.addView(btn(a, "Currency: ${Store.currency()}") {
            val opts = arrayOf("PKR", "USD", "AED", "SAR", "INR", "GBP")
            AlertDialog.Builder(a).setItems(opts) { _, i -> Store.setCurrency(opts[i]); (a as MainActivity).rebuild() }.show()
        })
        val recs = Store.filteredRecords(Store.recordFilter)
        val total = recs.sumOf { Store.convert(it.price) }
        c.addView(tv(a, "Total: $total ${Store.currency()}", ACCENT, 20f))
        c.addView(tv(a, "${recs.size} transactions", MUTED))
        c.addView(btn(a, "Export CSV") { Export.csv(a, recs, Store.currency()) })
        for (r in recs.reversed()) {
            val box = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12)
                setBackgroundColor(Color.parseColor(CARD))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6 }
            }
            box.addView(tv(a, "${r.number} — ${r.packageName}", TEXT))
            box.addView(tv(a, "${r.time} • ${r.bank} • ${r.price} PKR", MUTED, 12f))
            c.addView(box)
        }
        return wrap(c)
    }

    fun proofs(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "Proofs", TEXT, 24f))
        val list = Store.proofs()
        if (list.isEmpty()) c.addView(tv(a, "No proofs yet.", MUTED))
        for (p in list.reversed()) {
            val box = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12)
                setBackgroundColor(Color.parseColor(CARD))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6 }
            }
            box.addView(tv(a, "${p.time} • ${p.number} • ${p.packageName}", TEXT))
            box.addView(tv(a, "Price ${p.price} PKR (cropped from sent version)", MUTED, 12f))
            c.addView(box)
        }
        return wrap(c)
    }

    fun banks(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "Banks", TEXT, 24f))
        c.addView(btn(a, "Add bank account") { editBank(a, null) })
        for (b in Store.banks()) {
            val box = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(20, 12, 20, 12)
                setBackgroundColor(Color.parseColor(CARD))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6 }
            }
            val info = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            info.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            info.addView(tv(a, b.name, TEXT))
            info.addView(tv(a, "${b.account} • ${b.balance} PKR (low at ${b.lowAt})", MUTED, 12f))
            box.addView(info)
            box.addView(btn(a, "Edit") { editBank(a, b) })
            c.addView(box)
        }
        c.addView(tv(a, "Low-funds policy: ${Store.lowFundsPolicy()}", ACCENT, 16f))
        c.addView(btn(a, "Change policy") {
            val opts = arrayOf("Switch to next bank", "Alert me only", "Pause payments")
            AlertDialog.Builder(a).setItems(opts) { _, i -> Store.setLowFundsPolicy(opts[i]); (a as MainActivity).rebuild() }.show()
        })
        return wrap(c)
    }

    private fun editBank(a: Activity, b: Bank?) {
        val ll = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        val n = EditText(a).apply { hint = "Bank"; setText(b?.name ?: "") }
        val ac = EditText(a).apply { hint = "Account"; setText(b?.account ?: "") }
        val ba = EditText(a).apply { hint = "Balance"; inputType = InputType.TYPE_CLASS_NUMBER; setText(b?.balance?.toString() ?: "") }
        val lo = EditText(a).apply { hint = "Low threshold"; inputType = InputType.TYPE_CLASS_NUMBER; setText(b?.lowAt?.toString() ?: "200") }
        listOf(n, ac, ba, lo).forEach { ll.addView(it) }
        val d = AlertDialog.Builder(a).setView(ll)
            .setPositiveButton("Save") { _, _ ->
                Store.upsertBank(Bank(n.text.toString(), ac.text.toString(),
                    ba.text.toString().toIntOrNull() ?: 0,
                    lo.text.toString().toIntOrNull() ?: 200), b)
                (a as MainActivity).rebuild()
            }
            .setNegativeButton("Cancel", null)
        if (b != null) d.setNeutralButton("Delete") { _, _ -> Store.deleteBank(b); (a as MainActivity).rebuild() }
        d.show()
    }

    fun setup(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "Setup", TEXT, 24f))

        c.addView(tv(a, "Which WhatsApp to read?", TEXT, 16f))
        val rg = RadioGroup(a)
        listOf("both" to "Both", "wa" to "WhatsApp only", "biz" to "WhatsApp Business only").forEach { (k, l) ->
            rg.addView(RadioButton(a).apply {
                text = l; isChecked = Store.source() == k
                setOnClickListener { Store.setSource(k) }
            })
        }
        c.addView(rg)

        c.addView(tv(a, "Groups & currency", TEXT, 16f))
        if (Store.groups().isEmpty()) c.addView(tv(a, "No groups yet — they appear here when WhatsApp sends a notification.", MUTED, 12f))
        for (g in Store.groups()) {
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(15, 10, 15, 10)
                setBackgroundColor(Color.parseColor(CARD))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 5 }
            }
            row.addView(tv(a, g.name), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(btn(a, g.currency) {
                val opts = arrayOf("PKR", "USD", "AED", "SAR", "INR", "GBP")
                AlertDialog.Builder(a).setItems(opts) { _, i -> Store.setGroupCurrency(g.name, opts[i]); (a as MainActivity).rebuild() }.show()
            })
            row.addView(CheckBox(a).apply {
                isChecked = g.watched
                setOnCheckedChangeListener { _, v -> Store.setGroupWatched(g.name, v) }
            })
            c.addView(row)
        }

        c.addView(tv(a, "AI key (free Gemini from aistudio.google.com)", TEXT, 16f))
        val key = EditText(a).apply { setText(Store.aiKey()); hint = "API key" }
        c.addView(key)
        c.addView(btn(a, "Save AI key") { Store.setAiKey(key.text.toString().trim()); Toast.makeText(a, "Saved", Toast.LENGTH_SHORT).show() })

        c.addView(tv(a, "One-time upload: screen recording of Easypaisa purchase", TEXT, 16f))
        c.addView(tv(a, "Uploaded: ${if (Store.screenRecordingDone()) "Yes" else "No"}", MUTED))
        c.addView(btn(a, "Upload recording") { Store.pickRecording = true; pickFile(a) })

        c.addView(tv(a, "Known prefixes: ${Store.prefixMap().entries.joinToString { "${it.key}=${it.value}" }}", MUTED, 12f))
        return wrap(c)
    }

    fun help(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "AI Help", TEXT, 24f))
        c.addView(tv(a, "Ask anything about BundleBot", MUTED))
        val q = EditText(a).apply { hint = "Question" }
        val ans = tv(a, "", TEXT)
        c.addView(q)
        c.addView(btn(a, "Ask AI") {
            val question = q.text.toString().trim()
            if (question.isEmpty()) return@btn
            ans.text = "Thinking..."
            Thread {
                val reply = Ai.ask(question)
                a.runOnUiThread { ans.text = reply }
            }.start()
        })
        c.addView(ans)
        return wrap(c)
    }

    private fun pickFile(a: Activity) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        }
        try { a.startActivityForResult(i, 42) } catch (_: Exception) { Toast.makeText(a, "No file picker", Toast.LENGTH_SHORT).show() }
    }
}