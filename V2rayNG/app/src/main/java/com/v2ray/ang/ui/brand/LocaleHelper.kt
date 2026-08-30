package com.v2ray.ang.ui.brand

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    data class SupportedLanguage(
        val code: String,
        val displayName: String,
        val nativeName: String,
    )

    val supportedLanguages = listOf(
        SupportedLanguage(code = "fa", displayName = "Persian", nativeName = "فارسی"),
        SupportedLanguage(code = "en", displayName = "English", nativeName = "English"),
        SupportedLanguage(code = "ru", displayName = "Russian", nativeName = "Русский"),
        SupportedLanguage(code = "tr", displayName = "Turkish", nativeName = "Türkçe"),
        SupportedLanguage(code = "zh-CN", displayName = "Chinese", nativeName = "简体中文"),
    )

    /**
     * Resolves the target language tag according to rules:
     * - Respects device language if it matches a supported language (ru, tr, zh, en, fa).
     * - Defaults to Farsi / Persian ("fa") for any other language or when unconfigured.
     */
    fun resolveDefaultLanguageTag(): String {
        val deviceLanguage = Locale.getDefault().language.lowercase()
        return when {
            deviceLanguage.startsWith("ru") -> "ru"
            deviceLanguage.startsWith("tr") -> "tr"
            deviceLanguage.startsWith("zh") -> "zh-CN"
            deviceLanguage.startsWith("en") -> "en"
            deviceLanguage.startsWith("fa") -> "fa"
            else -> "fa" // Default language is Farsi/Persian
        }
    }

    fun initialize(context: Context) {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            val defaultTag = resolveDefaultLanguageTag()
            setLanguage(defaultTag)
        }
    }

    fun getCurrentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            val tag = locales.toLanguageTags()
            return when {
                tag.startsWith("fa") -> "fa"
                tag.startsWith("ru") -> "ru"
                tag.startsWith("zh") -> "zh-CN"
                tag.startsWith("tr") -> "tr"
                tag.startsWith("en") -> "en"
                else -> "fa"
            }
        }
        return resolveDefaultLanguageTag()
    }

    fun setLanguage(languageTag: String) {
        val appLocale = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}
