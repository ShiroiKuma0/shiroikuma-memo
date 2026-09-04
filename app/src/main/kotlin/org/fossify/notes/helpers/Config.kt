package org.fossify.notes.helpers

import android.content.Context
import android.os.Environment
import android.view.Gravity
import org.fossify.commons.helpers.BaseConfig
import org.fossify.notes.models.NoteType
import java.security.MessageDigest
import java.security.SecureRandom

@Suppress("TooManyFunctions")
class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var autosaveNotes: Boolean
        get() = prefs.getBoolean(AUTOSAVE_NOTES, true)
        set(autosaveNotes) = prefs.edit().putBoolean(AUTOSAVE_NOTES, autosaveNotes).apply()

    var displaySuccess: Boolean
        get() = prefs.getBoolean(DISPLAY_SUCCESS, false)
        set(displaySuccess) = prefs.edit().putBoolean(DISPLAY_SUCCESS, displaySuccess).apply()

    var clickableLinks: Boolean
        get() = prefs.getBoolean(CLICKABLE_LINKS, false)
        set(clickableLinks) = prefs.edit().putBoolean(CLICKABLE_LINKS, clickableLinks).apply()

    var monospacedFont: Boolean
        get() = prefs.getBoolean(MONOSPACED_FONT, false)
        set(monospacedFont) = prefs.edit().putBoolean(MONOSPACED_FONT, monospacedFont).apply()

    var showKeyboard: Boolean
        get() = prefs.getBoolean(SHOW_KEYBOARD, true)
        set(showKeyboard) = prefs.edit().putBoolean(SHOW_KEYBOARD, showKeyboard).apply()

    var showNotePicker: Boolean
        get() = prefs.getBoolean(SHOW_NOTE_PICKER, false)
        set(showNotePicker) = prefs.edit().putBoolean(SHOW_NOTE_PICKER, showNotePicker).apply()

    var showWordCount: Boolean
        get() = prefs.getBoolean(SHOW_WORD_COUNT, false)
        set(showWordCount) = prefs.edit().putBoolean(SHOW_WORD_COUNT, showWordCount).apply()

    var gravity: Int
        get() = prefs.getInt(GRAVITY, GRAVITY_START)
        set(size) = prefs.edit().putInt(GRAVITY, size).apply()

    var currentNoteId: Long
        get() = prefs.getLong(CURRENT_NOTE_ID, 1L)
        set(id) = prefs.edit().putLong(CURRENT_NOTE_ID, id).apply()

    var widgetNoteId: Long
        get() = prefs.getLong(WIDGET_NOTE_ID, 1L)
        set(id) = prefs.edit().putLong(WIDGET_NOTE_ID, id).apply()

    var placeCursorToEnd: Boolean
        get() = prefs.getBoolean(CURSOR_PLACEMENT, true)
        set(placement) = prefs.edit().putBoolean(CURSOR_PLACEMENT, placement).apply()

    var enableLineWrap: Boolean
        get() = prefs.getBoolean(ENABLE_LINE_WRAP, true)
        set(enableLineWrap) = prefs.edit().putBoolean(ENABLE_LINE_WRAP, enableLineWrap).apply()

    var lastUsedExtension: String
        get() = prefs.getString(LAST_USED_EXTENSION, "txt")!!
        set(lastUsedExtension) = prefs.edit().putString(LAST_USED_EXTENSION, lastUsedExtension).apply()

    var lastUsedSavePath: String
        get() = prefs.getString(LAST_USED_SAVE_PATH, Environment.getExternalStorageDirectory().toString())!!
        set(lastUsedSavePath) = prefs.edit().putString(LAST_USED_SAVE_PATH, lastUsedSavePath).apply()

    var useIncognitoMode: Boolean
        get() = prefs.getBoolean(USE_INCOGNITO_MODE, false)
        set(useIncognitoMode) = prefs.edit().putBoolean(USE_INCOGNITO_MODE, useIncognitoMode).apply()

    var lastCreatedNoteType: Int
        get() = prefs.getInt(LAST_CREATED_NOTE_TYPE, NoteType.TYPE_TEXT.value)
        set(lastCreatedNoteType) = prefs.edit().putInt(LAST_CREATED_NOTE_TYPE, lastCreatedNoteType).apply()

    var moveDoneChecklistItems: Boolean
        get() = prefs.getBoolean(MOVE_DONE_CHECKLIST_ITEMS, true)
        set(moveDoneChecklistItems) = prefs.edit().putBoolean(MOVE_DONE_CHECKLIST_ITEMS, moveDoneChecklistItems).apply()

    fun getTextGravity() = when (gravity) {
        GRAVITY_CENTER -> Gravity.CENTER_HORIZONTAL
        GRAVITY_END -> Gravity.END
        else -> Gravity.START
    }

    var fontSizePercentage: Int
        get() = prefs.getInt(FONT_SIZE_PERCENTAGE, 100)
        set(fontSizePercentage) = prefs.edit().putInt(FONT_SIZE_PERCENTAGE, fontSizePercentage).apply()

    var addNewChecklistItemsTop: Boolean
        get() = prefs.getBoolean(ADD_NEW_CHECKLIST_ITEMS_TOP, false)
        set(addNewCheckListItemsTop) = prefs.edit().putBoolean(ADD_NEW_CHECKLIST_ITEMS_TOP, addNewCheckListItemsTop).apply()

    fun getSorting(noteId: Long?): Int {
        return if (noteId == null) sorting else getFolderSorting(noteId.toString())
    }

    fun hasOwnSorting(noteId: Long?) = noteId != null && hasCustomSorting(noteId.toString())

    fun saveOwnSorting(noteId: Long, sorting: Int) = saveCustomSorting(noteId.toString(), sorting)

    fun removeOwnSorting(noteId: Long) = removeCustomSorting(noteId.toString())

    // 白い熊 メモ UI — granular theming: one Int override per color slot, THEME_UNSET = "follow the default".
    var themeV1Seeded: Boolean
        get() = prefs.getBoolean(THEME_V1_SEEDED, false)
        set(value) = prefs.edit().putBoolean(THEME_V1_SEEDED, value).apply()

    var pureYellowMigrated: Boolean
        get() = prefs.getBoolean(PURE_YELLOW_MIGRATED, false)
        set(value) = prefs.edit().putBoolean(PURE_YELLOW_MIGRATED, value).apply()

    fun getThemeOverride(key: String): Int = prefs.getInt(key, THEME_UNSET)

    fun setThemeOverride(key: String, color: Int) = prefs.edit().putInt(key, color).apply()

    fun clearThemeOverride(key: String) = prefs.edit().remove(key).apply()

    // Per-element fonts: family (filename, "" = default), weight (0 = default), size (sp, 0 = default).
    fun getFontFamily(slotKey: String): String = prefs.getString(FONT_FAMILY_PREFIX + slotKey, "")!!

    fun setFontFamily(slotKey: String, value: String) =
        prefs.edit().putString(FONT_FAMILY_PREFIX + slotKey, value).apply()

    fun getFontWeight(slotKey: String): Int = prefs.getInt(FONT_WEIGHT_PREFIX + slotKey, 0)

    fun setFontWeight(slotKey: String, value: Int) =
        prefs.edit().putInt(FONT_WEIGHT_PREFIX + slotKey, value).apply()

    fun getFontSize(slotKey: String): Int = prefs.getInt(FONT_SIZE_PREFIX + slotKey, 0)

    fun setFontSize(slotKey: String, value: Int) =
        prefs.edit().putInt(FONT_SIZE_PREFIX + slotKey, value).apply()

    // External-automation surface (StateExportReceiver + AutomationProvider): a master switch, and a
    // shared secret that is now OPTIONAL. Same model as every sister app's gate.
    //
    // Contract v2 (白い熊, 2026-09-04) flipped both defaults, and the reason is the clean phone: the
    // token used to be compulsory and the switch shipped OFF, so this app was unreachable until it had
    // been turned on and a 48-character secret pasted into the caller. A pasted secret cannot survive a
    // wipe, and restoring apps AND their data onto a wiped device is exactly what the family now exists
    // to do. So the switch ships ON, the token is opt-in, and the identity check that actually matters
    // moved to the data door, which can see who is calling (see automation/AutomationCallers).
    var automationEnabled: Boolean
        get() = prefs.getBoolean(AUTOMATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(AUTOMATION_ENABLED, value).apply()

    /** Whether a caller must also present [automationToken]. Default false — the token is opt-in now. */
    var automationRequireToken: Boolean
        get() = prefs.getBoolean(AUTOMATION_REQUIRE_TOKEN, false)
        set(value) = prefs.edit().putBoolean(AUTOMATION_REQUIRE_TOKEN, value).apply()

    /** The shared secret; generated on first read so the settings row always shows a value. */
    val automationToken: String
        get() = prefs.getString(AUTOMATION_TOKEN, null)?.takeIf { it.isNotEmpty() } ?: regenerateAutomationToken()

    fun regenerateAutomationToken(): String {
        val bytes = ByteArray(AUTOMATION_TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(AUTOMATION_TOKEN, token).apply()
        return token
    }

    /**
     * True when the caller's token matches the stored secret (constant-time). The enabled check is kept
     * separate so callers can report "automation disabled" and "bad token" as distinct failures.
     */
    fun isAutomationTokenValid(token: String?): Boolean {
        if (token.isNullOrEmpty()) {
            return false
        }
        return MessageDigest.isEqual(token.toByteArray(), automationToken.toByteArray())
    }

    /**
     * The whole automation gate, in the one place every entry point asks. Returns null to proceed, or the
     * exact `ERROR:` line to answer with.
     *
     * Written as one function on purpose: the receiver, the provider and the data service must not each
     * spell the two checks out in a subtly different order, which is how "automation disabled" and
     * "bad token" drift apart across a family of forty-two apps. They stay distinct answers because they
     * debug differently.
     *
     * **A token handed to an app that does not require one is IGNORED, never an error.** Tokens live in
     * task arguments and workspace variables that outlive the setting they were pasted for, and a caller
     * still sending one — because another app on the batch does want one — must be served. Refusing it
     * would turn "白い熊 turned a switch off" into "half the batch mysteriously fails".
     */
    fun refuseAutomation(candidate: String?): String? = when {
        !automationEnabled -> "ERROR:automation disabled"
        automationRequireToken && !isAutomationTokenValid(candidate) -> "ERROR:bad token"
        else -> null
    }
}
