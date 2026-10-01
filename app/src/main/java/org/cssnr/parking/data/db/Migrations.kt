package org.cssnr.parking.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

/**
 * Schema migrations for [AppDatabase].
 *
 * Written as manual migrations rather than `@AutoMigration` because automatic
 * migrations require `exportSchema = true` plus the Room Gradle plugin's
 * `schemaDirectory` to produce the version 1 schema bundle. Adding nullable
 * columns is a handful of `ALTER TABLE` statements, which does not justify
 * turning on schema export.
 */
object Migrations {

    /**
     * Adds location fix metadata, reverse geocoded address components, and the
     * [History.geocoded] marker the backfill keys off.
     *
     * Every added column is nullable, so existing rows stay valid and read back
     * with null metadata, and no column needs a DEFAULT clause. The `addr_`
     * prefixed columns match the prefix on the `@Embedded` [LocationAddress]
     * inside [History].
     *
     * `Migration.migrate` receives an [androidx.sqlite.SQLiteConnection] and
     * [execSQL] runs a single statement against it; the migration is already
     * wrapped in a transaction by Room.
     */
    val MIGRATION_1_2 = Migration(1, 2) { connection ->
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `fixAgeMillis` INTEGER")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `accuracy` REAL")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `altitude` REAL")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `verticalAccuracy` REAL")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_line` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_featureName` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_thoroughfare` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_subThoroughfare` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_premises` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_adminArea` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_subAdminArea` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_locality` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_subLocality` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_postalCode` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_countryName` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `addr_countryCode` TEXT")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `geocoded` INTEGER")
    }

    /**
     * Makes [History.latitude] and [History.longitude] nullable, so a disconnect
     * whose location request produced nothing can still be recorded.
     *
     * SQLite has no `ALTER COLUMN`, so dropping the `NOT NULL` requires the
     * standard create-copy-drop-rename rebuild. The column list below is the exact
     * one Room derives from the entity, in the same order it emits in
     * `createAllTables`. Room validates the table against that schema at open and
     * throws on any mismatch, so the two are written to agree exactly rather than
     * relying on the comparison tolerating a difference.
     *
     * Every existing row already has coordinates, so the copy is total. The
     * `AUTOINCREMENT` keyword is carried over so ids stay unique across the swap.
     */
    val MIGRATION_2_3 = Migration(2, 3) { connection ->
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `history_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `latitude` REAL,
                `longitude` REAL,
                `bluetoothAddress` TEXT NOT NULL,
                `bluetoothName` TEXT,
                `fixAgeMillis` INTEGER,
                `accuracy` REAL,
                `altitude` REAL,
                `verticalAccuracy` REAL,
                `geocoded` INTEGER,
                `addr_line` TEXT,
                `addr_featureName` TEXT,
                `addr_thoroughfare` TEXT,
                `addr_subThoroughfare` TEXT,
                `addr_premises` TEXT,
                `addr_adminArea` TEXT,
                `addr_subAdminArea` TEXT,
                `addr_locality` TEXT,
                `addr_subLocality` TEXT,
                `addr_postalCode` TEXT,
                `addr_countryName` TEXT,
                `addr_countryCode` TEXT
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO `history_new` (
                `id`, `timestamp`, `latitude`, `longitude`, `bluetoothAddress`,
                `bluetoothName`, `fixAgeMillis`, `accuracy`, `altitude`,
                `verticalAccuracy`, `geocoded`, `addr_line`, `addr_featureName`,
                `addr_thoroughfare`, `addr_subThoroughfare`, `addr_premises`,
                `addr_adminArea`, `addr_subAdminArea`, `addr_locality`,
                `addr_subLocality`, `addr_postalCode`, `addr_countryName`,
                `addr_countryCode`
            )
            SELECT
                `id`, `timestamp`, `latitude`, `longitude`, `bluetoothAddress`,
                `bluetoothName`, `fixAgeMillis`, `accuracy`, `altitude`,
                `verticalAccuracy`, `geocoded`, `addr_line`, `addr_featureName`,
                `addr_thoroughfare`, `addr_subThoroughfare`, `addr_premises`,
                `addr_adminArea`, `addr_subAdminArea`, `addr_locality`,
                `addr_subLocality`, `addr_postalCode`, `addr_countryName`,
                `addr_countryCode`
            FROM `history`
            """.trimIndent(),
        )
        connection.execSQL("DROP TABLE `history`")
        connection.execSQL("ALTER TABLE `history_new` RENAME TO `history`")
    }
}
