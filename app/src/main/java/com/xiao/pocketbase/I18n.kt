package com.xiao.pocketbase

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Locale

object I18n {
    private var langData = JSONObject()
    private var fallbackData = JSONObject()
    private const val FALLBACK_LANG = "en"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        val forcedLang = prefs.getString("forced_language", "auto") ?: "auto"

        val sysLang = if (forcedLang != "auto") {
            forcedLang
        } else {
            Locale.getDefault().toLanguageTag()
        }

        fallbackData = loadLangJson(context, FALLBACK_LANG)
        langData = loadLangJson(context, sysLang)

        if (langData.length() == 0 && sysLang.contains("-")) {
            val mainLang = sysLang.split("-")[0]
            if (mainLang != FALLBACK_LANG) {
                langData = loadLangJson(context, mainLang)
            }
        }
    }

    fun getAvailableLanguages(context: Context): Map<String, String> {
        val languages = mutableMapOf<String, String>()
        try {
            val files = context.assets.list("langs") ?: emptyArray()
            files.forEach { fileName ->
                if (fileName.endsWith(".json")) {
                    val langTag = fileName.removeSuffix(".json")
                    val displayName = getLangDisplayName(context, langTag)
                    languages[langTag] = displayName
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return languages
    }

    private fun getLangDisplayName(context: Context, langTag: String): String {
        return try {
            val fileName = "langs/$langTag.json"
            val jsonContent = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonContent)
            if (json.has("lang_name")) json.getString("lang_name") else langTag
        } catch (e: Exception) {
            langTag
        }
    }

    private fun loadLangJson(context: Context, langTag: String): JSONObject {
        val fileName = "langs/$langTag.json"
        val externalFile = File(context.getExternalFilesDir(null), fileName)
        
        if (externalFile.exists()) {
            try {
                return JSONObject(externalFile.readText())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return try {
            val jsonContent = context.assets.open(fileName).bufferedReader().use { it.readText() }
            JSONObject(jsonContent)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun t(key: String, vararg args: Any?): String {
        val raw = when {
            langData.has(key) -> langData.getString(key)
            fallbackData.has(key) -> fallbackData.getString(key)
            else -> key
        }

        return try {
            if (args.isEmpty()) raw else String.format(Locale.getDefault(), raw, *args)
        } catch (e: Exception) {
            raw
        }
    }
}