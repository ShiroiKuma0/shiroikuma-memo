package org.fossify.notes.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.notes.R
import org.fossify.notes.helpers.EXTRA_PROGRESS_APP
import org.fossify.notes.helpers.EXTRA_PROGRESS_CURRENT
import org.fossify.notes.helpers.EXTRA_PROGRESS_TEXT
import org.fossify.notes.helpers.EXTRA_PROGRESS_TOTAL
import org.fossify.notes.helpers.EXTRA_PROGRESS_UNIT
import org.fossify.notes.helpers.EXTRA_REPLY_ID
import org.fossify.notes.helpers.PROGRESS_THROTTLE_MS
import org.fossify.notes.helpers.ProgressReporter
import org.fossify.notes.helpers.SettingsExport
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data export or import for [AutomationProvider] actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run far longer. Two hard reasons it cannot be done anywhere
 * cheaper:
 *
 * - **A binder call holds the caller.** 白い熊 応用管理 is drawing a list; a long synchronous call would
 *   freeze its UI, report no progress and refuse cancellation.
 * - **A backgrounded app writing for a while is frozen mid-stream on 白い熊's phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored.
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to the
 * binder transaction and is closed the moment `call()` returns. This service owns the copy, takes it out
 * of [HANDOVER] before anything that can fail, and closes it on **every** path out — leaking one holds
 * the caller's file open, and a caller cannot checksum or encrypt a file that is still open.
 */
// Broad catches on purpose: whatever the filesystem, the archive or the caller's descriptor throws, the
// answer is the same one ERROR: line, and this service must never crash out from under a waiting caller.
@Suppress("TooGenericExceptionCaught")
class AutomationDataService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) == true
        val jobId = intent?.getStringExtra(EXTRA_JOB)
        // Drained BEFORE the first thing that can fail, and released by the finally unless the worker
        // below adopts it. Every other way out of this method — a missing job, a foreground start the
        // system refuses — would otherwise leave the caller's file held open by a map nobody looks at
        // again, with the caller waiting on a descriptor that will never close.
        val fd = jobId?.let { HANDOVER.remove(it) }
        var adopted = false
        try {
            // Unconditionally first: a service started with startForegroundService that returns without
            // this is killed by the system for not having called it.
            startForeground(NOTIFICATION_ID, notification(importing))
            if (intent == null || jobId == null || fd == null) {
                return START_NOT_STICKY
            }

            val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
            val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
            val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)
            val items = intent.getStringExtra(AutomationProvider.KEY_ITEMS)
            val replied = AtomicBoolean(false)

            fun reply(result: String) {
                // Exactly one terminal answer per job, whatever path got here — a synchronous failure
                // and an asynchronous success must never both fire.
                if (!replied.compareAndSet(false, true)) return
                AutomationJobs.finish(jobId)
                send(replyAction, replyPackage) {
                    putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                    putExtra(AutomationProvider.KEY_RESULT, result)
                }
            }

            val pulse = ProgressPulse(getString(startingLine(importing))) { current, total, unit, text ->
                send(progressAction, replyPackage) {
                    // The data door's correlation id is the job id, sent under BOTH names — `job_id`,
                    // which is what this door hands back, and `reply_id`, which is what every §1/§3
                    // listener in the family already keys on.
                    putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                    putExtra(EXTRA_REPLY_ID, jobId)
                    putExtra(EXTRA_PROGRESS_APP, getString(R.string.app_launcher_name))
                    putExtra(EXTRA_PROGRESS_TEXT, text)
                    putExtra(EXTRA_PROGRESS_CURRENT, current)
                    putExtra(EXTRA_PROGRESS_TOTAL, total)
                    putExtra(EXTRA_PROGRESS_UNIT, unit)
                }
            }
            if (!progressAction.isNullOrEmpty()) {
                pulse.start()
            }

            ensureBackgroundThread {
                try {
                    if (importing) {
                        runImport(fd, pulse.reporter, ::reply)
                    } else {
                        runExport(jobId, fd, items, pulse.reporter, ::reply)
                    }
                } catch (cancelled: SettingsExport.Cancelled) {
                    Log.i(TAG, "job $jobId ${cancelled.message}")
                    reply("ERROR:cancelled")
                } catch (e: Exception) {
                    reply("ERROR:${e.message ?: e.javaClass.simpleName}")
                } finally {
                    pulse.stop()
                    runCatching { fd.close() }
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                    stopSelf(startId)
                }
            }
            adopted = true
        } finally {
            if (!adopted) {
                runCatching { fd?.close() }
                jobId?.let { AutomationJobs.finish(it) }
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Write the archive straight into the caller's descriptor, counting as we go.
     *
     * Counted rather than stat'ed afterwards because the caller owns the file and we may not be able to
     * see it at all — it can be an anonymous pipe, or a descriptor into a directory this app cannot list.
     */
    private fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progress: ProgressReporter,
        reply: (String) -> Unit,
    ) {
        val cats = resolve(items) ?: run { reply("ERROR:unknown category in items: $items"); return }
        var written = 0L
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            val counting = object : OutputStream() {
                override fun write(b: Int) {
                    out.write(b)
                    written++
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    out.write(b, off, len)
                    written += len
                }
            }
            SettingsExport.export(
                context = this,
                cats = cats,
                out = counting,
                onProgress = progress,
                isCancelled = { AutomationJobs.isCancelled(jobId) },
            )
        }
        reply("OK:$written|${cats.size} categories")
    }

    /**
     * Spool the archive to the cache, then apply it entry by entry.
     *
     * Spooled rather than read into a `ByteArray` because the size of what arrives on this descriptor is
     * the CALLER's choice, not ours: reading it whole would put an unbounded allocation in the middle of
     * a restore. Spooled rather than applied straight off the descriptor because a read that failed
     * halfway would import half an archive, and a half-restored set of notes is worse than a restore that
     * refused — the spool is what lets the archive be inspected before anything is written.
     */
    private fun runImport(fd: ParcelFileDescriptor, progress: ProgressReporter, reply: (String) -> Unit) {
        val spool = File(cacheDir, "automation-import-${System.nanoTime()}.zip")
        try {
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                FileOutputStream(spool).use { input.copyTo(it) }
            }
            if (spool.length() == 0L) {
                reply("ERROR:empty archive")
                return
            }
            // Every category the archive actually carries, not every category we know about: asking for
            // one the archive lacks is how a restore ends up reporting success over nothing.
            val present = SettingsExport.categoriesIn(spool)
            if (present.isEmpty()) {
                reply("ERROR:archive carries no categories")
                return
            }
            SettingsExport.import(this, spool, present, progress)
            // 応用管理 force-stops this app straight after this reply. That is deliberate and belongs on
            // its side: a running process writes its cached SharedPreferences back out at orderly
            // shutdown and would silently undo the import. Our own writes are committed synchronously.
            reply("OK:${present.size} categories restored")
        } finally {
            spool.delete()
        }
    }

    /** Absent or empty `items` means this app's default set — here, everything it can export. */
    private fun resolve(items: String?): Set<SettingsExport.Cat>? {
        if (items.isNullOrBlank()) return SettingsExport.Cat.entries.toSet()
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { SettingsExport.Cat.byId(it) }.toSet()
        return found.takeIf { it.size == wanted.distinct().size }
    }

    private fun startingLine(importing: Boolean) =
        if (importing) R.string.automation_data_importing else R.string.automation_data_exporting

    /**
     * §3 progress that keeps speaking while nothing is moving.
     *
     * **A throttle is not a heartbeat.** A [ProgressReporter] only fires when the export CALLS it, and
     * the data door writes into a descriptor the CALLER supplied — which may be a pipe. When 応用管理 is
     * slow to drain that pipe a single `write` blocks for as long as it takes: the export reports
     * nothing, and after two minutes of silence the caller presumes this app dead and fails its slot.
     * The same is true in the other direction while an import is being read off the descriptor. That
     * stall has no relation to how much data this app holds, so "our export is small" is not an answer
     * to it.
     *
     * So the pulse is driven by a timer as well as by the work: at most one broadcast per
     * [PROGRESS_THROTTLE_MS] while the numbers are moving, and at least one per [HEARTBEAT_MS] while
     * they are not, repeating the last line it had. It starts with the same words as the notification,
     * so a job that blocks before its first counted step still proves it is alive.
     */
    private class ProgressPulse(
        initialText: String,
        private val emit: (current: Long, total: Long, unit: String, text: String) -> Unit,
    ) {
        // Volatile because the timer thread reads what the export thread last wrote.
        @Volatile
        private var current = 0L

        @Volatile
        private var total = 0L

        @Volatile
        private var unit = ""

        @Volatile
        private var text = initialText

        private val lock = Any()
        private var lastSent = 0L
        private var timer: ScheduledExecutorService? = null

        val reporter: ProgressReporter = { c, t, u, x ->
            current = c
            total = t
            unit = u
            text = x
            pulse(PROGRESS_THROTTLE_MS)
        }

        fun start() {
            timer = Executors.newSingleThreadScheduledExecutor().also { scheduler ->
                scheduler.scheduleWithFixedDelay(
                    { runCatching { pulse(HEARTBEAT_MS) } },
                    HEARTBEAT_MS,
                    HEARTBEAT_MS,
                    TimeUnit.MILLISECONDS,
                )
            }
        }

        fun stop() {
            timer?.shutdownNow()
            timer = null
        }

        /** Broadcast the current line if nothing has gone out for [quietFor] ms. */
        private fun pulse(quietFor: Long) {
            synchronized(lock) {
                val now = System.currentTimeMillis()
                if (now - lastSent < quietFor) return
                lastSent = now
            }
            // Deliberately outside the lock: a binder call must not be able to hold the export thread up.
            emit(current, total, unit, text)
        }
    }

    /**
     * One broadcast out, with the two flags this family learned the hard way: `setPackage` so it is not
     * an implicit broadcast, and `FLAG_INCLUDE_STOPPED_PACKAGES` because a backgrounded caller — or, on
     * a clean phone, one that has never been launched — would otherwise never hear it.
     */
    private fun send(action: String?, replyPackage: String?, extras: Intent.() -> Unit) {
        if (action.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
        try {
            sendBroadcast(
                Intent(action)
                    .setPackage(replyPackage)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .apply(extras)
            )
        } catch (e: Exception) {
            Log.w(TAG, "broadcast $action failed: $e")
        }
    }

    private fun notification(importing: Boolean): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.automation_data_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val title = if (importing) R.string.automation_data_importing else R.string.automation_data_exporting
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(title))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "MemoAutomationData"
        private const val CHANNEL = "automation_data"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        /**
         * How long the pulse may stay silent. Comfortably inside §3's "at least every 30 s" and far
         * inside the two minutes after which a caller presumes an app dead.
         */
        private const val HEARTBEAT_MS = 20_000L

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and the
         * copy's lifetime stops being ours to reason about. Handing it through a map keyed by the job id
         * keeps exactly one open descriptor with exactly one owner — this service, which drains the map
         * before anything that can fail and closes the descriptor on every path out.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /** Throws if the foreground service cannot be started; [AutomationProvider] answers that. */
        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ) {
            HANDOVER[jobId] = fd
            context.startForegroundService(
                Intent(context, AutomationDataService::class.java)
                    .putExtra(EXTRA_JOB, jobId)
                    .putExtra(EXTRA_IMPORTING, importing)
                    .putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
                    .putExtra(
                        AutomationProvider.KEY_REPLY_ACTION,
                        extras?.getString(AutomationProvider.KEY_REPLY_ACTION),
                    )
                    .putExtra(
                        AutomationProvider.KEY_REPLY_PACKAGE,
                        extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE),
                    )
                    .putExtra(
                        AutomationProvider.KEY_PROGRESS_ACTION,
                        extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION),
                    )
            )
        }

        /**
         * Release a descriptor whose service never started. Without this the handover map would hold the
         * caller's file open for the life of the process — and the caller cannot checksum or encrypt a
         * file that is still open, so it would be waiting on us forever.
         */
        fun discard(jobId: String) {
            HANDOVER.remove(jobId)?.let { runCatching { it.close() } }
        }
    }
}
