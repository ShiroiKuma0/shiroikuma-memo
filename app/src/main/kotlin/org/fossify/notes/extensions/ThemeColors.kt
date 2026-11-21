package org.fossify.notes.extensions

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import org.fossify.commons.helpers.FAMILY_YELLOW
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.isUiColorSet
import org.fossify.commons.helpers.resetAllUiColors
import org.fossify.commons.helpers.resetUiColor
import org.fossify.commons.helpers.setUiColor
import org.fossify.commons.helpers.uiColor
import org.fossify.notes.R
import org.fossify.notes.helpers.THEME_UNSET

// Granular, per-element theming for 白い熊 メモ.
//
// Each [ThemeSlot] is one colour role of this app's own surfaces, settable on the UI page in the section
// of its item. The roles of everything the commons draws (buttons, dialogs, switches, fields, bars…) are
// commons' [UiRole]s; a slot with a [ThemeSlot.role] IS that commons role, shown here under this app's
// label and section, and is stored in the role's key. Every other slot inherits from a role by default,
// so a single foundation change cascades, and only diverges once it is given an explicit override
// (stored as an Int; THEME_UNSET means "follow the default").
//
// No colour is written in this app's XML, and the two primaries (#FFFF00 / #000000) are written once, in
// commons' role table (helpers/UiRoles.kt). The only literals below are colours that carry meaning.

// Top-level sections of this app's own slots on the 白い熊 メモ UI page.
enum class ThemeSection(@StringRes val labelRes: Int) {
    FOUNDATION(R.string.theme_section_foundation),
    CHROME(R.string.theme_section_topbar),
    CONTENT(R.string.theme_section_content),
    MENUS(R.string.theme_section_menus),
}

// Subgroups within a section. A subgroup header is only drawn when its section has more than one
// group; single-group sections render their rows directly under the section header.
enum class ThemeGroup(val section: ThemeSection, @StringRes val labelRes: Int) {
    FOUNDATION(ThemeSection.FOUNDATION, R.string.theme_section_foundation),
    MAIN_BAR(ThemeSection.CHROME, R.string.theme_group_main_bar),
    SUB_HEADER(ThemeSection.CHROME, R.string.theme_group_sub_header),
    TEXT_NOTE(ThemeSection.CONTENT, R.string.theme_group_text_note),
    CHECKLIST(ThemeSection.CONTENT, R.string.theme_group_checklist),
    TABS(ThemeSection.CONTENT, R.string.theme_group_tabs),
    LOCKED(ThemeSection.CONTENT, R.string.theme_group_locked),
    NOTE_PICKER(ThemeSection.CONTENT, R.string.theme_group_note_picker),
    MENU_ICONS(ThemeSection.MENUS, R.string.theme_section_menus),
}

enum class ThemeSlot(
    val key: String,
    val group: ThemeGroup,
    @StringRes val labelRes: Int,
    val isFoundation: Boolean = false,
    // hasFont = true for concrete text views (family / weight / size are configurable per element)
    val hasFont: Boolean = false,
    // The commons role this slot is (see the head of the file); null = a role of this app alone.
    val role: UiRole? = null,
) {
    // Foundation — the three roles every other default is derived from (editing these repaints the whole app)
    BACKGROUND(
        "theme_background", ThemeGroup.FOUNDATION, R.string.theme_background,
        isFoundation = true, role = UiRole.BACKGROUND,
    ),
    PRIMARY("theme_primary", ThemeGroup.FOUNDATION, R.string.theme_primary, isFoundation = true, role = UiRole.ACCENT),
    TEXT("theme_text", ThemeGroup.FOUNDATION, R.string.theme_text, isFoundation = true, role = UiRole.TEXT),
    TEXT_SECONDARY("theme_text_secondary", ThemeGroup.FOUNDATION, R.string.theme_text_secondary),
    // The explanations on the UI settings page and the thin rule down their left edge (black-yellow theme).
    SETTINGS_NOTE("theme_settings_note", ThemeGroup.FOUNDATION, R.string.theme_settings_note),
    // A status line that reports something wrong (no export directory, no export yet).
    STATUS_WARNING("theme_status_warning", ThemeGroup.FOUNDATION, R.string.theme_status_warning),

    // Top bar & menu — main screen: the top-right action/overflow icons and the overflow menu's labels
    MENU_ICON("theme_menu_icon", ThemeGroup.MAIN_BAR, R.string.theme_menu_icon),
    MENU_TEXT("theme_menu_text", ThemeGroup.MAIN_BAR, R.string.theme_menu_text, role = UiRole.MENU_LABEL),

    // Top bar & menu — sub-pages: the toolbar title + back arrow shown on Settings and every sub-page
    HEADER_TITLE(
        "theme_header_title", ThemeGroup.SUB_HEADER, R.string.theme_header_title,
        hasFont = true, role = UiRole.TOOLBAR_TITLE,
    ),
    HEADER_ARROW("theme_header_arrow", ThemeGroup.SUB_HEADER, R.string.theme_header_arrow, role = UiRole.TOOLBAR_ICON),

    // Text note
    NOTE_TEXT("theme_note_text", ThemeGroup.TEXT_NOTE, R.string.theme_note_text, hasFont = true),
    NOTE_COUNTER("theme_note_counter", ThemeGroup.TEXT_NOTE, R.string.theme_note_counter),
    SEARCH_HIGHLIGHT("theme_search_highlight", ThemeGroup.TEXT_NOTE, R.string.theme_search_highlight),

    // Checklist
    CHECKLIST_TEXT("theme_checklist_text", ThemeGroup.CHECKLIST, R.string.theme_checklist_text, hasFont = true),
    CHECKLIST_DONE("theme_checklist_done", ThemeGroup.CHECKLIST, R.string.theme_checklist_done),
    CHECKLIST_CHECKBOX("theme_checklist_checkbox", ThemeGroup.CHECKLIST, R.string.theme_checklist_checkbox),
    CHECKLIST_HANDLE("theme_checklist_handle", ThemeGroup.CHECKLIST, R.string.theme_checklist_handle),
    CHECKLIST_GROUP("theme_checklist_group", ThemeGroup.CHECKLIST, R.string.theme_checklist_group),

    // Note tabs (the pager title strip)
    TAB_TEXT("theme_tab_text", ThemeGroup.TABS, R.string.theme_tab_text, hasFont = true),
    TAB_INDICATOR("theme_tab_indicator", ThemeGroup.TABS, R.string.theme_tab_indicator),

    // A locked note: the lock and its line on the note itself, and the two marks of the unlock dialog
    LOCKED_NOTE("theme_locked_note", ThemeGroup.LOCKED, R.string.theme_locked_note),
    LOCK_CLOSED("theme_lock_closed", ThemeGroup.LOCKED, R.string.theme_lock_closed),
    LOCK_OPEN("theme_lock_open", ThemeGroup.LOCKED, R.string.theme_lock_open),

    // The "Open note" picker: a card per note
    PICKER_TITLE("theme_picker_title", ThemeGroup.NOTE_PICKER, R.string.theme_picker_title),
    PICKER_TEXT("theme_picker_text", ThemeGroup.NOTE_PICKER, R.string.theme_picker_text),
    PICKER_FRAME("theme_picker_frame", ThemeGroup.NOTE_PICKER, R.string.theme_picker_frame),

    // Menus and row icons: an icon is coloured by what its row does; a destructive row is the warning
    // colour throughout. (Frame and label of the other rows are commons' menu roles, shown beneath.)
    MENU_ICON_OPEN("theme_menu_icon_open", ThemeGroup.MENU_ICONS, R.string.menu_icon_open),
    MENU_ICON_WRITE("theme_menu_icon_write", ThemeGroup.MENU_ICONS, R.string.menu_icon_write),
    MENU_ICON_SHARE("theme_menu_icon_share", ThemeGroup.MENU_ICONS, R.string.menu_icon_share),
    MENU_ICON_VIEW("theme_menu_icon_view", ThemeGroup.MENU_ICONS, R.string.menu_icon_view),
    MENU_ICON_LOCK("theme_menu_icon_lock", ThemeGroup.MENU_ICONS, R.string.menu_icon_lock),
    MENU_WARNING("theme_menu_warning", ThemeGroup.MENU_ICONS, R.string.menu_warning),
}

// How far from the background toward the text a muted (secondary) text sits — opaque, never faded.
private const val SECONDARY_TEXT_BLEND = 0.6f
private const val DONE_ITEM_BLEND = 0.4f

// Colours that carry meaning keep their settled defaults; each is the default of a role, nothing more.
// The house dim gold for settings explanations — a colour of its own, never a greyed-out text colour.
private const val SETTINGS_NOTE_DIM_GOLD = 0xFFC8C800.toInt()
private const val STATUS_WARNING_RED = 0xFFFF5252.toInt()
// Menu icon roles (白い熊, 2026-11-17): open / load green, write blue (lighter than pure blue, which
// vanishes as a thin glyph on black), share orange, lock magenta, delete red.
private const val MENU_GREEN = 0xFF00FF00.toInt()
private const val MENU_BLUE = 0xFF4D94FF.toInt()
private const val MENU_ORANGE = 0xFFFF8000.toInt()
private const val MENU_MAGENTA = 0xFFFF00FF.toInt()
private const val MENU_RED = 0xFFFF0000.toInt()

/** The effective color for a slot: the user's override if set, otherwise its inherited default. */
fun Context.themeColor(slot: ThemeSlot): Int {
    slot.role?.let { return uiColor(it) }
    val override = config.getThemeOverride(slot.key)
    return if (override != THEME_UNSET) override else themeDefault(slot)
}

// One readable mapping of every slot to its inherited default; the long `when` is intentional.
@Suppress("CyclomaticComplexMethod") // one branch per slot — the exhaustive when is the point
private fun Context.themeDefault(slot: ThemeSlot): Int = when (slot) {
    // A slot that is a commons role never gets here (themeColor returns the role); the arm keeps the map total.
    ThemeSlot.BACKGROUND, ThemeSlot.PRIMARY, ThemeSlot.TEXT, ThemeSlot.MENU_TEXT,
    ThemeSlot.HEADER_TITLE, ThemeSlot.HEADER_ARROW -> uiColor(slot.role!!)

    ThemeSlot.TEXT_SECONDARY ->
        ColorUtils.blendARGB(themeColor(ThemeSlot.BACKGROUND), themeColor(ThemeSlot.TEXT), SECONDARY_TEXT_BLEND)
    ThemeSlot.SETTINGS_NOTE -> SETTINGS_NOTE_DIM_GOLD
    ThemeSlot.STATUS_WARNING -> STATUS_WARNING_RED

    // Top bar & menu — main screen: the icons follow the accent
    ThemeSlot.MENU_ICON -> themeColor(ThemeSlot.PRIMARY)

    // Text note
    ThemeSlot.NOTE_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.NOTE_COUNTER -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.SEARCH_HIGHLIGHT -> themeColor(ThemeSlot.PRIMARY)

    // Checklist
    ThemeSlot.CHECKLIST_TEXT -> themeColor(ThemeSlot.TEXT)
    // a done item is drawn at full opacity in this colour, so it is the dimmer of the two muted texts
    ThemeSlot.CHECKLIST_DONE ->
        ColorUtils.blendARGB(themeColor(ThemeSlot.BACKGROUND), themeColor(ThemeSlot.TEXT), DONE_ITEM_BLEND)
    ThemeSlot.CHECKLIST_CHECKBOX -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CHECKLIST_HANDLE -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CHECKLIST_GROUP -> themeColor(ThemeSlot.TEXT)

    // Tabs
    ThemeSlot.TAB_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.TAB_INDICATOR -> themeColor(ThemeSlot.PRIMARY)

    // Locked notes: closed red, open green — the two states must not be told apart by shape alone
    ThemeSlot.LOCKED_NOTE -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.LOCK_CLOSED -> MENU_RED
    ThemeSlot.LOCK_OPEN -> MENU_GREEN

    // Open-note picker
    ThemeSlot.PICKER_TITLE -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.PICKER_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.PICKER_FRAME -> uiColor(UiRole.LIST_DIVIDER)

    ThemeSlot.MENU_ICON_OPEN -> MENU_GREEN
    ThemeSlot.MENU_ICON_WRITE -> MENU_BLUE
    ThemeSlot.MENU_ICON_SHARE -> MENU_ORANGE
    ThemeSlot.MENU_ICON_VIEW -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.MENU_ICON_LOCK -> MENU_MAGENTA
    ThemeSlot.MENU_WARNING -> MENU_RED
}

/** Set an explicit override for a slot. A slot that is a commons role is stored as that role. */
fun Context.setThemeColor(slot: ThemeSlot, color: Int) {
    val role = slot.role
    if (role == null) {
        config.setThemeOverride(slot.key, color)
        return
    }
    if (role.isFoundation) config.isSystemThemeEnabled = false
    setUiColor(role, color)
}

/** Put a slot back on its default. */
fun Context.resetThemeColor(slot: ThemeSlot) {
    slot.role?.let { resetUiColor(it) } ?: config.clearThemeOverride(slot.key)
}

/** Every colour role back on its default: this app's slots and the commons roles alike. */
fun Context.resetAllThemeColors() {
    ThemeSlot.entries.forEach { config.clearThemeOverride(it.key) }
    resetAllUiColors()
}

/** One-time switch away from the stock (system / dynamic) theme; the colours themselves are role defaults. */
fun Context.seedBlackYellowThemeIfNeeded() {
    if (config.themeV1Seeded) {
        return
    }

    config.isSystemThemeEnabled = false
    config.themeV1Seeded = true
}

// Fork: dialog accent frame width, in dp.
private const val DIALOG_FRAME_WIDTH_DP = 3

// Fork: dialogs are black-on-black and invisible without a frame, so commons' dialog frame is switched on
// (its colour is the role UiRole.DIALOG_FRAME), and with it the family's look: dialog buttons as pills,
// the outlined switch and the outlined floating button. Set on every launch — the look is not optional.
fun Context.seedFamilyLook() {
    config.dialogBorderWidth = DIALOG_FRAME_WIDTH_DP
    config.pillDialogButtons = true
}

/**
 * The overrides of slots that became commons roles (+018, commons sk19) move into the roles' own keys, so
 * a colour set before the rebuild is kept. Run at every start rather than once: an import of an older
 * export brings such overrides back, and they have to move again.
 */
fun Context.migrateSlotsToRoles() {
    ThemeSlot.entries.forEach { slot ->
        val role = slot.role ?: return@forEach
        if (role.isFoundation) return@forEach
        val override = config.getThemeOverride(slot.key)
        if (override != THEME_UNSET) {
            setUiColor(role, override)
            config.clearThemeOverride(slot.key)
        }
    }
}

private const val RGB_MASK = 0xFFFFFF
private const val OLD_MATERIAL_YELLOW_RGB = 0xFFEB3B // the palette yellow before the pure-yellow swap

/**
 * One-time rewrite of every persisted color whose RGB is the old material yellow (#FFEB3B) to the
 * pure yellow (#FFFF00), keeping the alpha byte — the stock commons colors plus
 * every set theme-slot override. Per-element fonts and the upstream per-note widget colors are
 * untouched (the latter were never seeded from the palette).
 */
fun Context.migratePureYellowIfNeeded() {
    if (config.pureYellowMigrated) {
        return
    }

    config.backgroundColor = config.backgroundColor.toPureYellow()
    config.textColor = config.textColor.toPureYellow()
    config.primaryColor = config.primaryColor.toPureYellow()
    config.accentColor = config.accentColor.toPureYellow()
    for (slot in ThemeSlot.entries) {
        val override = config.getThemeOverride(slot.key)
        if (override != THEME_UNSET && override != override.toPureYellow()) {
            config.setThemeOverride(slot.key, override.toPureYellow())
        }
    }
    config.pureYellowMigrated = true
}

private fun Int.toPureYellow(): Int = if (this and RGB_MASK == OLD_MATERIAL_YELLOW_RGB) {
    (this and RGB_MASK.inv()) or (FAMILY_YELLOW and RGB_MASK)
} else {
    this
}
