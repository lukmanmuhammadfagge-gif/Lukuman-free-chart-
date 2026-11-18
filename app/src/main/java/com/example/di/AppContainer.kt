package com.example.di

import android.content.Context
import com.example.R
import com.example.core.AppPreferences
import com.example.data.local.ChatDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactsRepository
import com.example.data.repository.GroupRepository
import com.example.data.repository.ProfileRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val databaseId: String = appContext.getString(R.string.firestore_database_id)
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(databaseId)
    val auth: FirebaseAuth = Firebase.auth
    val storage: FirebaseStorage? = try {
        FirebaseStorage.getInstance()
    } catch (_: Exception) {
        null
    }

    val preferences = AppPreferences(appContext)
    private val localDb = ChatDatabase.getInstance(appContext)

    val authRepository = AuthRepository(auth)
    val profileRepository = ProfileRepository(firestore, auth, storage)
    val contactsRepository = ContactsRepository(firestore, auth)
    val chatRepository = ChatRepository(
        db = firestore,
        auth = auth,
        conversationDao = localDb.conversationDao(),
        messageDao = localDb.messageDao(),
        pendingOutboxDao = localDb.pendingOutboxDao(),
        storage = storage
    )
    val groupRepository = GroupRepository(firestore, auth, storage)
}
