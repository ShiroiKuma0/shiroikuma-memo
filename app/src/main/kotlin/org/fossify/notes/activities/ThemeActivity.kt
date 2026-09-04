package org.fossify.notes.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.RadioItem
import org.fossify.notes.R
import org.fossify.notes.databinding.ActivityThemeBinding
import org.fossify.notes.databinding.ItemThemeColorBinding
import org.fossify.notes.databinding.ItemThemeSectionBinding
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
import org.fossify.notes.extensions.resetThemeColor
import org.fossify.notes.extensions.setThemeColor
import org.fossify.notes.extensions.showFontSample
import org.fossify.notes.extensions.themeColor
import org.fossify.notes.helpers.MAX_FONT_SIZE_SP
import org.fossify.notes.helpers.SettingsExport

// kxkb indent ladder: headings at 36dp, then 18dp per level (54 sub-heading, 72 row, 90 sub-row).
private const val HEADING_INDENT_DP = 36
private const val INDENT_STEP_DP = 18

// The Export/Import row (kxkb item look) and its status line.
private const val ROW_TITLE_SP = 16f
private const val ROW_SUMMARY_SP = 13f
private const val ROW_PAD_V_DP = 14
private const val ROW_PAD_END_DP = 16
private const val ROW_SUMMARY_GAP_DP = 3
private const val ROW_SUMMARY_ALPHA = 0.7f
private const val ROW_DESC_SCALE = 0.8f
private const val TOKEN_ABBREVIATION_EDGE = 8
private const val EXIM_WARN_COLOR = 0xFFFF5252.toInt()
private const val ZIP_MIME = SettingsExport.ZIP_MIME

@Suppress("TooManyFunctions")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    private var pendingFontSlot: ThemeSlot? = null
    private var pendingFontBinding: ItemThemeTextBinding? = null

    private var eximDialog: ExportImportDialog? = null
    private var eximFlash: androidx.appcompat.app.AlertDialog? = null
    private var eximStatusTv: TextView? = null
    private var pendingEximCats: Set<SettingsExport.Cat> = emptySet()

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

    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()

        val primaryColor = getProperPrimaryColor()

        // Export/Import is the first separated section on the page (Kōjiki-style), and the automation
        // rows sit inside it — every automation intent drives that same export.
        addSectionHeader(getString(R.string.eim_heading), primaryColor, isFirst = true)
        addEximportRow()
        addAutomationRows()

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

    private fun addSectionHeader(label: String, primaryColor: Int, isFirst: Boolean = false) {
        val item = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        item.themeSectionLabel.text = label
        item.themeSectionLabel.setTextColor(primaryColor)
        item.themeSectionUnderline.setBackgroundColor(primaryColor)
        if (isFirst) {
            // the full-width hairline marks the border to the PREVIOUS section — none above the first
            item.themeSectionDivider.visibility = View.GONE
        } else {
            item.themeSectionDivider.setBackgroundColor(primaryColor)
        }
        binding.themeHolder.addView(item.root)
    }

    private fun addSubgroupHeader(label: String, primaryColor: Int) {
        val item = ItemThemeSubgroupBinding.inflate(layoutInflater, binding.themeHolder, false)
        item.themeSubgroupLabel.text = label
        item.themeSubgroupLabel.setTextColor(primaryColor)
        item.themeSubgroupUnderline.setBackgroundColor(primaryColor)
        item.root.setPaddingRelative(indentPx(1), item.root.paddingTop, item.root.paddingEnd, item.root.paddingBottom)
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // Absolute start inset for one ladder level (kxkb convention: 36 + level*18 dp).
    private fun indentPx(level: Int) = dp(HEADING_INDENT_DP + level * INDENT_STEP_DP)

    // Place a row's content on the kxkb ladder: rows sit one level past their (sub)heading.
    // contentHasInset = true for the text block whose inner header already carries the base label inset.
    private fun indentRow(view: View, level: Int, contentHasInset: Boolean = false) {
        val baseInset = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.settings_label_start_margin)
        val start = indentPx(level + 1) - (if (contentHasInset) baseInset else 0)
        view.setPaddingRelative(start.coerceAtLeast(0), view.paddingTop, view.paddingEnd, view.paddingBottom)
    }

    // Indent a text element's font/weight/size/sample controls one full step past its name.
    private fun indentTextControls(b: ItemThemeTextBinding) {
        val step = dp(INDENT_STEP_DP)
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

    // --- Export / Import (Kōjiki flow; kxkb row look) ---

    private fun addEximportRow() {
        val textColor = getProperTextColor()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val outValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            setPadding(0, dp(ROW_PAD_V_DP), dp(ROW_PAD_END_DP), dp(ROW_PAD_V_DP))
            setOnClickListener { openExportImport() }
        }
        row.addView(TextView(this).apply {
            text = getString(R.string.eim_heading)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TITLE_SP)
            setTextColor(textColor)
        })
        row.addView(TextView(this).apply {
            text = getString(R.string.eim_row_summary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_SUMMARY_SP)
            setTextColor(textColor)
            alpha = ROW_SUMMARY_ALPHA
            setPadding(0, dp(ROW_SUMMARY_GAP_DP), 0, 0)
        })
        val statusTv = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_SUMMARY_SP)
            setTextColor(textColor)
            setPadding(0, dp(ROW_SUMMARY_GAP_DP), 0, 0)
        }
        eximStatusTv = statusTv
        row.addView(statusTv)
        indentRow(row, level = 1)
        binding.themeHolder.addView(row)
        refreshEximRowStatus()
    }

    // --- Automation (a part of Export / Import): the intent 白い熊 自由作業盤 exports through, and the
    // data door 白い熊 応用管理 restores this app through on a clean phone ---

    private fun addAutomationRows() {
        // Three rows, in the order every sister app uses: the master switch (default ON since contract
        // v2), the token opt-in (default OFF), and the token itself — shown only when it is actually
        // being asked for. A 48-character secret sitting under an off switch only invites 白い熊 to paste
        // it somewhere it would do nothing.
        addSwitchRow(
            title = getString(R.string.enable_automation),
            description = getString(R.string.enable_automation_desc),
            checked = config.automationEnabled,
        ) { config.automationEnabled = it }

        addSwitchRow(
            title = getString(R.string.automation_require_token),
            description = getString(R.string.automation_require_token_desc),
            checked = config.automationRequireToken,
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
        // (e.g. 白い熊's backup folder) outside Download/Documents. API 30+ only.
        if (isRPlus()) {
            val granted = Environment.isExternalStorageManager()
            val state = getString(if (granted) R.string.all_files_access_granted else R.string.all_files_access_needed)
            addValueRow(getString(R.string.all_files_access), state) { openAllFilesAccessSettings() }
        }
    }

    private fun addSwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val b = ItemThemeSwitchBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeSwitchLabel.text = titleWithDescription(title, description)
        b.themeSwitchLabel.setTextColor(getProperTextColor())
        b.themeSwitch.isChecked = checked
        b.root.setOnClickListener {
            b.themeSwitch.toggle()
            onChange(b.themeSwitch.isChecked)
        }
        indentRow(b.root, level = 1)
        binding.themeHolder.addView(b.root)
    }

    // A row's explanation, as a smaller dimmed line below its title — the summary styling, without
    // needing a second view in the switch layout.
    private fun titleWithDescription(title: String, description: String): CharSequence =
        SpannableStringBuilder(title).apply {
            append("\n")
            val start = length
            append(description)
            setSpan(RelativeSizeSpan(ROW_DESC_SCALE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(getProperTextColor().adjustAlpha(ROW_SUMMARY_ALPHA)),
                start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

    /**
     * The automation-token row: label plus the abbreviated token, tapping anywhere copies the full token,
     * and a Regenerate action on the right warns before invalidating pasted copies.
     */
    private fun addTokenRow() {
        val b = ItemThemeTokenBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeTokenLabel.text = getString(R.string.automation_token)
        b.themeTokenLabel.setTextColor(getProperTextColor())
        b.themeTokenValue.text = abbreviateToken(config.automationToken)
        b.themeTokenValue.setTextColor(getProperTextColor())
        b.themeTokenRegenerate.text = getString(R.string.automation_token_regenerate)
        b.themeTokenRegenerate.setTextColor(getProperPrimaryColor())
        b.root.setOnClickListener { copyToken() }
        b.themeTokenRegenerate.setOnClickListener { regenerateToken(b) }
        indentRow(b.root, level = 1)
        binding.themeHolder.addView(b.root)
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
            negative = org.fossify.commons.R.string.cancel,
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

    private fun addValueRow(title: String, value: String, onClick: () -> Unit) {
        val textColor = getProperTextColor()
        val b = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeValueLabel.text = title
        b.themeValueLabel.setTextColor(textColor)
        b.themeValueValue.text = value
        b.themeValueValue.setTextColor(textColor)
        b.root.setOnClickListener { onClick() }
        indentRow(b.root, level = 1)
        binding.themeHolder.addView(b.root)
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

    // Query the configured directory for the latest export whenever the page (re)builds.
    private fun refreshEximRowStatus() {
        ensureBackgroundThread {
            val (message, warn) = latestExportStatus(this)
            runOnUiThread {
                eximStatusTv?.text = message
                eximStatusTv?.setTextColor(if (warn) EXIM_WARN_COLOR else getProperTextColor())
                eximStatusTv?.typeface = if (warn) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
            }
        }
    }

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
            exportToFolder(dir, cats)
        }
    }

    private fun exportToFolder(dir: DocumentFile, cats: Set<SettingsExport.Cat>) {
        eximFlash = showEximFlash(this, R.string.eim_exporting)
        ensureBackgroundThread {
            val result = runCatching {
                val name = SettingsExport.exportFileName()
                val file = dir.createFile(ZIP_MIME, name) ?: error("could not create a file in the folder")
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
