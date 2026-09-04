package org.fossify.notes.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
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
import org.fossify.commons.helpers.PROTECTION_NONE
import org.fossify.commons.helpers.TEXT_COLOR
import org.fossify.commons.helpers.WIDGET_BG_COLOR
import org.fossify.commons.helpers.WIDGET_TEXT_COLOR
import org.fossify.notes.BuildConfig
import org.fossify.notes.R
import org.fossify.notes.databases.NotesDatabase
import org.fossify.notes.models.Note
import org.fossify.notes.models.NoteType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * (current, total, unit, text) — the progress channel of a headless export. Real counts only: [text] is
 * the numbers-first line a caller displays ("Notes 123/456"), never a percentage.
 */
typealias ProgressReporter = (current: Long, total: Long, unit: String, text: String) -> Unit

// Kōjiki-style category export/import of everything this app holds: a ZIP of one typed-JSON file per
// category (plus the imported font files), written to / read from a user-picked directory. One archive
// per app — the settings AND the notes themselves — so a restore needs exactly one file.
// The Export/Import panel and the headless automation export (StateExportReceiver) share this core.
@Suppress("TooManyFunctions")
object SettingsExport {

    const val FORMAT = "shiroikuma-memo-export"
    const val FORMAT_VERSION = 2
    const val ZIP_MIME = "application/zip"

    // What a half-written export is called until it is complete. Deliberately outside EXPORT_SUFFIX, so
    // latestExport() and any caller scanning for "*.zip" cannot mistake one for a backup.
    private const val PART_SUFFIX = ".part"
    private const val OCTET_MIME = "application/octet-stream"

    // The app's English dash-separated name, and the prefix every export of ours starts with — the whole
    // family names its backups "<app-name>_<yyyy-MM-dd_HH-mm-ss>.zip" so they sort and read uniformly in
    // 白い熊's one backup directory. Deliberately version-free: a backup is identified by when it was
    // taken, not by the build that wrote it (that is recorded inside, as manifest.json's appVersion).
    // Our older exports carried the version in the name and still match this prefix, so the "last export"
    // row keeps finding them.
    const val EXPORT_PREFIX = "shiroikuma-memo"
    const val EXPORT_SUFFIX = ".zip"

    private const val MANIFEST_NAME = "manifest.json"
    private const val FONTS_DIR = "fonts/"
    private const val JSON_INDENT = 2

    // Device-local prefs (never exported) holding the persisted export-directory URI.
    private const val EXIM_PREFS = "shiroikuma_eximport"
    private const val KEY_DIR_URI = "dir_uri"

    /**
     * A selectable export/import category. `id` is the JSON file name (`<id>.json`) inside the ZIP.
     *
     * [containsRes] is the line 応用管理 shows for this category in its backup list — it comes from the
     * `describe` header and is rendered **verbatim**, so each of these says what would actually be lost,
     * not what it is called. Notes are the one category no clean phone can re-supply from anywhere else.
     */
    enum class Cat(
        val id: String,
        @StringRes val labelRes: Int,
        @StringRes val shortLabelRes: Int,
        @StringRes val containsRes: Int,
    ) {
        COLORS("colors", R.string.eim_cat_colors, R.string.eim_cat_colors, R.string.automation_contains_colors),
        FONTS("fonts", R.string.eim_cat_fonts, R.string.eim_cat_fonts, R.string.automation_contains_fonts),
        APP_SETTINGS(
            "app_settings", R.string.eim_cat_app, R.string.eim_cat_app, R.string.automation_contains_app,
        ),
        NOTES(
            "notes", R.string.eim_cat_notes, R.string.eim_cat_notes_short, R.string.automation_contains_notes,
        );

        companion object {
            fun byId(id: String): Cat? = entries.firstOrNull { it.id == id }

            /**
             * The order the `describe` header lists categories in: what 白い熊 would actually mourn,
             * first. Anything added to the enum and not named here still appears, at the end, so a new
             * category can never go silently unlisted.
             */
            val describeOrder: List<Cat>
                get() = (listOf(NOTES, APP_SETTINGS, COLORS, FONTS) + entries).distinct()
        }
    }

    /** Thrown out of [export] when the caller's cancel flag turns true. The partial file is then deleted. */
    class Cancelled : Exception("cancelled")

    // The stock commons colors + our migration flags; theme-slot overrides match by "theme_" prefix.
    private val COLOR_KEYS = setOf(
        BACKGROUND_COLOR, TEXT_COLOR, PRIMARY_COLOR, ACCENT_COLOR, IS_SYSTEM_THEME_ENABLED,
        WIDGET_BG_COLOR, WIDGET_TEXT_COLOR, COLOR_PICKER_RECENT_COLORS, PURE_YELLOW_MIGRATED,
    )

    private val FONT_PREFIXES = listOf(FONT_FAMILY_PREFIX, FONT_WEIGHT_PREFIX, FONT_SIZE_PREFIX)

    // Device-local / identity keys that must never travel between installs — including the automation
    // switch and its shared secret: each device owns its own security state, so a restore can neither
    // flip automation on nor carry a token, and no export ZIP ever contains one.
    private val EXCLUDED_KEYS = setOf(
        APP_ID, APP_RUN_COUNT, LAST_VERSION, CURRENT_NOTE_ID, WIDGET_NOTE_ID,
        LAST_USED_EXTENSION, LAST_USED_SAVE_PATH, "internal_storage_path", "sd_card_path",
        AUTOMATION_ENABLED, AUTOMATION_REQUIRE_TOKEN, AUTOMATION_TOKEN,
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

    /** "shiroikuma-memo_2026-07-25_18-58-23.zip" — the app's English name, then when it was taken. */
    fun exportFileName(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
        return "${EXPORT_PREFIX}_$ts$EXPORT_SUFFIX"
    }

    // --- export ---

    /**
     * Write a ZIP of the selected categories to [out] — the export core, callable headlessly (no
     * Activity, no interaction). Reports real counts through [onProgress] (unthrottled; the caller
     * decides how often to surface them). Blocking, so call it on a background thread. Returns a short
     * human summary.
     */
    fun export(
        context: Context,
        cats: Set<Cat>,
        out: OutputStream,
        onProgress: ProgressReporter = { _, _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): String {
        // Declaration order, not the caller's, so a ZIP's contents never depend on how the set was built.
        val ordered = Cat.entries.filter { it in cats }
        require(ordered.isNotEmpty()) { "nothing selected" }
        val total = ordered.size.toLong()
        val unit = context.getString(R.string.state_progress_unit_category)
        val parts = mutableListOf<String>()

        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", FORMAT_VERSION)
                .put("app", context.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(ordered.map { it.id }))
            writeEntry(zip, MANIFEST_NAME, manifest.toString(JSON_INDENT).toByteArray())

            ordered.forEachIndexed { index, cat ->
                // Between entries, never mid-write: a cancelled export unwinds at a boundary rather than
                // being torn down half way through a ZIP entry.
                if (isCancelled()) throw Cancelled()
                val done = index + 1L
                val label = context.getString(cat.shortLabelRes)
                onProgress(done, total, unit, "$unit $done/$total — $label")
                parts += "$label: ${writeCategory(context, zip, cat, onProgress, isCancelled)}"
            }
        }
        return parts.joinToString("・")
    }

    /** Write one category's data into the ZIP; returns what went in, for the human summary. */
    private fun writeCategory(
        context: Context,
        zip: ZipOutputStream,
        cat: Cat,
        onProgress: ProgressReporter,
        isCancelled: () -> Boolean,
    ): Int =
        when (cat) {
            Cat.NOTES -> exportNotes(context, zip, onProgress, isCancelled)
            Cat.FONTS -> exportPrefsEntry(context, zip, cat) + exportFontFiles(context, zip)
            else -> exportPrefsEntry(context, zip, cat)
        }

    /** The category's SharedPreferences keys as one typed-JSON entry; returns how many were written. */
    private fun exportPrefsEntry(context: Context, zip: ZipOutputStream, cat: Cat): Int {
        val obj = JSONObject()
        for ((key, value) in context.getSharedPrefs().all) {
            if (categoryOf(key) != cat) continue
            typedEntry(value)?.let { obj.put(key, it) }
        }
        writeEntry(zip, "${cat.id}.json", obj.toString(JSON_INDENT).toByteArray())
        return obj.length()
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

    private fun exportFontFiles(context: Context, zip: ZipOutputStream): Int {
        var count = 0
        FontHelper.getFontsDir(context).listFiles()
            ?.filter { it.isFile }
            ?.forEach {
                writeEntry(zip, FONTS_DIR + it.name, it.readBytes())
                count++
            }
        return count
    }

    /** Every note, with its text — including the content of file-backed notes, so the ZIP stands alone. */
    private fun exportNotes(
        context: Context,
        zip: ZipOutputStream,
        onProgress: ProgressReporter,
        isCancelled: () -> Boolean,
    ): Int {
        val notes = NotesDatabase.getInstance(context).NotesDao().getNotes()
        val unit = context.getString(R.string.state_progress_unit_notes)
        val total = notes.size.toLong()
        val array = JSONArray()
        notes.forEachIndexed { index, note ->
            // The one loop that can run long enough to be worth interrupting inside; the entry itself is
            // still written whole, since nothing reaches the ZIP until the array is finished.
            if (isCancelled()) throw Cancelled()
            onProgress(index + 1L, total, unit, "$unit ${index + 1}/$total")
            array.put(
                JSONObject()
                    .put("title", note.title)
                    .put("value", note.getNoteStoredValue(context) ?: note.value)
                    .put("type", note.type.value)
                    .put("path", note.path)
                    .put("protectionType", note.protectionType)
                    .put("protectionHash", note.protectionHash)
            )
        }
        writeEntry(zip, "${Cat.NOTES.id}.json", array.toString(JSON_INDENT).toByteArray())
        return notes.size
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    // --- headless destination (automation) ---

    /**
     * A resolved headless export destination, written in two steps so a half-backup can never be left
     * behind: bytes go to `<final-name>.part`, and [commit] renames it into place only once the archive
     * is closed and complete. [abort] deletes the partial on any failure, timeout or cancellation.
     *
     * That distinction is the whole point. 白い熊 keeps every app's backups in one directory sorted by
     * date, so a truncated archive silently becomes "the latest backup" of this app and is
     * indistinguishable from a real one until the day someone tries to restore it.
     */
    class Target(
        val open: () -> OutputStream,
        val commit: () -> Unit,
        val abort: () -> Unit,
        val size: () -> Long,
        val displayPath: () -> String,
    )

    /**
     * Resolve where a headless export writes. Directory precedence, per the automation contract:
     * [pathOverride] (an absolute directory, created if missing) → the configured export directory →
     * null, which the caller reports as "no-directory".
     */
    fun headlessTarget(context: Context, pathOverride: String): Target? {
        val name = exportFileName()
        if (pathOverride.isNotEmpty()) {
            // /sdcard is a symlink; normalize it so the reply names the real path.
            val primary = Environment.getExternalStorageDirectory().absolutePath
            val dir = File(pathOverride.replaceFirst(Regex("^/sdcard"), primary))
            dir.mkdirs()
            require(dir.isDirectory) { "not a directory: $pathOverride" }
            val file = File(dir, name)
            val part = File(dir, name + PART_SUFFIX)
            return Target(
                open = { FileOutputStream(part) },
                commit = { check(part.renameTo(file)) { "cannot rename ${part.name} to $name" } },
                abort = { part.delete() },
                size = { file.length() },
                displayPath = { file.absolutePath },
            )
        }

        val dir = exportDir(context) ?: return null
        val file = stagedSafFile(dir, name)
        // A provider that would not take the .part name gave us the final name instead; then there is
        // nothing to rename, and abort() deleting the file is what keeps the directory clean.
        val staged = file.name == name + PART_SUFFIX
        return Target(
            open = { context.contentResolver.openOutputStream(file.uri) ?: error("cannot open ${file.uri}") },
            commit = { if (staged) check(file.renameTo(name)) { "cannot rename to $name" } },
            abort = { file.delete() },
            size = { file.length() },
            displayPath = { displayPathOf(file.uri) },
        )
    }

    /**
     * Create `<name>.part` in a SAF directory, falling back to the final name if the provider will not
     * have it.
     *
     * A `DocumentsProvider` rewrites a display name whose extension disagrees with the MIME type it was
     * given — asking for `…zip.part` as `application/zip` yields `…zip.part.zip`. `application/octet-stream`
     * is the one MIME every extension already agrees with, so the name survives; the result is checked
     * rather than assumed, because this is an OEM surface and being wrong here would leave a stray file
     * named after a backup that never completed.
     */
    private fun stagedSafFile(dir: DocumentFile, name: String): DocumentFile {
        val partName = name + PART_SUFFIX
        val part = dir.createFile(OCTET_MIME, partName)
        if (part != null && part.name == partName) {
            return part
        }
        part?.delete()
        return dir.createFile(ZIP_MIME, name) ?: error("cannot create $name in ${dir.name}")
    }

    /**
     * Best-effort filesystem path for a SAF document ("primary:〇/x.zip" → "/storage/emulated/0/〇/x.zip"),
     * so an automation reply names a path 白い熊 can actually open. Falls back to the URI.
     */
    private fun displayPathOf(uri: Uri): String {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return uri.toString()
        val volume = docId.substringBefore(':', "")
        val relative = docId.substringAfter(':', "")
        if (volume.isEmpty() || relative.isEmpty()) {
            return uri.toString()
        }
        val root = if (volume == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return "$root/$relative"
    }

    // --- import ---

    /**
     * The categories present in a spooled export ZIP, read one entry at a time.
     *
     * The [ByteArray] pair of this and [import] is fine for the Export/Import page, where 白い熊 picked
     * the file himself. It is not fine for the automation data door: there the archive arrives on a
     * descriptor whose size the CALLER chooses, and reading it whole would put an unbounded allocation
     * in the middle of a restore. These two overloads stream a spooled file instead, holding only the
     * entry being applied.
     */
    fun categoriesIn(file: File): Set<Cat> {
        val found = LinkedHashSet<Cat>()
        streamEntries(file) { name, _ ->
            Cat.entries.firstOrNull { "${it.id}.json" == name }?.let { found += it }
        }
        return found
    }

    /**
     * Apply the selected categories from a spooled ZIP, one entry at a time. See [categoriesIn].
     *
     * [onProgress] is also the caller's heartbeat: a restore that goes quiet for two minutes is presumed
     * dead and its slot failed, so every category applied reports its position as it goes.
     */
    fun import(
        context: Context,
        file: File,
        cats: Set<Cat>,
        onProgress: ProgressReporter = { _, _, _, _ -> },
    ): String {
        val applied = LinkedHashMap<Cat, Int>()
        val fontsDir = FontHelper.getFontsDir(context)
        val unit = context.getString(R.string.state_progress_unit_category)
        val total = cats.size.toLong()
        streamEntries(file) { name, read ->
            val cat = Cat.entries.firstOrNull { "${it.id}.json" == name }
            when {
                cat != null && cat in cats -> {
                    val json = read().decodeToString()
                    val n = if (cat == Cat.NOTES) importNotes(context, json) else importPrefs(context, cat, json)
                    applied[cat] = (applied[cat] ?: 0) + n
                    val done = applied.keys.size.toLong()
                    val label = context.getString(cat.shortLabelRes)
                    onProgress(done, total, unit, "$unit $done/$total — $label")
                }
                // Strip the zip path — no traversal outside the fonts directory.
                name.startsWith(FONTS_DIR) && Cat.FONTS in cats -> {
                    val fileName = File(name).name
                    val wrote = fileName.isNotEmpty() &&
                        runCatching { File(fontsDir, fileName).writeBytes(read()) }.isSuccess
                    if (wrote) {
                        applied[Cat.FONTS] = (applied[Cat.FONTS] ?: 0) + 1
                    }
                }
            }
        }
        return if (applied.isEmpty()) {
            "nothing imported"
        } else {
            applied.entries.joinToString("\n") { "${context.getString(it.key.labelRes)}: ${it.value}" }
        }
    }

    /**
     * Walk a ZIP's file entries, handing each one its name and a lambda that reads THAT entry's bytes.
     * An entry whose bytes are never asked for is skipped without ever being decompressed into memory.
     */
    private fun streamEntries(file: File, onEntry: (name: String, read: () -> ByteArray) -> Unit) {
        ZipInputStream(FileInputStream(file).buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    onEntry(entry.name) { zip.readBytes() }
                }
                entry = zip.nextEntry
            }
        }
    }

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
            var applied = when (cat) {
                Cat.NOTES -> importNotes(context, data.decodeToString())
                else -> importPrefs(context, cat, data.decodeToString())
            }
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
        // commit(), not apply(): 応用管理 force-stops this app the instant an automation import reports
        // success — it has to, because an orderly shutdown writes cached preferences back out and would
        // silently undo the import. An apply()'s disk write is asynchronous and would simply be lost to
        // that kill. Both callers already run off the main thread, so the synchronous write costs nothing.
        editor.commit()
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

    /** Merge notes by title — the app's own identity for a note — so a restore updates, never duplicates. */
    private fun importNotes(context: Context, json: String): Int {
        val dao = NotesDatabase.getInstance(context).NotesDao()
        val array = JSONArray(json)
        var applied = 0
        for (entry in (0 until array.length()).mapNotNull { array.optJSONObject(it) }) {
            val title = entry.optString("title")
            if (title.isEmpty()) continue
            dao.insertOrUpdate(noteOf(entry, title, dao.getNoteIdWithTitleCaseSensitive(title)))
            applied++
        }
        return applied
    }

    private fun noteOf(entry: JSONObject, title: String, existingId: Long?): Note {
        // A file-backed note keeps pointing at its file only while that file is still here; otherwise the
        // backup's own copy of the text becomes the note's content.
        val path = entry.optString("path")
        val keepPath = path.isNotEmpty() && (path.startsWith("content://") || File(path).exists())
        return Note(
            id = existingId,
            title = title,
            value = entry.optString("value"),
            type = NoteType.fromValue(entry.optInt("type")),
            path = if (keepPath) path else "",
            protectionType = entry.optInt("protectionType", PROTECTION_NONE),
            protectionHash = entry.optString("protectionHash"),
        )
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
