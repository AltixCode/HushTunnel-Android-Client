package com.v2ray.ang.ui.brand

import java.util.Locale

object JalaliDateUtils {
    private val PERSIAN_MONTHS = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianDigits(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch in '0'..'9') {
                sb.append(PERSIAN_DIGITS[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var gDays = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + (33 * (gDays / 12053))
        gDays %= 12053
        jy += 4 * (gDays / 1461)
        gDays %= 1461
        if (gDays > 365) {
            jy += (gDays - 1) / 365
            gDays = (gDays - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (gDays < 186) {
            jm = 1 + (gDays / 31)
            jd = 1 + (gDays % 31)
        } else {
            jm = 7 + ((gDays - 186) / 30)
            jd = 1 + ((gDays - 186) % 30)
        }
        return Triple(jy, jm, jd)
    }

    /**
     * Returns just the Shamsi calendar string (e.g. "۱۱ شهریور ۱۴۰۵"), or null if `currentLang`
     * isn't Persian or `dateStr` couldn't be parsed as a YYYY-MM-DD-prefixed date.
     */
    fun formatShamsiOnly(dateStr: String?, currentLang: String? = LocaleHelper.getCurrentLanguageTag()): String? {
        if (dateStr.isNullOrBlank()) return null
        val effectiveLang = currentLang ?: LocaleHelper.getCurrentLanguageTag()
        if (!(effectiveLang == "fa" || effectiveLang.startsWith("fa"))) return null

        val parts = dateStr.take(10).split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null

        val (jy, jm, jd) = gregorianToJalali(year, month, day)
        val monthName = PERSIAN_MONTHS.getOrElse(jm - 1) { "" }
        return toPersianDigits("$jd $monthName $jy")
    }

    /**
     * Formats an ISO or YYYY-MM-DD date string with Persian/Shamsi conversion when locale is 'fa'.
     * Example: "2026-09-02 (۱۱ شهریور ۱۴۰۵)"
     */
    fun formatDateWithShamsi(dateStr: String?, currentLang: String? = LocaleHelper.getCurrentLanguageTag()): String {
        if (dateStr.isNullOrBlank()) return ""
        val cleanDate = dateStr.take(10) // YYYY-MM-DD
        val shamsiStr = formatShamsiOnly(dateStr, currentLang) ?: return cleanDate
        return "$cleanDate ($shamsiStr)"
    }
}
