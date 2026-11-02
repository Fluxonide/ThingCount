package com.ranit.thingcount.data.update

data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val apkFileName: String = "ThingCount-update.apk"
)
