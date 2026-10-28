package com.bundlebot

data class Parsed(val number: String?, val gb: Int, val days: Int, val name: String, val network: String, val price: Int)

object Parser {
    // Catches +92312..., 0312..., 312..., with or without spaces/hyphens
    private val numberRegex = Regex("""(?:\+?92|0)?[\s-]*3\d{2}[\s-]*\d{7}""")
    private val gbRegex = Regex("""(\d+)\s*(gb|g\b)""", RegexOption.IGNORE_CASE)
    private val dayRegex = Regex("""(\d+)\s*(day|din|days)""", RegexOption.IGNORE_CASE)
    private val priceRegex = Regex("""(?:rs\.?|pkr)?\s*(\d{3,5})""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Parsed {
        val text = raw.trim()
        
        // 1. Find the raw number
        val rawNumberMatch = numberRegex.find(text)?.value
        var cleanNumber: String? = null
        
        if (rawNumberMatch != null) {
            // 2. Strip out ALL non-digit characters (spaces, hyphens, plus signs)
            var digitsOnly = rawNumberMatch.replace(Regex("[^0-9]"), "")
            
            // 3. Standardize to 11 digits starting with 0
            if (digitsOnly.startsWith("92") && digitsOnly.length == 12) {
                digitsOnly = "0" + digitsOnly.substring(2) // Convert 92312... to 0312...
            } else if (digitsOnly.startsWith("3") && digitsOnly.length == 10) {
                digitsOnly = "0" + digitsOnly // Convert 312... to 0312...
            }
            
            // Final validation: Must be exactly 11 digits starting with 03
            if (digitsOnly.length == 11 && digitsOnly.startsWith("03")) {
                cleanNumber = digitsOnly
            }
        }

        val gb = gbRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val days = dayRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        
        var price = 0
        val tempText = text.replace(rawNumberMatch ?: "", "").replace(Regex("""\d+\s*gb""", RegexOption.IGNORE_CASE), "")
        val priceMatch = priceRegex.find(tempText)
        if (priceMatch != null) {
            price = priceMatch.groupValues[1].toIntOrNull() ?: 0
        }

        val net = cleanNumber?.let { Store.networkFor(it) } ?: ""
        val name = text.lowercase()
            .replace(rawNumberMatch ?: "", "")
            .replace(Regex("""\d+\s*gb"""), "")
            .replace(Regex("""\d+\s*days?"""), "")
            .replace(Regex("""(?:rs\.?|pkr)?\s*\d{3,5}"""), "")
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