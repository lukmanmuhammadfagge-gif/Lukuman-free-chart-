package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM cached_conversations ORDER BY lastMessageAtMs DESC")
    fun observeConversations(): Flow<List<CachedConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedConversationEntity>)

    @Query("DELETE FROM cached_conversations")
    suspend fun clearAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM cached_messages WHERE conversationId = :conversationId ORDER BY timestampMs ASC")
    fun observeMessages(conversationId: String): Flow<List<CachedMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedMessageEntity>)

    @Query("DELETE FROM cached_messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)

    @Query("DELETE FROM cached_messages")
    suspend fun clearAll()
}

@Dao
interface PendingOutboxDao {
    @Query("SELECT * FROM pending_outbox WHERE conversationId = :conversationId ORDER BY createdAtMs ASC")
    fun observePendingForConversation(conversationId: String): Flow<List<PendingOutboxEntity>>

    @Query("SELECT * FROM pending_outbox ORDER BY createdAtMs ASC")
    suspend fun getAllPending(): List<PendingOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PendingOutboxEntity)

    @Query("UPDATE pending_outbox SET failed = :failed WHERE localId = :localId")
    suspend fun setFailed(localId: String, failed: Boolean)

    @Query("DELETE FROM pending_outbox WHERE localId = :localId")
    suspend fun deleteById(localId: String)

    @Query("DELETE FROM pending_outbox")
    suspend fun clearAll()
}

@Database(
    entities = [
        CachedConversationEntity::class,
        CachedMessageEntity::class,
        PendingOutboxEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingOutboxDao(): PendingOutboxDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "lukuman_free_chat_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
