package com.example.mynewsapp.db

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the version 6 to 7 SQL on SQLite with the investHistory table exactly as the exported version 6 schema
 * creates it. Room's own check of the migrated table against the entity is in StockDatabaseMigrationTest.
 */
class InvestHistoryMigrationSqlTest {
    private lateinit var connection: Connection

    private data class Row(val id: Int, val stockNo: String, val date: Long, val price: Double, val amount: Int, val status: Int)

    private val rows = listOf(
        Row(1, "2330", 1_700_000_000_000, 550.0, 1000, 0),
        Row(2, "2330", 1_700_100_000_000, 560.5, 500, 0),
        Row(3, "0050", 1_700_200_000_000, 112.55, 5, 0),
        Row(4, "2330", 1_700_300_000_000, 600.0, 300, 1),
        Row(5, "0050", 1_700_400_000_000, 120.0, 5, 1),
    )

    @Before
    fun createVersion6Table() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { it.execute(version6CreateSql()) }
        connection.prepareStatement(
            "INSERT INTO investHistory (id, stockNo, date, price, amount, status) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { insert ->
            for (row in rows) {
                insert.setInt(1, row.id)
                insert.setString(2, row.stockNo)
                insert.setLong(3, row.date)
                insert.setDouble(4, row.price)
                insert.setInt(5, row.amount)
                insert.setInt(6, row.status)
                insert.executeUpdate()
            }
        }
    }

    @After
    fun close() {
        connection.close()
    }

    @Test
    fun everyRecordIsKeptWithAnUnknownFeeTaxAndMemo() {
        migrate()

        val read = mutableListOf<Row>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, stockNo, date, price, amount, status, fee, tax, memo FROM investHistory ORDER BY id")
                .use { result ->
                    while (result.next()) {
                        read += Row(
                            result.getInt("id"), result.getString("stockNo"), result.getLong("date"),
                            result.getDouble("price"), result.getInt("amount"), result.getInt("status"),
                        )
                        val id = result.getInt("id")
                        for (column in listOf("fee", "tax", "memo")) {
                            assertEquals("$column of record $id", null, result.getObject(column))
                        }
                    }
                }
        }
        assertEquals(rows, read)
    }

    @Test
    fun theNewColumnsAreNullableWithoutADefault() {
        migrate()

        val columns = mutableMapOf<String, Triple<String, Boolean, String?>>()
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(investHistory)").use { result ->
                while (result.next()) {
                    columns[result.getString("name")] =
                        Triple(result.getString("type"), result.getInt("notnull") == 1, result.getString("dflt_value"))
                }
            }
        }
        assertEquals(Triple("INTEGER", false, null), columns["fee"])
        assertEquals(Triple("INTEGER", false, null), columns["tax"])
        assertEquals(Triple("TEXT", false, null), columns["memo"])
        assertEquals(setOf("id", "stockNo", "date", "price", "amount", "status", "fee", "tax", "memo"), columns.keys)
    }

    @Test
    fun aNewRecordCanStoreAFeeTaxAndMemo() {
        migrate()

        connection.createStatement().use {
            it.executeUpdate(
                "INSERT INTO investHistory (stockNo, date, price, amount, status, fee, tax, memo) " +
                    "VALUES ('2330', 0, 757.0, 10, 1, 6, 22, 'note')"
            )
        }

        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT fee, tax, memo FROM investHistory WHERE id = 6").use { result ->
                assertTrue(result.next())
                assertEquals(6, result.getInt("fee"))
                assertEquals(22, result.getInt("tax"))
                assertEquals("note", result.getString("memo"))
            }
        }
    }

    private fun migrate() {
        connection.createStatement().use { statement ->
            InvestHistoryMigrationSql.V6_TO_V7.forEach { statement.execute(it) }
        }
    }

    /** The createSql of investHistory in the exported version 6 schema, with its table name filled in. */
    private fun version6CreateSql(): String {
        val schema = File("schemas/com.example.mynewsapp.db.StockDatabase/6.json").readText()
        val entity = schema.substringAfter("\"tableName\": \"investHistory\"")
        val createSql = Regex("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"").find(entity)!!.groupValues[1]
        return createSql.replace("\\\"", "\"").replace("\${TABLE_NAME}", "investHistory")
    }
}
