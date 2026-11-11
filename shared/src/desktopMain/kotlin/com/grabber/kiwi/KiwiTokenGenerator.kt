package com.grabber.kiwi

object KiwiTokenGenerator {
    fun generateToken(): Long {
        val nowMs = System.currentTimeMillis()
        // Mascheramento bitwise basato sul timestamp a 64-bit del client KiwiSDR
        return (nowMs and 0x1FFFFFFFFFFFFF) or 0x4000000000000000L
    }
}