package com.github.mytv.myearthquakealert.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** SHA-256 helpers (pure JVM, unit tested). */
object Sha256 {
    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(Locale.ROOT, it) }

    /**
     * Compares [actual] with the manifest's hash and, when the Release publishes one, its
     * `.sha256` asset. Returns the failure, or null when everything matches.
     */
    fun check(actual: String, manifest: String, release: String?): UpdateFailure? = when {
        manifest.isBlank() -> UpdateFailure.MANIFEST
        !actual.equals(manifest.trim(), ignoreCase = true) -> UpdateFailure.SHA256
        release != null && !actual.equals(release.trim(), ignoreCase = true) -> UpdateFailure.SHA256
        else -> null
    }
}

/**
 * Signing certificates of a package as SHA-256 digests: the current signer(s) and, for a
 * single signer that rotated its key (APK Signature Scheme v3), the lineage oldest first.
 */
data class SignerSet(val current: Set<String>, val lineage: List<String> = emptyList()) {
    val isEmpty: Boolean get() = current.isEmpty()
}

object SignatureMatch {
    /**
     * The update must be signed like the installed app: the same signer set, or — for a
     * rotated single signer — a lineage that contains the installed signing certificate.
     */
    fun matches(installed: SignerSet, archive: SignerSet): Boolean {
        if (installed.isEmpty || archive.isEmpty) return false
        if (installed.current == archive.current) return true
        return installed.current.size == 1 && archive.current.size == 1 && installed.current.single() in archive.lineage
    }
}

/**
 * 校验 (design §8): SHA-256 against `update.json` and the Release's `.sha256`, then the
 * package itself — this app's package name, the announced versionCode, newer than the
 * installed build, and signed with the installed app's certificate. Any mismatch throws
 * [UpdateException]; the caller deletes the file.
 */
class ApkVerifier(private val context: Context) {

    fun verify(apk: File, info: UpdateInfo, releaseSha256: String?) {
        if (!apk.isFile) throw UpdateException(UpdateFailure.FILE_MISSING)
        if (info.size > 0 && apk.length() != info.size) throw UpdateException(UpdateFailure.INCOMPLETE)
        Sha256.check(Sha256.of(apk), info.sha256, releaseSha256)?.let { throw UpdateException(it) }
        verifyPackage(apk, info)
    }

    private fun verifyPackage(apk: File, info: UpdateInfo) {
        val installedCode = BuildConfig.VERSION_CODE.toLong()
        if (info.versionCode <= installedCode) throw UpdateException(UpdateFailure.NOT_NEWER)
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: throw UpdateException(UpdateFailure.UNREADABLE)
        if (archive.packageName != context.packageName) throw UpdateException(UpdateFailure.PACKAGE)
        if (versionCodeOf(archive) != info.versionCode) throw UpdateException(UpdateFailure.VERSION)
        val installed = installedInfo(pm) ?: throw UpdateException(UpdateFailure.SIGNATURE)
        if (!SignatureMatch.matches(signers(installed), signers(archive))) throw UpdateException(UpdateFailure.SIGNATURE)
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageArchiveInfo(apk.absolutePath, flags)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageInfo(context.packageName, flags)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): SignerSet {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return SignerSet(emptySet())
            return if (signing.hasMultipleSigners()) {
                SignerSet(digests(signing.apkContentsSigners).toSet())
            } else {
                val lineage = digests(signing.signingCertificateHistory)
                SignerSet(setOfNotNull(lineage.lastOrNull()), lineage)
            }
        }
        return SignerSet(digests(info.signatures).toSet())
    }

    private fun digests(signatures: Array<Signature>?): List<String> =
        signatures.orEmpty().map { Sha256.of(it.toByteArray()) }
}
