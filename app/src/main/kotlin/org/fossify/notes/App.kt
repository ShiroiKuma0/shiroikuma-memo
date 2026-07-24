package org.fossify.notes

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.notes.activities.MainActivity
import org.fossify.notes.extensions.ThemeSlot
import org.fossify.notes.extensions.applyThemeFontIfSet
import org.fossify.notes.extensions.migratePureYellowIfNeeded
import org.fossify.notes.extensions.seedBlackYellowThemeIfNeeded
import org.fossify.notes.extensions.themeColor

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
        // Rewrite any persisted old material-yellow color to the pure yellow, once.
        migratePureYellowIfNeeded()
        // Theme every sub-page's top bar (the "Settings" title + back arrow, and the same headers on
        // Theme / About / Customization / …) from the HEADER_TITLE / HEADER_ARROW slots, so it
        // propagates even to commons-owned screens we don't edit.
        registerActivityLifecycleCallbacks(TopBarColorizer())
    }
}

// Restyles a screen's toolbar title + back arrow + overflow icon from the header slots after it
// resumes. The main screen is skipped — it owns its own search-bar / tab chrome (themed elsewhere).
//
// Commons only ever sets the title *color* (in updateTopBarColors, also on every scroll re-tint), so
// the per-element font/weight/size we apply here sticks; the color, like in commons, may be repainted
// when the page scrolls. We run in a post() so we land after setupTopAppBar has created the title view.
@Suppress("EmptyFunctionBlock") // the unused ActivityLifecycleCallbacks overrides are intentional no-ops
private class TopBarColorizer : Application.ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) {
            return
        }

        val content = activity.findViewById<View>(android.R.id.content) ?: return
        content.post {
            val toolbar = content.findToolbar() ?: return@post
            val titleColor = activity.themeColor(ThemeSlot.HEADER_TITLE)
            val arrowColor = activity.themeColor(ThemeSlot.HEADER_ARROW)
            toolbar.setTitleTextColor(titleColor)
            toolbar.navigationIcon?.applyColorFilter(arrowColor)
            toolbar.overflowIcon?.applyColorFilter(titleColor)
            toolbar.findTitleTextView()?.applyThemeFontIfSet(ThemeSlot.HEADER_TITLE)
        }
    }

    private fun View.findToolbar(): Toolbar? {
        if (this is Toolbar) {
            return this
        }
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                getChildAt(i).findToolbar()?.let { return it }
            }
        }
        return null
    }

    // The toolbar's title lives in a private internal TextView; locate it by its (already-set) text.
    private fun Toolbar.findTitleTextView(): TextView? {
        val current = title ?: return null
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is TextView && child.text == current) {
                return child
            }
        }
        return null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
