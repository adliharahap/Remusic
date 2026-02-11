package com.example.remusic.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.remusic.data.local.entity.CachedArtist
import com.example.remusic.data.local.entity.CachedSong
import com.example.remusic.data.local.entity.LikedSong
import com.example.remusic.data.local.entity.SearchHistoryEntity

@Database(
    entities = [CachedSong::class, CachedArtist::class, LikedSong::class, SearchHistoryEntity::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class) // Registrasi TypeConverter di sini
abstract class MusicDatabase : RoomDatabase() {

    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_songs ADD COLUMN lyricsUpdatedAt TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Hapus data lama biar bersih (Opsional, tapi aman buat dev)
                db.execSQL("DROP TABLE IF EXISTS search_history")
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`songId` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`), FOREIGN KEY(`songId`) REFERENCES `cached_songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tambahkan kolom baru untuk Smart Queue
                // Karena SQLite tidak support add multiple columns sekaligus secara standard, kita satu-satu
                db.execSQL("ALTER TABLE cached_songs ADD COLUMN language TEXT")
                // moods disimpan sebagai JSON String (Text) karena kita pakai TypeConverter
                db.execSQL("ALTER TABLE cached_songs ADD COLUMN moods TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE cached_songs ADD COLUMN artistId TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_songs ADD COLUMN canvasUrl TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Pastikan kolom artistName ada (jika belum)
                val cursor = db.query("PRAGMA table_info(cached_songs)")
                var hasArtistName = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "artistName") {
                        hasArtistName = true
                        break
                    }
                }
                cursor.close()

                if (!hasArtistName) {
                    db.execSQL("ALTER TABLE cached_songs ADD COLUMN artistName TEXT NOT NULL DEFAULT 'Unknown'")
                }
            }
        }
        
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_songs ADD COLUMN featuredArtists TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                                context.applicationContext,
                                MusicDatabase::class.java,
                                "music_database"
                            )
                                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}