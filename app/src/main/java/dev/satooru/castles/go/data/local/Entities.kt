package dev.satooru.castles.go.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一覧ミラー．wiki の `castles` の写し列．同期のみが書き込む． */
@Entity(
    tableName = "castle",
    indices = [
        Index("scale"),
        Index("lat"),
        Index("lng"),
        Index("prefecture"),
    ],
)
data class CastleEntity(
    @PrimaryKey val id: String,
    val currentRevisionNumber: Int,
    val name: String,
    val nameKana: String,
    val searchText: String,
    val lat: Double,
    val lng: Double,
    val scale: Int,
    val castleKind: String?,
    val locationType: String?,
    /** 国（`JP`・`KR`）．既存の行は日本 */
    @ColumnInfo(defaultValue = "'JP'") val country: String = "JP",
    /** 韓国の市・道のコード．日本の城では NULL */
    val region: String? = null,
    val prefecture: String?,
    val tenshuStatus: String?,
    val meijoSelection: String?,
    val meijoNumber: Int?,
    val primaryPhotoId: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "castle_tag")
data class CastleTagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "castle_tag_assignment",
    primaryKeys = ["castleId", "tagId"],
    indices = [Index("tagId")],
)
data class CastleTagAssignmentEntity(
    val castleId: String,
    val tagId: String,
)

/** 詳細のキャッシュ．`revisionNumber` と `castle.currentRevisionNumber` を比べて再取得を判断する． */
@Entity(tableName = "castle_detail")
data class CastleDetailEntity(
    @PrimaryKey val castleId: String,
    val revisionNumber: Int,
    val body: String,
    /** 属性の JSON 文字列 */
    val attributes: String,
    /** 写真の一覧の JSON 文字列 */
    val photos: String,
)

/** 訪問記録．`castle` と別テーブルにし，同期で上書きされないようにする． */
@Entity(tableName = "visit")
data class VisitEntity(
    @PrimaryKey val castleId: String,
    val visitedAt: Long,
    val memo: String,
)

/** 同期の状態．行は 1 つだけ（`id = 1`）． */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val cursor: String?,
    /** 最後に同期が成功した時刻．サーバーの `serverTime` */
    val lastSyncedAt: Long?,
    /** 最後にサーバーへ確認しに行った端末の時刻 */
    val lastCheckedAt: Long?,
    val tagsEtag: String?,
)

/** 車載で通知した城．重複通知の防止に使う． */
@Entity(tableName = "notified_castle")
data class NotifiedCastleEntity(
    @PrimaryKey val castleId: String,
    val notifiedAt: Long,
)
