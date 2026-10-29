package com.bundlebot

data class Parsed(val number: String?, val gb: Int, val days: Int, val name: String, val network: String, val price: Int)

object Parser {
    private val numberRegex = Regex("""(?:\+?92|0)?[\s-]*3\d{2}[\s-]*\d{7}""")
    private val gbRegex = Regex("""(\d+)\s*(gb|g\b)""", RegexOption.IGNORE_CASE)
    private val mbRegex = Regex("""(\d+)\s*(mb)""", RegexOption.IGNORE_CASE)
    private val dayRegex = Regex("""(\d+)\s*(day|din|days)""", RegexOption.IGNORE_CASE)
    private val wordDaysRegex = Regex("""(daily|weekly|monthly|fortnightly)""", RegexOption.IGNORE_CASE)
    private val priceRegex = Regex("""(?:rs\.?|pkr)?\s*(\d{3,5})""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Parsed {
        val text = raw.trim()
        val rawNumberMatch = numberRegex.find(text)?.value
        var cleanNumber: String? = null
        
        if (rawNumberMatch != null) {
            var digitsOnly = rawNumberMatch.replace(Regex("[^0-9]"), "")
            if (digitsOnly.startsWith("92") && digitsOnly.length == 12) digitsOnly = "0" + digitsOnly.substring(2)
            else if (digitsOnly.startsWith("3") && digitsOnly.length == 10) digitsOnly = "0" + digitsOnly
            if (digitsOnly.length == 11 && digitsOnly.startsWith("03")) cleanNumber = digitsOnly
        }

        var gb = gbRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (gb == 0) {
            // Support MB
            val mb = mbRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (mb > 0) gb = (mb / 1024.0).toInt().coerceAtLeast(1)
        }

        var days = dayRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (days == 0) {
            val wordMatch = wordDaysRegex.find(text)?.value?.lowercase()
            if (wordMatch != null) {
                days = when {
                    wordMatch.contains("daily") -> 1
                    wordMatch.contains("weekly") -> 7
                    wordMatch.contains("fortnightly") -> 15
                    wordMatch.contains("monthly") -> 30
                    else -> 0
                }
            }
        }
        
        var price = 0
        val tempText = text.replace(rawNumberMatch ?: "", "").replace(Regex("""\d+\s*gb""", RegexOption.IGNORE_CASE), "")
        val priceMatch = priceRegex.find(tempText)
        if (priceMatch != null) price = priceMatch.groupValues[1].toIntOrNull() ?: 0

        val net = cleanNumber?.let { Store.networkFor(it) } ?: ""
        val name = text.lowercase()
            .replace(rawNumberMatch ?: "", "")
            .replace(Regex("""\d+\s*gb"""), "")
            .replace(Regex("""\d+\s*mb"""), "")
            .replace(Regex("""\d+\s*days?"""), "")
            .replace(Regex("""(?:rs\.?|pkr)?\s*\d{3,5}"""), "")
            .replace(wordDaysRegex, "")
            .trim()
            
        return Parsed(cleanNumber, gb, days, name, net, price)
    }

    fun parseWithAI(raw: String): Parsed {
        val key = Store.aiKey()
        if (key.isNotBlank()) {
            val ai = Ai.parseMessage(raw)
            if (ai != null) {
                var aiNumber = ai.optString("number", "").replace(Regex("[^0-9]"), "")
                if (aiNumber.startsWith("92") && aiNumber.length == 12) aiNumber = "0" + aiNumber.substring(2)
                else if (aiNumber.startsWith("3") && aiNumber.length == 10) aiNumber = "0" + aiNumber
                
                return Parsed(aiNumber.ifBlank { null },
                    ai.optInt("gb", 0), ai.optInt("days", 0),
                    ai.optString("name", ""),
                    ai.optString("network", Store.networkFor(aiNumber)),
                    ai.optInt("price", 0))
            }
        }
        return parse(raw)
    }
}