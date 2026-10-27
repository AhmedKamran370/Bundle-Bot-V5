package com.bundlebot

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Records {
    fun add(number: String, pkg: Package, bank: String, price: Int) {
        val now = System.currentTimeMillis()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(now))
        Store.addRecord(Rec(number, pkg.name, bank, price, time, now))
    }
}