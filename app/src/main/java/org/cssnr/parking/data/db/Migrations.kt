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
}
