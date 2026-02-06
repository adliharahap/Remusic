package com.example.remusic.data.network

import com.google.gson.annotations.SerializedName

data class TelegramFileResponse(
    @SerializedName("ok")
    val ok: Boolean,
    @SerializedName("result")
    val result: TelegramResult?
)

data class TelegramResult(
    @SerializedName("file_id")
    val fileId: String,
    @SerializedName("file_size")
    val fileSize: Long,
    @SerializedName("file_path")
    val filePath: String // Ini yang kita cari!
)
