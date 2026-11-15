package dev.satooru.castles.go.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** `GET /sync/castles` の 1 件．仕様は docs/api.md に従う．未知のキーは無視する． */
@Serializable
data class CastleDto(
    val id: String,
    val revision: Int,
    val name: String,
    val nameKana: String = "",
    val searchText: String = "",
    val lat: Double,
    val lng: Double,
    val scale: Int,
    val castleKind: String? = null,
    val locationType: String? = null,
    val prefecture: String? = null,
    val tenshuStatus: String? = null,
    val meijoSelection: String? = null,
    val meijoNumber: Int? = null,
    val primaryPhotoId: String? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class SyncCastlesResponse(
    val castles: List<CastleDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val serverTime: Long = 0,
)

@Serializable
data class TagDto(val id: String, val name: String)

@Serializable
data class TagAssignmentDto(val castleId: String, val tagIds: List<String> = emptyList())

@Serializable
data class SyncTagsResponse(
    val tags: List<TagDto> = emptyList(),
    val assignments: List<TagAssignmentDto> = emptyList(),
)

@Serializable
data class PhotoDto(
    val id: String,
    val caption: String? = null,
    val photographer: String? = null,
    val license: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class CastleDetailDto(
    val revision: Int,
    val body: String = "",
    /** 属性．各値は `{ "state": "known", "value": ... }` または `{ "state": "unknown" }` */
    val attributes: Map<String, JsonElement> = emptyMap(),
    val photos: List<PhotoDto> = emptyList(),
)
