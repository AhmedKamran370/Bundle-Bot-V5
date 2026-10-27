package com.bundlebot

data class Parsed(val number: String?, val gb: Int, val days: Int, val name: String, val network: String, val price: Int)

object Parser {
    private val numberRegex = Regex("""(\+?92|0)?3\d{2}[\s-]?\d{7}""")
    private val gbRegex = Regex("""(\d+)\s*(gb|g\b)""", RegexOption.IGNORE_CASE)
    private val dayRegex = Regex("""(\d+)\s*(day|din|days)""", RegexOption.IGNORE_CASE)
    private val priceRegex = Regex("""(?:rs\.?|pkr)?\s*(\d{3,5})""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Parsed {
        val text = raw.trim()
        val number = numberRegex.find(text)?.value?.replace("-", "")?.replace(" ", "")
        val gb = gbRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val days = dayRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        
        // Look for price (e.g., "500", "Rs 500")
        var price = 0
        val tempText = text.replace(number ?: "", "").replace(Regex("""\d+\s*gb""", RegexOption.IGNORE_CASE), "")
        val priceMatch = priceRegex.find(tempText)
        if (priceMatch != null) {
            price = priceMatch.groupValues[1].toIntOrNull() ?: 0
        }

        val net = number?.let { Store.networkFor(it) } ?: ""
        val name = text.lowercase()
            .replace(number ?: "", "")
            .replace(Regex("""\d+\s*gb"""), "")
            .replace(Regex("""\d+\s*days?"""), "")
            .replace(Regex("""(?:rs\.?|pkr)?\s*\d{3,5}"""), "")
            .trim()
            
        return Parsed(number, gb, days, name, net, price)
    }

    fun parseWithAI(raw: String): Parsed {
        val key = Store.aiKey()
        if (key.isNotBlank()) {
            val ai = Ai.parseMessage(raw)
            if (ai != null) {
                return Parsed(ai.optString("number", "").ifBlank { null },
                    ai.optInt("gb", 0), ai.optInt("days", 0),
                    ai.optString("name", ""),
                    ai.optString("network", Store.networkFor(ai.optString("number", ""))),
                    ai.optInt("price", 0))
            }
        }
        return parse(raw)
    }
}