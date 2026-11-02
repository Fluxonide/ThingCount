package com.ranit.thingcount.data.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import com.ranit.thingcount.data.local.AppDatabase
import com.ranit.thingcount.data.model.Counter
import com.ranit.thingcount.data.model.CounterHistory
import com.ranit.thingcount.data.model.SyncBackupPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Collections

class GoogleDriveSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val gson = Gson()
    private val syncFileName = "thingcount_sync.json"

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("ThingCount").build()
    }

    suspend fun syncWithDrive(account: GoogleSignInAccount): SyncResult = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(account)

            // Step 1: Check if the backup file exists in Drive AppData
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$syncFileName' and trashed = false")
                .setFields("files(id, name, modifiedTime)")
                .execute()

            val remoteFile = fileList.files?.firstOrNull()

            val localCounters = database.counterDao().getAllCountersRaw()
            val localHistory = database.counterHistoryDao().getAllHistoryRaw()

            var mergedPayload: SyncBackupPayload

            if (remoteFile != null) {
                // Step 2: Download remote payload
                val inputStream = driveService.files().get(remoteFile.id).executeMediaAsInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                reader.close()

                val remotePayload = try {
                    gson.fromJson(jsonString, SyncBackupPayload::class.java)
                } catch (e: Exception) {
                    null
                }

                if (remotePayload != null) {
                    // Step 3: Merge local and remote
                    mergedPayload = mergePayloads(
                        localCounters = localCounters,
                        remoteCounters = remotePayload.counters,
                        localHistory = localHistory,
                        remoteHistory = remotePayload.history
                    )
                } else {
                    mergedPayload = SyncBackupPayload(
                        counters = localCounters,
                        history = localHistory
                    )
                }

                // Update local Room database
                database.counterDao().upsertAll(mergedPayload.counters)
                database.counterHistoryDao().upsertAll(mergedPayload.history)

                // Step 4: Overwrite the remote file on Drive
                val updatedJsonBytes = gson.toJson(mergedPayload).toByteArray(Charsets.UTF_8)
                val mediaContent = ByteArrayContent("application/json", updatedJsonBytes)

                driveService.files().update(remoteFile.id, null, mediaContent).execute()
            } else {
                // Remote file does not exist yet; upload current local state
                mergedPayload = SyncBackupPayload(
                    counters = localCounters,
                    history = localHistory
                )

                val newFile = com.google.api.services.drive.model.File().apply {
                    name = syncFileName
                    parents = listOf("appDataFolder")
                }

                val jsonBytes = gson.toJson(mergedPayload).toByteArray(Charsets.UTF_8)
                val mediaContent = ByteArrayContent("application/json", jsonBytes)

                driveService.files().create(newFile, mediaContent).execute()
            }

            SyncResult.Success(
                countersSynced = mergedPayload.counters.size,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            SyncResult.Error(
                message = e.localizedMessage ?: "Unknown Google Drive sync error",
                throwable = e
            )
        }
    }

    private fun mergePayloads(
        localCounters: List<Counter>,
        remoteCounters: List<Counter>,
        localHistory: List<CounterHistory>,
        remoteHistory: List<CounterHistory>
    ): SyncBackupPayload {
        // Merge counters: Last-Write-Wins based on updatedAt
        val counterMap = mutableMapOf<String, Counter>()

        localCounters.forEach { counterMap[it.id] = it }

        remoteCounters.forEach { remoteCounter ->
            val localCounter = counterMap[remoteCounter.id]
            if (localCounter == null) {
                counterMap[remoteCounter.id] = remoteCounter
            } else {
                // Keep the version with the highest updatedAt
                if (remoteCounter.updatedAt > localCounter.updatedAt) {
                    counterMap[remoteCounter.id] = remoteCounter
                }
            }
        }

        // Merge history: Union of history records by UUID
        val historyMap = mutableMapOf<String, CounterHistory>()
        localHistory.forEach { historyMap[it.id] = it }
        remoteHistory.forEach { historyMap[it.id] = it }

        return SyncBackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            counters = counterMap.values.toList(),
            history = historyMap.values.toList()
        )
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val payload = SyncBackupPayload(
            counters = database.counterDao().getAllCountersRaw(),
            history = database.counterHistoryDao().getAllHistoryRaw()
        )
        gson.toJson(payload)
    }

    suspend fun importJson(json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = gson.fromJson(json, SyncBackupPayload::class.java)
            if (payload != null && payload.counters.isNotEmpty()) {
                database.counterDao().upsertAll(payload.counters)
                database.counterHistoryDao().upsertAll(payload.history)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
