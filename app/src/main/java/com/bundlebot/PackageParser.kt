package com.bundlebot

import java.io.InputStream
import java.util.zip.ZipInputStream

object PackageParser {
    fun fromText(text: String, forcedNet: String): List<Package> {
        val out = mutableListOf<Package>()
        var currentSec = "Other"

        for (line in text.lines()) {
            var l = line.replace("|", " ")
                .replace("+", " ")
                .replace("-", " ")
                .replace("*", "")
                .replace("\\", "")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (l.isEmpty()) continue

            if (l.contains("Package", true) && !l.any { it.isDigit() } && !l.contains("SR.NO", true)) {
                currentSec = l
                continue
            }

            val gbMatch = Regex("""(\d+)\s*(?:GB|gb)""").find(l)
            val gb = gbMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val priceMatch = Regex("""(?:SAR|Rs\.?|PKR)\s*(\d+)""", RegexOption.IGNORE_CASE).find(l)
            var price = priceMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
            if (price == 0) {
                val lastNum = Regex("""(\d+)""").findAll(l).lastOrNull()
                if (lastNum != null) price = lastNum.groupValues[1].toIntOrNull() ?: 0
            }

            if (gb == 0 || price == 0) continue

            var name = l
            if (gbMatch != null) name = name.substringBefore(gbMatch.value)
            if (priceMatch != null) name = name.substringBefore(priceMatch.value)
            else if (l.endsWith(price.toString())) name = name.substringBefore(price.toString())

            name = name.replace(Regex("^\\d+\\.?\\s*"), "").trim()
            if (name.isEmpty()) name = "$gb GB"

            // HERE IS THE MAGIC: It uses the SIM card you clicked on!
            out.add(Package(name, forcedNet, currentSec, gb, 30, price))
        }
        return out
    }

    fun fromDocx(input: InputStream, forcedNet: String): List<Package> {
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xmlText = zip.bufferedReader().readText()
                        
                        val cleanText = xmlText
                            .replace("</w:p>", "\n")
                            .replace("</w:tr>", "\n")
                            .replace(Regex("<[^>]*>"), " ")
                            .replace("&amp;", "&")
                            .replace(Regex("[ \t]+"), " ")
                            .replace(Regex("\n+"), "\n")

                        return fromText(cleanText, forcedNet)
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) { }
        return emptyList()
    }
}