package com.example.mynewsapp.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Stock::class, InvestHistory::class, FollowingList::class, CashDividend::class, StockDividend::class],
    version = 6,
    autoMigrations = [AutoMigration(from = 3, to = 4)],
    exportSchema = true
)
abstract class StockDatabase:RoomDatabase() {

    abstract fun stockDao(): StockDao

    companion object {
        @Volatile
        private var INSTANCE: StockDatabase? = null

        fun getDatabase(context: Context): StockDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StockDatabase::class.java,
                    "stock_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
        val MIGRATION_3_4 = object :Migration(3,4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `StockDividend` (`id` INTEGER NOT NULL, `date` INTEGER NOT NULL, `amount` FLOAT NOT NULL, `stockNo` TEXT NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `CashDividend` (`id` INTEGER NOT NULL, `date` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `stockNo` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_4_5 = object :Migration(4,5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE stocks ADD COLUMN price TEXT NOT NULL DEFAULT '0'")
            }
        }

        val MIGRATION_5_6 = object :Migration(5,6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS stocks_new (
                stockNo TEXT NOT NULL PRIMARY KEY,
                parentFollowingListId INTEGER NOT NULL DEFAULT 0,
                price TEXT NOT NULL DEFAULT '0'
            )
        """)

                // 将数据从旧表复制到新表，不包括 id 字段
                database.execSQL("""
            INSERT INTO stocks_new (stockNo, parentFollowingListId, price)
            SELECT stockNo, parentFollowingListId, price
            FROM stocks
        """)

                // 删除旧表
                database.execSQL("DROP TABLE stocks")

                // 将新表重命名为旧表名
                database.execSQL("ALTER TABLE stocks_new RENAME TO stocks")
            }
        }
    }


    @RenameTable(fromTableName = "stockList", toTableName = "stocks")
    class MyExampleAutoMigration : AutoMigrationSpec {
        @Override
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            // Invoked once auto migration is done
        }
    }

}