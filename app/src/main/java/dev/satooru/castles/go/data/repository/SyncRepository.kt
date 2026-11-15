package dev.satooru.castles.go.data.repository

import android.util.Log
import androidx.room.withTransaction
import dev.satooru.castles.go.data.local.AppDatabase
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.local.CastleTagAssignmentEntity
import dev.satooru.castles.go.data.local.CastleTagEntity
import dev.satooru.castles.go.data.local.SyncStateEntity
import dev.satooru.castles.go.data.remote.CastleDto
import dev.satooru.castles.go.data.remote.CastleWikiApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import java.io.IOException

sealed interface SyncResult {
    data class Success(val updatedCount: Int) : SyncResult
    data object Skipped : SyncResult
    data class Failure(val cause: Throwable, val retryable: Boolean) : SyncResult {
        /** 画面に出す短い説明 */
        val summary: String get() = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
    }
}

/**
 * wiki の API から差分を取得し，Room に反映する．同期のみが `castle` に書き込む．
 * 車載の接続中は同期しない（[isCarConnected]）．
 */
class SyncRepository(
    private val api: CastleWikiApi,
    private val db: AppDatabase,
    private val isCarConnected: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val castleDao = db.castleDao()
    private val tagDao = db.tagDao()
    private val detailDao = db.detailDao()
    private val stateDao = db.syncStateDao()

    fun observeState(): Flow<SyncStateEntity?> = stateDao.observe()

    /** 前回の確認から [staleAfterMillis] 以上経っているときのみ同期する． */
    suspend fun syncIfStale(staleAfterMillis: Long = STALE_AFTER_MILLIS): SyncResult {
        val last = stateDao.get()?.lastCheckedAt
        if (last != null && clock() - last < staleAfterMillis) return SyncResult.Skipped
        return sync()
    }

    suspend fun sync(): SyncResult {
        if (isCarConnected()) return SyncResult.Skipped
        return mutex.withLock {
            try {
                val updated = syncCastles()
                syncTags()
                // 全ページとタグがそろってから「確認済み」にする（途中で失敗したら次回も再開する）．
                markChecked()
                SyncResult.Success(updated)
            } catch (e: HttpStatusException) {
                Log.w(TAG, "同期に失敗: ${e.message}", e)
                SyncResult.Failure(e, retryable = e.code == 429 || e.code >= 500)
            } catch (e: IOException) {
                Log.w(TAG, "同期に失敗（通信）: $e", e)
                SyncResult.Failure(e, retryable = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 想定外の JSON（SerializationException）や Room の例外．再試行しても直らない．
                Log.w(TAG, "同期に失敗（想定外）: $e", e)
                SyncResult.Failure(e, retryable = false)
            }
        }
    }

    /** 取得済みのカーソルを捨て，全件の再取得に備える．列の追加などで使う． */
    suspend fun resetCursor() {
        val state = stateDao.get() ?: return
        stateDao.upsert(state.copy(cursor = null, tagsEtag = null, lastCheckedAt = null))
    }

    private suspend fun syncCastles(): Int {
        var total = 0
        var cursor = stateDao.get()?.cursor
        while (true) {
            val response = api.syncCastles(cursor)
            if (response.code() == 304) return total
            if (!response.isSuccessful) throw HttpStatusException(response.code())
            val body = response.body() ?: throw HttpStatusException(response.code())
            val next = if (body.castles.isEmpty()) cursor else body.nextCursor ?: cursor
            db.withTransaction {
                apply(body.castles)
                saveCursor(next, serverTime = body.serverTime)
            }
            total += body.castles.size
            if (!body.hasMore) return total
            // 続きがあるのにカーソルが進まない場合は，無限ループを避けて打ち切る．
            if (next == cursor) throw HttpStatusException(502)
            cursor = next
        }
    }

    private suspend fun apply(castles: List<CastleDto>) {
        if (castles.isEmpty()) return
        val (deleted, alive) = castles.partition { it.deletedAt != null }
        if (alive.isNotEmpty()) castleDao.upsertAll(alive.map { it.toEntity() })
        if (deleted.isNotEmpty()) {
            val ids = deleted.map { it.id }
            ids.chunked(SQLITE_VARIABLE_LIMIT).forEach {
                // タグの対応は消さない（復元時にタグの ETag が変わらなくても戻るように）．
                castleDao.deleteByIds(it)
                detailDao.deleteByIds(it)
            }
        }
    }

    private suspend fun saveCursor(cursor: String?, serverTime: Long?) {
        val state = stateDao.get() ?: SyncStateEntity(cursor = null, lastSyncedAt = null, lastCheckedAt = null, tagsEtag = null)
        stateDao.upsert(state.copy(cursor = cursor, lastSyncedAt = serverTime ?: state.lastSyncedAt))
    }

    private suspend fun markChecked() {
        val state = stateDao.get() ?: SyncStateEntity(cursor = null, lastSyncedAt = null, lastCheckedAt = null, tagsEtag = null)
        stateDao.upsert(state.copy(lastCheckedAt = clock()))
    }

    private suspend fun syncTags() {
        val state = stateDao.get()
        val response = api.syncCastleTags(state?.tagsEtag)
        if (response.code() == 304) return
        if (!response.isSuccessful) throw HttpStatusException(response.code())
        val body = response.body() ?: throw HttpStatusException(response.code())
        val etag = response.headers()["ETag"]
        db.withTransaction {
            tagDao.clearTags()
            tagDao.clearAssignments()
            tagDao.upsertTags(body.tags.map { CastleTagEntity(it.id, it.name) })
            body.assignments
                .flatMap { a -> a.tagIds.map { CastleTagAssignmentEntity(a.castleId, it) } }
                .chunked(SQLITE_VARIABLE_LIMIT)
                .forEach { tagDao.upsertAssignments(it) }
            val latest = stateDao.get() ?: SyncStateEntity(cursor = null, lastSyncedAt = null, lastCheckedAt = null, tagsEtag = null)
            stateDao.upsert(latest.copy(tagsEtag = etag))
        }
    }

    companion object {
        private const val TAG = "SyncRepository"
        const val STALE_AFTER_MILLIS = 6L * 60 * 60 * 1000
        private const val SQLITE_VARIABLE_LIMIT = 500
    }
}

class HttpStatusException(val code: Int) : RuntimeException("HTTP $code")

internal fun CastleDto.toEntity() = CastleEntity(
    id = id,
    currentRevisionNumber = revision,
    name = name,
    nameKana = nameKana,
    searchText = searchText,
    lat = lat,
    lng = lng,
    scale = scale,
    castleKind = castleKind,
    locationType = locationType,
    prefecture = prefecture,
    tenshuStatus = tenshuStatus,
    meijoSelection = meijoSelection,
    meijoNumber = meijoNumber,
    primaryPhotoId = primaryPhotoId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
