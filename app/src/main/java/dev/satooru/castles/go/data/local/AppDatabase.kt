package dev.satooru.castles.go.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
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
            Room.databaseBuilder(context, AppDatabase::class.java, "castle-go.db").build()
    }
}
