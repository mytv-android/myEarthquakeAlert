package com.github.mytv.myearthquakealert.update

import androidx.annotation.StringRes
import com.github.mytv.myearthquakealert.R
import java.io.IOException

/**
 * Why a step of the update pipeline failed. Each reason has its own message; [hasArgument]
 * reasons format the failure detail into it (API level, size, HTTP code).
 */
enum class UpdateFailure(@param:StringRes val message: Int, val hasArgument: Boolean = false, val retryable: Boolean = false) {
    NETWORK(R.string.update_error_network, retryable = true),
    RATE_LIMITED(R.string.update_error_rate_limit),
    SERVER(R.string.upd_err_server, retryable = true),
    MANIFEST(R.string.upd_err_manifest),
    MIN_SDK(R.string.upd_err_min_sdk, hasArgument = true),
    STORAGE(R.string.upd_err_storage, hasArgument = true),
    HTTP(R.string.upd_err_http, hasArgument = true),
    INCOMPLETE(R.string.upd_err_incomplete, retryable = true),
    SHA256(R.string.upd_err_sha256),
    SIGNATURE(R.string.upd_err_signature),
    PACKAGE(R.string.upd_err_package),
    VERSION(R.string.upd_err_version),
    UNREADABLE(R.string.upd_err_unreadable),
    NOT_NEWER(R.string.upd_err_not_newer),
    FILE_MISSING(R.string.upd_err_file_missing),
    INSTALL_BLOCKED(R.string.upd_err_install_blocked),
    INSTALL_ABORTED(R.string.upd_err_install_aborted),
    INSTALL_INVALID(R.string.upd_err_install_invalid),
    INSTALL_CONFLICT(R.string.upd_err_install_conflict),
    INSTALL_STORAGE(R.string.upd_err_install_storage),
    INSTALL_INCOMPATIBLE(R.string.upd_err_install_incompatible),
    INSTALL_TIMEOUT(R.string.upd_err_install_timeout),
    INSTALL_FAILED(R.string.upd_err_install_failed);

    companion object {
        fun fromName(name: String?): UpdateFailure? = entries.firstOrNull { it.name == name }
    }
}

/** A pipeline failure with its reason; [detail] is the format argument or a system message. */
class UpdateException(
    val failure: UpdateFailure,
    val detail: String? = null,
    cause: Throwable? = null,
) : IOException("${failure.name}${detail?.let { ": $it" } ?: ""}", cause)
