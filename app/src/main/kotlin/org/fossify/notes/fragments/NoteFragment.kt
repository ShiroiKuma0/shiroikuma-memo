package org.fossify.notes.fragments

import android.graphics.Typeface
import org.fossify.commons.extensions.pillDrawable
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import org.fossify.notes.extensions.ThemeSlot
import org.fossify.notes.extensions.themeColor
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.PROTECTION_NONE
import org.fossify.notes.activities.MainActivity
import org.fossify.notes.extensions.config
import org.fossify.notes.extensions.getPercentageFontSize
import org.fossify.notes.helpers.NotesHelper
import org.fossify.notes.models.Note

private const val PILL_PAD_H_DP = 18
private const val PILL_PAD_V_DP = 9

abstract class NoteFragment : Fragment() {
    protected var note: Note? = null
    var shouldShowLockedContent = false

    protected fun setupLockedViews(binding: CommonNoteBinding, note: Note) {
        binding.apply {
            noteLockedLayout.beVisibleIf(note.isLocked() && !shouldShowLockedContent)
            val lockedColor = requireContext().themeColor(ThemeSlot.LOCKED_NOTE)
            noteLockedImage.applyColorFilter(lockedColor)

            noteLockedLabel.setTextColor(lockedColor)
            noteLockedLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, binding.root.context.getPercentageFontSize())

            // the one action of a locked note: an outlined pill, the main one (never bare underlined text)
            noteLockedShow.apply {
                val density = resources.displayMetrics.density
                setTextColor(context.uiColor(UiRole.BUTTON_TEXT))
                setTypeface(typeface, Typeface.BOLD)
                background = context.pillDrawable(
                    context.uiColor(UiRole.BUTTON_FRAME), context.uiColor(UiRole.BUTTON_GROUND), main = true,
                )
                val h = (PILL_PAD_H_DP * density).toInt()
                val v = (PILL_PAD_V_DP * density).toInt()
                setPadding(h, v, h, v)
            }
            noteLockedShow.setTextSize(TypedValue.COMPLEX_UNIT_PX, binding.root.context.getPercentageFontSize())
            noteLockedShow.setOnClickListener {
                handleUnlocking()
            }
        }
    }

    protected fun saveNoteValue(note: Note, content: String?, callback: ((note: Note) -> Unit)? = null) {
        if (note.path.isEmpty()) {
            NotesHelper(requireActivity()).insertOrUpdateNote(note) {
                (activity as? MainActivity)?.noteSavedSuccessfully(note.title)
                callback?.invoke(note)
            }
        } else {
            if (content != null) {
                val displaySuccess = activity?.config?.displaySuccess ?: false
                (activity as? MainActivity)?.tryExportNoteValueToFile(note.path, note.title, content, displaySuccess) {
                    callback?.invoke(note)
                }
            }
        }
    }

    fun handleUnlocking(callback: (() -> Unit)? = null) {
        if (callback != null && (note!!.protectionType == PROTECTION_NONE || shouldShowLockedContent)) {
            callback()
            return
        }

        activity?.performSecurityCheck(
            protectionType = note!!.protectionType,
            requiredHash = note!!.protectionHash,
            successCallback = { _, _ ->
                shouldShowLockedContent = true
                checkLockState()
                callback?.invoke()
            }
        )
    }

    fun updateNoteValue(value: String) {
        note?.value = value
    }

    fun updateNotePath(path: String) {
        note?.path = path
    }

    abstract fun checkLockState()

    interface CommonNoteBinding {
        val root: View
        val noteLockedLayout: View
        val noteLockedImage: ImageView
        val noteLockedLabel: TextView
        val noteLockedShow: TextView
    }
}
