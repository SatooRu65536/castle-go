package dev.satooru.castles.go

import android.content.Context
import dev.satooru.castles.go.data.local.AppDatabase
import dev.satooru.castles.go.data.remote.CastleWikiApi
import dev.satooru.castles.go.data.repository.CastleRepository
import dev.satooru.castles.go.data.repository.SyncRepository
import dev.satooru.castles.go.data.repository.VisitRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 依存の組み立て．スマートフォンと Android Auto は同じ Repository を通じて同じ Room を参照する． */
class AppContainer(context: Context) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val database: AppDatabase = AppDatabase.create(context)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: CastleWikiApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()
        .create(CastleWikiApi::class.java)

    /** Android Auto の接続中は同期しない． */
    val carConnected = AtomicBoolean(false)

    val syncRepository = SyncRepository(api, database, isCarConnected = { carConnected.get() })
    val castleRepository = CastleRepository(api, database, json)
    val visitRepository = VisitRepository(database)
}
