---
name: connectivity-flow-builder
description: Build a complete startup connectivity entry flow for an Android or Kotlin Multiplatform (Compose) app — a polished branded Loading screen that observes network state, a No Internet screen with a Retry button, fully blocked system-back navigation on both, and clean integration with the existing theme and navigation graph. Use this skill whenever the user wants to add a splash / loading / startup flow, handle the "no internet" case at app launch, gate the app on connectivity before the home screen renders, fix users seeing blank screens when offline, add a retry-on-no-network flow, wire `ConnectivityManager` / `NetworkCallback` / `callbackFlow` for reactive connectivity, or set up `BackHandler` to lock users on the loading/no-internet screens. Applies to fresh KMP / Android apps that lack a startup gate, and to existing apps whose offline UX is broken or placeholder-y. Skip if the project already has a working connectivity gate in place — extend it carefully rather than rewriting it from scratch.
---

# Connectivity Flow Builder

You are a senior Android / Kotlin Multiplatform engineer specialising in startup flows, navigation, Compose UI, and offline-first UX. The goal is a production-quality connectivity entry flow — Loading screen → (Connected → app) | (Offline → No Internet screen → Retry → Loading) — that feels native, polished, and visually cohesive with the host app.

**The Loading screen is the only doorway into the app.** Every transition that reveals the app must pass through Loading first — cold start, Retry, *and* an automatic reconnect detected while the No Internet screen is showing. The app is never revealed directly from the No Internet screen; connectivity returning there routes back through Loading (with its minimum dwell), then into the app. This keeps cold-start work, re-init, and the branded reveal consistent no matter how the user got online.

## Discovery — always do this first

Before writing any code, read the project to understand its shape. Don't guess; the difference between a Hilt + Compose-Navigation Android app and a Decompose + Koin KMP app is huge, and the right code is very different.

1. **Project type.** Pure Android (`app/`, `MainActivity`) vs KMP (`composeApp/`, `shared/`, `commonMain/`, `androidMain/`, `iosMain/`). Look at `settings.gradle.kts`.
2. **Theme system.** Grep for `MaterialTheme`, `Theme.kt`, `Color.kt`, `Typography.kt`, custom `*Theme {}` wrappers. The Loading and No Internet screens **must** consume the project's actual tokens — never hard-code colors.
3. **Navigation library.** Compose Navigation (`NavHost`, `NavController`), Voyager (`Navigator { … }`), Decompose (`ChildStack`, `Navigation`), or a hand-rolled state-machine. Each has different idioms for blocking back, registering routes, and triggering navigation from a state holder.
4. **DI / state-holder convention.** Hilt, Koin, manual `remember { … }`, Decompose components — match what the codebase uses.
5. **Existing connectivity utilities.** Grep for `ConnectivityManager`, `NetworkCallback`, `isConnected`, `ConnectivityObserver`, network repositories. If something already exists, extend or reuse it — don't introduce a parallel implementation.
6. **Code style.** Package layout, naming, file size, existing composable patterns. A new file should look like it always belonged here.

If anything critical is ambiguous (KMP vs Android-only, nav library, whether to gate every launch or only cold start), ask the user **one focused question** before proceeding. Don't ask a 5-item form.

## Why a connectivity gate matters

This isn't decoration — it solves real UX failures. Without a gate, users on flaky networks see blank lists, half-loaded screens, indefinite spinners, and stuck states that look like crashes. They blame the app, not the network. A Loading + No Internet flow gives the app:

- A clear "we know what's happening" surface from the very first frame.
- A single retry button instead of pull-to-refresh discovery scattered across every screen.
- A safe place to defer expensive cold-start work until the network is reachable.
- Locked back navigation so users can't escape into a half-initialised app.

Keep this in mind when making trade-offs — the gate exists to **protect** the rest of the app from network-induced ambiguity. Don't water it down with optimistic fallthroughs.

## Loading screen

- Observe connectivity reactively, not by polling. On Android, wrap `ConnectivityManager.NetworkCallback` in a `callbackFlow` that emits when capabilities change; never use deprecated `getActiveNetworkInfo()`. For KMP, expose an `interface ConnectivityObserver` in `commonMain` and provide `expect`/`actual` impls.
- Render a branded, calm loading UI — an animated logo, a shimmer/pulse on the brand mark, an infinite-transition gradient — that uses the app's `colorScheme`, `typography`, and `shapes`. A bare `CircularProgressIndicator` alone is a smell.
- Enforce a **minimum display duration** (~800–1200ms). A perfectly fast network making the screen flash for 80ms looks like a bug. Combine with the connectivity check using `delay(min) + check` in parallel.
- Navigate to the main destination when connectivity is confirmed. Navigate to No Internet when it isn't. Use a sealed-state machine (`LoadingState.Checking | Connected | Disconnected`) and observe from the screen.
- Don't put the navigation `navController.navigate(...)` call inside a composable's body — emit a one-shot event (`Channel.receiveAsFlow()` or `SharedFlow`) and consume it in a `LaunchedEffect`.
- **Always reveal the app *through* Loading — never straight from No Internet.** When the gate is in its `Disconnected`/Offline state and connectivity returns, switch back to the loading state (re-applying the minimum dwell) before routing to the app. Don't let the reactive observer flip Offline → app in one hop. Sketch of the gate's collect loop:

  ```kotlin
  observer.status.collect { status ->
      if (status == Available) {
          if (phase == Offline) { phase = Loading; delay(MIN_DWELL) } else dwell.join()
          phase = Online        // → reveal app
      } else {
          if (phase == Loading) dwell.join()
          phase = Offline       // → No Internet
      }
  }
  ```

  This makes reconnect-while-offline behave exactly like a Retry: loading shows first, then the app. (Initial cold start still shows Loading once with no extra delay.)

## No Internet screen

- Friendly, non-technical copy. "Looks like you're offline" beats "ERROR: NETWORK_UNAVAILABLE".
- A single prominent Retry button using the project's button component (don't introduce a new one).
- An illustration / icon / animated visual that matches the rest of the app's style.
- Retry navigates **back to the Loading screen**, which re-runs the connectivity check. Don't try to "soft retry" in place — the loading screen already owns the check, so just go through it again.
- The same rule applies to **automatic reconnect**: if the network comes back on its own while the user is sitting on the No Internet screen, route back to Loading first (see the Loading screen section), then into the app. The user should always see the branded loading reveal — never an abrupt jump from No Internet straight to content.

## Locking back navigation

This is the most-skipped step and the easiest to get wrong.

> **Rule (Android): system back MUST be locked on both the Loading screen and the No Internet screen.** This is non-negotiable, not best-effort. Every Loading and No Internet composable on the Android side must register `BackHandler(enabled = true) { /* no-op */ }` so the system back gesture/button is fully swallowed and the user can never back out of the gate. A connectivity flow that ships without this lock on either screen is incomplete — treat it as a failing self-check.

- Android Compose: `BackHandler(enabled = true) { /* no-op */ }` inside both screens. Place it at the top of the composable so it's always registered.
- KMP: use the platform-appropriate handler. On Android side that's `androidx.activity.compose.BackHandler`; with Decompose, configure the component's back-pressed callback to swallow the event; on iOS the platform doesn't expose a system back, so this is usually a non-issue.
- The point: users on the Loading / No Internet screen should **never** be able to back-out into a half-initialised app or exit the process from the gate.

## Architecture

- Clean separation: UI composables / state holder / data layer (`ConnectivityObserver` interface + platform impl).
- Use `StateFlow` (or `MutableState` for purely local state) for reactive UI state.
- Sealed types for screen state — exhaustive `when` is much safer than booleans like `var isLoading: Boolean`.
- Keep navigation triggers out of composables; observe state or consume one-shot events in a `LaunchedEffect`.
- Use `viewModelScope` / Decompose `coroutineScope()` / a properly scoped Koin coroutine scope. Never `GlobalScope`.
- Lifecycle-aware collection: `collectAsStateWithLifecycle()` or `repeatOnLifecycle(STARTED)` so connectivity observation pauses when the screen is backgrounded.

## UI quality bar

- Material 3 components, `MaterialTheme` tokens (`colorScheme`, `typography`, `shapes`).
- Tasteful motion: `AnimatedVisibility` on state changes, `animateFloatAsState` for tint shifts, `rememberInfiniteTransition` for the brand pulse, `Crossfade` between `Checking` / `Disconnected` content. Avoid jarring or excessively bouncy animations.
- Generous, intentional spacing on a 4 / 8 / 12 / 16 / 24 / 32 dp scale.
- `WindowInsets` / safe-area handling so the brand mark isn't clipped by status / nav bars.
- Support dark mode if the rest of the app does.
- Both screens should look like polished product surfaces, not placeholders.

## Code quality

- Production-ready: no TODOs, no `println` debugging, no orphan imports.
- Modular composables — split a screen into smaller named pieces (`LoadingMark`, `LoadingMessage`, `NoInternetIllustration`, `RetryButton`).
- Names communicate intent — `ConnectivityObserver`, not `NetworkUtil`; `LoadingViewModel`, not `MainVM`.
- Use Kotlin idioms (sealed interfaces, scope functions, extension functions) instead of Java-style boilerplate.
- Don't add comments restating what well-named code already says. Only add one when the *why* is non-obvious (a workaround, a timing constraint, a deliberate `delay`).

## Suggested file layout

Adapt to match what the project already does — directory shape is less important than placing each file in the right source set with sensible neighbours.

```
feature/connectivity/
├── data/
│   └── ConnectivityObserver.kt          (interface, plus platform impl(s))
├── presentation/
│   ├── loading/
│   │   ├── LoadingScreen.kt
│   │   ├── LoadingViewModel.kt
│   │   └── LoadingState.kt
│   └── nointernet/
│       ├── NoInternetScreen.kt
│       └── NoInternetContent.kt
└── navigation/
    └── ConnectivityNavigation.kt        (or fold into existing nav graph)
```

## Workflow

1. **Discover.** Read theme, nav graph, existing connectivity code, app entrypoint.
2. **Plan.** Briefly outline the files you'll create / modify and how they hook into the nav graph or app entry.
3. **Implement.** Write each file. Modify integration points (`NavGraph`, `MainActivity`, `App.kt`, `MainViewController.kt` for KMP) with care.
4. **Verify.** Re-read each output: imports correct, no orphan references, theme tokens exist, navigation routes wired, both back-handlers in place.
5. **Summarise.** Brief write-up: the flow, key decisions (why `callbackFlow`, why the min-display window), and any manual integration step the user needs to do (e.g. AndroidManifest permission `ACCESS_NETWORK_STATE`).

## Self-check before reporting done

- [ ] System back is fully blocked on **both** Loading and No Internet.
- [ ] All flows are lifecycle-aware; no leaks via unscoped coroutines.
- [ ] Theme integration uses existing tokens — zero hard-coded hex values.
- [ ] Transitions are smooth (no <100ms flicker on fast networks; min-display in place).
- [ ] Retry actually re-runs the connectivity check (goes back to Loading, not a stale state).
- [ ] Reconnect-while-offline routes No Internet → Loading → app (never No Internet → app directly); the min-dwell still applies on that path.
- [ ] No unnecessary recomposition — `remember`, `derivedStateOf`, stable params.
- [ ] KMP code (if applicable) uses `expect` / `actual` correctly and is in the right source set.
- [ ] Existing nav routes still work; nothing else broke.
- [ ] `ACCESS_NETWORK_STATE` permission declared in the Android manifest if it wasn't already.

## When to ask before acting

- Navigation library is ambiguous or non-standard.
- The project uses an architecture pattern that would clash with the proposed structure.
- No existing theme system — you need visual direction from the user.
- Connectivity utilities already exist — confirm whether to extend or replace.
- The project's app-entry composable already has a connectivity gate of some shape — confirm before rewriting it; the user may already be relying on it.

Be decisive, autonomous, and detail-obsessed. The user shouldn't have to ask you to come back and polish the screens — deliver flows that look like they were always part of the app.
