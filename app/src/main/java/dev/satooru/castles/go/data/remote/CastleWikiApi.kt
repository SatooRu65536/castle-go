package dev.satooru.castles.go.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/** castle-wiki の読み取り専用 API．仕様は docs/api.md．ログインは不要． */
interface CastleWikiApi {
    @GET("sync/castles")
    suspend fun syncCastles(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int = 1000,
    ): Response<SyncCastlesResponse>

    @GET("sync/castle-tags")
    suspend fun syncCastleTags(
        @Header("If-None-Match") etag: String?,
    ): Response<SyncTagsResponse>

    @GET("castles/{id}")
    suspend fun castleDetail(
        @Path("id") id: String,
        @Header("If-None-Match") etag: String?,
    ): Response<CastleDetailDto>
}
