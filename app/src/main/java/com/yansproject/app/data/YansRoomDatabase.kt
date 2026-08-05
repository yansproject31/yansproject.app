package com.yansproject.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OfflineActionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class YansRoomDatabase : RoomDatabase() {
    abstract fun offlineActionDao(): OfflineActionDao

    companion object {
        @Volatile
        private var INSTANCE: YansRoomDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_actions ADD COLUMN additionalMeta TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): YansRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    YansRoomDatabase::class.java,
                    "yans_local_secure.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
