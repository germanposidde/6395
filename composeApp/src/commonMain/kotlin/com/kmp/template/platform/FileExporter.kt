package com.kmp.hook.platform

/**
 * Platform bridge for exporting/sharing a text document (CSV / JSON backup)
 * and importing one back. Mirrors the existing PlatformWebView expect/actual pattern.
 */
expect class FileExporter() {
    /** Save/share [content] as a file via the platform share sheet. */
    suspend fun shareText(fileName: String, mimeType: String, content: String)

    /** Let the user pick a text file and return its contents, or null if cancelled/unsupported. */
    suspend fun pickTextFile(): String?
}
