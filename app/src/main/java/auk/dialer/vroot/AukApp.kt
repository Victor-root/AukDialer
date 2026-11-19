package auk.dialer.vroot

import android.app.Application
import auk.dialer.vroot.controller.BlockedCallWorker
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AukApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AukApp)
            modules(appModule)
        }
        BlockedCallWorker.schedule(this, get())
    }
}
