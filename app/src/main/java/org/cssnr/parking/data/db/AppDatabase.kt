package org.cssnr.parking.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

/**
 * The parking history database.
 *
 * Schema changes are destructive: [fallbackToDestructiveMigration] drops every
 * table and recreates them from the entities when no migration path exists, which
 * removes the need to hand-write a [androidx.room3.migration.Migration] per
 * change. That is a deliberate choice while the app is in beta, where losing a
 * parking history costs nobody anything, and it should be revisited before a
 * release anyone relies on.
 *
 * [version] must stay different from every version already shipped, including
 * ones no longer reachable by a migration. Room compares it against
 * `PRAGMA user_version`, so reusing a number that an installed copy already has
 * means Room sees no change at all, skips the destructive path, and then fails
 * schema validation on the stale table. Versions 1 and 2 have shipped; this is 3.
 */
@Database(
    entities = [History::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parking.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
