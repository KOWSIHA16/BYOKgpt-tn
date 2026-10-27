package com.example.byokgpt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE sessionType = :sessionType ORDER BY updatedAt DESC")
    fun getConversationsBySessionType(sessionType: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET userFeedback = :feedback WHERE id = :messageId")
    suspend fun updateMessageFeedback(messageId: String, feedback: String?)

    @Query("UPDATE messages SET content = :newContent WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: String, newContent: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearMessagesForConversation(conversationId: String)

    @Query("SELECT COUNT(*) FROM messages")
    fun getTotalMessageCount(): Flow<Int>

    // --- Memory Queries ---
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAllMemoriesSnapshot(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()

    // --- Browser Bookmark Queries ---
    @Query("SELECT * FROM browser_bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarksFlow(): Flow<List<BrowserBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BrowserBookmarkEntity)

    @Query("DELETE FROM browser_bookmarks WHERE url = :url")
    suspend fun deleteBookmark(url: String)

    @Query("SELECT EXISTS(SELECT 1 FROM browser_bookmarks WHERE url = :url)")
    fun isBookmarkedFlow(url: String): Flow<Boolean>

    // --- Browser History Queries ---
    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC LIMIT 100")
    fun getAllHistoryFlow(): Flow<List<BrowserHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: BrowserHistoryEntity)

    @Query("DELETE FROM browser_history WHERE id = :id")
    suspend fun deleteHistoryItem(id: String)

    @Query("DELETE FROM browser_history")
    suspend fun clearAllHistory()
}
