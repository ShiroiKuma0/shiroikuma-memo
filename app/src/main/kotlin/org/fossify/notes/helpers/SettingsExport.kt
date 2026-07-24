package org.fossify.notes.helpers

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.ACCENT_COLOR
import org.fossify.commons.helpers.APP_ID
import org.fossify.commons.helpers.APP_RUN_COUNT
import org.fossify.commons.helpers.BACKGROUND_COLOR
import org.fossify.commons.helpers.COLOR_PICKER_RECENT_COLORS
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.IS_SYSTEM_THEME_ENABLED
import org.fossify.commons.helpers.LAST_VERSION
import org.fossify.commons.helpers.PRIMARY_COLOR
import org.fossify.commons.helpers.TEXT_COLOR
import org.fossify.commons.helpers.WIDGET_BG_COLOR
import org.fossify.commons.helpers.WIDGET_TEXT_COLOR
import org.fossify.notes.BuildConfig
import org.fossify.notes.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Kōjiki-style category export/import of every setting in the app: a ZIP of one typed-JSON file
// per category (plus the imported font files), written to / read from a user-picked directory.
@Suppress("TooManyFunctions")
object SettingsExport {

    const val FORMAT = "shiroikuma-memo-export"
    const val FORMAT_VERSION = 1
    const val EXPORT_PREFIX = "shiroikuma-memo-"
    const val EXPORT_SUFFIX = ".zip"

    private const val MANIFEST_NAME = "manifest.json"
    private const val FONTS_DIR = "fonts/"
    private const val JSON_INDENT = 2

    // Device-local prefs (never exported) holding the persisted export-directory URI.
    private const val EXIM_PREFS = "shiroikuma_eximport"
    private const val KEY_DIR_URI = "dir_uri"

    /** A selectable export/import category. `id` is the JSON file name (`<id>.json`) inside the ZIP. */
    enum class Cat(val id: String, @StringRes val labelRes: Int) {
        COLORS("colors", R.string.eim_cat_colors),
        FONTS("fonts", R.string.eim_cat_fonts),
        APP_SETTINGS("app_settings", R.string.eim_cat_app),
    }

    // The stock commons colors + our migration flags; theme-slot overrides match by "theme_" prefix.
    private val COLOR_KEYS = setOf(
        BACKGROUND_COLOR, TEXT_COLOR, PRIMARY_COLOR, ACCENT_COLOR, IS_SYSTEM_THEME_ENABLED,
        WIDGET_BG_COLOR, WIDGET_TEXT_COLOR, COLOR_PICKER_RECENT_COLORS, PURE_YELLOW_MIGRATED,
    )

    private val FONT_PREFIXES = listOf(FONT_FAMILY_PREFIX, FONT_WEIGHT_PREFIX, FONT_SIZE_PREFIX)

    // Device-local / identity keys that must never travel between installs.
    private val EXCLUDED_KEYS = setOf(
        APP_ID, APP_RUN_COUNT, LAST_VERSION, CURRENT_NOTE_ID, WIDGET_NOTE_ID,
        LAST_USED_EXTENSION, LAST_USED_SAVE_PATH, "internal_storage_path", "sd_card_path",
    )
    private val EXCLUDED_FRAGMENTS = listOf("tree_uri", "otg_", "password", "protection", "sort_folder_")

    private fun isExcluded(key: String) =
        key in EXCLUDED_KEYS || EXCLUDED_FRAGMENTS.any { key.contains(it) }

    private fun categoryOf(key: String): Cat? = when {
        isExcluded(key) -> null
        FONT_PREFIXES.any { key.startsWith(it) } -> Cat.FONTS
        key in COLOR_KEYS || key.startsWith("theme_") -> Cat.COLORS
        else -> Cat.APP_SETTINGS
    }

    // --- the persisted export directory (device-local) ---

    private fun eximPrefs(context: Context) =
        context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE)

    fun getDirUri(context: Context): Uri? =
        eximPrefs(context).getString(KEY_DIR_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setDirUri(context: Context, uri: Uri) =
        eximPrefs(context).edit().putString(KEY_DIR_URI, uri.toString()).apply()

    fun exportDir(context: Context): DocumentFile? =
        getDirUri(context)
            ?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    /** Newest export file in the configured directory, or null (no directory / no export yet). */
    fun latestExport(context: Context): DocumentFile? {
        val dir = exportDir(context) ?: return null
        return runCatching {
            dir.listFiles()
                .filter {
                    it.isFile &&
                        it.name?.startsWith(EXPORT_PREFIX) == true &&
                        it.name?.endsWith(EXPORT_SUFFIX) == true
                }
                .maxByOrNull { it.lastModified() }
        }.getOrNull()
    }

    fun exportFileName(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
        return "$EXPORT_PREFIX${BuildConfig.VERSION_NAME}-export_$ts$EXPORT_SUFFIX"
    }

    // --- export ---

    /** Write a ZIP of the selected categories to [out]. Returns a short human summary. */
    fun export(context: Context, cats: Set<Cat>, out: OutputStream): String {
        var count = 0
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", FORMAT_VERSION)
                .put("app", context.packageName)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(cats.map { it.id }))
            writeEntry(zip, MANIFEST_NAME, manifest.toString(JSON_INDENT).toByteArray())

            for (cat in cats) {
                writeEntry(zip, "${cat.id}.json", exportPrefs(context, cat).toByteArray())
                if (cat == Cat.FONTS) {
                    exportFontFiles(context, zip)
                }
                count++
            }
        }
        return "$count categor${if (count == 1) "y" else "ies"}"
    }

    private fun exportPrefs(context: Context, cat: Cat): String {
        val obj = JSONObject()
        for ((key, value) in context.getSharedPrefs().all) {
            if (categoryOf(key) != cat) continue
            typedEntry(value)?.let { obj.put(key, it) }
        }
        return obj.toString(JSON_INDENT)
    }

    private fun typedEntry(value: Any?): JSONObject? {
        val entry = JSONObject()
        when (value) {
            is Boolean -> { entry.put("t", "b"); entry.put("v", value) }
            is Int -> { entry.put("t", "i"); entry.put("v", value) }
            is Long -> { entry.put("t", "l"); entry.put("v", value) }
            is Float -> { entry.put("t", "f"); entry.put("v", value.toDouble()) }
            is String -> { entry.put("t", "s"); entry.put("v", value) }
            is Set<*> -> { entry.put("t", "ss"); entry.put("v", JSONArray(value.map { it.toString() })) }
            else -> return null
        }
        return entry
    }

    private fun exportFontFiles(context: Context, zip: ZipOutputStream) {
        FontHelper.getFontsDir(context).listFiles()
            ?.filter { it.isFile }
            ?.forEach { writeEntry(zip, FONTS_DIR + it.name, it.readBytes()) }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    // --- import ---

    /** The categories present in an export ZIP (empty = not one of our exports). */
    fun categoriesIn(zipBytes: ByteArray): Set<Cat> =
        readZip(zipBytes).keys
            .mapNotNull { name -> Cat.entries.firstOrNull { "${it.id}.json" == name } }
            .toSet()

    /** Apply the selected categories from a ZIP. Missing files are skipped. Returns a human summary. */
    fun import(context: Context, zipBytes: ByteArray, cats: Set<Cat>): String {
        val files = readZip(zipBytes)
        val parts = mutableListOf<String>()
        for (cat in cats) {
            val data = files["${cat.id}.json"] ?: continue
            var applied = importPrefs(context, cat, data.decodeToString())
            if (cat == Cat.FONTS) {
                applied += importFontFiles(context, files)
            }
            parts.add("${context.getString(cat.labelRes)}: $applied")
        }
        return if (parts.isEmpty()) "nothing imported" else parts.joinToString("\n")
    }

    private fun importPrefs(context: Context, cat: Cat, json: String): Int {
        val obj = JSONObject(json)
        val editor = context.getSharedPrefs().edit() // merge — never clear, device-local keys survive
        var applied = 0
        for (key in obj.keys()) {
            // categoryOf also re-checks exclusions — a foreign file can't smuggle excluded keys in
            val entry = obj.optJSONObject(key)
            if (categoryOf(key) != cat || entry == null) continue
            if (applyEntry(editor, key, entry)) {
                applied++
            }
        }
        editor.apply()
        return applied
    }

    @Suppress("CyclomaticComplexMethod") // one branch per stored type — the exhaustive when is the point
    private fun applyEntry(editor: android.content.SharedPreferences.Editor, key: String, entry: JSONObject): Boolean {
        when (entry.optString("t")) {
            "b" -> editor.putBoolean(key, entry.optBoolean("v"))
            "i" -> editor.putInt(key, entry.optInt("v"))
            "l" -> editor.putLong(key, entry.optLong("v"))
            "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
            "s" -> editor.putString(key, entry.optString("v"))
            "ss" -> {
                val arr = entry.optJSONArray("v") ?: JSONArray()
                val set = HashSet<String>()
                for (i in 0 until arr.length()) {
                    set.add(arr.optString(i))
                }
                editor.putStringSet(key, set)
            }

            else -> return false
        }
        return true
    }

    private fun importFontFiles(context: Context, files: Map<String, ByteArray>): Int {
        val fontsDir = FontHelper.getFontsDir(context)
        var applied = 0
        for ((name, data) in files) {
            val fileName = File(name).name // strip the zip path — no traversal outside the fonts dir
            if (!name.startsWith(FONTS_DIR) || fileName.isEmpty()) continue
            runCatching {
                File(fontsDir, fileName).writeBytes(data)
                applied++
            }
        }
        return applied
    }

    private fun readZip(zipBytes: ByteArray): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        files[entry.name] = zip.readBytes()
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return files
    }
}
