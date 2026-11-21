package org.fossify.notes.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.chip.ChipGroup
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.circleDrawable
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.pillDrawable
import org.fossify.commons.extensions.rippleDrawable
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.UiRoleSection
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.helpers.resetUiColor
import org.fossify.commons.helpers.setUiColor
import org.fossify.commons.helpers.uiColor
import org.fossify.commons.models.RadioItem
import org.fossify.notes.R
import org.fossify.notes.databinding.ActivityThemeBinding
import org.fossify.notes.databinding.ItemThemeActionBinding
import org.fossify.notes.databinding.ItemThemeColorBinding
import org.fossify.notes.databinding.ItemThemeSubgroupBinding
import org.fossify.notes.databinding.ItemThemeSwitchBinding
import org.fossify.notes.databinding.ItemThemeTextBinding
import org.fossify.notes.databinding.ItemThemeTokenBinding
import org.fossify.notes.databinding.ItemThemeValueBinding
import org.fossify.notes.dialogs.AlphaColorPickerDialog
import org.fossify.notes.dialogs.ExportImportDialog
import org.fossify.notes.dialogs.FontPickerDialog
import org.fossify.notes.dialogs.latestExportStatus
import org.fossify.notes.dialogs.showEximFlash
import org.fossify.notes.dialogs.showExportDoneDialog
import org.fossify.notes.dialogs.showImportDoneDialog
import org.fossify.notes.extensions.FontWeightOption
import org.fossify.notes.extensions.ThemeGroup
import org.fossify.notes.extensions.ThemeSection
import org.fossify.notes.extensions.ThemeSlot
import org.fossify.notes.extensions.config
import org.fossify.notes.extensions.fontDisplayName
import org.fossify.notes.extensions.importFont
import org.fossify.notes.extensions.resetAllThemeColors
import org.fossify.notes.extensions.resetThemeColor
import org.fossify.notes.extensions.setThemeColor
import org.fossify.notes.extensions.showFontSample
import org.fossify.notes.extensions.themeColor
import org.fossify.notes.helpers.MAX_FONT_SIZE_SP
import org.fossify.notes.helpers.MenuPills
import org.fossify.notes.helpers.SettingsExport
import java.util.Locale
import org.fossify.commons.R as CR

// Each cascade level is indented one more step: a section's rows one step in, a subgroup's rows two.
private const val INDENT_STEP_DP = 18

// Every row is only as tall as its content (black-yellow theme, "Row padding — minimal, every row").
private const val ROW_PADDING_VERTICAL_DP = 2
private const val SECTION_GAP_DP = 10
private const val SECTION_HEADER_PAD_DP = 8
private const val SECTION_UNDERLINE_DP = 2
private const val SECTION_UNDERLINE_GAP_DP = 2
private const val SECTION_CHEVRON_SP = 22f

// The chip index under the app-settings pill.
private const val INDEX_GAP_DP = 6
private const val INDEX_TEXT_SP = 13f
private const val INDEX_PAD_H_DP = 10
private const val INDEX_PAD_V_DP = 5
private const val INDEX_DRAGGED_ALPHA = 0.3f

// A row's icon, before its label.
private const val ROW_ICON_DP = 20
private const val ROW_ICON_GAP_DP = 10
private const val NOTE_TEXT_SP = 13f

private const val TOKEN_ABBREVIATION_EDGE = 8
private const val ZIP_MIME = SettingsExport.ZIP_MIME

/** A top-level section of the page: its stored [key], its heading, and what builds its rows. */
private class UiSection(val key: String, val title: String, val fill: () -> Unit)

/**
 * The icon a row of the page carries, coloured by what the row does (black-yellow theme: "Every row has an
 * icon"). The colours are the menu icon roles, so a row and a menu line that do the same wear the same colour.
 */
private enum class RowIcon(val res: Int, val role: ThemeSlot) {
    SETTINGS(CR.drawable.ic_settings_cog_vector, ThemeSlot.MENU_ICON_VIEW),
    TOGGLE(CR.drawable.ic_check_circle_vector, ThemeSlot.MENU_ICON_VIEW),
    COLOR(R.drawable.ic_palette_vector, ThemeSlot.MENU_ICON_VIEW),
    TEXT(R.drawable.ic_text_fields_vector, ThemeSlot.MENU_ICON_VIEW),
    WEIGHT(R.drawable.ic_format_bold_vector, ThemeSlot.MENU_ICON_VIEW),
    SIZE(R.drawable.ic_tune_vector, ThemeSlot.MENU_ICON_VIEW),
    RESET(CR.drawable.ic_reset_vector, ThemeSlot.MENU_ICON_WRITE),
    SECRET(CR.drawable.ic_lock_vector, ThemeSlot.MENU_ICON_LOCK),
    SHARE(CR.drawable.ic_share_vector, ThemeSlot.MENU_ICON_SHARE),
    DIRECTORY(CR.drawable.ic_folder_vector, ThemeSlot.MENU_ICON_OPEN),
    AUTOMATION(CR.drawable.ic_code_vector, ThemeSlot.MENU_ICON_SHARE),
    DELETE(CR.drawable.ic_delete_vector, ThemeSlot.MENU_WARNING),
}

@Suppress("TooManyFunctions", "LargeClass")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    private var pendingFontSlot: ThemeSlot? = null
    private var pendingFontBinding: ItemThemeTextBinding? = null

    private var eximDialog: ExportImportDialog? = null
    private var eximFlash: androidx.appcompat.app.AlertDialog? = null
    private var eximRow: ItemThemeActionBinding? = null
    private var eximDirValue: TextView? = null
    private var pendingEximCats: Set<SettingsExport.Cat> = emptySet()

    // Where the row builders add their rows: the page itself, or the body of the section being filled.
    private lateinit var body: ViewGroup

    // The page's sections in their shown order, and each one's views by its key.
    private var sections: List<UiSection> = emptyList()
    private val sectionWrappers = HashMap<String, View>()
    private val sectionBodies = HashMap<String, LinearLayout>()
    private val sectionChevrons = HashMap<String, TextView>()
    private val sectionsFilled = HashSet<String>()

    private val fontImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onFontImported(uri)
    }

    private val eximDirPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                onEximDirPicked(uri)
            }
        }

    private val eximSaveAsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME)) { uri ->
            if (uri != null) {
                exportToUri(uri)
            }
        }

    private val eximImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importFromUri(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.themeNestedScrollview))
        setupMaterialScrollListener(binding.themeNestedScrollview, binding.themeAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.themeAppbar, NavigationIcon.Arrow)
        buildRows()
    }

    private fun stepPx() = dp(INDENT_STEP_DP)

    // The page: the way to the app's own settings, the chip index, then the sections — each a folding block,
    // folded unless 白い熊 opened it, in the order 白い熊 dragged the chips into (black-yellow theme, "Every UI
    // page — a folding accordion with a draggable chip index"). A folded section's rows are not built until
    // it is first opened.
    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()
        sectionWrappers.clear()
        sectionBodies.clear()
        sectionChevrons.clear()
        sectionsFilled.clear()
        eximRow = null
        eximDirValue = null
        body = binding.themeHolder

        val accent = getProperPrimaryColor()

        // The way to the app's own settings first, as the page's one thick pill; it is not a section.
        addActionRow(
            getString(R.string.app_settings_link), getString(R.string.app_settings_link_desc), 0,
            main = true, icon = RowIcon.SETTINGS,
        ) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Your data first (backup, housekeeping), then the look, surface by surface. The keys are stored:
        // never rename one.
        val built = ArrayList<UiSection>()
        built.add(UiSection("exim", getString(R.string.eim_heading)) { addExportImportSection() })
        built.add(UiSection("automation", getString(R.string.automation_section)) { addAutomationRows() })
        built.add(UiSection("maintenance", getString(R.string.maintenance_section)) { addMaintenanceSection() })
        built.add(themeSection(ThemeSection.FOUNDATION))
        // The roles of what the commons draws, each in the section of its item.
        fun roleSection(section: UiRoleSection, titleRes: Int = section.labelRes) = built.add(
            UiSection("roles_" + section.name.lowercase(Locale.ROOT), getString(titleRes)) { addRoleRows(section) }
        )
        roleSection(UiRoleSection.TEXT)
        roleSection(UiRoleSection.FIELDS)
        roleSection(UiRoleSection.BUTTONS)
        roleSection(UiRoleSection.DIALOGS)
        roleSection(UiRoleSection.SWITCHES)
        roleSection(UiRoleSection.MARKS)
        built.add(themeSection(ThemeSection.MENUS) { addRoleRows(UiRoleSection.MENUS) })
        roleSection(UiRoleSection.LISTS)
        built.add(themeSection(ThemeSection.CHROME))
        roleSection(UiRoleSection.BARS)
        built.add(themeSection(ThemeSection.CONTENT))
        roleSection(UiRoleSection.TOASTS)
        sections = orderedSections(built)

        addSectionIndex(accent)
        sections.forEachIndexed { index, section -> addSection(section, index == 0, accent) }
    }

    // One of this app's own sections: its groups, each under a sub-heading when there is more than one.
    private fun themeSection(section: ThemeSection, after: () -> Unit = {}) =
        UiSection("theme_" + section.name.lowercase(Locale.ROOT), getString(section.labelRes)) {
            val groups = ThemeGroup.entries.filter { it.section == section }
            val showSubgroups = groups.size > 1
            if (section == ThemeSection.MENUS) addNote(getString(R.string.menu_icon_note), stepPx())
            groups.forEach { group ->
                if (showSubgroups) {
                    addSubgroupHeader(getString(group.labelRes), stepPx())
                    addGroupSlots(group, stepPx() * 2)
                } else {
                    addGroupSlots(group, stepPx())
                }
            }
            after()
        }

    private fun addGroupSlots(group: ThemeGroup, indent: Int) {
        ThemeSlot.entries.filter { it.group == group }.forEach { slot ->
            if (slot.hasFont) addTextSlot(slot, indent) else addColorSlot(slot, indent)
        }
    }

    /** The stored order applied to [built]: stored keys first, then sections a later build added, at their place. */
    private fun orderedSections(built: List<UiSection>): List<UiSection> {
        val byKey = built.associateBy { it.key }
        val stored = config.uiSectionOrder.split(',').mapNotNull { byKey[it] }.distinct()
        return stored + built.filter { it !in stored }
    }

    // The jump index: a wrapping grid of small outlined chips, one per section. A tap unfolds the section
    // and scrolls to it; a long-press lifts the chip, the chips reorder live under the finger, and the
    // sections follow on drop.
    private fun addSectionIndex(accent: Int) {
        val margin = resources.getDimensionPixelSize(CR.dimen.activity_margin)
        val group = ChipGroup(this).apply {
            chipSpacingHorizontal = dp(INDEX_GAP_DP)
            chipSpacingVertical = dp(INDEX_GAP_DP)
            setPaddingRelative(margin, dp(INDEX_GAP_DP), margin, dp(INDEX_GAP_DP))
        }
        val background = getProperBackgroundColor()
        sections.forEach { section ->
            group.addView(TextView(this).apply {
                text = section.title
                tag = section.key
                setTextColor(accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, INDEX_TEXT_SP)
                setPadding(dp(INDEX_PAD_H_DP), dp(INDEX_PAD_V_DP), dp(INDEX_PAD_H_DP), dp(INDEX_PAD_V_DP))
                this.background = pillDrawable(uiColor(UiRole.BUTTON_FRAME), background, main = false)
                setOnClickListener { jumpToSection(section.key) }
                setOnLongClickListener { chip ->
                    chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    chip.startDragAndDrop(null, View.DragShadowBuilder(chip), chip, 0)
                    chip.alpha = INDEX_DRAGGED_ALPHA
                    true
                }
            })
        }

        val orderBefore = sections.map { it.key }
        // The chip last moved past: the same chip is not swapped again until the finger has been over the
        // dragged one, or two chips of unequal width would swap back and forth under a still finger.
        var lastTarget: View? = null
        group.setOnDragListener { _, event ->
            val dragged = event.localState as? View
            if (dragged == null || dragged.parent !== group) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val target = (0 until group.childCount).map { group.getChildAt(it) }
                        .firstOrNull { it.holds(event.x, event.y) }
                    if (target === dragged) {
                        lastTarget = null
                    } else if (target != null && target !== lastTarget) {
                        val to = group.indexOfChild(target)
                        group.removeView(dragged)
                        group.addView(dragged, to)
                        lastTarget = target
                    }
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    dragged.alpha = 1f
                    val order = (0 until group.childCount).map { group.getChildAt(it).tag as String }
                    if (order != orderBefore) {
                        config.uiSectionOrder = order.joinToString(",")
                        // not from inside the drag dispatch: the rebuild removes the view being dispatched to
                        group.post { buildRows() }
                    }
                }
            }
            true
        }
        binding.themeHolder.addView(group)
    }

    private fun View.holds(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom

    private fun wrapContent() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    // A folding section: the 1 px darker-accent line above it (every section but the first; it stays whether
    // the section is folded or open), its bold heading underlined as wide as its text with › folded / ⌄ open
    // at the right, and its rows in a body that folds away.
    private fun addSection(section: UiSection, first: Boolean, accent: Int) {
        val margin = resources.getDimensionPixelSize(CR.dimen.activity_margin)
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (!first) {
            wrapper.addView(
                View(this).apply { setBackgroundColor(MenuPills.divider(this@ThemeActivity)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    .apply { topMargin = dp(SECTION_GAP_DP) },
            )
        }

        val label = TextView(this).apply {
            text = section.title
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(CR.dimen.bigger_text_size))
            setTypeface(typeface, Typeface.BOLD)
        }
        val labelColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label, wrapContent())
            // as wide as the column, which is as wide as the label's text
            addView(
                View(this@ThemeActivity).apply { setBackgroundColor(accent) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(SECTION_UNDERLINE_DP))
                    .apply { topMargin = dp(SECTION_UNDERLINE_GAP_DP) },
            )
        }
        val chevron = TextView(this).apply {
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, SECTION_CHEVRON_SP)
            includeFontPadding = false
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(margin, dp(SECTION_HEADER_PAD_DP), margin, dp(SECTION_HEADER_PAD_DP))
            background = rippleDrawable()
            addView(labelColumn, wrapContent())
            addView(View(this@ThemeActivity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(chevron)
            setOnClickListener { setSectionOpen(section.key, section.key !in config.uiSectionsOpen) }
        }
        wrapper.addView(header)

        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(inner)
        binding.themeHolder.addView(wrapper)

        sectionWrappers[section.key] = wrapper
        sectionBodies[section.key] = inner
        sectionChevrons[section.key] = chevron
        showSectionState(section.key, section.key in config.uiSectionsOpen)
    }

    private fun setSectionOpen(key: String, open: Boolean) {
        val stored = config.uiSectionsOpen
        config.uiSectionsOpen = if (open) stored + key else stored - key
        showSectionState(key, open)
    }

    private fun showSectionState(key: String, open: Boolean) {
        val inner = sectionBodies[key] ?: return
        if (open && sectionsFilled.add(key)) {
            // rows are added to `body`; it points here only while this section's own rows are built
            body = inner
            sections.firstOrNull { it.key == key }?.fill?.invoke()
            body = binding.themeHolder
        }
        inner.beVisibleIf(open)
        sectionChevrons[key]?.text = if (open) "⌄" else "›"
    }

    /** A tap on an index chip: unfold the section and scroll to it. */
    private fun jumpToSection(key: String) {
        val wrapper = sectionWrappers[key] ?: return
        setSectionOpen(key, true)
        binding.themeHolder.post { binding.themeNestedScrollview.smoothScrollTo(0, wrapper.top) }
    }

    // ---- Rows ----

    private fun addSubgroupHeader(label: String, indent: Int) {
        val accent = getProperPrimaryColor()
        val item = ItemThemeSubgroupBinding.inflate(layoutInflater, body, false)
        item.themeSubgroupLabel.text = label
        item.themeSubgroupLabel.setTextColor(accent)
        item.themeSubgroupUnderline.setBackgroundColor(accent)
        val margin = resources.getDimensionPixelSize(CR.dimen.activity_margin)
        item.root.apply { setPaddingRelative(margin + indent, paddingTop, paddingEnd, paddingBottom) }
        body.addView(item.root)
    }

    // A loose note that belongs to no single row: dim gold, no rule.
    private fun addNote(text: String, indent: Int) {
        val margin = resources.getDimensionPixelSize(CR.dimen.activity_margin)
        body.addView(TextView(this).apply {
            this.text = text
            setTextColor(themeColor(ThemeSlot.SETTINGS_NOTE))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, NOTE_TEXT_SP)
            val vertical = dp(ROW_PADDING_VERTICAL_DP)
            setPaddingRelative(margin + indent + rowIconInset(), vertical, margin, vertical)
        })
    }

    private fun addColorSlot(slot: ThemeSlot, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(getProperTextColor())
        row.themeColorLabel.setRowIcon(RowIcon.COLOR)
        row.themeColorPreview.background = swatch(themeColor(slot))
        row.root.background = rippleDrawable()
        row.root.setOnClickListener { openColorPicker(slot) }
        indentRow(row.root, indent)
        previews[slot] = row.themeColorPreview
        body.addView(row.root)
    }

    // ---- Colour roles of what the commons draws ----

    // One swatch row per role of [section] that no slot of this app already shows under its own label.
    private fun addRoleRows(section: UiRoleSection) {
        val shownAsSlots = ThemeSlot.entries.mapNotNull { it.role }.toSet()
        if (section == UiRoleSection.BARS) addNote(getString(R.string.system_bar_role_note), stepPx())
        UiRole.entries.filter { it.section == section && it !in shownAsSlots }.forEach { addRoleRow(it, stepPx()) }
    }

    private fun addRoleRow(role: UiRole, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        row.themeColorLabel.text = getString(role.labelRes)
        row.themeColorLabel.setTextColor(getProperTextColor())
        row.themeColorLabel.setRowIcon(RowIcon.COLOR)
        row.themeColorPreview.background = swatch(uiColor(role))
        row.root.background = rippleDrawable()
        row.root.setOnClickListener {
            AlphaColorPickerDialog(this, uiColor(role), addDefaultColorButton = true) { wasPositive, color ->
                if (wasPositive) setUiColor(role, color) else resetUiColor(role)
                // a role may be the default of others, and the page itself is drawn with several of them
                recreate()
            }
        }
        indentRow(row.root, indent)
        body.addView(row.root)
    }

    // A swatch: the colour inside a thin ring of the text role, so a swatch in the background colour shows.
    private fun swatch(color: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(1), getProperTextColor())
    }

    @Suppress("EmptyFunctionBlock") // SeekBar's start/stop-tracking callbacks are intentionally no-ops
    private fun addTextSlot(slot: ThemeSlot, indent: Int) {
        val textColor = getProperTextColor()
        val b = ItemThemeTextBinding.inflate(layoutInflater, body, false)
        b.themeTextLabel.text = getString(slot.labelRes)
        listOf(
            b.themeTextLabel, b.themeTextFontTitle, b.themeTextFontValue,
            b.themeTextWeightTitle, b.themeTextWeightValue, b.themeTextSizeTitle, b.themeTextSizeValue
        ).forEach { it.setTextColor(textColor) }
        // the current values in the accent colour: they are what a tap changes
        listOf(b.themeTextFontValue, b.themeTextWeightValue).forEach { it.setTextColor(getProperPrimaryColor()) }

        // the element's own line sets its colour; the lines beneath set its font, weight and size
        b.themeTextLabel.setRowIcon(RowIcon.COLOR)
        b.themeTextFontTitle.setRowIcon(RowIcon.TEXT)
        b.themeTextWeightTitle.setRowIcon(RowIcon.WEIGHT)
        b.themeTextSizeTitle.setRowIcon(RowIcon.SIZE)
        b.themeTextColorPreview.background = swatch(themeColor(slot))
        b.themeTextFontValue.text = fontDisplayName(config.getFontFamily(slot.key))
        b.themeTextWeightValue.text = getString(FontWeightOption.fromValue(config.getFontWeight(slot.key)).labelRes)
        b.themeTextSizeSeekbar.max = MAX_FONT_SIZE_SP
        b.themeTextSizeSeekbar.progress = config.getFontSize(slot.key)
        b.themeTextSizeValue.text = sizeLabel(config.getFontSize(slot.key))
        refreshSample(b, slot)

        listOf(b.themeTextColorRow, b.themeTextFontRow, b.themeTextWeightRow).forEach {
            it.background = rippleDrawable()
        }
        b.themeTextColorRow.setOnClickListener { openTextColorPicker(slot, b) }
        b.themeTextFontRow.setOnClickListener { openFontPicker(slot, b) }
        b.themeTextWeightRow.setOnClickListener { openWeightPicker(slot, b) }
        b.themeTextSizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                config.setFontSize(slot.key, progress)
                b.themeTextSizeValue.text = sizeLabel(progress)
                refreshSample(b, slot)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        indentRow(b.themeTextColorRow, indent)
        // the element's font / weight / size / sample sit one full step deeper than its label row
        listOf(b.themeTextFontRow, b.themeTextWeightRow, b.themeTextSizeRow, b.themeTextSample).forEach {
            indentRow(it, indent + stepPx())
        }
        body.addView(b.root)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // The row's icon, before its label and in the colour of what the row does.
    private fun TextView.setRowIcon(icon: RowIcon) {
        val size = dp(ROW_ICON_DP)
        val drawable = resources.getColoredDrawableWithColor(icon.res, themeColor(icon.role))
        drawable.setBounds(0, 0, size, size)
        setCompoundDrawablesRelative(drawable, null, null, null)
        compoundDrawablePadding = dp(ROW_ICON_GAP_DP)
    }

    // How far a line beneath a label is moved in, so that it starts under the label's text and not under its icon.
    private fun rowIconInset() = dp(ROW_ICON_DP + ROW_ICON_GAP_DP)

    /**
     * Every plain row passes through here, so this is also where the vertical rhythm is set: the commons
     * row style pads a single line of text generously above and below, which is replaced with
     * [ROW_PADDING_VERTICAL_DP]; the horizontal padding is kept and the indent added. No minimum height.
     */
    private fun indentRow(view: View, indent: Int) {
        val vertical = dp(ROW_PADDING_VERTICAL_DP)
        view.minimumHeight = 0
        view.setPaddingRelative(view.paddingStart + indent, vertical, view.paddingEnd, vertical)
    }

    private fun refreshSample(b: ItemThemeTextBinding, slot: ThemeSlot) {
        b.themeTextSample.showFontSample(
            config.getFontFamily(slot.key),
            config.getFontWeight(slot.key),
            config.getFontSize(slot.key),
            themeColor(slot)
        )
    }

    private fun sizeLabel(sp: Int) = if (sp > 0) "$sp sp" else getString(R.string.theme_size_default)

    private fun openColorPicker(slot: ThemeSlot) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            if (slot.role != null || slot.group == ThemeGroup.FOUNDATION || slot.group == ThemeGroup.MENU_ICONS) {
                // a commons role or a foundation colour cascades into the chrome, this page's own rows and
                // every inheriting preview
                recreate()
            } else {
                previews[slot]?.background = swatch(themeColor(slot))
            }
        }
    }

    private fun openTextColorPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            b.themeTextColorPreview.background = swatch(themeColor(slot))
            refreshSample(b, slot)
        }
    }

    private fun openFontPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        FontPickerDialog(
            activity = this,
            onAddFont = {
                pendingFontSlot = slot
                pendingFontBinding = b
                fontImportLauncher.launch(arrayOf("*/*"))
            },
            onPick = { fileName ->
                config.setFontFamily(slot.key, fileName)
                b.themeTextFontValue.text = fontDisplayName(fileName)
                refreshSample(b, slot)
            }
        )
    }

    private fun openWeightPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        val items = ArrayList(FontWeightOption.entries.map { RadioItem(it.value, getString(it.labelRes)) })
        RadioGroupDialog(this, items, config.getFontWeight(slot.key)) {
            val weight = it as Int
            config.setFontWeight(slot.key, weight)
            b.themeTextWeightValue.text = getString(FontWeightOption.fromValue(weight).labelRes)
            refreshSample(b, slot)
        }
    }

    // ---- Export / Import (Kōjiki flow): where exports go, and the panel that does the work ----

    private fun addExportImportSection() {
        eximDirValue = addValueRow(getString(R.string.eim_dir_caption), eximDirLabel(), RowIcon.DIRECTORY) {
            eximDirPickerLauncher.launch(SettingsExport.getDirUri(this))
        }.themeValueValue
        eximRow = addActionRow(
            getString(R.string.eim_open), getString(R.string.eim_row_summary), stepPx(),
            main = true, icon = RowIcon.SHARE,
        ) { openExportImport() }
        refreshEximRowStatus()
    }

    // ---- Automation: the intent 白い熊 自由作業盤 exports through, and the data door 白い熊 応用管理
    // restores this app through on a clean phone ----

    private fun addAutomationRows() {
        // Three rows, in the order every sister app uses: the master switch (default ON since contract
        // v2), the token opt-in (default OFF), and the token itself — shown only when it is actually
        // being asked for. A 48-character secret sitting under an off switch only invites 白い熊 to paste
        // it somewhere it would do nothing.
        addSwitchRow(
            title = getString(R.string.enable_automation),
            description = getString(R.string.enable_automation_desc),
            checked = config.automationEnabled,
            icon = RowIcon.AUTOMATION,
        ) { config.automationEnabled = it }

        addSwitchRow(
            title = getString(R.string.automation_require_token),
            description = getString(R.string.automation_require_token_desc),
            checked = config.automationRequireToken,
            icon = RowIcon.SECRET,
        ) {
            config.automationRequireToken = it
            // Rebuilt rather than toggled: the token row below exists only while this switch is on.
            // Posted, so the rows are not torn down inside their own click dispatch.
            binding.themeHolder.post { buildRows() }
        }

        if (config.automationRequireToken) {
            addTokenRow()
        }

        // All-files access: needed so an automation broadcast can write to an arbitrary absolute path
        // (e.g. 白い熊's backup directory) outside Download/Documents. API 30+ only. It opens a system
        // screen, so it is a pill; its state is the first line of the explanation beneath.
        if (isRPlus()) {
            val granted = Environment.isExternalStorageManager()
            val state = getString(if (granted) R.string.all_files_access_granted else R.string.all_files_access_needed)
            val row = addActionRow(
                getString(R.string.all_files_access), state + "\n" + getString(R.string.all_files_access_desc),
                stepPx(), icon = RowIcon.DIRECTORY,
            ) { openAllFilesAccessSettings() }
            if (!granted) row.themeActionDesc.setTextColor(themeColor(ThemeSlot.STATUS_WARNING))
        }
    }

    // ---- Maintenance ----

    private fun addMaintenanceSection() {
        addActionRow(
            getString(R.string.reset_section_order), getString(R.string.reset_section_order_desc), stepPx(),
            opens = false, icon = RowIcon.RESET,
        ) {
            config.uiSectionOrder = ""
            config.uiSectionsOpen = emptySet()
            buildRows()
        }
        addActionRow(
            getString(R.string.reset_all_colours), getString(R.string.reset_all_colours_desc), stepPx(),
            opens = false, destructive = true, icon = RowIcon.DELETE,
        ) {
            ConfirmationDialog(
                activity = this,
                message = getString(R.string.reset_all_colours_confirm),
                positive = R.string.reset_action,
                negative = CR.string.cancel,
                destructive = true,
            ) {
                resetAllThemeColors()
                recreate()
            }
        }
    }

    /**
     * A switch row with its explanation (black-yellow theme, "Settings rows with explanations"): bold accent
     * label, the explanation beneath in the settings-note dim gold, a thin dim-gold rule down the left edge.
     */
    private fun addSwitchRow(
        title: String,
        description: String,
        checked: Boolean,
        icon: RowIcon = RowIcon.TOGGLE,
        onChange: (Boolean) -> Unit,
    ) {
        val b = ItemThemeSwitchBinding.inflate(layoutInflater, body, false)
        b.root.background = rippleDrawable()
        val note = themeColor(ThemeSlot.SETTINGS_NOTE)
        b.themeSwitchLabel.text = title
        b.themeSwitchLabel.setTextColor(getProperPrimaryColor())
        b.themeSwitchLabel.setTypeface(b.themeSwitchLabel.typeface, Typeface.BOLD)
        b.themeSwitchLabel.setRowIcon(icon)
        b.themeSwitchDesc.setPaddingRelative(rowIconInset(), b.themeSwitchDesc.paddingTop, 0, 0)
        b.themeSwitchDesc.text = description
        b.themeSwitchDesc.setTextColor(note)
        b.themeSwitchRule.setBackgroundColor(note)
        b.themeSwitch.minHeight = 0
        b.themeSwitch.minimumHeight = 0
        b.themeSwitch.isChecked = checked
        b.root.setOnClickListener {
            b.themeSwitch.toggle()
            onChange(b.themeSwitch.isChecked)
        }
        indentRow(b.root, stepPx())
        body.addView(b.root)
    }

    /**
     * The automation-token row: label plus the abbreviated token, tapping anywhere copies the full token,
     * and a Regenerate pill on the right warns before invalidating pasted copies.
     */
    private fun addTokenRow() {
        val b = ItemThemeTokenBinding.inflate(layoutInflater, body, false)
        b.root.background = rippleDrawable()
        b.themeTokenLabel.text = getString(R.string.automation_token)
        b.themeTokenLabel.setTextColor(getProperTextColor())
        b.themeTokenLabel.setRowIcon(RowIcon.SECRET)
        b.themeTokenValue.text = abbreviateToken(config.automationToken)
        b.themeTokenValue.setTextColor(getProperPrimaryColor())
        b.themeTokenValue.setPaddingRelative(rowIconInset(), 0, 0, 0)
        // An action, so a pill (regular weight) — never bare text.
        b.themeTokenRegenerate.text = getString(R.string.automation_token_regenerate)
        b.themeTokenRegenerate.setTextColor(uiColor(UiRole.BUTTON_TEXT))
        b.themeTokenRegenerate.background =
            pillDrawable(uiColor(UiRole.BUTTON_FRAME), uiColor(UiRole.BUTTON_GROUND), main = false)
        b.root.setOnClickListener { copyToken() }
        b.themeTokenRegenerate.setOnClickListener { regenerateToken(b) }
        indentRow(b.root, stepPx())
        body.addView(b.root)
    }

    private fun copyToken() {
        // Not commons' copyToClipboard: that one toasts the value itself, which would put the full
        // secret back on screen right after we deliberately abbreviated it.
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.automation_token), config.automationToken))
        toast(R.string.automation_token_copied)
    }

    private fun regenerateToken(row: ItemThemeTokenBinding) {
        ConfirmationDialog(
            activity = this,
            message = getString(R.string.automation_token_regenerate_warning),
            positive = R.string.automation_token_regenerate,
            negative = CR.string.cancel,
        ) {
            row.themeTokenValue.text = abbreviateToken(config.regenerateAutomationToken())
            toast(R.string.automation_token_regenerated)
        }
    }

    // Shown abbreviated so the secret is not left on screen; the tap still copies it in full.
    private fun abbreviateToken(token: String): String =
        if (token.length <= TOKEN_ABBREVIATION_EDGE * 2) {
            token
        } else {
            token.take(TOKEN_ABBREVIATION_EDGE) + "…" + token.takeLast(TOKEN_ABBREVIATION_EDGE)
        }

    // A control row: a label and, in the accent colour, the value a tap changes.
    private fun addValueRow(title: String, value: String, icon: RowIcon, onClick: () -> Unit): ItemThemeValueBinding {
        val b = ItemThemeValueBinding.inflate(layoutInflater, body, false)
        b.root.background = rippleDrawable()
        b.themeValueLabel.text = title
        b.themeValueLabel.setTextColor(getProperTextColor())
        b.themeValueLabel.setRowIcon(icon)
        b.themeValueValue.text = value
        b.themeValueValue.setTextColor(getProperPrimaryColor())
        b.root.setOnClickListener { onClick() }
        indentRow(b.root, stepPx())
        body.addView(b.root)
        return b
    }

    /**
     * A pressable row (black-yellow theme, "every pressable row is a pill"): an outlined pill as wide as
     * its label — 3 dp frame and bold for the [main] action of its section, 1.25 dp and normal otherwise —
     * with a › inside when it [opens] another screen, picker or panel (none when it acts in place after a
     * confirmation), and its [description] beneath in the settings-note dim gold. A [destructive] one lends
     * the delete red to frame and label. Only the pill takes taps.
     */
    @Suppress("LongParameterList")
    private fun addActionRow(
        title: String,
        description: CharSequence?,
        indent: Int,
        main: Boolean = false,
        opens: Boolean = true,
        destructive: Boolean = false,
        icon: RowIcon = RowIcon.SETTINGS,
        onClick: () -> Unit,
    ): ItemThemeActionBinding {
        val b = ItemThemeActionBinding.inflate(layoutInflater, body, false)
        val text = if (destructive) uiColor(UiRole.BUTTON_DESTRUCTIVE) else uiColor(UiRole.BUTTON_TEXT)
        val frame = if (destructive) uiColor(UiRole.BUTTON_DESTRUCTIVE) else uiColor(UiRole.BUTTON_FRAME)
        b.themeActionTitle.text = title
        b.themeActionTitle.setTextColor(text)
        b.themeActionTitle.setTypeface(null, if (main) Typeface.BOLD else Typeface.NORMAL)
        b.themeActionTitle.setRowIcon(icon)
        b.themeActionDesc.setPaddingRelative(
            b.themeActionDesc.paddingStart + rowIconInset(), b.themeActionDesc.paddingTop, 0, 0,
        )
        b.themeActionChevron.setTextColor(text)
        b.themeActionChevron.beVisibleIf(opens)
        b.themeActionPill.background = pillDrawable(frame, uiColor(UiRole.BUTTON_GROUND), main)
        b.themeActionPill.setOnClickListener { onClick() }
        setActionDescription(b, description)
        // The indent only — the row keeps its own ≈3 dp around the pill rather than the rows' 2 dp.
        b.root.apply { setPaddingRelative(paddingStart + indent, paddingTop, paddingEnd, paddingBottom) }
        body.addView(b.root)
        return b
    }

    // The explanation under an action pill; the dim-gold rule runs down the left only when there is one.
    private fun setActionDescription(b: ItemThemeActionBinding, description: CharSequence?) {
        val note = themeColor(ThemeSlot.SETTINGS_NOTE)
        b.themeActionDesc.text = description ?: ""
        b.themeActionDesc.setTextColor(note)
        b.themeActionDesc.beVisibleIf(!description.isNullOrEmpty())
        b.themeActionRule.setBackgroundColor(note)
        b.themeActionRule.beVisibleIf(!description.isNullOrEmpty())
    }

    // Both settings screens are OEM-dependent: catch anything either throws and fall back, since the
    // only useful reaction to "this ROM has no such screen" is trying the other one.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun openAllFilesAccessSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                showErrorToast(e2)
            }
        }
    }

    // Query the configured directory for the latest export whenever the page (re)builds: the answer is
    // the explanation under the Export / Import pill, in the warning role when something is missing.
    private fun refreshEximRowStatus() {
        ensureBackgroundThread {
            val (message, warn) = latestExportStatus(this)
            runOnUiThread {
                val row = eximRow ?: return@runOnUiThread
                setActionDescription(row, getString(R.string.eim_row_summary) + "\n" + message)
                if (warn) row.themeActionDesc.setTextColor(themeColor(ThemeSlot.STATUS_WARNING))
                eximDirValue?.text = eximDirLabel()
            }
        }
    }

    private fun eximDirLabel(): String =
        SettingsExport.exportDir(this)?.name ?: SettingsExport.getDirUri(this)?.lastPathSegment
            ?: getString(R.string.eim_dir_unset)

    private fun openExportImport() {
        eximDialog = ExportImportDialog(
            activity = this,
            onPickDir = { eximDirPickerLauncher.launch(SettingsExport.getDirUri(this)) },
            onExport = ::onEximExport,
            onImport = ::onEximImport,
        )
    }

    private fun onEximDirPicked(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        SettingsExport.setDirUri(this, uri)
        eximDialog?.refreshStatus()
        refreshEximRowStatus()
    }

    private fun onEximExport(cats: Set<SettingsExport.Cat>) {
        val dir = SettingsExport.exportDir(this)
        if (dir == null) {
            pendingEximCats = cats
            eximSaveAsLauncher.launch(SettingsExport.exportFileName()) // no directory set → save-as picker
        } else {
            exportToDirectory(dir, cats)
        }
    }

    private fun exportToDirectory(dir: DocumentFile, cats: Set<SettingsExport.Cat>) {
        eximFlash = showEximFlash(this, R.string.eim_exporting)
        ensureBackgroundThread {
            val result = runCatching {
                val name = SettingsExport.exportFileName()
                val file = dir.createFile(ZIP_MIME, name) ?: error("could not create a file in the directory")
                contentResolver.openOutputStream(file.uri)?.use { out ->
                    SettingsExport.export(this, cats, out)
                } ?: error("no output stream")
                name
            }
            runOnUiThread { onExportFinished(result) }
        }
    }

    private fun exportToUri(uri: Uri) {
        val cats = pendingEximCats
        eximFlash = showEximFlash(this, R.string.eim_exporting)
        ensureBackgroundThread {
            val result = runCatching {
                contentResolver.openOutputStream(uri)?.use { out ->
                    SettingsExport.export(this, cats, out)
                } ?: error("no output stream")
                DocumentFile.fromSingleUri(this, uri)?.name ?: SettingsExport.EXPORT_PREFIX
            }
            runOnUiThread { onExportFinished(result) }
        }
    }

    // Success: yellow-bordered OK dialog; acknowledging it closes the panel and this page too.
    // Failure: a toast, and the panel stays open.
    private fun onExportFinished(result: Result<String>) {
        eximFlash?.dismiss()
        eximFlash = null
        result.onSuccess { name ->
            refreshEximRowStatus()
            eximDialog?.refreshStatus()
            showExportDoneDialog(this, name) { closeEximChain() }
        }.onFailure {
            toast(getString(R.string.eim_export_fail, it.message ?: ""))
        }
    }

    private fun onEximImport(cats: Set<SettingsExport.Cat>) {
        pendingEximCats = cats
        eximImportLauncher.launch(arrayOf(ZIP_MIME, "application/octet-stream", "*/*"))
    }

    private fun importFromUri(uri: Uri) {
        val cats = pendingEximCats
        eximFlash = showEximFlash(this, R.string.eim_importing)
        ensureBackgroundThread {
            val result = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("no input stream")
                require(SettingsExport.categoriesIn(bytes).isNotEmpty()) { getString(R.string.eim_import_none) }
                SettingsExport.import(this, bytes, cats)
            }
            runOnUiThread { onImportFinished(result) }
        }
    }

    // Success: bordered info dialog — "Later" closes the whole chain, "Restart now" restarts the app.
    // Failure: a toast, and the panel stays open.
    private fun onImportFinished(result: Result<String>) {
        eximFlash?.dismiss()
        eximFlash = null
        result.onSuccess { summary ->
            showImportDoneDialog(
                activity = this,
                summary = summary,
                onLater = { closeEximChain() },
                onRestart = { restartApp() },
            )
        }.onFailure {
            toast(getString(R.string.eim_import_fail, it.message ?: ""))
        }
    }

    /** Close the whole chain beneath an acknowledged info dialog: the panel, then this page. */
    private fun closeEximChain() {
        eximDialog?.dismiss()
        eximDialog = null
        finish()
    }

    private fun restartApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(Intent.makeRestartActivityTask(launchIntent.component))
        Runtime.getRuntime().exit(0)
    }

    private fun onFontImported(uri: Uri?) {
        val slot = pendingFontSlot
        val b = pendingFontBinding
        pendingFontSlot = null
        pendingFontBinding = null
        if (uri == null || slot == null) {
            return
        }

        val fileName = importFont(uri)
        if (fileName == null) {
            toast(R.string.font_invalid)
            return
        }

        config.setFontFamily(slot.key, fileName)
        b?.themeTextFontValue?.text = fontDisplayName(fileName)
        if (b != null) {
            refreshSample(b, slot)
        }
    }
}
