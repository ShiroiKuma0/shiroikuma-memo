package org.fossify.notes.receivers

import java.util.concurrent.atomic.AtomicReference

/**
 * The one headless export that may be running, and the flag it watches to stop.
 *
 * ## Why this exists at all
 *
 * 白い熊 must be able to stop a long export from where he started it. 自由作業盤's 中止 button used to
 * only stop the panel *listening*: the app carried on to the end, renamed its part-file into place and
 * delivered a backup that had been cancelled, while its reply arrived with nobody waiting. So the stop
 * has to reach the export itself, and the export runs on a background thread that the receiver which
 * started it has long since returned from — something has to hold the two together.
 *
 * ## Why it is process-local and never persisted
 *
 * A persisted "export in progress" flag wedges the app for good after a single crash: every later
 * request answers `ERROR:export already running` and no backup is possible until the process is killed.
 * Here the state dies with the process, and [end] is called from a `finally`, so the only way to be stuck
 * is to be genuinely still running.
 */
object StateExportJob {

    /** One export in flight. [cancelled] is read between ZIP entries, never mid-write. */
    class Run(val replyId: String) {
        @Volatile
        var cancelled = false
    }

    private val running = AtomicReference<Run?>(null)

    /** The run to hand to the export, or null when one is already going — the contract forbids two. */
    fun begin(replyId: String): Run? {
        val run = Run(replyId)
        return if (running.compareAndSet(null, run)) run else null
    }

    fun end(run: Run) {
        running.compareAndSet(run, null)
    }

    /**
     * Ask the running export to stop. An empty [replyId] means "whatever you are running", which is
     * unambiguous because only one export runs at a time.
     *
     * A silent no-op when nothing is running, or when the id names a run that already finished: 自由作業盤
     * fires this whenever 白い熊 presses 中止, without knowing how far the export got, and answering that
     * as an error would make every well-behaved caller look broken.
     */
    fun requestCancel(replyId: String) {
        val run = running.get() ?: return
        if (replyId.isEmpty() || replyId == run.replyId) {
            run.cancelled = true
        }
    }
}
