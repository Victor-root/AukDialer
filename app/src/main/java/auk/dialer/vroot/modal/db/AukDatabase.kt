package auk.dialer.vroot.modal.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PrivateContactEntity::class], version = 2, exportSchema = false)
abstract class AukDatabase : RoomDatabase() {
    abstract fun privateContactDao(): PrivateContactDao
}
