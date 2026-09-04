package org.fossify.notes.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.notes.R
import org.fossify.notes.extensions.config
import org.fossify.notes.helpers.ACTION_CANCEL_EXPORT
import org.fossify.notes.helpers.ACTION_EXPORT_STATE
import org.fossify.notes.helpers.ACTION_LIST_CATEGORIES
import org.fossify.notes.helpers.EXTRA_AUTOMATION_TOKEN
import org.fossify.notes.helpers.EXTRA_BACKUP_PATH
import org.fossify.notes.helpers.EXTRA_EXPORT_ITEMS
import org.fossify.notes.helpers.EXTRA_PROGRESS_ACTION
import org.fossify.notes.helpers.EXTRA_PROGRESS_APP
import org.fossify.notes.helpers.EXTRA_PROGRESS_CURRENT
import org.fossify.notes.helpers.EXTRA_PROGRESS_TEXT
import org.fossify.notes.helpers.EXTRA_PROGRESS_TOTAL
import org.fossify.notes.helpers.EXTRA_PROGRESS_UNIT
import org.fossify.notes.helpers.EXTRA_REPLY_ACTION
import org.fossify.notes.helpers.EXTRA_REPLY_ID
import org.fossify.notes.helpers.EXTRA_REPLY_PACKAGE
import org.fossify.notes.helpers.EXTRA_REPLY_RESULT
import org.fossify.notes.helpers.PROGRESS_THROTTLE_MS
import org.fossify.notes.helpers.ProgressReporter
import org.fossify.notes.helpers.SettingsExport
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The 保存復元 state-export contract, for 白い熊 自由作業盤's one-run backup of every sister app.
 *
 * Three exported actions, gated by [org.fossify.notes.helpers.Config.refuseAutomation] — the switch
 * ships ON and the token is opt-in since contract v2, so a token sent to this app while it is not asking
 * for one is IGNORED rather than refused:
 *  - [ACTION_LIST_CATEGORIES] — instant; replies "OK:" plus one `id<TAB>label` line per selectable
 *    category, so the caller can render its own checkbox picker. Our list is flat, so no line carries
 *    the optional third `parent-id` field.
 *  - [ACTION_EXPORT_STATE] — runs the same category ZIP export as the Export/Import page, headlessly
 *    (no Activity, no interaction), and replies with the written path and its real size. Extras:
 *    optional "token", optional "path" (an absolute directory that OVERRIDES the configured export
 *    directory), optional "items" (comma-separated category ids; absent = everything), optional
 *    "progress_action", plus "reply_action"/"reply_package"/"reply_id".
 *  - [ACTION_CANCEL_EXPORT] — stops the running export, deletes its partial file and lets it answer
 *    "ERROR:cancelled". Fire-and-forget: it sends no reply of its own, and is a silent no-op when
 *    nothing is running. See [StateExportJob].
 *
 * The archive is written to `<final-name>.part` and renamed into place only once it is closed and
 * complete, so a cancelled, failed or killed export never leaves something a later restore would find.
 * 白い熊 keeps every app's backups in one directory sorted by date, where a truncated archive silently
 * becomes "the latest backup" of this app.
 *
 * Importing is deliberately NOT here. This receiver is exported with no permission, so an import action
 * on it would let any app on the phone overwrite every note; it lives behind
 * [org.fossify.notes.automation.AutomationProvider], which can identify its caller.
 *
 * Directory precedence: the "path" extra → the app's configured export directory → ERROR:no-directory.
 * One request writes exactly one ZIP — settings and notes together — which is the file a restore takes.
 *
 * The reply is a plain broadcast carrying "reply_id" + "result" — the only channel that works on 白い熊's
 * EMUI (verified 2026-07-23): the ordered-broadcast result is severed between third-party apps and a
 * Binder-bearing extra (ResultReceiver, PendingIntent, Messenger) may be dropped outright. Exactly one
 * terminal reply per request, guarded by an [AtomicBoolean] so an async success and a synchronous error
 * can never both fire, and [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] so a stopped caller still hears it.
 *
 * Progress is reported as real counts, never a percentage — "Notes 123/456" — throttled to one broadcast
 * per [PROGRESS_THROTTLE_MS], with an unthrottled final one at completion.
 */
// Every catch here is deliberately broad: whatever a request, a filesystem or a SAF provider throws, the
// answer is the same — turn it into the one ERROR: line the caller is waiting for, and never crash out of
// a broadcast that another app is blocked on.
@Suppress("TooGenericExceptionCaught")
class StateExportReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "MemoStateExport"
        private const val KILO = 1024.0
    }

    /** What a parsed request turned out to be: already answerable, or an export to run. */
    private sealed class Request {
        class Done(val result: String) : Request()
        class Export(val cats: Set<SettingsExport.Cat>, val path: String) : Request()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_EXPORT_STATE && action != ACTION_LIST_CATEGORIES && action != ACTION_CANCEL_EXPORT) {
            return
        }

        if (action == ACTION_CANCEL_EXPORT) {
            cancel(context.applicationContext, intent)
            return
        }

        // goAsync() holds the broadcast open until finish(); the guard makes finishWith idempotent, so
        // the async success path and any synchronous error path can neither double-finish nor leave the
        // caller waiting forever.
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val appContext = context.applicationContext
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.trim().orEmpty()

        fun finishWith(result: String) {
            if (!finished.compareAndSet(false, true)) return
            Log.i(TAG, "result → $result")
            if (replyAction.isNotEmpty() && replyId.isNotEmpty()) {
                try {
                    appContext.sendBroadcast(
                        Intent(replyAction)
                            .setPackage(replyPackage.ifEmpty { null })
                            .putExtra(EXTRA_REPLY_ID, replyId)
                            .putExtra(EXTRA_REPLY_RESULT, result)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    )
                    Log.i(TAG, "reply broadcast sent → $replyAction ($replyPackage, id=$replyId)")
                } catch (e: Exception) {
                    Log.w(TAG, "reply broadcast failed: $e")
                }
            }
            pending.setResultData(result) // correct AOSP behaviour; never our only reply
            pending.finish()
        }

        val request = try {
            parse(appContext, intent, action)
        } catch (e: Exception) {
            Request.Done("ERROR:${reason(e)}")
        }

        when (request) {
            is Request.Done -> finishWith(request.result)
            is Request.Export -> {
                // Process-local and released in a finally: §1 forbids two exports at once, and a guard
                // that could outlive its export would wedge the app until the process died.
                val run = StateExportJob.begin(replyId)
                if (run == null) {
                    finishWith("ERROR:export already running")
                } else {
                    val progress = throttledProgress(appContext, progressAction, replyPackage, replyId)
                    ensureBackgroundThread {
                        try {
                            finishWith(export(appContext, request.cats, request.path, progress, run))
                        } finally {
                            StateExportJob.end(run)
                        }
                    }
                }
            }
        }
    }

    /**
     * Stop the running export. No reply of its own — the one terminal reply belongs to the export this
     * stopped, which answers `ERROR:cancelled` through the channel its caller is already listening on.
     *
     * The gate is checked but its refusal is swallowed: there is nothing to report a refusal *to*, and a
     * cancel is safe to send at any time, so a closed app simply ignores it.
     */
    private fun cancel(context: Context, intent: Intent) {
        val refusal = context.config.refuseAutomation(intent.getStringExtra(EXTRA_AUTOMATION_TOKEN))
        if (refusal != null) {
            Log.i(TAG, "cancel ignored: $refusal")
            return
        }
        StateExportJob.requestCancel(intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty())
    }

    /**
     * Decide the request without doing any work: the gate first (the switch and the token report
     * distinctly, since they debug differently), then the instant category list, then the export's own
     * validation — so a malformed request is answered before anything is written.
     */
    private fun parse(context: Context, intent: Intent, action: String?): Request {
        val config = context.config
        val token = intent.getStringExtra(EXTRA_AUTOMATION_TOKEN)
        val itemsRaw = intent.getStringExtra(EXTRA_EXPORT_ITEMS)?.trim().orEmpty()
        val path = intent.getStringExtra(EXTRA_BACKUP_PATH)?.trim().orEmpty()
        val cats = parseItems(itemsRaw)
        Log.i(
            TAG,
            "received $action: enabled=${config.automationEnabled}, " +
                "requireToken=${config.automationRequireToken}, tokenLen=${token?.length ?: 0}, " +
                "items=$itemsRaw, path=$path"
        )

        // One function, so "automation disabled" and "bad token" cannot drift apart between the three
        // entry points — and so a token this app is not asking for is ignored rather than refused.
        config.refuseAutomation(token)?.let { return Request.Done(it) }

        return when {
            action == ACTION_LIST_CATEGORIES -> Request.Done(categoryList(context))
            cats == null -> Request.Done("ERROR:unknown category in items: $itemsRaw")
            path.isNotEmpty() && !path.startsWith("/") ->
                Request.Done("ERROR:$EXTRA_BACKUP_PATH must be an absolute directory")

            else -> Request.Export(cats, path)
        }
    }

    /**
     * "OK:" plus one `id<TAB>label` line per category — the ids are exactly the ones "items" accepts, and
     * the same names their data carries inside the ZIP.
     */
    private fun categoryList(context: Context): String =
        SettingsExport.Cat.entries.joinToString(separator = "\n", prefix = "OK:") {
            "${it.id}\t${context.getString(it.labelRes)}"
        }

    /** The requested categories, or null when [itemsRaw] names an id we do not export; empty = all. */
    private fun parseItems(itemsRaw: String): Set<SettingsExport.Cat>? {
        val ids = itemsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return SettingsExport.Cat.entries.toSet()
        val cats = ids.mapNotNull { SettingsExport.Cat.byId(it) }.toSet()
        return cats.takeIf { it.size == ids.distinct().size }
    }

    /** Runs on a background thread; returns the single result line and never throws. */
    private fun export(
        context: Context,
        cats: Set<SettingsExport.Cat>,
        path: String,
        progress: ThrottledProgress,
        run: StateExportJob.Run,
    ): String {
        val target = try {
            SettingsExport.headlessTarget(context, path) ?: return "ERROR:no-directory"
        } catch (e: Exception) {
            return storageError(path, e)
        }

        return try {
            // The counted length is the fallback for a destination we cannot stat; it is final once
            // export() returns, which is after the ZIP's central directory has been flushed.
            val counting = CountingOutputStream(target.open())
            counting.use { SettingsExport.export(context, cats, it, progress.reporter) { run.cancelled } }
            // Only now does the archive get its real name. Everything above this line wrote to a
            // ".part" that no restore would ever pick up.
            target.commit()
            val bytes = target.size().takeIf { it > 0 } ?: counting.count
            progress.final(cats.size.toLong())
            "OK:${target.displayPath()}|$bytes|${humanSize(bytes)}|${cats.size} categories"
        } catch (cancelled: SettingsExport.Cancelled) {
            // The point of the cancel action: the directory is left exactly as it was found.
            Log.i(TAG, "export ${cancelled.message}, partial file removed")
            target.abort()
            "ERROR:cancelled"
        } catch (e: Exception) {
            target.abort()
            storageError(path, e)
        }
    }

    // An absolute path we were told to write but cannot needs All-files access; name that specifically,
    // since it is the one failure 白い熊 fixes with a toggle rather than a code change.
    private fun storageError(path: String, e: Exception): String {
        val noAllFiles = isRPlus() && !Environment.isExternalStorageManager()
        return if (path.isNotEmpty() && noAllFiles) "ERROR:no-storage-access" else "ERROR:${reason(e)}"
    }

    private fun reason(e: Throwable): String =
        (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName).replace('\n', ' ')

    /** Display size for the reply line — the caller cannot stat the file, so we compute both forms. */
    private fun humanSize(bytes: Long): String = when {
        bytes < KILO -> "$bytes B"
        bytes < KILO * KILO -> "%.1f KB".format(Locale.ROOT, bytes / KILO)
        bytes < KILO * KILO * KILO -> "%.1f MB".format(Locale.ROOT, bytes / (KILO * KILO))
        else -> "%.2f GB".format(Locale.ROOT, bytes / (KILO * KILO * KILO))
    }

    private fun throttledProgress(
        context: Context,
        progressAction: String,
        replyPackage: String,
        replyId: String,
    ): ThrottledProgress {
        val appLabel = context.getString(R.string.app_launcher_name)
        val unitCategory = context.getString(R.string.state_progress_unit_category)

        fun send(current: Long, total: Long, unit: String, text: String) {
            try {
                context.sendBroadcast(
                    Intent(progressAction)
                        .setPackage(replyPackage.ifEmpty { null })
                        .putExtra(EXTRA_REPLY_ID, replyId)
                        .putExtra(EXTRA_PROGRESS_APP, appLabel)
                        .putExtra(EXTRA_PROGRESS_TEXT, text)
                        .putExtra(EXTRA_PROGRESS_CURRENT, current)
                        .putExtra(EXTRA_PROGRESS_TOTAL, total)
                        .putExtra(EXTRA_PROGRESS_UNIT, unit)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                )
            } catch (e: Exception) {
                Log.w(TAG, "progress broadcast failed: $e")
            }
        }

        var lastSent = 0L
        return ThrottledProgress(
            reporter = { current, total, unit, text ->
                val now = System.currentTimeMillis()
                if (progressAction.isNotEmpty() && now - lastSent >= PROGRESS_THROTTLE_MS) {
                    lastSent = now
                    send(current, total, unit, text)
                }
            },
            final = { categories ->
                if (progressAction.isNotEmpty()) {
                    send(categories, categories, unitCategory, "$unitCategory $categories/$categories")
                }
            },
        )
    }

    /** The throttled progress channel plus the unthrottled completion broadcast. */
    private class ThrottledProgress(val reporter: ProgressReporter, val final: (Long) -> Unit)

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }
}
