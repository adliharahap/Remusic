package com.example.remusic.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class Artist(
    val id: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val description: String = "",
    val normalizedName: String = "",
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
): Parcelable