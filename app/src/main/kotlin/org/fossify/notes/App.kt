package org.fossify.notes

import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.SIDELOADING_FALSE
import org.fossify.notes.extensions.seedBlackYellowThemeIfNeeded

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        // Fossify Commons' sideloading detection probes for a Commons drawable that resource
        // shrinking strips from our custom-signed build, then shows a "corrupt/fake version"
        // dialog. Mark the app as not sideloaded here — before ANY activity runs its check
        // (incl. screens cold-started straight from a shortcut, which never pass through
        // SplashActivity), clearing any previously persisted verdict.
        baseConfig.appSideloadingStatus = SIDELOADING_FALSE
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
    }
}
