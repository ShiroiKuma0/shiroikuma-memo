package org.fossify.notes.activities

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.notes.R
import org.fossify.notes.databinding.ActivityThemeBinding
import org.fossify.notes.databinding.ItemThemeColorBinding
import org.fossify.notes.databinding.ItemThemeSectionBinding
import org.fossify.notes.databinding.ItemThemeSubgroupBinding
import org.fossify.notes.databinding.ItemThemeTextBinding
import org.fossify.notes.dialogs.AlphaColorPickerDialog
import org.fossify.notes.dialogs.FontPickerDialog
import org.fossify.notes.extensions.FontWeightOption
import org.fossify.notes.extensions.ThemeGroup
import org.fossify.notes.extensions.ThemeSection
import org.fossify.notes.extensions.ThemeSlot
import org.fossify.notes.extensions.config
import org.fossify.notes.extensions.fontDisplayName
import org.fossify.notes.extensions.importFont
import org.fossify.notes.extensions.resetThemeColor
import org.fossify.notes.extensions.setThemeColor
import org.fossify.notes.extensions.showFontSample
import org.fossify.notes.extensions.themeColor
import org.fossify.notes.helpers.MAX_FONT_SIZE_SP

// Multiplier on activity_margin for one level of category/subcategory indentation.
private const val INDENT_STEP_MULTIPLIER = 3

@Suppress("TooManyFunctions")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    private var pendingFontSlot: ThemeSlot? = null
    private var pendingFontBinding: ItemThemeTextBinding? = null

    private val fontImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onFontImported(uri)
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

    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()

        val primaryColor = getProperPrimaryColor()

        ThemeSection.entries.forEach { section ->
            addSectionHeader(getString(section.labelRes), primaryColor)
            val groups = ThemeGroup.entries.filter { it.section == section }
            val showSubgroups = groups.size > 1
            groups.forEach { group ->
                if (showSubgroups) {
                    // subgroup header sits one level in; its rows another level in
                    addSubgroupHeader(getString(group.labelRes), primaryColor)
                    addGroupSlots(group, indentLevel = 2)
                } else {
                    // section with no subgroups: its rows sit one level in
                    addGroupSlots(group, indentLevel = 1)
                }
            }
        }
    }

    private fun addGroupSlots(group: ThemeGroup, indentLevel: Int) {
        ThemeSlot.entries.filter { it.group == group }.forEach { slot ->
            if (slot.hasFont) addTextSlot(slot, indentLevel) else addColorSlot(slot, indentLevel)
        }
    }

    private fun addSectionHeader(label: String, primaryColor: Int) {
        val item = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        item.themeSectionLabel.text = label
        item.themeSectionLabel.setTextColor(primaryColor)
        item.themeSectionDivider.setBackgroundColor(primaryColor)
        binding.themeHolder.addView(item.root)
    }

    private fun addSubgroupHeader(label: String, primaryColor: Int) {
        val item = ItemThemeSubgroupBinding.inflate(layoutInflater, binding.themeHolder, false)
        item.themeSubgroupLabel.text = label
        item.themeSubgroupLabel.setTextColor(primaryColor)
        item.themeSubgroupUnderline.setBackgroundColor(primaryColor)
        indentRow(item.root, level = 1)
        binding.themeHolder.addView(item.root)
    }

    private fun addColorSlot(slot: ThemeSlot, indentLevel: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(getProperTextColor())
        row.themeColorPreview.background.setTint(themeColor(slot))
        row.root.setOnClickListener { openColorPicker(slot) }
        indentRow(row.root, indentLevel)
        previews[slot] = row.themeColorPreview
        binding.themeHolder.addView(row.root)
    }

    @Suppress("EmptyFunctionBlock") // SeekBar's start/stop-tracking callbacks are intentionally no-ops
    private fun addTextSlot(slot: ThemeSlot, indentLevel: Int) {
        val textColor = getProperTextColor()
        val b = ItemThemeTextBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeTextLabel.text = getString(slot.labelRes)
        listOf(
            b.themeTextLabel, b.themeTextFontTitle, b.themeTextFontValue,
            b.themeTextWeightTitle, b.themeTextWeightValue, b.themeTextSizeTitle, b.themeTextSizeValue
        ).forEach { it.setTextColor(textColor) }

        b.themeTextColorPreview.background.setTint(themeColor(slot))
        b.themeTextFontValue.text = fontDisplayName(config.getFontFamily(slot.key))
        b.themeTextWeightValue.text = getString(FontWeightOption.fromValue(config.getFontWeight(slot.key)).labelRes)
        b.themeTextSizeSeekbar.max = MAX_FONT_SIZE_SP
        b.themeTextSizeSeekbar.progress = config.getFontSize(slot.key)
        b.themeTextSizeValue.text = sizeLabel(config.getFontSize(slot.key))
        refreshSample(b, slot)

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

        indentRow(b.root, indentLevel, contentHasInset = true)
        indentTextControls(b)
        binding.themeHolder.addView(b.root)
    }

    private fun indentStepPx() =
        resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin) * INDENT_STEP_MULTIPLIER

    // Place a row's content at an absolute (baseInset + level*step) so every level nests in equal,
    // prominent steps regardless of the item's own base padding. contentHasInset = true for the text
    // block whose inner header already carries the base label inset.
    private fun indentRow(view: View, level: Int, contentHasInset: Boolean = false) {
        val baseInset = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.settings_label_start_margin)
        val start = level * indentStepPx() + (if (contentHasInset) 0 else baseInset)
        view.setPaddingRelative(start, view.paddingTop, view.paddingEnd, view.paddingBottom)
    }

    // Indent a text element's font/weight/size/sample controls one full step past its name.
    private fun indentTextControls(b: ItemThemeTextBinding) {
        val step = indentStepPx()
        listOf(b.themeTextFontRow, b.themeTextWeightRow, b.themeTextSizeRow, b.themeTextSample).forEach {
            it.setPaddingRelative(it.paddingStart + step, it.paddingTop, it.paddingEnd, it.paddingBottom)
        }
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
            if (slot.isFoundation) {
                // foundation cascades into the chrome + every inheriting preview
                recreate()
            } else {
                previews[slot]?.background?.setTint(themeColor(slot))
            }
        }
    }

    private fun openTextColorPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            b.themeTextColorPreview.background.setTint(themeColor(slot))
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
