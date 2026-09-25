package org.cssnr.parking.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

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
     * Adds location fix metadata and reverse geocoded address components.
     *
     * Every added column is nullable, so existing rows stay valid and read back
     * with null metadata. The `addr_` prefixed columns match the prefix on the
     * `@Embedded` [LocationAddress] inside [History].
     */
    val MIGRATION_1_2 = Migration(1, 2) { connection ->
        connection.exec("ALTER TABLE `history` ADD COLUMN `fixTimestamp` INTEGER")
        connection.exec("ALTER TABLE `history` ADD COLUMN `fixAgeMillis` INTEGER")
        connection.exec("ALTER TABLE `history` ADD COLUMN `accuracy` REAL")
        connection.exec("ALTER TABLE `history` ADD COLUMN `altitude` REAL")
        connection.exec("ALTER TABLE `history` ADD COLUMN `verticalAccuracy` REAL")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_line` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_featureName` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_thoroughfare` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_subThoroughfare` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_premises` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_adminArea` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_subAdminArea` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_locality` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_subLocality` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_postalCode` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_countryName` TEXT")
        connection.exec("ALTER TABLE `history` ADD COLUMN `addr_countryCode` TEXT")
    }
}

/**
 * Room 3 migrations hand over an [androidx.sqlite.SQLiteConnection] rather than the
 * old `SupportSQLiteDatabase`, and there is no `executeSQL` extension on the new
 * API, so statements are prepared and stepped directly.
 */
private fun SQLiteConnection.exec(sql: String) {
    prepare(sql).use { it.step() }
}
