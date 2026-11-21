package org.fossify.notes.adapters

import org.fossify.commons.extensions.circleRippleDrawable
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import org.fossify.notes.extensions.ThemeSlot
import org.fossify.notes.extensions.themeColor
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.LOWER_ALPHA_INT
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.views.MyRecyclerView
import org.fossify.notes.R
import org.fossify.notes.databinding.OpenNoteItemBinding
import org.fossify.notes.extensions.config
import org.fossify.notes.models.Note
import org.fossify.notes.models.NoteType
import org.fossify.notes.models.Task

private const val CARD_RADIUS_DP = 12f

class OpenNoteAdapter(
    activity: BaseSimpleActivity,
    var items: List<Note>,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    override fun getActionMenuId() = 0

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = itemCount

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.id?.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun prepareActionMode(menu: Menu) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(OpenNoteItemBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bindView(item, allowSingleClick = true, allowLongClick = false) { itemView, _ ->
            setupView(itemView, item)
        }

        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    private fun setupView(view: View, note: Note) {
        OpenNoteItemBinding.bind(view).apply {
            root.setupCard()
            openNoteItemTitle.apply {
                text = note.title
                setTextColor(activity.themeColor(ThemeSlot.PICKER_TITLE))
            }

            val formattedText = note.getFormattedValue(root.context)
            openNoteItemText.beGoneIf(formattedText.isNullOrBlank() || note.isLocked())
            iconLock.beVisibleIf(note.isLocked())
            iconLock.setImageDrawable(
                activity.resources.getColoredDrawableWithColor(
                    org.fossify.commons.R.drawable.ic_lock_vector, activity.themeColor(ThemeSlot.MENU_ICON_LOCK),
                )
            )
            openNoteItemText.apply {
                text = formattedText
                setTextColor(activity.themeColor(ThemeSlot.PICKER_TEXT))
            }

            openNoteItemIcon.apply {
                beVisibleIf(note.path.isNotEmpty())
                applyColorFilter(activity.themeColor(ThemeSlot.MENU_ICON_OPEN))
                if (background == null) background = activity.circleRippleDrawable()
                tooltipText = context.getString(R.string.this_note_is_linked)

                setOnClickListener {
                    performLongClick()
                }
            }
        }
    }

    // A card per note: nothing filled, a thin frame in the picker-frame role, the pressed-row ripple inside.
    private fun View.setupCard() {
        val density = resources.displayMetrics.density
        val radius = CARD_RADIUS_DP * density
        val frame = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(density.toInt().coerceAtLeast(1), context.themeColor(ThemeSlot.PICKER_FRAME))
        }
        val mask = GradientDrawable().apply {
            cornerRadius = radius
            setColor(context.uiColor(UiRole.ACCENT))
        }
        background = RippleDrawable(ColorStateList.valueOf(context.uiColor(UiRole.LIST_PRESSED)), frame, mask)
    }

    private fun Note.getFormattedValue(context: Context): CharSequence? {
        return when (type) {
            NoteType.TYPE_TEXT -> getNoteStoredValue(context)
            NoteType.TYPE_CHECKLIST -> {
                val taskType = object : TypeToken<List<Task>>() {}.type
                var items = Gson().fromJson<List<Task>>(getNoteStoredValue(context), taskType) ?: listOf()
                items = items.let {
                    val sorting = context.config.getSorting(id)
                    Task.sorting = sorting
                    var result = it
                    if (Task.sorting and SORT_BY_CUSTOM == 0) {
                        result = result.sorted()
                    }
                    if (context.config.moveDoneChecklistItems) {
                        result = result.sortedBy { it.isDone }
                    }
                    result
                }

                val linePrefix = "• "
                val stringifiedItems = items.joinToString(separator = System.lineSeparator()) {
                    "${linePrefix}${it.title}"
                }

                val formattedText = SpannableString(stringifiedItems)
                var currentPos = 0
                items.forEach { item ->
                    currentPos += linePrefix.length
                    if (item.isDone) {
                        formattedText.setSpan(StrikethroughSpan(), currentPos, currentPos + item.title.length, 0)
                    }
                    currentPos += item.title.length
                    currentPos += System.lineSeparator().length
                }

                formattedText
            }
        }
    }
}
