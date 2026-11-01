package auk.dialer.vroot.controller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import auk.dialer.vroot.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Access to [PrivilegedRecorderService] through Shizuku: whether it can be used, the permission it
 * needs, and the connection to it once it can.
 */
object ShizukuRecorder {

    enum class Status {
        /** Shizuku (or Sui) is not running, or too old for a service built with a Context. */
        Unavailable,
        NeedsPermission,
        /** The user refused and asked not to be asked again, so only the Shizuku app can allow it. */
        Denied,
        Ready
    }

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    private const val PERMISSION_REQUEST_CODE = 4242
    private const val MIN_SHIZUKU_VERSION = 13
    private const val BIND_TIMEOUT_MS = 5000L

    private val _status = MutableStateFlow(Status.Unavailable)
    val status = _status.asStateFlow()

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, PrivilegedRecorderService::class.java.name)
    )
        // Shizuku tells services apart by tag, and the class name changes with R8.
        .tag("call_recorder")
        .processNameSuffix("recorder")
        .daemon(false)
        .version(BuildConfig.VERSION_CODE)

    // Only touched on the main thread.
    private var connection: CompletableDeferred<IPrivilegedRecorder>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                connection?.complete(IPrivilegedRecorder.Stub.asInterface(binder))
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connection = null
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            connection = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
    }

    fun refresh() {
        // Each of these is a call to the Shizuku server, which may die in between.
        _status.value = runCatching {
            when {
                !Shizuku.pingBinder() || Shizuku.isPreV11() || Shizuku.getVersion() < MIN_SHIZUKU_VERSION -> Status.Unavailable
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.Ready
                Shizuku.shouldShowRequestPermissionRationale() -> Status.Denied
                else -> Status.NeedsPermission
            }
        }.getOrDefault(Status.Unavailable)
    }

    fun requestPermission() {
        if (_status.value == Status.NeedsPermission) {
            runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
        }
    }

    /** Opens the Shizuku app, where it is started and its permissions managed, or its download page. */
    fun openShizuku(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** The recorder service, started if needed, or null when Shizuku can't provide it. */
    suspend fun recorder(): IPrivilegedRecorder? {
        val pending = withContext(Dispatchers.Main) {
            refresh()
            if (_status.value != Status.Ready) null else connection ?: bind()
        } ?: return null
        val remote = withTimeoutOrNull(BIND_TIMEOUT_MS) { pending.await() } ?: return null
        if (remote.asBinder().isBinderAlive) return remote
        // The service died without Shizuku reporting it: the next recording starts a new one.
        withContext(Dispatchers.Main) { if (connection === pending) connection = null }
        return null
    }

    private fun bind(): CompletableDeferred<IPrivilegedRecorder>? {
        val deferred = CompletableDeferred<IPrivilegedRecorder>()
        connection = deferred
        if (runCatching { Shizuku.bindUserService(serviceArgs, serviceConnection) }.isFailure) {
            connection = null
            return null
        }
        return deferred
    }
}
