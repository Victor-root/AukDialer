package auk.dialer.vroot.modal.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PrivateContactEntity::class], version = 3, exportSchema = true)
abstract class AukDatabase : RoomDatabase() {
    abstract fun privateContactDao(): PrivateContactDao

    companion object {
        /**
         * One entry per version step, e.g. a Migration(2, 3) when version goes to 3. Room refuses to
         * open the database when a step is missing instead of wiping the private contacts.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // Private contacts gain a company.
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE private_contacts ADD COLUMN company TEXT")
                }
            }
        )
    }
}
