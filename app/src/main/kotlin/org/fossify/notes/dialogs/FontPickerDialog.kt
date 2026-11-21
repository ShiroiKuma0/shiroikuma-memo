package org.fossify.notes.dialogs

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import org.fossify.notes.extensions.ThemeSlot
import org.fossify.notes.extensions.themeColor
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.notes.R
import org.fossify.notes.databinding.DialogFontPickerBinding
import org.fossify.notes.databinding.ItemFontOptionBinding
import org.fossify.notes.extensions.availableFontOptions
import org.fossify.notes.extensions.fontTypeface

// Lists every available font with its name drawn in its own typeface, plus an "Add font…" action.
class FontPickerDialog(
    private val activity: Activity,
    private val onAddFont: () -> Unit,
    private val onPick: (fileName: String) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private val binding = DialogFontPickerBinding.inflate(activity.layoutInflater)

    init {
        val textColor = activity.getProperTextColor()
        val primaryColor = activity.getProperPrimaryColor()

        activity.availableFontOptions().forEach { option ->
            val row = ItemFontOptionBinding.inflate(activity.layoutInflater, binding.fontPickerHolder, false)
            row.fontOptionLabel.text = option.displayName
            row.fontOptionLabel.setTextColor(textColor)
            row.fontOptionLabel.setIcon(R.drawable.ic_text_fields_vector, ThemeSlot.MENU_ICON_VIEW)
            row.fontOptionLabel.typeface = activity.fontTypeface(option.fileName)
            row.fontOptionLabel.setOnClickListener {
                dialog?.dismiss()
                onPick(option.fileName)
            }
            binding.fontPickerHolder.addView(row.root)
        }

        // the choices above, the one action below: a clear line between the two groups
        binding.fontPickerHolder.addView(
            View(activity).apply { setBackgroundColor(activity.uiColor(UiRole.LIST_DIVIDER)) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.resources.displayMetrics.density.toInt().coerceAtLeast(1),
            ),
        )
        val addRow = ItemFontOptionBinding.inflate(activity.layoutInflater, binding.fontPickerHolder, false)
        addRow.fontOptionLabel.text = activity.getString(R.string.theme_add_font)
        addRow.fontOptionLabel.setTextColor(primaryColor)
        addRow.fontOptionLabel.setIcon(org.fossify.commons.R.drawable.ic_plus_vector, ThemeSlot.MENU_ICON_OPEN)
        addRow.fontOptionLabel.setOnClickListener {
            dialog?.dismiss()
            onAddFont()
        }
        binding.fontPickerHolder.addView(addRow.root)

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.theme_font) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    // A row's icon, before its label and in the colour of what the row does.
    private fun TextView.setIcon(iconRes: Int, slot: ThemeSlot) {
        val density = resources.displayMetrics.density
        val size = (ICON_DP * density).toInt()
        val icon = resources.getColoredDrawableWithColor(iconRes, activity.themeColor(slot))
        icon.setBounds(0, 0, size, size)
        setCompoundDrawablesRelative(icon, null, null, null)
        compoundDrawablePadding = (ICON_GAP_DP * density).toInt()
    }

    private companion object {
        const val ICON_DP = 20
        const val ICON_GAP_DP = 10
    }
}
