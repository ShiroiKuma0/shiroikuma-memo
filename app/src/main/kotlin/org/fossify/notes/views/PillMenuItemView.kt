package org.fossify.notes.views

import org.fossify.commons.extensions.menuPanelDrawable
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.menu.ForkMenuAccess
import androidx.appcompat.view.menu.ListMenuItemView
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.graphics.ColorUtils
import org.fossify.notes.R
import org.fossify.notes.helpers.MenuPills

/**
 * The row of every popup menu — the toolbar ⋮ overflow, the selection menus' overflow, submenus — drawn by
 * the black-yellow theme's menu rule, so a menu shows at a glance which lines can be pressed:
 *  - an action row is an outlined pill spanning the menu, its icon inside at the left and a › at the right
 *    when it opens something;
 *  - the icon is coloured by what the row does (helpers/MenuPills), frame and label stay in the menu colour;
 *  - a destructive action takes the warning colour throughout, a disabled one is dimmed;
 *  - a toggle row stays plain and gets the outlined switch, never a checkbox.
 *
 * It is swapped in through the app's own copies of AppCompat's `abc_popup_menu_item_layout` and
 * `abc_cascading_menu_item_layout` — an app layout overrides the library layout of the same name — so no
 * call site asks for it. Ported from 白い熊 連絡先.
 */
@SuppressLint("RestrictedApi")
class PillMenuItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.listMenuViewStyle,
) : ListMenuItemView(context, attrs, defStyleAttr) {
    private var ready = false
    private var content: LinearLayout? = null
    private var title: TextView? = null
    private var arrow: ImageView? = null
    private var padStart = 0
    private var padTop = 0
    private var padEnd = 0
    private var padBottom = 0
    private var contentHeight = 0

    @Suppress("NestedBlockDepth")
    override fun onFinishInflate() {
        super.onFinishInflate()
        try {
            content = findViewById(androidx.appcompat.R.id.content)
            title = findViewById(androidx.appcompat.R.id.title)
            arrow = findViewById(androidx.appcompat.R.id.submenuarrow)
            val holder = content
            ready = holder != null && title != null
            if (holder != null && ready) {
                padStart = holder.paddingStart
                padTop = holder.paddingTop
                padEnd = holder.paddingEnd
                padBottom = holder.paddingBottom
                contentHeight = holder.layoutParams.height
                findViewById<View>(androidx.appcompat.R.id.group_divider)?.let { divider ->
                    // The stock divider is all but invisible on black: a clear line, as wide as the pills.
                    divider.setBackgroundColor(MenuPills.divider(context))
                    (divider.layoutParams as? MarginLayoutParams)?.let { lp ->
                        lp.leftMargin = dp(MenuPills.SIDE_MARGIN_DP)
                        lp.rightMargin = dp(MenuPills.SIDE_MARGIN_DP)
                        divider.layoutParams = lp
                    }
                }
            }
        } catch (ignored: Throwable) {
            ready = false
        }
    }

    override fun initialize(itemData: MenuItemImpl, menuType: Int) {
        var icons = true
        if (ready) {
            try {
                icons = MenuPills.anyIcon(ForkMenuAccess.menuOf(itemData))
                // Also keeps the icon column for a row whose icon is still missing.
                setForceShowIcon(icons)
            } catch (ignored: Throwable) {
            }
        }
        super.initialize(itemData, menuType)
        if (!ready) return
        try {
            if (icons && itemData.icon == null) {
                val res = MenuPills.iconFor(itemData)
                if (res != 0) setIcon(AppCompatResources.getDrawable(context, res))
            }
            style(itemData)
        } catch (ignored: Throwable) {
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The pill carries the ripple; the list's rectangular press highlight would paint over its round ends.
        val list = parent as? AbsListView ?: return
        if (ready && list.selector !is ColorDrawable) {
            list.selector = ColorDrawable(Color.TRANSPARENT)
            paintPanel(list)
        }
    }

    // The popup's own panel: the outermost ancestor that carries a ground (the theme's popup background)
    // is given the menu ground inside the menu frame, built from their roles.
    private fun paintPanel(list: View) {
        var panel: View? = null
        var v: View? = list
        while (v != null) {
            if (v.background != null) panel = v
            v = v.parent as? View
        }
        panel?.background = context.menuPanelDrawable()
    }

    @Suppress("CyclomaticComplexMethod") // one pass over every part of the row; splitting it scatters the rule
    private fun style(item: MenuItemImpl) {
        val holder = content ?: return
        val label = title ?: return
        val toggle = MenuPills.isToggle(item)
        val pill = MenuPills.isPill(item)
        val destructive = pill && MenuPills.isDestructive(item)

        fun dimmed(color: Int) =
            if (item.isEnabled) color else ColorUtils.setAlphaComponent(color, MenuPills.DISABLED_ALPHA)
        val rowColor = dimmed(if (destructive) MenuPills.warning(context) else MenuPills.accent(context))
        val iconColor = dimmed(MenuPills.iconColor(context, item))

        val lp = holder.layoutParams as MarginLayoutParams
        val h = dp(MenuPills.SIDE_MARGIN_DP)
        val v = if (pill) dp(MenuPills.PILL_MARGIN_DP) else 0
        // A toggle row has nothing to frame, so it is only as tall as its switch needs.
        val height = if (toggle) dp(MenuPills.TOGGLE_HEIGHT_DP) else contentHeight
        val placed = lp.leftMargin == h && lp.topMargin == v && lp.rightMargin == h && lp.bottomMargin == v
        if (!placed || lp.height != height) {
            lp.height = height
            lp.setMargins(h, v, h, v)
            holder.layoutParams = lp
        }
        // a destructive row lends its colour to the frame as well; every other frame has its own role
        val frameColor = if (destructive) rowColor else dimmed(MenuPills.frame(context))
        holder.background = if (pill) pillBackground(frameColor) else null
        // The switch of a toggle row ends where the pills end.
        holder.setPaddingRelative(padStart, padTop, if (toggle) 0 else padEnd, padBottom)

        // Plain text: a colour span left on the title by older styling would outrank the row's colour.
        label.text = item.title?.toString().orEmpty()
        label.setTextColor(rowColor)

        arrow?.let { chevronView ->
            val opens = pill && MenuPills.opens(item)
            if (opens) {
                AppCompatResources.getDrawable(context, R.drawable.ic_menu_chevron)?.mutate()?.let { chevron ->
                    chevron.setTint(rowColor)
                    chevronView.setImageDrawable(chevron)
                }
            }
            chevronView.visibility = if (opens) View.VISIBLE else View.GONE
        }

        for (i in 0 until holder.childCount) {
            val child = holder.getChildAt(i)
            if (child is CheckBox) {
                styleSwitch(child)
            } else if (child.id == androidx.appcompat.R.id.icon) {
                styleIcon(child, iconColor)
            }
        }
    }

    private fun styleSwitch(box: CheckBox) {
        if (box.tag != null) return
        box.buttonDrawable = OutlinedSwitchDrawable(
            MenuPills.switchOn(context), MenuPills.switchOff(context), dp(SWITCH_WIDTH_DP), dp(SWITCH_HEIGHT_DP), dp(2)
        )
        box.buttonTintList = null
        box.background = null
        // A long label stops short of the switch.
        (box.layoutParams as? MarginLayoutParams)?.let { lp ->
            lp.marginStart = dp(SWITCH_GAP_DP)
            box.layoutParams = lp
        }
        box.tag = true
    }

    private fun styleIcon(icon: View, color: Int) {
        // Clear of the round end of the pill, and not squeezed in a low row.
        val lp = icon.layoutParams as? MarginLayoutParams
        if (lp != null && lp.topMargin != dp(2)) {
            lp.marginStart = dp(ICON_INSET_DP)
            lp.marginEnd = -dp(ICON_PULL_DP)
            lp.topMargin = dp(2)
            lp.bottomMargin = dp(2)
            icon.layoutParams = lp
        }
        // The icon is coloured by what the row does, not by the row.
        (icon as? ImageView)?.apply {
            clearColorFilter()
            drawable?.mutate()?.clearColorFilter()
            imageTintList = ColorStateList.valueOf(color)
        }
    }

    private fun pillBackground(color: Int): Drawable {
        val radius = dp(PILL_RADIUS_DP).toFloat()
        val frame = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(maxOf(1, Math.round(PILL_STROKE_DP * resources.displayMetrics.density)), color)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        val ripple = ColorUtils.setAlphaComponent(color, Math.round(Color.alpha(color) * RIPPLE_ALPHA))
        return RippleDrawable(ColorStateList.valueOf(ripple), frame, mask)
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    private companion object {
        const val PILL_RADIUS_DP = 100
        const val PILL_STROKE_DP = 1.25f
        const val RIPPLE_ALPHA = 0.3f
        const val SWITCH_WIDTH_DP = 44
        const val SWITCH_HEIGHT_DP = 24
        const val SWITCH_GAP_DP = 8
        const val ICON_INSET_DP = 14
        const val ICON_PULL_DP = 6
    }
}
