package dev.satooru.castles.go.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import dev.satooru.castles.go.data.local.AppDatabase
import dev.satooru.castles.go.data.local.CastleDetailEntity
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.local.CastleTagEntity
import dev.satooru.castles.go.data.local.ScaleMask
import dev.satooru.castles.go.data.remote.CastleWikiApi
import dev.satooru.castles.go.data.remote.PhotoDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos

/** 絞り込みの条件．すべて端末の Room で処理する． */
data class CastleFilter(
    val query: String = "",
    val prefecture: String? = null,
    val minScale: Int = 1,
    /** 表示する規模の集合（[ScaleMask]）．既定はすべて */
    val scaleMask: Int = ScaleMask.ALL,
    val castleKind: String? = null,
    val tenshuStatus: String? = null,
    val locationType: String? = null,
    val meijoSelection: String? = null,
    val tagId: String? = null,
    val unvisitedOnly: Boolean = false,
)

data class GeoPoint(val lat: Double, val lng: Double)

/** 城の詳細．本文・属性・写真の一覧． */
data class CastleDetail(
    val revision: Int,
    val body: String,
    val attributes: Map<String, JsonElement>,
    val photos: List<PhotoDto>,
)

sealed interface DetailResult {
    data class Success(val detail: CastleDetail, val fromCache: Boolean) : DetailResult
    /** 詳細が `410`．城は端末からも削除済みにした． */
    data object Gone : DetailResult
    /** 通信に失敗し，使えるキャッシュもない． */
    data class Failure(val cause: Throwable) : DetailResult
}

class CastleRepository(
    private val api: CastleWikiApi,
    private val db: AppDatabase,
    private val json: Json,
) {
    private val castleDao = db.castleDao()
    private val tagDao = db.tagDao()
    private val detailDao = db.detailDao()

    fun observeCastle(id: String): Flow<CastleEntity?> = castleDao.observe(id)

    suspend fun findCastle(id: String): CastleEntity? = castleDao.find(id)

    fun observeCount(): Flow<Int> = castleDao.observeCount()

    fun observeTags(): Flow<List<CastleTagEntity>> = tagDao.observeTags()

    fun observeTagsOf(castleId: String): Flow<List<CastleTagEntity>> = tagDao.observeTagsOf(castleId)

    /** 一覧．現在地があれば近い順，なければ読み仮名順．1 万行を一度に読まず Paging で読む． */
    fun pagedCastles(filter: CastleFilter, origin: GeoPoint?): Flow<PagingData<CastleEntity>> =
        Pager(PagingConfig(pageSize = 50, enablePlaceholders = false)) {
            if (origin == null) {
                castleDao.pagingByKana(
                    filter.query.trim().let(::escapeLike), filter.prefecture, filter.minScale, filter.scaleMask, filter.castleKind,
                    filter.tenshuStatus, filter.locationType, filter.meijoSelection, filter.tagId,
                    if (filter.unvisitedOnly) 1 else 0,
                )
            } else {
                castleDao.pagingByDistance(
                    origin.lat, origin.lng, cosSquared(origin.lat),
                    filter.query.trim().let(::escapeLike), filter.prefecture, filter.minScale, filter.scaleMask, filter.castleKind,
                    filter.tenshuStatus, filter.locationType, filter.meijoSelection, filter.tagId,
                    if (filter.unvisitedOnly) 1 else 0,
                )
            }
        }.flow

    /**
     * 現在地の周辺の城．緯度経度の範囲で絞ってから近い順に並べる．
     * [radiusKm] が NULL のときは件数のみで切る（範囲は全体）．
     */
    suspend fun nearby(
        origin: GeoPoint,
        filter: CastleFilter,
        limit: Int,
        radiusKm: Double?,
    ): List<NearbyCastle> {
        val (dLat, dLng) = if (radiusKm == null) {
            180.0 to 360.0
        } else {
            val lat = radiusKm / KM_PER_DEGREE
            lat to lat / maxOf(cos(Math.toRadians(origin.lat)), MIN_COS)
        }
        val rows = castleDao.nearby(
            userLat = origin.lat, userLng = origin.lng, cosLatSquared = cosSquared(origin.lat),
            minLat = origin.lat - dLat, maxLat = origin.lat + dLat,
            minLng = origin.lng - dLng, maxLng = origin.lng + dLng,
            // 半径の円の外側を取り除くため，範囲の矩形では余分に取る
            limit = if (radiusKm == null) limit else limit * 4,
            query = filter.query.trim().let(::escapeLike), prefecture = filter.prefecture, minScale = filter.minScale, scaleMask = filter.scaleMask,
            castleKind = filter.castleKind, tenshuStatus = filter.tenshuStatus,
            locationType = filter.locationType, meijoSelection = filter.meijoSelection,
            tagId = filter.tagId, unvisitedOnly = if (filter.unvisitedOnly) 1 else 0,
        )
        return rows
            .map { NearbyCastle(it, DistanceCalculator.meters(origin, GeoPoint(it.lat, it.lng))) }
            .filter { radiusKm == null || it.distanceMeters <= radiusKm * 1000 }
            .sortedBy { it.distanceMeters }
            .take(limit)
    }

    fun observeInBounds(
        minLat: Double, maxLat: Double, minLng: Double, maxLng: Double, minScale: Int, scaleMask: Int, limit: Int,
    ): Flow<List<CastleEntity>> = castleDao.observeInBounds(minLat, maxLat, minLng, maxLng, minScale, scaleMask, limit)

    fun observeDetailCache(castleId: String): Flow<CastleDetailEntity?> = detailDao.observe(castleId)

    /**
     * 詳細を返す．`castle.currentRevisionNumber` とキャッシュの版が一致すれば API を呼ばない．
     * 不一致・未取得のときのみ詳細 API を 1 回呼び，版つきで保存する．
     */
    suspend fun loadDetail(castleId: String): DetailResult = withContext(Dispatchers.Default) {
        loadDetailInternal(castleId)
    }

    private suspend fun loadDetailInternal(castleId: String): DetailResult {
        val castle = castleDao.find(castleId)
        val cached = detailDao.find(castleId)
        if (castle != null && cached != null && cached.revisionNumber == castle.currentRevisionNumber) {
            return DetailResult.Success(cached.toDetail(), fromCache = true)
        }
        return try {
            val response = api.castleDetail(castleId, cached?.let { "\"${it.revisionNumber}\"" })
            when {
                response.code() == 304 && cached != null ->
                    DetailResult.Success(cached.toDetail(), fromCache = true)
                response.code() == 410 -> {
                    db.withTransaction {
                        castleDao.deleteByIds(listOf(castleId))
                        detailDao.deleteByIds(listOf(castleId))
                    }
                    DetailResult.Gone
                }
                response.isSuccessful -> {
                    val dto = response.body() ?: return fallback(cached, HttpStatusException(response.code()))
                    detailDao.upsert(
                        CastleDetailEntity(
                            castleId = castleId,
                            revisionNumber = dto.revision,
                            body = dto.body,
                            attributes = json.encodeToString(JsonObject(dto.attributes)),
                            photos = json.encodeToString(dto.photos),
                        ),
                    )
                    DetailResult.Success(CastleDetail(dto.revision, dto.body, dto.attributes, dto.photos), fromCache = false)
                }
                else -> fallback(cached, HttpStatusException(response.code()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 通信の失敗，想定外の JSON，Room の例外．キャッシュがあればそれを使う．
            fallback(cached, e)
        }
    }

    suspend fun clearDetailCache() = detailDao.clear()

    private fun fallback(cached: CastleDetailEntity?, cause: Throwable): DetailResult =
        if (cached != null) DetailResult.Success(cached.toDetail(), fromCache = true) else DetailResult.Failure(cause)

    private fun CastleDetailEntity.toDetail() = CastleDetail(
        revision = revisionNumber,
        body = body,
        attributes = (json.parseToJsonElement(attributes) as? JsonObject) ?: emptyMap<String, JsonElement>().let { JsonObject(it) },
        photos = json.decodeFromString(photos),
    )

    private fun cosSquared(lat: Double): Double = cos(Math.toRadians(lat)).let { it * it }

    companion object {
        private const val KM_PER_DEGREE = 111.0
        private const val MIN_COS = 0.01
    }
}

/** LIKE のワイルドカード（`%`・`_`）と `\` をエスケープする．SQL 側は `ESCAPE '\'`． */
internal fun escapeLike(q: String): String =
    q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

data class NearbyCastle(val castle: CastleEntity, val distanceMeters: Double)

object DistanceCalculator {
    /** 2 点間の直線（大圏）距離．メートル． */
    fun meters(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.asin(minOf(1.0, Math.sqrt(h)))
    }
}

/** 方角の計算．方位は真北から時計回りの度（0 以上 360 未満）． */
object Direction {
    private val COMPASS = listOf("北", "北東", "東", "南東", "南", "南西", "西", "北西")
    private val RELATIVE = listOf("前方", "右前方", "右", "右後方", "後方", "左後方", "左", "左前方")

    /** [from] から [to] への方位 */
    fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val y = Math.sin(dLng) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
        return normalize(Math.toDegrees(Math.atan2(y, x)))
    }

    /** 方位を 8 方位（北・北東…）にする */
    fun compass(bearing: Double): String = COMPASS[sector(bearing)]

    /** 進行方向 [heading] から見た [bearing] の向きを 8 方向（前方・右前方…）にする */
    fun relative(bearing: Double, heading: Double): String = RELATIVE[sector(bearing - heading)]

    private fun normalize(degrees: Double): Double = ((degrees % 360) + 360) % 360

    private fun sector(degrees: Double): Int = (Math.floor((normalize(degrees) + 22.5) / 45).toInt()) % 8
}
