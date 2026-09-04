package org.fossify.notes.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.fossify.notes.extensions.config
import org.fossify.notes.helpers.SettingsExport
import org.json.JSONArray
import org.json.JSONObject

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of the redesign.
 *
 * **A broadcast cannot tell you who sent it.** The old contract's answer to that was a shared secret,
 * which cannot survive the wipe that this feature exists to recover from. A provider gets the caller's
 * identity from the framework for free — see [AutomationCallers] for what is actually checked and why a
 * package-name prefix would have been worse than the token it replaced.
 *
 * **A list needs a synchronous answer.** 白い熊 応用管理 draws a row per installed app before any export
 * exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. `call()` validates, starts a foreground service and returns — megabytes over minutes
 * inside a binder call would block the caller, report no progress, refuse cancellation and die silently
 * if this process were killed. The bytes go through a file descriptor the caller opened.
 *
 * ## Why a descriptor and not a path
 *
 * Because a backup is not a stable directory while it is being assembled. 応用管理 writes into a
 * temporary path and renames on commit; it encrypts and checksums **per file it knows about**. A file
 * this app dropped into that directory itself would be renamed out from under it, would sit in plaintext
 * inside an encrypted backup, and would be unverified rather than verified-and-failing. A descriptor is
 * also a capability that **expires when it is closed**.
 *
 * ## Why `import` lives only here
 *
 * An import overwrites every note in the app. The §1 receiver is `exported="true"` with no permission —
 * an import action there would let any app on the phone wipe this one's notes. Here the caller is
 * identified by package, uid and pinned signing certificate before anything is read.
 */
// The count is the ContentProvider contract's own: five abstract members we must override in order to
// refuse them, plus the four automation methods.
@Suppress("TooManyFunctions")
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary the
     * broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a misbehaving
     * caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then this app's own switches — a token is ignored unless this app asks for one.
        ctx.config.refuseAutomation(extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }

            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw a row
     * before an export exists, and at restore must judge compatibility **before** streaming tens of
     * megabytes into an app that would reject them — which it cannot do if the header is buried inside an
     * encrypted archive.
     *
     * `contains` is rendered verbatim, so it names what would be lost rather than what the categories are
     * called: this app's notes are authored text, and no reinstall, sync or clean-phone restore of
     * anything else can bring them back.
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION") val versionCode = pkg.versionCode
        val contains = JSONArray(SettingsExport.Cat.describeOrder.map { ctx.getString(it.containsRes) })
        val header = JSONObject()
            .put("app_id", ctx.packageName)
            .put("version_code", versionCode)
            .put("version_name", pkg.versionName.orEmpty())
            .put("format", FORMAT)
            .put("min_format_readable", MIN_FORMAT_READABLE)
            // Our import merges into whatever is there and creates the database on demand, so it is safe
            // against a freshly installed app that has never been opened — which is the order 応用管理
            // restores in: install, do NOT launch, import, force-stop.
            .put("requires_launch_first", false)
            .put("contains", contains)
        return "OK:$header"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to the
     * binder transaction and is closed when `call()` returns; a service reading it afterwards would find
     * it shut. That is a bug you only see under load, so it is not left to the service to remember.
     *
     * A failed service start is answered, not thrown. Android 12+ refuses a foreground service started
     * from the background unless the app is exempt, and 白い熊's phone reports exactly that API level —
     * so this is a refusal 応用管理 will really see, and it must arrive as a readable `ERROR:` line
     * rather than as our stack trace. The duplicate and the job are released on that path, because
     * nothing will ever read the one or finish the other.
     */
    @Suppress("ReturnCount")
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION")
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        val started = runCatching { AutomationDataService.start(ctx, jobId, dup, importing, extras) }
        started.exceptionOrNull()?.let {
            AutomationDataService.discard(jobId)
            AutomationJobs.finish(jobId)
            return fail("ERROR:${it.message ?: it.javaClass.simpleName}")
        }
        return ok("OK:$jobId")
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }
    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever call()ed still has to answer these. Refusing loudly beats returning an
    // empty cursor, which reads downstream as "there is no data" rather than "wrong door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /** This app's archive format — the same number the ZIP's own manifest.json records. */
        const val FORMAT = SettingsExport.FORMAT_VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a caller
         * refuse the second case at discovery time, before anything is streamed. Our import reads a
         * category by its entry name and merges per key, so a format-1 archive still restores.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
