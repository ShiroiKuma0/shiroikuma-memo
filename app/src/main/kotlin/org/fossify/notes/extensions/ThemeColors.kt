package org.fossify.notes.extensions

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.notes.R
import org.fossify.notes.helpers.PALETTE_BLACK
import org.fossify.notes.helpers.PALETTE_YELLOW
import org.fossify.notes.helpers.THEME_UNSET

private const val SECONDARY_TEXT_ALPHA = 0.6f

// Granular, per-element theming for 白い熊 メモ.
//
// Each [ThemeSlot] is one customizable color. Foundation slots reuse the stock commons colors
// (background / primary / text); every other slot inherits from a foundation slot by default
// (two-tier), so the whole app stays coherent and a single foundation change cascades. A slot
// only diverges once the user gives it an explicit override (stored as an Int; THEME_UNSET means
// "follow the default"). The default look is seeded to black background + yellow text/accents.

// Top-level sections shown on the 白い熊 メモ UI page (accent header + divider).
enum class ThemeSection(@StringRes val labelRes: Int) {
    FOUNDATION(R.string.theme_section_foundation),
    CHROME(R.string.theme_section_topbar),
    CONTENT(R.string.theme_section_content),
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
}

enum class ThemeSlot(
    val key: String,
    val group: ThemeGroup,
    @StringRes val labelRes: Int,
    val isFoundation: Boolean = false,
    // hasFont = true for concrete text views (family / weight / size are configurable per element)
    val hasFont: Boolean = false,
) {
    // Foundation — reuse the stock commons colors (editing these repaints the whole app)
    BACKGROUND("theme_background", ThemeGroup.FOUNDATION, R.string.theme_background, isFoundation = true),
    PRIMARY("theme_primary", ThemeGroup.FOUNDATION, R.string.theme_primary, isFoundation = true),
    TEXT("theme_text", ThemeGroup.FOUNDATION, R.string.theme_text, isFoundation = true),
    TEXT_SECONDARY("theme_text_secondary", ThemeGroup.FOUNDATION, R.string.theme_text_secondary),

    // Top bar & menu — main screen: the top-right action/overflow icons, the overflow ("hamburger")
    // item text, and the 設定 double-button characters
    MENU_ICON("theme_menu_icon", ThemeGroup.MAIN_BAR, R.string.theme_menu_icon),
    MENU_TEXT("theme_menu_text", ThemeGroup.MAIN_BAR, R.string.theme_menu_text),
    SETTINGS_BUTTON("theme_settings_button", ThemeGroup.MAIN_BAR, R.string.theme_settings_button, hasFont = true),

    // Top bar & menu — sub-pages: the toolbar title + back arrow shown on Settings and every sub-page
    HEADER_TITLE("theme_header_title", ThemeGroup.SUB_HEADER, R.string.theme_header_title, hasFont = true),
    HEADER_ARROW("theme_header_arrow", ThemeGroup.SUB_HEADER, R.string.theme_header_arrow),

    // Text note
    NOTE_TEXT("theme_note_text", ThemeGroup.TEXT_NOTE, R.string.theme_note_text, hasFont = true),
    NOTE_COUNTER("theme_note_counter", ThemeGroup.TEXT_NOTE, R.string.theme_note_counter),

    // Checklist
    CHECKLIST_TEXT("theme_checklist_text", ThemeGroup.CHECKLIST, R.string.theme_checklist_text, hasFont = true),
    CHECKLIST_DONE("theme_checklist_done", ThemeGroup.CHECKLIST, R.string.theme_checklist_done),
    CHECKLIST_CHECKBOX("theme_checklist_checkbox", ThemeGroup.CHECKLIST, R.string.theme_checklist_checkbox),

    // Note tabs (the pager title strip)
    TAB_TEXT("theme_tab_text", ThemeGroup.TABS, R.string.theme_tab_text, hasFont = true),
}

/** The effective color for a slot: the user's override if set, otherwise its inherited default. */
fun Context.themeColor(slot: ThemeSlot): Int {
    val override = config.getThemeOverride(slot.key)
    return if (override != THEME_UNSET) override else themeDefault(slot)
}

// One readable mapping of every slot to its inherited default; the long `when` is intentional.
@Suppress("CyclomaticComplexMethod") // one branch per slot — the exhaustive when is the point
private fun Context.themeDefault(slot: ThemeSlot): Int = when (slot) {
    // Foundation reads the stock commons colors (seeded to black/yellow on first run)
    ThemeSlot.BACKGROUND -> getProperBackgroundColor()
    ThemeSlot.PRIMARY -> getProperPrimaryColor()
    ThemeSlot.TEXT -> getProperTextColor()
    ThemeSlot.TEXT_SECONDARY -> themeColor(ThemeSlot.TEXT).adjustAlpha(SECONDARY_TEXT_ALPHA)

    // Top bar & menu — main screen: icons + 設定 button follow the accent, overflow text follows the body text
    ThemeSlot.MENU_ICON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.MENU_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.SETTINGS_BUTTON -> themeColor(ThemeSlot.PRIMARY)

    // Sub-pages: contrast the bar (which commons paints in the background color), matching its own default
    ThemeSlot.HEADER_TITLE -> themeColor(ThemeSlot.BACKGROUND).getContrastColor()
    ThemeSlot.HEADER_ARROW -> themeColor(ThemeSlot.BACKGROUND).getContrastColor()

    // Text note
    ThemeSlot.NOTE_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.NOTE_COUNTER -> themeColor(ThemeSlot.TEXT_SECONDARY)

    // Checklist
    ThemeSlot.CHECKLIST_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CHECKLIST_DONE -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.CHECKLIST_CHECKBOX -> themeColor(ThemeSlot.PRIMARY)

    // Tabs
    ThemeSlot.TAB_TEXT -> themeColor(ThemeSlot.TEXT)
}

/** Set an explicit override for a slot. Foundation slots write through to the stock commons colors. */
fun Context.setThemeColor(slot: ThemeSlot, color: Int) {
    when (slot) {
        ThemeSlot.PRIMARY -> {
            config.isSystemThemeEnabled = false
            config.primaryColor = color
            config.accentColor = color
        }

        ThemeSlot.BACKGROUND -> {
            config.isSystemThemeEnabled = false
            config.backgroundColor = color
        }

        ThemeSlot.TEXT -> {
            config.isSystemThemeEnabled = false
            config.textColor = color
        }

        else -> config.setThemeOverride(slot.key, color)
    }
}

/** Revert a slot to its default (palette for the editable foundation colors, inherited otherwise). */
fun Context.resetThemeColor(slot: ThemeSlot) {
    when (slot) {
        ThemeSlot.BACKGROUND -> setThemeColor(slot, PALETTE_BLACK)
        ThemeSlot.PRIMARY, ThemeSlot.TEXT -> setThemeColor(slot, PALETTE_YELLOW)
        else -> config.clearThemeOverride(slot.key)
    }
}

/** One-time seed of the default black/yellow look across the whole app (via the stock colors). */
fun Context.seedBlackYellowThemeIfNeeded() {
    if (config.themeV1Seeded) {
        return
    }

    config.isSystemThemeEnabled = false
    config.backgroundColor = PALETTE_BLACK
    config.textColor = PALETTE_YELLOW
    config.primaryColor = PALETTE_YELLOW
    config.accentColor = PALETTE_YELLOW
    config.themeV1Seeded = true
}

private const val RGB_MASK = 0xFFFFFF
private const val OLD_MATERIAL_YELLOW_RGB = 0xFFEB3B // PALETTE_YELLOW before the pure-yellow swap

/**
 * One-time rewrite of every persisted color whose RGB is the old material yellow (#FFEB3B) to the
 * pure-yellow PALETTE_YELLOW (#FFFF00), keeping the alpha byte — the stock commons colors plus
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
    (this and RGB_MASK.inv()) or (PALETTE_YELLOW and RGB_MASK)
} else {
    this
}
