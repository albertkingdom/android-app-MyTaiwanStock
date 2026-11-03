package com.example.mynewsapp.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Upgrades a version 6 database to 7 and lets Room check the migrated tables against the entities. */
@RunWith(AndroidJUnit4::class)
class StockDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), StockDatabase::class.java)

    @Test
    fun version6RecordsAreKeptWithAnUnknownFeeTaxAndMemo() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            val records = listOf(
                "(1, '2330', 1700000000000, 550.0, 1000, 0)",
                "(2, '2330', 1700100000000, 560.5, 500, 0)",
                "(3, '0050', 1700200000000, 112.55, 5, 0)",
                "(4, '2330', 1700300000000, 600.0, 300, 1)",
                "(5, '0050', 1700400000000, 120.0, 5, 1)",
            )
            records.forEach {
                db.execSQL("INSERT INTO investHistory (id, stockNo, date, price, amount, status) VALUES $it")
            }
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, StockDatabase.MIGRATION_6_7)

        db.query("SELECT id, stockNo, price, amount, status, fee, tax, memo FROM investHistory ORDER BY id").use { cursor ->
            assertEquals(5, cursor.count)
            val ids = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ids += cursor.getInt(0)
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("fee")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("tax")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("memo")))
            }
            assertEquals(listOf(1, 2, 3, 4, 5), ids)
        }
        db.query("SELECT stockNo, price, amount, status FROM investHistory WHERE id = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("0050", cursor.getString(0))
            assertEquals(112.55, cursor.getDouble(1), 0.0)
            assertEquals(5, cursor.getInt(2))
            assertEquals(0, cursor.getInt(3))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
