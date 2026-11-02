package auk.dialer.vroot.controller

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import auk.dialer.vroot.controller.util.LauncherIconManager
import auk.dialer.vroot.controller.util.PreferenceManager

/**
 * Auto backup as before (fullBackupOnly in the manifest: the whole app data is saved and restored
 * by the system). The agent only exists to act once a restore is done: the chosen launcher icon
 * lives in which alias is enabled, which a backup does not carry, so it is set again from the
 * restored preference.
 */
class AukBackupAgent : BackupAgent() {
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        runCatching { LauncherIconManager(this, PreferenceManager(this)).applySavedColor() }
    }
}
