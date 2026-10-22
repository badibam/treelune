package app.treelune.core.ai.prompts

import android.content.Context
import android.content.res.Configuration
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The language of what the app writes for the AI: English, whatever the app's, as the prompt is.
 * The texts stay translated in the string files, for whoever reads them in the app's language.
 *
 * A schema's labels are also what the user sees on screen: they come in the app's language beside
 * the English ("Shortcuts (Raccourcis)"), so that the AI names a setting with the screen's words.
 * Its descriptions stay in English alone.
 */
object AiLanguage {

    /** [context] speaking English, for the texts made for the AI. */
    fun english(context: Context): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })

    /** Whether the app speaks English already, its labels then said once. */
    fun appIsEnglish(context: Context): Boolean = context.resources.configuration.locales[0].language == Locale.ENGLISH.language

    /**
     * The JSON schema [build] makes, as the AI reads it: built in English, each label (its title)
     * followed by the app's when they differ. [build] runs once per language: both come from the
     * same declaration and have the same shape.
     */
    inline fun schema(context: Context, build: (Context) -> String): String {
        val english = JSONObject(build(english(context)))
        if (appIsEnglish(context)) return english.toString()
        return withScreenLabels(english, JSONObject(build(context))).toString()
    }

    /** [english] with each title followed by its counterpart in [local], where they differ; the rest as in [english]. */
    fun withScreenLabels(english: JSONObject, local: JSONObject): JSONObject {
        val merged = JSONObject()
        english.keys().forEach { key ->
            val en = english.get(key)
            val other = local.opt(key)
            merged.put(key, when {
                key == "title" && en is String && other is String && other != en -> "$en ($other)"
                else -> merge(en, other)
            })
        }
        return merged
    }

    private fun merge(english: Any, local: Any?): Any = when {
        english is JSONObject && local is JSONObject -> withScreenLabels(english, local)
        english is JSONArray && local is JSONArray && english.length() == local.length() ->
            JSONArray().also { array -> for (i in 0 until english.length()) array.put(merge(english.get(i), local.opt(i))) }
        else -> english
    }
}
