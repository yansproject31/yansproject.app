package com.yansproject.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OfflineActionEntity::class],
    version = 4,
    exportSchema = true
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

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_actions ADD COLUMN queueVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE offline_actions ADD COLUMN payloadVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE offline_actions ADD COLUMN schemaVersion INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_actions ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
            }
        }

        fun getDatabase(context: Context): YansRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    YansRoomDatabase::class.java,
                    "yans_local_secure.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
