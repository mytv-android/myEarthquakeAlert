package com.github.mytv.myearthquakealert.update

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Where update packages live: `no_backup/updates` — app-private, never backed up and not
 * cleared by the system under storage pressure like the cache, so a paused download
 * survives. Packages of builds that are not newer than the installed one are removed.
 */
class UpdateFiles(val dir: File) {

    constructor(context: Context) : this(File(context.noBackupFilesDir, "updates"))

    fun apk(versionCode: Long): File = File(dir, "myEarthquakeAlert-$versionCode.apk")

    fun part(versionCode: Long): File = File(dir, "myEarthquakeAlert-$versionCode.apk.part")

    fun meta(versionCode: Long): File = File(dir, "myEarthquakeAlert-$versionCode.apk.part.json")

    fun deleteDownload(versionCode: Long) {
        part(versionCode).delete()
        meta(versionCode).delete()
    }

    fun deleteAll(versionCode: Long) {
        deleteDownload(versionCode)
        apk(versionCode).delete()
    }

    /** Removes everything except the files of [keep] (when newer than [installedCode]). */
    fun cleanup(installedCode: Long, keep: Long?) {
        dir.listFiles()?.forEach { file ->
            val code = NAME.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
            if (code == null || code <= installedCode || code != keep) file.delete()
        }
    }

    /** Bytes of the partial download of [versionCode] on disk. */
    fun partLength(versionCode: Long): Long = part(versionCode).let { if (it.isFile) it.length() else 0L }

    private companion object {
        val NAME = Regex("""myEarthquakeAlert-(\d+)\.apk(?:\.part(?:\.json(?:\.tmp)?)?)?""")
    }
}

/** "14.2 MB" / "820 KB". */
fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024)
    bytes >= 1024L -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
