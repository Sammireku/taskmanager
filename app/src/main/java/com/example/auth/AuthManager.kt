package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AuthManager(context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (_: Exception) { null }
    private val prefs: SharedPreferences = context.getSharedPreferences("cobby_auth_prefs", Context.MODE_PRIVATE)

    private fun getOrCreateDeviceUid(): String {
        val existing = prefs.getString("isolated_device_uid", null)
        if (!existing.isNull_or_empty()) {
            return existing!!
        }
        val newUid = "anon_" + UUID.randomUUID().toString()
        prefs.edit().putString("isolated_device_uid", newUid).apply()
        return newUid
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.isBlank()

    suspend fun ensureAuthenticatedUser(): String {
        val current = auth?.currentUser
        if (current != null) {
            return current.uid
        }
        return try {
            val result = auth?.signInAnonymously()?.await()
            val uid = result?.user?.uid
            if (!uid.isNull_or_empty()) {
                uid!!
            } else {
                getOrCreateDeviceUid()
            }
        } catch (e: Exception) {
            getOrCreateDeviceUid()
        }
    }

    fun getCurrentUserId(): String {
        val fbUid = auth?.currentUser?.uid
        if (!fbUid.isNull_or_empty()) {
            return fbUid!!
        }
        return getOrCreateDeviceUid()
    }

    fun isAnonymous(): Boolean {
        return auth?.currentUser?.isAnonymous ?: true
    }

    suspend fun signInWithGoogle(context: Context): Boolean {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("YOUR_WEB_CLIENT_ID")
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is GoogleIdTokenCredential) {
                val firebaseAuthCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
                auth?.signInWithCredential(firebaseAuthCredential)?.await()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun signOut() {
        auth?.signOut()
    }
}
