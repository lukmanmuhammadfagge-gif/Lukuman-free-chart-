package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.ChatDatabase
import com.example.domain.model.PublicUserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var localDb: ChatDatabase

    @Before
    fun setUpLocalDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        localDb = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeLocalDb() {
        localDb.close()
    }

    @Test
    fun profileAndConversationFlow_authenticatedParticipants_succeedsAndIsolatesOtherUsers() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceEmail = "alice_$suffix@test.com"
        val bobEmail = "bob_$suffix@test.com"
        val charlieEmail = "charlie_$suffix@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceProfileRepo = ProfileRepository(firestore, auth, null)
        withTimeout(TIMEOUT_MS) { aliceProfileRepo.ensureUserProfilesInitialized().getOrThrow() }

        val bobUid = signInTestUser(bobEmail)
        val bobProfileRepo = ProfileRepository(firestore, auth, null)
        withTimeout(TIMEOUT_MS) { bobProfileRepo.ensureUserProfilesInitialized().getOrThrow() }

        // Switch back to Alice to create conversation & send message
        signInTestUser(aliceEmail)
        val chatRepo = ChatRepository(
            db = firestore,
            auth = auth,
            conversationDao = localDb.conversationDao(),
            messageDao = localDb.messageDao(),
            pendingOutboxDao = localDb.pendingOutboxDao(),
            storage = null
        )

        val convId = withTimeout(TIMEOUT_MS) {
            chatRepo.getOrCreateDirectConversation(
                PublicUserProfile(userId = aliceUid, displayName = "Alice", username = "alice_$suffix"),
                PublicUserProfile(userId = bobUid, displayName = "Bob", username = "bob_$suffix")
            ).getOrThrow()
        }

        withTimeout(TIMEOUT_MS) {
            chatRepo.sendDirectMessage(
                conversationId = convId,
                senderName = "Alice",
                otherUserId = bobUid,
                otherUserName = "Bob",
                otherUserAvatar = "",
                text = "Sannu Bob $suffix"
            ).getOrThrow()
        }

        // Bob can observe the conversation & message
        signInTestUser(bobEmail)
        val bobMessages = withTimeout(TIMEOUT_MS) {
            chatRepo.observeMessagesWithCache(convId, bobUid, 50L)
                .first { list -> list.any { it.text == "Sannu Bob $suffix" } }
        }
        assertTrue(bobMessages.any { it.text == "Sannu Bob $suffix" })

        // Charlie (non-participant) cannot read the conversation document
        signInTestUser(charlieEmail)
        var deniedException: FirebaseFirestoreException? = null
        try {
            withTimeout(TIMEOUT_MS) {
                chatRepo.observeConversationDoc(convId).first()
            }
        } catch (e: Exception) {
            deniedException = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED for non-participant Charlie", deniedException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, deniedException?.code)
    }

    @Test
    fun observeConversations_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val chatRepo = ChatRepository(
            db = firestore,
            auth = auth,
            conversationDao = localDb.conversationDao(),
            messageDao = localDb.messageDao(),
            pendingOutboxDao = localDb.pendingOutboxDao(),
            storage = null
        )
        var deniedException: FirebaseFirestoreException? = null
        try {
            withTimeout(TIMEOUT_MS) {
                chatRepo.observeConversations("any_uid").first()
            }
        } catch (e: Exception) {
            deniedException = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED when unauthenticated", deniedException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, deniedException?.code)
    }

    @Test
    fun groupCreationAndAdminPermissions_enforceSecurityRules() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceEmail = "galice_$suffix@test.com"
        val bobEmail = "gbob_$suffix@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val bobUid = signInTestUser(bobEmail)

        signInTestUser(aliceEmail)
        val groupRepo = GroupRepository(firestore, auth, null)
        val groupId = withTimeout(TIMEOUT_MS) {
            groupRepo.createGroup(
                name = "Hausa Tech $suffix",
                description = "Developers",
                creatorProfile = PublicUserProfile(userId = aliceUid, displayName = "Alice"),
                initialMembers = listOf(PublicUserProfile(userId = bobUid, displayName = "Bob")),
                onlyAdminsCanPost = true,
                onlyAdminsCanAddMembers = true
            ).getOrThrow()
        }

        val group = withTimeout(TIMEOUT_MS) {
            groupRepo.observeGroup(groupId).first { it != null }!!
        }

        // Bob is member, not admin, and onlyAdminsCanPost = true -> message send MUST fail
        signInTestUser(bobEmail)
        val bobSendRes = withTimeout(TIMEOUT_MS) {
            groupRepo.sendGroupMessage(group, "Bob", "Trying to post")
        }
        assertTrue(bobSendRes.isFailure)
        val fsErr = bobSendRes.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    private companion object {
        const val TIMEOUT_MS = 6000L
    }
}
