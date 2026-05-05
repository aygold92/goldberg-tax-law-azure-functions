package com.goldberg.law.database

import java.security.MessageDigest

object H2Functions {
    @JvmStatic
    fun sha2(input: String, bitLength: Int): String {
        val algorithm = "SHA-$bitLength"
        val bytes = MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}