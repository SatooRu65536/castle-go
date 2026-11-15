package dev.satooru.castles.go.data.remote

import dev.satooru.castles.go.BuildConfig

/** 写真の URL．`primaryPhotoId` から端末で組み立てる．`size` は 400・1024・2048． */
object PhotoUrl {
    fun of(photoId: String, size: Int): String =
        "${BuildConfig.API_BASE_URL.trimEnd('/')}/photos/$photoId/images/$size"
}
