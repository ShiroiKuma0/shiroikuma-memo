@file:Suppress("TooManyFunctions") // one small builder per export/import surface — splitting hurts cohesion

package org.fossify.notes.dialogs

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.notes.R
import org.fossify.notes.helpers.SettingsExport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Shared styling for the Export/Import panel and its info dialogs: black (background-colour) boxes
// with a yellow (accent) border, and ArcaneChat-style round pill buttons.
private const val BOX_RADIUS_DP = 16
private const val DIR_RADIUS_DP = 10
private const val PILL_RADIUS_DP = 100 // > half the button height → a pill
private const val BORDER_DP = 2f
private const val PILL_STROKE_DP = 1.5f
private const val TITLE_SP = 18f
private const val INFO_TITLE_SP = 19f
private const val DESC_SP = 13f
private const val CAPTION_SP = 12f
private const val VALUE_SP = 15f
private const val STATUS_SP = 14f
private const val CHECK_SP = 15f
private const val PAD_XL = 22
private const val PAD_L = 20
private const val PAD_M = 16
private const val PAD_S = 12
private const val PAD_XS = 10
private const val PAD_XXS = 8
private const val GAP = 8
private const val PAD_TINY = 6
private const val PILL_PAD_V = 10
private const val CHECK_PAD_V = 7
private const val DESC_ALPHA = 0.85f
private const val STATUS_ALPHA = 0.8f
private const val DIVIDER_ALPHA = 0.4f
private const val WARN_COLOR = 0xFFFF5252.toInt()
private const val FLASH_DISMISS_MS = 1400L
private const val FLASH_PAD_H = 24
private const val FLASH_PAD_V = 14
private const val FLASH_SP = 15f
private const val FLASH_BOTTOM_MARGIN_DP = 96

private fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

private fun Context.dpF(value: Float) = value * resources.displayMetrics.density

/** The bordered surface every export/import box uses: background fill + accent stroke, rounded. */
private fun Context.eximBorder(radiusDp: Int, accent: Int, fill: Int) = GradientDrawable().apply {
    cornerRadius = dpF(radiusDp.toFloat())
    setColor(fill)
    setStroke(dp(BORDER_DP.toInt()), accent)
}

private fun Context.eximText(value: String, sizeSp: Float, color: Int, bold: Boolean = false) =
    TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) {
            typeface = Typeface.DEFAULT_BOLD
        }
    }

/** An ArcaneChat-style round pill outline button: fully rounded ends, accent border and text. */
private fun Context.eximPillButton(label: String, accent: Int, fill: Int) = Button(this).apply {
    text = label
    isAllCaps = false
    setTextColor(accent)
    background = GradientDrawable().apply {
        cornerRadius = dpF(PILL_RADIUS_DP.toFloat())
        setColor(fill)
        setStroke(dp(PILL_STROKE_DP.toInt()), accent)
    }
    stateListAnimator = null
    minWidth = 0
    minimumWidth = 0
    setPadding(dp(PAD_L), dp(PILL_PAD_V), dp(PAD_L), dp(PILL_PAD_V))
}

private fun showEximBoxDialog(activity: Activity, box: LinearLayout, cancelable: Boolean): AlertDialog {
    val dialog = AlertDialog.Builder(activity)
        .setView(NestedScrollView(activity).apply { addView(box) })
        .setCancelable(cancelable)
        .create()
    dialog.show()
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    return dialog
}

/**
 * The Export/Import panel (Kōjiki flow): persisted export directory + latest-export status,
 * category checkboxes, and a pill button row — Cancel alone on the left, Import/Export right.
 * The action buttons never auto-dismiss; failures keep the panel open.
 */
@Suppress("TooManyFunctions")
class ExportImportDialog(
    private val activity: Activity,
    private val onPickDir: () -> Unit,
    private val onExport: (Set<SettingsExport.Cat>) -> Unit,
    private val onImport: (Set<SettingsExport.Cat>) -> Unit,
) {
    private val accent = activity.getProperPrimaryColor()
    private val fill = activity.getProperBackgroundColor()
    private val textColor = activity.getProperTextColor()
    private val checks = LinkedHashMap<SettingsExport.Cat, CheckBox>()
    private var folderValueTv: TextView? = null
    private var statusTv: TextView? = null
    private val dialog: AlertDialog

    init {
        dialog = showEximBoxDialog(activity, buildBox(), cancelable = true)
        refreshStatus()
    }

    fun dismiss() = dialog.dismiss()

    /** Re-read the persisted directory and scan it for the latest export (SAF scan off the UI thread). */
    fun refreshStatus() {
        val dirName = SettingsExport.exportDir(activity)?.name
            ?: SettingsExport.getDirUri(activity)?.lastPathSegment
        folderValueTv?.text = dirName ?: activity.getString(R.string.eim_dir_unset)
        folderValueTv?.setTextColor(if (dirName == null) WARN_COLOR else textColor)
        ensureBackgroundThread {
            val status = latestExportStatus(activity)
            activity.runOnUiThread {
                statusTv?.text = status.first
                statusTv?.setTextColor(if (status.second) WARN_COLOR else textColor)
                statusTv?.alpha = if (status.second) 1f else STATUS_ALPHA
            }
        }
    }

    private fun buildBox(): LinearLayout {
        val ctx = activity
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(PAD_L), ctx.dp(PAD_M), ctx.dp(PAD_L), ctx.dp(PAD_M))
            background = ctx.eximBorder(BOX_RADIUS_DP, accent, fill)
        }

        box.addView(ctx.eximText(ctx.getString(R.string.eim_dialog_title), TITLE_SP, accent, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, ctx.dp(2), 0, ctx.dp(PAD_TINY))
        })
        box.addView(ctx.eximText(ctx.getString(R.string.eim_dialog_desc), DESC_SP, textColor).apply {
            alpha = DESC_ALPHA
            setPadding(0, 0, 0, ctx.dp(PAD_XS))
        })

        box.addView(buildDirBox(), matchWrapParams().also {
            it.topMargin = ctx.dp(PAD_TINY)
            it.bottomMargin = ctx.dp(PAD_TINY)
        })
        val status = ctx.eximText("", STATUS_SP, textColor).apply {
            setPadding(ctx.dp(2), 0, 0, ctx.dp(PAD_XXS))
        }
        statusTv = status
        box.addView(status)

        box.addView(divider())
        box.addView(buildCategoryChecks())
        box.addView(divider().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ctx.dp(PAD_XXS) })
        box.addView(buildButtonRow())
        return box
    }

    private fun buildDirBox(): LinearLayout {
        val ctx = activity
        val dirBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setPadding(ctx.dp(PAD_S), ctx.dp(PAD_XS), ctx.dp(PAD_S), ctx.dp(PAD_XS))
            background = ctx.eximBorder(DIR_RADIUS_DP, accent, fill)
            setOnClickListener { onPickDir() }
        }
        dirBox.addView(ctx.eximText(ctx.getString(R.string.eim_dir_caption), CAPTION_SP, accent))
        val folderValue = ctx.eximText("", VALUE_SP, textColor, bold = true)
        folderValueTv = folderValue
        dirBox.addView(folderValue)
        return dirBox
    }

    private fun buildCategoryChecks(): LinearLayout {
        val ctx = activity
        val holder = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val selectAll = checkbox(ctx.getString(R.string.eim_select_all), bold = true).apply { isChecked = true }
        holder.addView(selectAll)
        for (cat in SettingsExport.Cat.entries) {
            val cb = checkbox(ctx.getString(cat.labelRes)).apply { isChecked = true }
            checks[cat] = cb
            holder.addView(cb)
        }
        selectAll.setOnCheckedChangeListener { _, isChecked ->
            checks.values.forEach { it.isChecked = isChecked }
        }
        return holder
    }

    // ArcaneChat-style dialog button row: round pills, Cancel alone left, Import/Export right.
    private fun buildButtonRow(): LinearLayout {
        val ctx = activity
        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ctx.dp(PAD_S), 0, 0)
        }
        buttons.addView(ctx.eximPillButton(ctx.getString(org.fossify.commons.R.string.cancel), accent, fill).apply {
            setOnClickListener { dismiss() }
        })
        buttons.addView(View(ctx), LinearLayout.LayoutParams(0, 0, 1f))
        buttons.addView(ctx.eximPillButton(ctx.getString(R.string.eim_import), accent, fill).apply {
            layoutParams = wrapParams().also { it.marginEnd = ctx.dp(GAP) }
            setOnClickListener { withSelection(onImport) }
        })
        buttons.addView(ctx.eximPillButton(ctx.getString(R.string.eim_export), accent, fill).apply {
            setOnClickListener { withSelection(onExport) }
        })
        return buttons
    }

    private fun withSelection(action: (Set<SettingsExport.Cat>) -> Unit) {
        val selected = checks.filterValues { it.isChecked }.keys
        if (selected.isEmpty()) {
            activity.toast(R.string.eim_none_selected)
        } else {
            action(selected)
        }
    }

    private fun checkbox(label: String, bold: Boolean = false): CheckBox = CheckBox(activity).apply {
        text = label
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, CHECK_SP)
        if (bold) {
            typeface = Typeface.DEFAULT_BOLD
        }
        buttonTintList = android.content.res.ColorStateList.valueOf(accent)
        setPadding(activity.dp(GAP), activity.dp(CHECK_PAD_V), 0, activity.dp(CHECK_PAD_V))
    }

    private fun divider(): View = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(accent)
        alpha = DIVIDER_ALPHA
    }

    private fun matchWrapParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun wrapParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

/**
 * A transient, toast-like "Exporting…" / "Importing…" flash in the export/import look: black
 * (background-colour) box, yellow (accent) border and text. Non-modal, auto-dismisses; the caller
 * may dismiss it earlier when the result arrives.
 */
fun showEximFlash(activity: Activity, textRes: Int): AlertDialog {
    val accent = activity.getProperPrimaryColor()
    val fill = activity.getProperBackgroundColor()
    val box = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            activity.dp(FLASH_PAD_H), activity.dp(FLASH_PAD_V),
            activity.dp(FLASH_PAD_H), activity.dp(FLASH_PAD_V),
        )
        background = activity.eximBorder(BOX_RADIUS_DP, accent, fill)
        addView(activity.eximText(activity.getString(textRes), FLASH_SP, accent, bold = true))
    }
    val dialog = AlertDialog.Builder(activity)
        .setView(box)
        .setCancelable(false)
        .create()
    dialog.window?.apply {
        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        setGravity(Gravity.BOTTOM)
        attributes = attributes.apply { y = activity.dp(FLASH_BOTTOM_MARGIN_DP) }
    }
    dialog.show()
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    box.postDelayed({ dialog.dismiss() }, FLASH_DISMISS_MS)
    return dialog
}

/** (message, isWarning) for the "last export" line — also used by the UI page's row summary. */
fun latestExportStatus(context: Context): Pair<String, Boolean> {
    if (SettingsExport.exportDir(context) == null) {
        return context.getString(R.string.eim_warn_nodir) to true
    }
    val newest = SettingsExport.latestExport(context)
        ?: return context.getString(R.string.eim_warn_none) to true
    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(newest.lastModified()))
    return context.getString(R.string.eim_last, ts) to false
}

/** Black/yellow bordered "Export finished" info dialog; OK acknowledges and closes the whole chain. */
fun showExportDoneDialog(activity: Activity, fileName: String, onAck: () -> Unit) {
    showEximInfoDialog(
        activity = activity,
        title = activity.getString(R.string.eim_export_done_title),
        body = activity.getString(R.string.eim_export_ok, fileName),
        buttons = listOf(activity.getString(org.fossify.commons.R.string.ok) to onAck),
    )
}

/** Import-finished dialog: "Later" closes the chain, "Restart now" restarts the app. */
fun showImportDoneDialog(activity: Activity, summary: String, onLater: () -> Unit, onRestart: () -> Unit) {
    showEximInfoDialog(
        activity = activity,
        title = activity.getString(R.string.eim_import_done_title),
        body = activity.getString(R.string.eim_import_done_body, summary),
        buttons = listOf(
            activity.getString(R.string.eim_restart_later) to onLater,
            activity.getString(R.string.eim_restart_now) to onRestart,
        ),
    )
}

private fun showEximInfoDialog(
    activity: Activity,
    title: String,
    body: String,
    buttons: List<Pair<String, () -> Unit>>,
) {
    val accent = activity.getProperPrimaryColor()
    val fill = activity.getProperBackgroundColor()
    val box = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(PAD_XL), activity.dp(PAD_L), activity.dp(PAD_XL), activity.dp(PAD_M))
        background = activity.eximBorder(BOX_RADIUS_DP, accent, fill)
    }
    box.addView(activity.eximText(title, INFO_TITLE_SP, accent, bold = true))
    box.addView(activity.eximText(body, STATUS_SP, accent).apply {
        setPadding(0, activity.dp(PAD_XS), 0, 0)
    })

    val buttonRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, activity.dp(PAD_M), 0, 0)
    }
    lateinit var dialog: AlertDialog
    buttons.forEachIndexed { index, (label, action) ->
        buttonRow.addView(activity.eximPillButton(label, accent, fill).apply {
            if (index < buttons.lastIndex) {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = activity.dp(PAD_XS) }
            }
            setPadding(activity.dp(PAD_M + 2), activity.dp(PAD_XXS), activity.dp(PAD_M + 2), activity.dp(PAD_XXS))
            setOnClickListener {
                dialog.dismiss()
                action()
            }
        })
    }
    box.addView(buttonRow)
    dialog = showEximBoxDialog(activity, box, cancelable = false)
}
