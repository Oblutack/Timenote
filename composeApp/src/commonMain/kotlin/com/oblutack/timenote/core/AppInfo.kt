package com.oblutack.timenote.core

/** What the app knows about itself, for the About section and for bug reports. Built by each platform. */
data class AppInfo(
    /** "1.1.0" */
    val versionName: String = "",
    /** "Android 16 (API 36) · Google Pixel 8" */
    val platformSummary: String = ""
)

const val SUPPORT_EMAIL = "timenotesupport@gmail.com"

/**
 * The public privacy policy page. While this is empty, the About section does not show the link (a link that goes
 * nowhere is worse than none). Set it to the address of the published page.
 */
const val PRIVACY_POLICY_URL = "https://tide-tanker-4ac.notion.site/Privacy-Policy-376c4478d91080efbe71ee7dd4ded2c6"

/** A "new email" link to the support address, with the app and system version already in the text. */
fun bugReportLink(info: AppInfo): String {
    val version = info.versionName.ifBlank { "(version unknown)" }
    val body = "What happened?\n\n\n\nPlease keep the lines below, they help to find the problem.\n" +
        "Timenote $version\n${info.platformSummary}"
    return "mailto:$SUPPORT_EMAIL?subject=${encodeUriComponent("Timenote bug report")}&body=${encodeUriComponent(body)}"
}

private const val HEX = "0123456789ABCDEF"

/** Percent-encodes [text] (as UTF-8) so it can be used as a value inside a link. */
fun encodeUriComponent(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '-' || char == '_' || char == '.' || char == '~') {
            append(char)
        } else {
            append('%')
            append(HEX[value shr 4])
            append(HEX[value and 0x0F])
        }
    }
}

/** The libraries Timenote is built with, as shown in Settings > About. */
val OPEN_SOURCE_LIBRARIES: List<Pair<String, String>> = listOf(
    "Kotlin, Compose Multiplatform, kotlinx.coroutines, kotlinx.serialization, kotlinx.datetime, Ktor" to "JetBrains · Apache License 2.0",
    "AndroidX: Room, DataStore, Lifecycle, Navigation, WorkManager, Activity, Core" to "Google · Apache License 2.0",
    "OkHttp" to "Square · Apache License 2.0",
    "SQLite" to "Public domain",
    "JetBrains Mono (the font for clocks and durations)" to "The JetBrains Mono Project Authors · SIL Open Font License 1.1",
    "Google Play services (sign-in to Google Drive)" to "Google · Google's terms of service"
)
