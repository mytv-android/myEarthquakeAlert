package com.github.mytv.myearthquakealert.util

fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }

    for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
        val r = remoteParts.getOrNull(i) ?: 0
        val l = localParts.getOrNull(i) ?: 0
        if (r > l) return true
        if (r < l) return false
    }
    return false
}
