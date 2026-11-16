package dev.satooru.castles.go.data.repository

import dev.satooru.castles.go.data.local.AppDatabase
import dev.satooru.castles.go.data.local.MeijoProgressRow
import dev.satooru.castles.go.data.local.VisitEntity
import dev.satooru.castles.go.data.local.VisitedCastleRow
import kotlinx.coroutines.flow.Flow

/** 訪問記録．端末のみに保存し，API は使わない． */
class VisitRepository(db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.visitDao()

    fun observe(castleId: String): Flow<VisitEntity?> = dao.observe(castleId)

    fun observeAll(): Flow<List<VisitEntity>> = dao.observeAll()

    fun observeVisitedCastles(): Flow<List<VisitedCastleRow>> = dao.observeVisitedCastles()

    fun observeMeijoProgress(): Flow<List<MeijoProgressRow>> = dao.observeMeijoProgress()

    suspend fun markVisited(castleId: String, visitedAt: Long = clock(), memo: String = "") =
        dao.upsert(VisitEntity(castleId, visitedAt, memo))

    suspend fun update(visit: VisitEntity) = dao.upsert(visit)

    suspend fun unmark(castleId: String) = dao.delete(castleId)
}
