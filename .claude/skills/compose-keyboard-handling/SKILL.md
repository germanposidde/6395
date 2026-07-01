---
name: compose-keyboard-handling
description: >-
  Implement soft-keyboard UX in Jetpack Compose (Android) and Compose Multiplatform (KMP):
  dismiss the keyboard when the user taps empty space or scrolls, wire IME action buttons
  (Done / Search / Next / Go), move focus between fields, and stop the keyboard from covering
  the focused input or the submit button (imePadding / adjustResize). Use this skill whenever a
  TextField, BasicTextField, OutlinedTextField, search bar, form, or any text input is added or
  edited; whenever the user says the keyboard "won't close", "stays open", "covers the
  field/button", "tapping outside does nothing", or asks to hide/dismiss the keyboard on tap or
  scroll; and when wiring the Done/Next/Search key or advancing focus between fields. Works in
  commonMain too — LocalFocusManager and detectTapGestures are multiplatform, no expect/actual
  needed. Use proactively right after adding inputs to a screen, even if the keyboard isn't
  mentioned explicitly.
---

# Compose Keyboard Handling (Android + KMP)

A focused, well-behaved soft keyboard is one of the highest-leverage polish items in a form-
heavy app. The default Compose behaviour leaves the keyboard open until something explicitly
takes focus away, which feels broken to users: they tap a button, scroll, or look at the rest of
the screen and the keyboard just sits there covering content. This skill is the contract for
making the keyboard behave the way people expect.

Everything here lives in `commonMain` and works on Android and iOS unchanged — `LocalFocusManager`,
`detectTapGestures`, `KeyboardOptions`, `KeyboardActions`, and `imePadding()` are all in the
multiplatform Compose API. Don't reach for `expect`/`actual` or platform `View` code for any of
this.

## The core idea

The soft keyboard is a side effect of **focus**. A text field shows the keyboard while it holds
focus and hides it when focus is cleared. So "dismiss the keyboard" almost always means "clear
focus":

```kotlin
val focusManager = LocalFocusManager.current
focusManager.clearFocus()
```

Reach for `LocalSoftwareKeyboardController.current?.hide()` only when you specifically want to
hide the keyboard *without* moving focus (rare — e.g. a search field that should keep its caret).
For the usual "user is done typing here" case, clearing focus is the right tool because it also
visually deselects the field.

## 1. Tap empty space to dismiss

This is the behaviour users expect most and Compose ships least. Add it **once, high up** — at the
root of the screen or, better, the app's content host — so every screen with an input inherits it.

```kotlin
val focusManager = LocalFocusManager.current
Box(
    Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        },
) {
    // screen content (scrollable lists, fields, buttons, …)
}
```

Why `detectTapGestures` and **not** `Modifier.clickable`:

- `clickable` adds a ripple, a semantics "button" role, and steals every tap — it fights real
  clicks and accessibility. `detectTapGestures` is silent and intent-specific.
- `detectTapGestures` only fires on a genuine tap (pointer down + up without significant
  movement). A drag is not a tap, so **list scrolling and swipes still work** — the gesture
  detector ignores them.
- In Compose, children hit-test first. Taps that land on a button, a field, or any child that
  consumes the event never reach the parent's detector, so this doesn't break child clicks. Only
  taps on genuinely empty space clear focus.

Put it on a parent the content draws **inside**, not as a sibling overlay — it relies on being the
fallback that gets taps the children didn't consume.

### The overlay / dialog caveat (important)

A custom bottom sheet, dialog, or any surface that **absorbs taps to avoid dismissing itself**
swallows the tap before it reaches the root handler — so inside that surface, tap-to-dismiss
silently stops working. Anywhere you have a tap-swallowing container like:

```kotlin
.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
    /* swallow so taps don't close the sheet */
}
```

make that same swallow clear focus instead of doing nothing:

```kotlin
.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
    focusManager.clearFocus()
}
```

Now tapping empty space inside the sheet drops the keyboard too. This is the single most common
reason "tap to dismiss works everywhere except in my dialog".

## 2. Dismiss on scroll (optional, often nice)

On long forms, users frequently start scrolling to read the rest while the keyboard is up.
Clearing focus when a scroll begins feels natural. Observe the scroll state and clear focus when it
starts:

```kotlin
val listState = rememberLazyListState()
val focusManager = LocalFocusManager.current
LaunchedEffect(listState.isScrollInProgress) {
    if (listState.isScrollInProgress) focusManager.clearFocus()
}
```

This is optional — don't add it if the design wants the keyboard to persist during scroll (e.g. a
chat composer). Match the product's intent rather than applying it reflexively.

## 3. IME action button (Done / Next / Search / Go)

The key in the bottom-right of the keyboard should do something. Set it with `KeyboardOptions` and
handle it with `KeyboardActions`. Two common shapes:

**Last/only field → Done that dismisses:**

```kotlin
keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
```

**Multi-field form → Next that advances, Done that submits:**

```kotlin
// field 1..n-1
keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),

// last field
keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
keyboardActions = KeyboardActions(onDone = {
    focusManager.clearFocus()
    onSubmit()           // optional: trigger the form's primary action
}),
```

Pick the `keyboardType` too (`KeyboardType.Email`, `.Number`, `.Password`, …) — it changes the
layout the user gets and is part of good input UX, not a separate concern.

For multi-field focus advance to work, the fields must be focusable in order; the default top-to-
bottom traversal usually suffices. Use `Modifier.focusRequester(...)` + `focusProperties { next = ... }`
only when the visual order and traversal order genuinely differ.

## 4. Keep the keyboard from covering the input

When the IME opens it can hide the focused field or the submit button. Two layers fix this:

- **Android window flag:** the host Activity should use `adjustResize` so the window shrinks
  instead of getting overlaid:
  ```xml
  <activity android:windowSoftInputMode="adjustResize" ... />
  ```
- **Compose insets:** add `Modifier.imePadding()` to the scrollable/content container so it makes
  room for the keyboard, and let a `LazyColumn`/`verticalScroll` bring the focused field into view.
  For edge-to-edge screens, prefer `WindowInsets.ime` (or `safeDrawing`, which already includes the
  IME) over hard-coded bottom padding.

A pinned bottom action bar (e.g. a "Save" button) should also respect `imePadding()` so it rides
above the keyboard instead of being buried under it.

## Quick checklist when adding a text field

- [ ] Root content host clears focus on empty-space tap (`detectTapGestures` + `clearFocus()`).
- [ ] Any tap-swallowing sheet/dialog clears focus on its swallow tap.
- [ ] Each field sets a sensible `keyboardType` and `imeAction`.
- [ ] `KeyboardActions` handles the action — Done clears focus (and may submit); Next advances.
- [ ] Content scrolls and uses `imePadding()` so the focused field/submit button isn't covered.
- [ ] Activity uses `windowSoftInputMode="adjustResize"` (Android).

## Common pitfalls

- **Using `clickable` for tap-to-dismiss.** Adds ripple/semantics and eats real clicks. Use
  `detectTapGestures`.
- **Putting the dismiss handler too low.** If it's on a sibling of the content rather than a parent
  the content sits inside, it never receives the leftover taps. Hoist it to the screen/host root.
- **Forgetting tap-swallowing surfaces.** Bottom sheets and dialogs that absorb taps need their own
  `clearFocus()` — see the caveat in section 1.
- **Reaching for platform code.** None of this needs `expect`/`actual`; it's all common Compose.
  Adding Android-only `View`/`InputMethodManager` calls in a KMP module is a smell here.
- **`hide()` when you meant `clearFocus()`.** Hiding the keyboard while the field keeps focus
  leaves a blinking caret and the keyboard pops back on the next recomposition/tap. Clear focus
  unless you have a specific reason not to.
