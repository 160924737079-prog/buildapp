package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Emerald Highlights
val Emerald500 = Color(0xFF10B981)
val Emerald600 = Color(0xFF059669)
val Emerald700 = Color(0xFF047857)
val Emerald100 = Color(0xFFD1FAE5)
val Emerald50 = Color(0xFFECFDF5)

// Deep Navy Slate Palette
val Navy950 = Color(0xFF0B1120)
val Navy900 = Color(0xFF0F172A)
val Navy800 = Color(0xFF1E293B)
val Navy700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

// Warning & Alert Red / Amber
val AlertRed600 = Color(0xFFDC2626)
val AlertRed500 = Color(0xFFEF4444)
val AlertRed100 = Color(0xFFFEE2E2)
val WarningAmber600 = Color(0xFFD97706)
val WarningAmber500 = Color(0xFFF59E0B)
val WarningAmber100 = Color(0xFFFEF3C7)

fun parseHexColor(hex: String, fallback: Color = Emerald500): Color {
    return try {
        val cleaned = hex.trim().removePrefix("#")
        val longVal = when (cleaned.length) {
            6 -> ("FF$cleaned").toLong(16)
            8 -> cleaned.toLong(16)
            else -> return fallback
        }
        Color(longVal)
    } catch (e: Exception) {
        fallback
    }
}
