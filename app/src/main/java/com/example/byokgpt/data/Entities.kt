package com.example.byokgpt.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val provider: String,
    val modelId: String,
    val sessionType: String = "CHAT" // "CHAT" or "WEB_SEARCH"
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS",
    val errorMessage: String? = null,
    val provider: String,
    val modelId: String,
    val attachmentUri: String? = null,
    val attachmentName: String? = null,
    val attachmentMime: String? = null,
    val attachmentBase64: String? = null,
    val generatedImageUrl: String? = null,
    val userFeedback: String? = null
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val key: String,
    val content: String,
    val source: String = "AUTO", // "AUTO" or "MANUAL"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "browser_bookmarks")
data class BrowserBookmarkEntity(
    @PrimaryKey val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "browser_history")
data class BrowserHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)
