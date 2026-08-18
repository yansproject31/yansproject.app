package com.yansproject.app.data

import java.security.MessageDigest

sealed class ChecksumResult {
    data class Success(val hash: String) : ChecksumResult()
    data class Failed(val reason: String) : ChecksumResult()
}

object ChecksumCalculator {
    fun calculateChecksum(input: String?): ChecksumResult {
        if (input == null) return ChecksumResult.Failed("Input payload is null")
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            val hash = bytes.joinToString("") { "%02x".format(it) }
            if (hash.isNotBlank()) ChecksumResult.Success(hash)
            else ChecksumResult.Failed("Generated hash string was empty")
        } catch (e: Exception) {
            ChecksumResult.Failed(e.message ?: "SHA-256 calculation exception")
        }
    }

    fun calculateReplayHash(idempotencyKey: String, userId: String, payload: String): ChecksumResult {
        return calculateChecksum("$idempotencyKey:$userId:$payload")
    }
}
