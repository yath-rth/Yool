package com.yatharth.yool.io.strong

/** Minimal RFC-4180-ish CSV reader: quoted fields may contain delimiters, newlines and doubled quotes. */
object Csv {
    /** Picks `;` or `,` by whichever appears more (outside quotes) in the header line. */
    fun detectDelimiter(text: String): Char {
        var commas = 0
        var semis = 0
        var inQuotes = false
        for (c in text) {
            if (c == '"') inQuotes = !inQuotes
            else if (!inQuotes) {
                if (c == '\n') break
                if (c == ',') commas++ else if (c == ';') semis++
            }
        }
        return if (semis > commas) ';' else ','
    }

    fun parse(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")
        fun endField() {
            row.add(field.toString()); field.setLength(0)
        }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = mutableListOf()
        }
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when {
                c == '"' -> inQuotes = true
                c == delimiter -> endField()
                c == '\r' -> { if (i + 1 < s.length && s[i + 1] == '\n') i++; endRow() }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
