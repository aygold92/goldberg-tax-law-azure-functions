package com.goldberg.law.database

import java.security.MessageDigest

object H2Functions {
    @JvmStatic
    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}