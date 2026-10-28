package com.bundlebot

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*

object Pages {
    private const val BG = "#0F172A"; private const val CARD = "#1E293B"
    private const val TEXT = "#F1F5F9"; private const val MUTED = "#94A3B8"
    private const val ACCENT = "#22C55E"; private const val WARN = "#EF4444"
    private const val BLUE = "#3B82F6"

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
    private fun btn(a: Activity, t: String, bg: String = ACCENT, f: () -> Unit) = Button(a).apply {
        text = t; setOnClickListener { f() }; setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor(bg))
    }
    
    private fun simCard(a: Activity, title: String, color: String, content: View) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 20, 20, 20)
        val drawable = GradientDrawable().apply {
            setColor(Color.parseColor(CARD))
            setStroke(3, Color.parseColor(color))
            cornerRadius = 25f
        }
        background = drawable
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 }
        addView(tv(a, title, color, 20f))
        addView(content)
    }

    fun home(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "Welcome", TEXT, 24f))
        c.addView(tv(a, "Today: ${Store.todayCount()} sales • ${Store.convert(Store.todayTotal())} ${Store.currency()}", MUTED))
        
        if (Store.groups().isEmpty()) {
            val alert = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            alert.addView(tv(a, "⚠ Setup Required", WARN, 18f))
            alert.addView(tv(a, "You haven't added any WhatsApp groups yet. Go to the Setup tab to add your groups.", MUTED, 12f))
            alert.addView(btn(a, "Go to Setup", BLUE) { (a as MainActivity).rebuild(); a.findViewById<View>(android.R.id.content).post { Pages.setup(a) } })
            c.addView(card(a, alert))
        } else if (Store.groups().none { it.watched }) {
            val alert = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            alert.addView(tv(a, "⚠ Automation Paused", WARN, 18f))
            alert.addView(tv(a, "You have groups added, but none are ticked. Go to Setup and tick the groups you want to maintain.", MUTED, 12f))
            c.addView(card(a, alert))
        }

        if (Store.alarmActive) {
            val al = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            al.addView(tv(a, "⚠ ERROR — manual action needed", WARN, 18f))
            al.addView(btn(a, "Stop alarm", WARN) {
                Alarm.stop(a); Store.alarmActive = false; (a as MainActivity).rebuild()
            })
            c.addView(card(a, al))
        }
        
        val perms = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        perms.addView(tv(a, "Permissions", ACCENT, 16f))
        perms.addView(btn(a, "WhatsApp message access") { a.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        perms.addView(btn(a, "Screen control (Easypaisa)") { a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
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
            val isErr = m.contains("⚠")
            val box = TextView(a).apply {
                text = m.substringAfter(":", m).trim(); setTextColor(Color.WHITE)
                setPadding(20, 15, 20, 15); textSize = 14f
                setBackgroundColor(Color.parseColor(if (isIn) "#334155" else if (isErr) "#7F1D1D" else "#166534"))
            }
            c.addView(box)
            c.addView(View(a).apply { layoutParams = LinearLayout.LayoutParams(-1, 6) })
        }
        return wrap(c)
    }

    fun packages(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "Packages", TEXT, 24f))
        
        c.addView(btn(a, "Delete All Packages", WARN) {
            Store.clearPackages()
            (a as MainActivity).rebuild()
        })

        val allPkgs = Store.packages()
        val networks = listOf("Jazz", "Zong", "Telenor", "Ufone", "Other")
        val colors = mapOf("Jazz" to "#EF4444", "Zong" to "#22C55E", "Telenor" to "#3B82F6", "Ufone" to "#F59E0B", "Other" to "#94A3B8")

        for (net in networks) {
            val netPkgs = allPkgs.filter { it.network.equals(net, true) }
            
            val netContent = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            
            for (p in netPkgs) {
                val row = LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(10, 10, 10, 10)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
                }
                val info = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
                info.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                
                info.addView(tv(a, p.name, TEXT, 16f))
                val displayPrice = Store.convert(p.price)
                info.addView(tv(a, "${p.section} • ${p.gb} GB • ${p.days} Days • $displayPrice ${Store.currency()}", MUTED, 12f))
                
                row.addView(info)
                row.addView(btn(a, "Edit") { editPackage(a, p) })
                netContent.addView(row)
            }

            netContent.addView(btn(a, "➕ Add $net Packages", BLUE) {
                Store.pendingUploadNetwork = net
                Store.pickRecording = false
                pickFile(a)
            })

            c.addView(simCard(a, "$net SIM", colors[net] ?: "#94A3B8", netContent))
        }
        return wrap(c)
    }

    private fun editPackage(a: Activity, p: Package?) {
        val ll = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        val name = EditText(a).apply { hint = "Name"; setText(p?.name ?: "") }
        val net = EditText(a).apply { hint = "Network (Zong/Jazz/Telenor/Ufone)"; setText(p?.network ?: "") }
        val sec = EditText(a).apply { hint = "Section (Weekly/Monthly/etc)"; setText(p?.section ?: "") }
        val gb = EditText(a).apply { hint = "GB"; inputType = InputType.TYPE_CLASS_NUMBER; setText(p?.gb?.toString() ?: "") }
        val days = EditText(a).apply { hint = "Days"; inputType = InputType.TYPE_CLASS_NUMBER; setText(p?.days?.toString() ?: "") }
        val price = EditText(a).apply { hint = "Price (in PKR)"; inputType = InputType.TYPE_CLASS_NUMBER; setText(p?.price?.toString() ?: "") }
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
            box.addView(tv(a, "${r.time} • ${r.bank} • ${Store.convert(r.price)} ${Store.currency()}", MUTED, 12f))
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
            box.addView(tv(a, "Price ${Store.convert(p.price)} ${Store.currency()} (cropped)", MUTED, 12f))
            c.addView(box)
        }
        return wrap(c)
    }

    fun banks(a: Activity): View {
        val c = col(a)
        c.addView(tv(a, "Banks", TEXT, 24f))
        c.addView(tv(a, "Add your Easypaisa account so the app knows your balance.", MUTED, 12f))
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
        return wrap(c)
    }

    private fun editBank(a: Activity, b: Bank?) {
        val ll = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        val n = EditText(a).apply { hint = "Bank (e.g. Easypaisa)"; setText(b?.name ?: "") }
        val ac = EditText(a).apply { hint = "Account Number"; setText(b?.account ?: "") }
        val ba = EditText(a).apply { hint = "Current Balance (PKR)"; inputType = InputType.TYPE_CLASS_NUMBER; setText(b?.balance?.toString() ?: "") }
        val lo = EditText(a).apply { hint = "Low threshold (e.g. 200)"; inputType = InputType.TYPE_CLASS_NUMBER; setText(b?.lowAt?.toString() ?: "200") }
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
        c.addView(tv(a, "Setup Wizard", TEXT, 24f))
        c.addView(tv(a, "Follow these steps to start the automation.", MUTED, 12f))

        c.addView(tv(a, "1. Select WhatsApp App", ACCENT, 18f))
        val rg = RadioGroup(a)
        listOf("both" to "Both", "wa" to "WhatsApp only", "biz" to "WhatsApp Business only").forEach { (k, l) ->
            rg.addView(RadioButton(a).apply {
                text = l; isChecked = Store.source() == k
                setOnClickListener { Store.setSource(k) }
            })
        }
        c.addView(rg)

        c.addView(tv(a, "2. Add Groups to Maintain", ACCENT, 18f))
        c.addView(tv(a, "Type the exact name of your WhatsApp group below and press Add.", MUTED, 12f))
        
        val inputRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val groupInput = EditText(a).apply { hint = "e.g. Kakarot"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        inputRow.addView(groupInput)
        inputRow.addView(btn(a, "Add") {
            val name = groupInput.text.toString().trim()
            if (name.isNotEmpty()) {
                Store.addGroup(name)
                groupInput.setText("")
                (a as MainActivity).rebuild()
            }
        })
        c.addView(inputRow)
        
        c.addView(tv(a, "Your Groups (Tick to start automation):", TEXT, 14f))
        if (Store.groups().isEmpty()) c.addView(tv(a, "No groups added yet.", MUTED, 12f))
        
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

        c.addView(tv(a, "3. Easypaisa Settings", ACCENT, 18f))
        val pin = EditText(a).apply { 
            setText(Store.easypaisaPin); hint = "Enter 5-digit MPIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        c.addView(pin)
        c.addView(btn(a, "Save PIN") { 
            Store.easypaisaPin = pin.text.toString().trim()
            Toast.makeText(a, "PIN Saved", Toast.LENGTH_SHORT).show() 
        })

        c.addView(tv(a, "4. Train AI (Optional)", ACCENT, 18f))
        c.addView(tv(a, "Status: ${if (Store.screenRecordingDone()) "Uploaded ✅" else "Not Uploaded ❌"}", MUTED, 12f))
        c.addView(btn(a, "Upload Recording", BLUE) { 
            Store.pickRecording = true
            pickFile(a) 
        })
        
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