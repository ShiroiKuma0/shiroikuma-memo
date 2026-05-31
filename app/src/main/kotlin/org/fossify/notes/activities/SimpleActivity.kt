package org.fossify.notes.activities

import android.os.Bundle
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.notes.R

open class SimpleActivity : BaseSimpleActivity() {
    // Fossify Commons' BaseSimpleActivity.onCreate shows a hardcoded "You are using a fake version…"
    // dialog (showModdedAppWarning) whenever packageName does not start with "org.fossify.". Our installed
    // id is shiroikuma.memo, so it would fire — guaranteed on first launch (appRunCount 0 % 100 == 0) and
    // then ~2% per screen open via its random() branch. The check ignores appSideloadingStatus, so it
    // can't be suppressed by the usual flag. It reads packageName exactly once, at the end of onCreate,
    // so we return an "org.fossify."-prefixed id for just that window; every other caller (shortcuts,
    // PackageManager, FileProvider authority, …) still sees the real shiroikuma.memo id.
    private var spoofPackageForModCheck = false

    override fun getPackageName(): String =
        if (spoofPackageForModCheck) "org.fossify.notes" else super.getPackageName()

    override fun onCreate(savedInstanceState: Bundle?) {
        spoofPackageForModCheck = true
        try {
            super.onCreate(savedInstanceState)
        } finally {
            spoofPackageForModCheck = false
        }
    }

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher_orange,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Notes"
}
