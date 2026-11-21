package org.fossify.notes.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import org.fossify.notes.R
import org.fossify.notes.extensions.ThemeSlot
import org.fossify.notes.extensions.themeColor
import org.fossify.commons.R as CR

/**
 * What a menu row needs to know to be drawn by the black-yellow theme's menu rule (views/PillMenuItemView):
 * its icon when the menu resource gave it none, whether it opens something (a › at the right end), and what
 * it does — which colours its icon. Menu items carry none of that, so this table supplies it by item id.
 *
 * Every menu item gets an icon in an instructive colour (白い熊, 2026-11-17): frame and label stay in the
 * menu colour — the warning colour for a destructive row — while the icon tells what the row does:
 * open / load green, write / create blue, share / export orange, view / settings yellow, lock magenta,
 * delete red. Each is a settable colour role (UI settings → Menus).
 *
 * A new menu item: add a line to [ROWS]. One without a line still becomes a pill, with a view-coloured
 * icon column and no ›.
 */
@SuppressLint("RestrictedApi")
@Suppress("TooManyFunctions") // one small question per part of a row
object MenuPills {
    const val DISABLED_ALPHA = 97 // 38 %
    const val TOGGLE_HEIGHT_DP = 36 // a toggle row: the switch and little more
    const val PILL_MARGIN_DP = 3 // above and below a pill
    const val SIDE_MARGIN_DP = 8 // from the pill to the edge of the menu

    /** What a row does; decides its icon colour. */
    enum class Role(val slot: ThemeSlot) {
        OPEN(ThemeSlot.MENU_ICON_OPEN),
        WRITE(ThemeSlot.MENU_ICON_WRITE),
        SHARE(ThemeSlot.MENU_ICON_SHARE),
        VIEW(ThemeSlot.MENU_ICON_VIEW),
        LOCK(ThemeSlot.MENU_ICON_LOCK),
        DELETE(ThemeSlot.MENU_WARNING),
    }

    class Row(val role: Role, val opens: Boolean = false, val icon: Int = 0)

    private val ROWS: Map<Int, Row> = mapOf(
        // main screen: the top bar and its ⋮
        R.id.save_note to Row(Role.WRITE),
        R.id.open_search to Row(Role.VIEW, opens = true),
        R.id.open_note to Row(Role.OPEN, opens = true),
        R.id.new_note to Row(Role.WRITE, opens = true),
        R.id.undo to Row(Role.VIEW),
        R.id.redo to Row(Role.VIEW),
        R.id.rename_note to Row(Role.WRITE, opens = true),
        R.id.remove_done_items to Row(Role.DELETE, icon = CR.drawable.ic_delete_vector),
        R.id.uncheck_all_items to Row(Role.WRITE, icon = CR.drawable.ic_reset_vector),
        R.id.sort_checklist to Row(Role.VIEW, opens = true),
        R.id.share to Row(Role.SHARE, opens = true),
        R.id.cab_create_shortcut to Row(Role.SHARE, icon = CR.drawable.ic_plus_vector),
        R.id.lock_note to Row(Role.LOCK, opens = true, icon = CR.drawable.ic_lock_vector),
        R.id.unlock_note to Row(Role.LOCK, opens = true, icon = R.drawable.ic_lock_open_vector),
        R.id.open_file to Row(Role.OPEN, opens = true, icon = CR.drawable.ic_folder_outline_vector),
        R.id.import_folder to Row(Role.OPEN, opens = true, icon = CR.drawable.ic_folder_vector),
        R.id.export_as_file to Row(Role.SHARE, opens = true, icon = CR.drawable.ic_share_outline_vector),
        R.id.print to Row(Role.SHARE, opens = true, icon = CR.drawable.ic_print_vector),
        R.id.delete_note to Row(Role.DELETE),
        R.id.ui_settings to Row(Role.VIEW, opens = true),
        R.id.settings to Row(Role.VIEW, opens = true),
        R.id.about to Row(Role.VIEW, opens = true),
        R.id.more_apps_from_us to Row(Role.VIEW, opens = true, icon = CR.drawable.ic_apps_vector),
        // checklist selection bar
        R.id.cab_move_to_top to Row(Role.WRITE),
        R.id.cab_move_to_bottom to Row(Role.WRITE),
        R.id.cab_rename to Row(Role.WRITE, opens = true),
        R.id.cab_delete to Row(Role.DELETE),
    )

    private fun row(item: MenuItem) = ROWS[item.itemId]

    /** The label of every row and, a role of its own, its frame. */
    fun accent(context: Context) = context.uiColor(UiRole.MENU_LABEL)

    fun frame(context: Context) = context.uiColor(UiRole.MENU_ROW_FRAME)

    fun warning(context: Context) = context.themeColor(ThemeSlot.MENU_WARNING)

    fun iconColor(context: Context, item: MenuItem) = context.themeColor((row(item)?.role ?: Role.VIEW).slot)

    /** The table icon of an item that brought none, or 0. */
    fun iconFor(item: MenuItem) = row(item)?.icon ?: 0

    fun opens(item: MenuItem): Boolean =
        item.hasSubMenu() || row(item)?.opens == true || item.title?.toString()?.trim()?.endsWith("…") == true

    fun isDestructive(item: MenuItem) = row(item)?.role == Role.DELETE

    /** The disabled title line a menu opens with. */
    private fun isHeader(item: MenuItem) = !item.isEnabled && item.itemId == 0 && !item.hasSubMenu()

    /** A row with a switch: checkable, and not one of an exclusive (radio) group. */
    fun isToggle(item: MenuItemImpl) = item.isCheckable && !item.isExclusiveCheckable

    /** An action row: everything that is not a toggle, a radio choice or a header. */
    fun isPill(item: MenuItemImpl) = !item.isCheckable && !isHeader(item)

    /**
     * Whether the rows of this menu keep an icon column. A list of pure choices with no icon anywhere stays
     * iconless; as soon as one row has an icon every row keeps the column, so the labels share one left edge.
     */
    fun anyIcon(menu: MenuBuilder?): Boolean {
        menu ?: return true
        return try {
            menu.visibleItems.any { it.icon != null || iconFor(it) != 0 }
        } catch (ignored: Throwable) {
            false
        }
    }

    /** The line between two groups: clearly there, quieter than a pill frame. A role, never a constant. */
    fun divider(context: Context) = context.uiColor(UiRole.MENU_DIVIDER)

    /** A switch that is on, in a menu as everywhere. */
    fun switchOn(context: Context) = context.uiColor(UiRole.SWITCH_ON_OUTLINE)

    /** A switch that is off: an opaque darker accent, never a faded one. */
    fun switchOff(context: Context) = context.uiColor(UiRole.SWITCH_OFF_OUTLINE)
}
