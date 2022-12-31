package com.example.mynewsapp.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Stock::class, InvestHistory::class, FollowingList::class, CashDividend::class, StockDividend::class],
    version = 4,
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
    }


    @RenameTable(fromTableName = "stockList", toTableName = "stocks")
    class MyExampleAutoMigration : AutoMigrationSpec {
        @Override
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            // Invoked once auto migration is done
        }
    }

}