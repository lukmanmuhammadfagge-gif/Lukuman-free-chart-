package com.example.domain.validation

object Validators {
    private val usernameRegex = Regex("^[a-z0-9_]{3,30}$")
    private val e164Regex = Regex("^\\+[1-9][0-9]{6,14}$")

    fun normalizeUsername(raw: String): String {
        return raw.trim().lowercase().removePrefix("@")
    }

    fun isValidUsername(raw: String): Boolean {
        return usernameRegex.matches(normalizeUsername(raw))
    }

    fun sanitizeUsernameFromEmailOrName(input: String, fallbackUid: String): String {
        val base = input.substringBefore("@")
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        val candidate = if (base.length >= 3) base.take(24) else "user_${fallbackUid.take(6).lowercase()}"
        return candidate.padEnd(3, '0')
    }

    fun isValidDisplayName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed.length <= 80
    }

    fun isValidGroupName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed.length <= 80
    }

    fun isValidPhoneE164(phone: String): Boolean {
        return phone.isEmpty() || e164Regex.matches(phone)
    }

    fun deterministicConversationId(uidA: String, uidB: String): String {
        return if (uidA <= uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"
    }

    fun deterministicFriendRequestId(uidA: String, uidB: String): String {
        return if (uidA <= uidB) "req_${uidA}_${uidB}" else "req_${uidB}_${uidA}"
    }
}
