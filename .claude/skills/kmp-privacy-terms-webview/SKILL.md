---
name: kmp-privacy-terms-webview
description: Add Privacy Policy and Terms of Use rows to a Kotlin Multiplatform (KMP / Compose Multiplatform) Profile or Settings screen, each opening in a shared WebView screen backed by an expect/actual PlatformWebView (Android WebView, iOS WKWebView). Use this skill whenever the user asks to add legal links, privacy policy, terms of use, terms and conditions, EULA, "legal section", a WebView screen, in-app webview, WKWebView, AndroidView+WebView, or open a URL inside a KMP/Compose Multiplatform app — even when they don't mention the exact APIs. Also applies when fixing or refactoring an existing legal links flow in a KMP app. **Android shows only Privacy Policy. iOS shows both Privacy Policy and Terms of Use.**
---

# KMP — Privacy Policy & Terms of Use (WebView)

This skill defines the exact contract for adding **Privacy Policy** and **Terms of Use** entries to the Profile/Settings screen of a Kotlin Multiplatform Mobile (KMP / Compose Multiplatform) app. The legal pages open inside a shared in-app `WebViewScreen` backed by an `expect`/`actual` `PlatformWebView`.

You are an expert KMP developer. Stay in scope.

## Hard platform rule

- **Android** shows **only Privacy Policy** in the Legal section.
- **iOS** shows **both Privacy Policy and Terms of Use** in the Legal section.

Use `expect/actual` (or a small platform-conditional helper in common code) to render the right rows on each platform. Do not show Terms of Use on Android. Do not omit Terms of Use on iOS.

## Out of scope — do NOT change

- App architecture, DI, build configuration beyond the single Manifest line below.
- The navigation library or navigation pattern (use the existing one).
- Any unrelated screen, ViewModel, theme, or color token definition.
- The existing dialog / card / row components — reuse them; do not add new design-system primitives.

If a step below conflicts with the existing project (different navigation library, different theme tokens, different package layout), adapt to the project's conventions, but keep the **structure and contract** identical.

---

## Pre-flight checks (do these first)

Before writing any code, locate these in the project so the new code blends in:

1. **Source set roots** — `commonMain`, `androidMain`, `iosMain` under the shared module. Confirm package layout (e.g. `com.example.app.presentation.profile`).
2. **Theme/color tokens** — find the existing object (often `AppColors`, `Theme.colors`, `MaterialTheme.colorScheme`). Use the project's actual tokens. The code snippets below use `AppColors.Background / Surface / TextPrimary / TextSecondary / Accent` as placeholders — replace with whatever the project uses. **Do not invent a new color object.**
3. **Profile / Settings screen** — find `ProfileScreen.kt` or equivalent (`SettingsScreen`, `AccountScreen`). That's where the Legal section goes.
4. **Navigation** — find the existing nav graph (`AppNavigation.kt`, `NavGraph.kt`, etc.). Confirm it uses `androidx.navigation:navigation-compose` or a KMP-friendly equivalent (Voyager, Decompose, PreCompose). Adapt the route registration to match.
5. **Existing row/card component** — if the project already has a `SettingsRow`, `CelestialCard`, `ProfileItem`, etc., reuse it for the Legal rows instead of creating a new `LegalRow`. Only create `LegalRow` if nothing suitable exists.
6. **Internet permission** — check `androidApp/src/main/AndroidManifest.xml` (or `composeApp/src/androidMain/AndroidManifest.xml`) for `android.permission.INTERNET`. Add only if missing.

---

## URLs

```
Privacy Policy : there is gonna be link
Terms of Use   : there is gonna be link
```

If the user provided different URLs in their prompt, use those instead. Never hardcode placeholder `https://` empties — if the URL is unknown, ask the user before generating.

---

## Step 1 — `WebViewScreen` (commonMain)

Create `presentation/webview/WebViewScreen.kt` in `commonMain`:

```kotlin
@Composable
fun WebViewScreen(
    url: String,
    title: String,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background) // existing background token
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Surface) // existing surface token
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            TextButton(onClick = onNavigateBack) {
                Text(
                    text = "← Back",
                    color = AppColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColors.TextSecondary.copy(alpha = 0.15f))
        )
        PlatformWebView(url = url, modifier = Modifier.fillMaxSize())
    }
}
```

Replace `AppColors.*` with the project's actual tokens. Keep structure (top bar with centered title and back text button, 1dp divider, full-bleed WebView below) unchanged.

---

## Step 2 — `PlatformWebView` (expect / actual)

### `commonMain` — `ui/components/PlatformWebView.kt`

```kotlin
@Composable
expect fun PlatformWebView(url: String, modifier: Modifier = Modifier)
```

### `androidMain` — `ui/components/PlatformWebView.android.kt`

```kotlin
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
        modifier = modifier
    )
}
```

Imports: `android.webkit.WebView`, `android.webkit.WebViewClient`, `androidx.compose.ui.viewinterop.AndroidView`.

### `iosMain` — `ui/components/PlatformWebView.ios.kt`

```kotlin
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    UIKitView(
        factory = {
            WKWebView().apply {
                val nsUrl = NSURL.URLWithString(url) ?: return@apply
                loadRequest(NSURLRequest.requestWithURL(nsUrl))
            }
        },
        update = { webView ->
            NSURL.URLWithString(url)?.let { nsUrl ->
                webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
            }
        },
        modifier = modifier
    )
}
```

Imports: `androidx.compose.ui.interop.UIKitView`, `platform.WebKit.WKWebView`, `platform.Foundation.NSURL`, `platform.Foundation.NSURLRequest`, `kotlinx.cinterop.ExperimentalForeignApi`.

> If the project's Compose Multiplatform version exposes `UIKitView` under a different package (older versions used `androidx.compose.ui.interop`, newer use `androidx.compose.ui.viewinterop`), adapt the import — do not change the structure.

---

## Step 3 — Navigation

Add a `WebView` destination to the existing nav graph (`AppNavigation.kt`):

```kotlin
sealed class Screen(val route: String) {
    // ... existing screens stay untouched ...
    object WebView : Screen("webview?url={url}&title={title}") {
        fun createRoute(url: String, title: String) =
            "webview?url=${Uri.encode(url)}&title=${Uri.encode(title)}"
    }
}
```

Add the composable destination inside the existing `NavHost`:

```kotlin
composable(
    route = Screen.WebView.route,
    arguments = listOf(
        navArgument("url") { type = NavType.StringType },
        navArgument("title") { type = NavType.StringType }
    )
) { backStackEntry ->
    val url = backStackEntry.arguments?.getString("url").orEmpty()
    val title = backStackEntry.arguments?.getString("title").orEmpty()
    WebViewScreen(
        url = url,
        title = title,
        onNavigateBack = { navController.popBackStack() }
    )
}
```

If the project uses Voyager, Decompose, or PreCompose instead of `navigation-compose`, register the destination using *that* library's pattern. Keep the same route name and `(url, title)` argument contract.

`Uri.encode` lives in `android.net.Uri` on Android. For KMP common code, use `io.ktor.http.encodeURLParameter` or a small `expect/actual` `urlEncode` helper if `createRoute` lives in `commonMain`. Do not paste raw URLs into the route string — they contain `?`, `&`, and `:` which break route parsing.

---

## Step 4 — Profile screen Legal section

Add to the existing `ProfileScreen.kt` (or `SettingsScreen.kt`). Place the Legal section at the bottom of the screen, **below** existing profile content.

```kotlin
// Legal section
Text(
    text = "Legal",
    color = AppColors.TextSecondary,
    fontSize = 12.sp,
    letterSpacing = 1.sp,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
)

LegalRow(
    label = "Privacy Policy",
    onClick = {
        navController.navigate(
            Screen.WebView.createRoute(
                url = "https://",
                title = "Privacy Policy"
            )
        )
    }
)

if (isIos()) {
    LegalRow(
        label = "Terms of Use",
        onClick = {
            navController.navigate(
                Screen.WebView.createRoute(
                    url = "https://",
                    title = "Terms of Use"
                )
            )
        }
    )
}
```

`isIos()` is an `expect/actual` returning `Boolean` — `true` on iOS, `false` on Android. If the project already has a platform helper (`Platform.isIos`, `getPlatform().isIos()`), use that instead. Do not introduce a second platform-detection helper.

### `LegalRow` (only if no equivalent exists)

Private composable inside `ProfileScreen.kt`:

```kotlin
@Composable
private fun LegalRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = AppColors.Surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = AppColors.TextPrimary,
                fontSize = 15.sp
            )
            Text(
                text = "→",
                color = AppColors.Accent,
                fontSize = 16.sp
            )
        }
    }
}
```

If the project already has a row component (`SettingsRow`, `CelestialCard`, etc.), use it instead and skip `LegalRow`.

---

## Step 5 — Android `INTERNET` permission

Add to `AndroidManifest.xml` (only if missing):

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Path is project-dependent — usually `composeApp/src/androidMain/AndroidManifest.xml` or `androidApp/src/main/AndroidManifest.xml`. Don't add it twice.

No iOS-side network permission is required for `WKWebView` to load `https://` URLs. App Transport Security defaults already allow HTTPS — do **not** add ATS exceptions.

---

## Files to create / modify

| File (relative to shared module) | Status |
|---|---|
| `commonMain/.../presentation/webview/WebViewScreen.kt` | New |
| `commonMain/.../ui/components/PlatformWebView.kt` (expect) | New |
| `androidMain/.../ui/components/PlatformWebView.android.kt` (actual) | New |
| `iosMain/.../ui/components/PlatformWebView.ios.kt` (actual) | New |
| `commonMain/.../navigation/AppNavigation.kt` | Modify — add `Screen.WebView` + composable destination |
| `commonMain/.../presentation/profile/ProfileScreen.kt` | Modify — add Legal section (Privacy Policy always, Terms of Use iOS-only) |
| `androidMain/.../AndroidManifest.xml` | Modify — add `INTERNET` permission if missing |

Generate every file completely. No `// TODO`, no placeholder URLs, no commented-out blocks. If the existing project diverges from the assumed layout (different nav library, different theme object, different package names), adapt to it — keep the structure and the platform rule intact.

---

## Common gotchas

- **Compose Multiplatform `UIKitView` import path** moved between versions. If the build fails, check `androidx.compose.ui.interop.UIKitView` vs `androidx.compose.ui.viewinterop.UIKitView`.
- **`Uri.encode` is Android-only.** If `createRoute` lives in `commonMain`, use a multiplatform URL-encode (`io.ktor.http.encodeURLParameter`, or an `expect/actual` `urlEncode`). Don't drop the encoding — telegra.ph URLs contain `-` which is safe, but `&` / `?` / `=` in titles will break the route otherwise.
- **`statusBarsPadding()` is in `androidx.compose.foundation.layout`**, available on Compose Multiplatform 1.5+. If the build complains, the project may be on an older CM version — use `windowInsetsPadding(WindowInsets.statusBars)` instead.
- **JavaScript on the Android WebView** is enabled for compatibility with telegra.ph and similar hosts. If the user's policy page is plain HTML, leaving `javaScriptEnabled = true` is still fine — don't disable it without a stated security review requirement.
- **Don't add iOS Terms of Use to Android.** This is the single most common mistake when copy-pasting between platforms. Gate Terms of Use behind `isIos()` (or the project's platform helper).
