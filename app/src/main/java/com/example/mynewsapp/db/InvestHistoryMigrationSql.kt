package com.example.mynewsapp.db

/**
 * The SQL of the investHistory migrations, kept free of Android types so that a JVM test can run it on SQLite.
 */
object InvestHistoryMigrationSql {
    /**
     * Version 7 stores the fee, the tax and the memo of each record. The columns are nullable without a default,
     * so every existing record keeps its values and its fee, tax and memo are unknown.
     */
    val V6_TO_V7: List<String> = listOf(
        "ALTER TABLE `investHistory` ADD COLUMN `fee` INTEGER",
        "ALTER TABLE `investHistory` ADD COLUMN `tax` INTEGER",
        "ALTER TABLE `investHistory` ADD COLUMN `memo` TEXT",
    )
}
