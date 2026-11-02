package com.ranit.thingcount.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.ranit.thingcount.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {

    suspend fun checkLatestRelease(
        owner: String,
        repo: String
    ): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = BuildConfig.VERSION_NAME

        if (owner.isBlank() || repo.isBlank()) {
            return@withContext UpdateInfo(
                hasUpdate = false,
                currentVersion = currentVersion,
                latestVersion = currentVersion,
                releaseNotes = "GitHub repository owner and name not configured."
            )
        }

        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JsonParser.parseString(response).asJsonObject
                val tagName = json.get("tag_name")?.asString ?: ""
                val releaseNotes = json.get("body")?.asString ?: ""

                // Locate APK asset
                var downloadUrl = ""
                var apkFileName = "ThingCount-$tagName.apk"
                val assets = json.getAsJsonArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.size()) {
                        val asset = assets[i].asJsonObject
                        val name = asset.get("name")?.asString ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.get("browser_download_url")?.asString ?: ""
                            apkFileName = name
                            break
                        }
                    }
                }

                val cleanLatest = tagName.removePrefix("v").trim()
                val cleanCurrent = currentVersion.removePrefix("v").trim()
                val isNewer = isVersionNewer(cleanLatest, cleanCurrent)

                UpdateInfo(
                    hasUpdate = (isNewer || cleanLatest != cleanCurrent) && downloadUrl.isNotBlank(),
                    currentVersion = currentVersion,
                    latestVersion = tagName,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    apkFileName = apkFileName
                )
            } else {
                UpdateInfo(
                    hasUpdate = false,
                    currentVersion = currentVersion,
                    latestVersion = currentVersion,
                    releaseNotes = "HTTP ${connection.responseCode}: ${connection.responseMessage}"
                )
            }
        } catch (e: Exception) {
            UpdateInfo(
                hasUpdate = false,
                currentVersion = currentVersion,
                latestVersion = currentVersion,
                releaseNotes = "Error checking update: ${e.localizedMessage}"
            )
        }
    }

    private fun isVersionNewer(remote: String, local: String): Boolean {
        if (remote.isBlank() || remote == local) return false
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return remote != local
    }

    fun startDownloadAndInstall(
        context: Context,
        downloadUrl: String,
        apkFileName: String
    ) {
        Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Follow redirects (GitHub Releases redirect to AWS S3)
                var currentUrl = downloadUrl
                var connection: HttpURLConnection
                var redirects = 0

                while (true) {
                    val url = URL(currentUrl)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15000
                        readTimeout = 30000
                    }
                    val status = connection.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == 307 || status == 308
                    ) {
                        currentUrl = connection.getHeaderField("Location")
                        redirects++
                        if (redirects > 5) break
                        continue
                    }
                    break
                }

                val destinationFile = File(context.cacheDir, apkFileName.ifBlank { "update.apk" })
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }

                connection.inputStream.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download complete. Opening installer...", Toast.LENGTH_SHORT).show()
                    installApk(context, destinationFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Toast.makeText(context, "Downloaded APK is invalid or empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "Please allow ThingCount to install updates", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}
