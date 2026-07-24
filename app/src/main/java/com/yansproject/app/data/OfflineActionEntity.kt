package com.yansproject.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_actions")
data class OfflineActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stringPayload: String,
    val targetCollection: String,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val additionalMeta: String = ""
)
