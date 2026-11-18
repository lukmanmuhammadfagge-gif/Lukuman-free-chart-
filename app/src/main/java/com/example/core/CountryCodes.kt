package com.example.core

data class CountryDialCode(
    val isoCode: String,
    val nameEn: String,
    val nameHa: String,
    val dialCode: String,
    val flagEmoji: String
)

object CountryCodes {
    val defaultNigeria = CountryDialCode("NG", "Nigeria", "Najeriya", "+234", "🇳🇬")

    val all: List<CountryDialCode> = listOf(
        defaultNigeria,
        CountryDialCode("GH", "Ghana", "Gana", "+233", "🇬🇭"),
        CountryDialCode("NE", "Niger", "Nijar", "+227", "🇳🇪"),
        CountryDialCode("CM", "Cameroon", "Kamaru", "+237", "🇨🇲"),
        CountryDialCode("TD", "Chad", "Cadi", "+235", "🇹🇩"),
        CountryDialCode("BJ", "Benin", "Benin", "+229", "🇧🇯"),
        CountryDialCode("TG", "Togo", "Togo", "+228", "🇹🇬"),
        CountryDialCode("SN", "Senegal", "Senegal", "+221", "🇸🇳"),
        CountryDialCode("KE", "Kenya", "Kenya", "+254", "🇰🇪"),
        CountryDialCode("ZA", "South Africa", "Afirka ta Kudu", "+27", "🇿🇦"),
        CountryDialCode("EG", "Egypt", "Masar", "+20", "🇪🇬"),
        CountryDialCode("GB", "United Kingdom", "Birtaniya", "+44", "🇬🇧"),
        CountryDialCode("US", "United States", "Amurka", "+1", "🇺🇸"),
        CountryDialCode("CA", "Canada", "Kanada", "+1", "🇨🇦"),
        CountryDialCode("SA", "Saudi Arabia", "Saudiyya", "+966", "🇸🇦"),
        CountryDialCode("AE", "United Arab Emirates", "Hadaddiyar Daular Larabawa", "+971", "🇦🇪"),
        CountryDialCode("DE", "Germany", "Jamus", "+49", "🇩🇪"),
        CountryDialCode("FR", "France", "Faransa", "+33", "🇫🇷"),
        CountryDialCode("IN", "India", "Indiya", "+91", "🇮🇳")
    )

    fun findByDialCode(code: String): CountryDialCode {
        return all.firstOrNull { it.dialCode == code } ?: defaultNigeria
    }

    fun formatE164(countryCode: String, localNumber: String): String {
        val cleanedCode = if (countryCode.startsWith("+")) countryCode else "+$countryCode"
        var digits = localNumber.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        // Strip leading 0 for national trunk prefix (e.g., 0803... -> +234803...)
        if (digits.startsWith("0") && digits.length > 1) {
            digits = digits.drop(1)
        }
        return "$cleanedCode$digits"
    }
}
