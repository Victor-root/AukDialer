package auk.dialer.vroot

import androidx.room.Room
import auk.dialer.vroot.modal.db.AukDatabase
import auk.dialer.vroot.controller.BackupViewModel
import auk.dialer.vroot.controller.CallLogViewModel
import auk.dialer.vroot.controller.ContactsViewModel
import auk.dialer.vroot.modal.`interface`.ICallLogRepository
import auk.dialer.vroot.modal.`interface`.IContactsRepository
import auk.dialer.vroot.modal.repository.CallLogRepository
import auk.dialer.vroot.modal.repository.ContactsRepository
import auk.dialer.vroot.controller.util.LauncherIconManager
import auk.dialer.vroot.controller.util.PreferenceManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AukDatabase::class.java,
            "auk_database"
        ).allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<AukDatabase>().privateContactDao() }

    single<IContactsRepository> {
        ContactsRepository(androidContext(), get())
    }
    single<ICallLogRepository> {
        CallLogRepository(androidContext().contentResolver, androidContext(), get())
    }
    single {
        PreferenceManager(androidContext())
    }
    single {
        LauncherIconManager(androidContext(), get())
    }
    viewModel { ContactsViewModel(get(), get()) }
    viewModel { CallLogViewModel(get(), androidContext().contentResolver) }
    viewModel { BackupViewModel(get(), get()) }
}