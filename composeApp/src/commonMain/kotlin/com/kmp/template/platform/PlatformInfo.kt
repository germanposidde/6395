package com.kmp.hook.platform

/** Per kmp-privacy-terms-webview rule: Android shows Privacy only; iOS shows Privacy + Terms. */
expect val platformShowsTerms: Boolean

expect val platformName: String

/** Platform-specific legal document URLs (Android and iOS have different published policies). */
expect val platformPrivacyUrl: String

/** Only meaningful when [platformShowsTerms] is true (iOS). */
expect val platformTermsUrl: String
