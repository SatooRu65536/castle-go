package dev.satooru.castles.go.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CastleEntity::class,
        CastleTagEntity::class,
        CastleTagAssignmentEntity::class,
        CastleDetailEntity::class,
        VisitEntity::class,
        SyncStateEntity::class,
        NotifiedCastleEntity::class,
    ],
    version = 2,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun castleDao(): CastleDao
    abstract fun tagDao(): TagDao
    abstract fun detailDao(): DetailDao
    abstract fun visitDao(): VisitDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun notifiedCastleDao(): NotifiedCastleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "castle-go.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * 城に国（`country`）と韓国の市・道（`region`）を追加する．
         * 既存の城は日本．韓国の城を取り込むため，同期のカーソルと ETag を初期化して全件を取得し直す
         * （docs/api.md「取得が必要な列が増えた」）．訪問記録などの利用者のデータは残す．
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE castle ADD COLUMN country TEXT NOT NULL DEFAULT 'JP'")
                db.execSQL("ALTER TABLE castle ADD COLUMN region TEXT")
                db.execSQL("UPDATE sync_state SET cursor = NULL, tagsEtag = NULL, lastCheckedAt = NULL")
            }
        }
    }
}
