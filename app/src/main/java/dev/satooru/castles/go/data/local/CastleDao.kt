package dev.satooru.castles.go.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 絞り込みの条件．NULL（または空文字・0）は「条件なし」を表す．
 * 条件の SQL は [CastleFilterSql] に集約する．
 */
private const val SELECT_CASTLE = "SELECT * FROM castle WHERE deletedAt IS NULL AND "

object CastleFilterSql {
    const val WHERE =
        "(:query = '' OR searchText LIKE '%' || :query || '%') " +
            "AND (:prefecture IS NULL OR prefecture = :prefecture) " +
            "AND scale >= :minScale " +
            "AND (:castleKind IS NULL OR castleKind = :castleKind) " +
            "AND (:tenshuStatus IS NULL OR tenshuStatus = :tenshuStatus) " +
            "AND (:locationType IS NULL OR locationType = :locationType) " +
            "AND (:meijoSelection IS NULL OR meijoSelection = :meijoSelection) " +
            "AND (:tagId IS NULL OR id IN (SELECT castleId FROM castle_tag_assignment WHERE tagId = :tagId)) " +
            "AND (:unvisitedOnly = 0 OR id NOT IN (SELECT castleId FROM visit))"

    /** 緯度経度の差の二乗和（経度は緯度の余弦で補正）．距離順の並べ替えに使う近似値 */
    const val DISTANCE =
        "((lat - :userLat) * (lat - :userLat) + (lng - :userLng) * (lng - :userLng) * :cosLatSquared)"
}

@Dao
interface CastleDao {
    @Upsert
    suspend fun upsertAll(castles: List<CastleEntity>)

    @Query("DELETE FROM castle WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM castle WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<CastleEntity?>

    @Query("SELECT * FROM castle WHERE id = :id")
    suspend fun find(id: String): CastleEntity?

    @Query("SELECT COUNT(*) FROM castle WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query(
        SELECT_CASTLE + CastleFilterSql.WHERE + " ORDER BY nameKana, id",
    )
    fun pagingByKana(
        query: String,
        prefecture: String?,
        minScale: Int,
        castleKind: String?,
        tenshuStatus: String?,
        locationType: String?,
        meijoSelection: String?,
        tagId: String?,
        unvisitedOnly: Int,
    ): PagingSource<Int, CastleEntity>

    @Query(
        SELECT_CASTLE + CastleFilterSql.WHERE + " ORDER BY " + CastleFilterSql.DISTANCE + ", id",
    )
    fun pagingByDistance(
        userLat: Double,
        userLng: Double,
        cosLatSquared: Double,
        query: String,
        prefecture: String?,
        minScale: Int,
        castleKind: String?,
        tenshuStatus: String?,
        locationType: String?,
        meijoSelection: String?,
        tagId: String?,
        unvisitedOnly: Int,
    ): PagingSource<Int, CastleEntity>

    /** 緯度経度の範囲で絞ってから，近い順に上位 [limit] 件を返す．周辺の城・車載に使う． */
    @Query(
        SELECT_CASTLE + "lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng AND " +
            CastleFilterSql.WHERE + " ORDER BY " + CastleFilterSql.DISTANCE + ", id LIMIT :limit",
    )
    suspend fun nearby(
        userLat: Double,
        userLng: Double,
        cosLatSquared: Double,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        limit: Int,
        query: String,
        prefecture: String?,
        minScale: Int,
        castleKind: String?,
        tenshuStatus: String?,
        locationType: String?,
        meijoSelection: String?,
        tagId: String?,
        unvisitedOnly: Int,
    ): List<CastleEntity>

    /** 地図の表示範囲の城．規模で間引くため [minScale] 以上のみ返す． */
    @Query(
        "SELECT * FROM castle WHERE deletedAt IS NULL AND lat BETWEEN :minLat AND :maxLat " +
            "AND lng BETWEEN :minLng AND :maxLng AND scale >= :minScale LIMIT :limit",
    )
    fun observeInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        minScale: Int,
        limit: Int,
    ): Flow<List<CastleEntity>>
}

@Dao
interface TagDao {
    @Query("SELECT * FROM castle_tag ORDER BY name")
    fun observeTags(): Flow<List<CastleTagEntity>>

    @Query("SELECT t.* FROM castle_tag t JOIN castle_tag_assignment a ON a.tagId = t.id WHERE a.castleId = :castleId ORDER BY t.name")
    fun observeTagsOf(castleId: String): Flow<List<CastleTagEntity>>

    @Query("DELETE FROM castle_tag")
    suspend fun clearTags()

    @Query("DELETE FROM castle_tag_assignment")
    suspend fun clearAssignments()

    @Upsert
    suspend fun upsertTags(tags: List<CastleTagEntity>)

    @Upsert
    suspend fun upsertAssignments(assignments: List<CastleTagAssignmentEntity>)

    @Query("DELETE FROM castle_tag_assignment WHERE castleId IN (:castleIds)")
    suspend fun deleteAssignmentsOf(castleIds: List<String>)
}

@Dao
interface DetailDao {
    @Query("SELECT * FROM castle_detail WHERE castleId = :castleId")
    suspend fun find(castleId: String): CastleDetailEntity?

    @Query("SELECT * FROM castle_detail WHERE castleId = :castleId")
    fun observe(castleId: String): Flow<CastleDetailEntity?>

    @Upsert
    suspend fun upsert(detail: CastleDetailEntity)

    @Query("DELETE FROM castle_detail WHERE castleId IN (:castleIds)")
    suspend fun deleteByIds(castleIds: List<String>)

    @Query("DELETE FROM castle_detail")
    suspend fun clear()
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visit WHERE castleId = :castleId")
    fun observe(castleId: String): Flow<VisitEntity?>

    @Query("SELECT * FROM visit ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<VisitEntity>>

    @Upsert
    suspend fun upsert(visit: VisitEntity)

    @Query("DELETE FROM visit WHERE castleId = :castleId")
    suspend fun delete(castleId: String)

    /** 名城の選定ごとの総数と訪問済みの数 */
    @Query(
        "SELECT c.meijoSelection AS selection, COUNT(*) AS total, " +
            "SUM(CASE WHEN v.castleId IS NOT NULL THEN 1 ELSE 0 END) AS visited " +
            "FROM castle c LEFT JOIN visit v ON v.castleId = c.id " +
            "WHERE c.deletedAt IS NULL AND c.meijoSelection IS NOT NULL GROUP BY c.meijoSelection",
    )
    fun observeMeijoProgress(): Flow<List<MeijoProgressRow>>
}

data class MeijoProgressRow(
    val selection: String,
    val total: Int,
    val visited: Int,
)

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observe(): Flow<SyncStateEntity?>

    @Upsert
    suspend fun upsert(state: SyncStateEntity)
}

@Dao
interface NotifiedCastleDao {
    @Query("SELECT castleId FROM notified_castle WHERE notifiedAt >= :since")
    suspend fun notifiedSince(since: Long): List<String>

    @Upsert
    suspend fun upsert(notified: NotifiedCastleEntity)

    @Query("DELETE FROM notified_castle WHERE notifiedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
