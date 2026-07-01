---
name: camera-gallery-logic
description: Implement or refactor camera capture and gallery/photo-library picking on Android (CameraX, Photo Picker, runtime permissions) or iOS (AVFoundation, PHPickerViewController, photo-library permissions). Use this skill whenever the user mentions taking photos, picking images, opening the camera, choosing from gallery/photo library, camera permissions, photo library permissions, AVCaptureDevice, PHPicker, CameraX, PreviewView, ImageCapture, READ_MEDIA_IMAGES, Manifest.permission.CAMERA, or any flow that opens the device camera or media picker — even if they don't explicitly use those terms. Also applies when the user is fixing crashes, permission loops, "settings deep-link" buttons, or stale state in existing camera/gallery code. Covers Kotlin Multiplatform / Compose Multiplatform too (expect/actual MediaController, in-app CameraX preview, PHPicker), including the Compose-Dialog-on-iOS presentation race where the gallery won't open and the camera works only once.
---

# Camera & Gallery Logic (Android + iOS)

This skill defines the *exact* contract for camera capture and gallery picking on mobile. It is opinionated on purpose — the rules below have been chosen so the resulting flows behave consistently across platforms, never crash on cold permission state, and never deep-link the user out to system Settings.

Apply the rules to whichever platform(s) the current project targets. If the project is Kotlin Multiplatform / cross-platform, apply both the Android and iOS sections to their respective source sets.

## Before you write any code

1. **Identify the platform(s).** Look at the project structure: `*.kt` + `AndroidManifest.xml` ⇒ Android; `*.swift` + `Info.plist` ⇒ iOS; both ⇒ KMP/cross-platform — apply both sections.
2. **Find the existing dialog/alert component.** Grep the project for an alert/dialog/modal helper (e.g. `AppDialog`, `AlertView`, `showDialog`, `Composable` named like `*Dialog`). You **must** reuse it. Do not add a new dialog component.
3. **Find any existing permission manager/helper.** Grep for `Permission`, `requestPermission`, `checkSelfPermission`, `AVCaptureDevice.authorizationStatus`. If one exists, extend it. If not, create one centralized helper — do not scatter permission logic across screens.
4. **Check the project's CameraX / dependency setup.** On Android, look at `libs.versions.toml` and `build.gradle.kts` to see if CameraX is already wired up. Add the latest stable version — do **not** hardcode a version literal; reference the version catalog or use `latest.release`.

Only after these four checks: start implementing.

---

## Permission Flow — Camera

The camera **must never** be instantiated, presented, or have its preview surface created until permission is confirmed `granted` in the current execution path. "Granted on app launch" is not enough — the user may revoke between sessions.

### iOS — Camera

Use `AVCaptureDevice.authorizationStatus(for: .video)` and branch:

| Status | Action |
|---|---|
| `.notDetermined` | Call `AVCaptureDevice.requestAccess(for: .video)`. **Wait for the async callback** before any further branching. On the callback, re-enter the flow (treat as `.authorized` or `.denied` accordingly). |
| `.authorized` | Open the camera. |
| `.denied`, `.restricted` | Show the in-app dialog (existing project component) explaining the camera is unavailable. The dialog has **only a Close/Dismiss button**. No "Open Settings" button. No `UIApplication.open(UIApplication.openSettingsURLString)`. Do not open the camera. |

### Android — Camera

Use `ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)`:

| State | Action |
|---|---|
| `PERMISSION_GRANTED` | Open the camera. |
| Not granted | Launch the request via `ActivityResultLauncher` registered with `registerForActivityResult(ActivityResultContracts.RequestPermission())`. Handle the result. |
| Just denied, and `shouldShowRequestPermissionRationale(activity, CAMERA) == true` | Show in-app rationale dialog (existing project component). If the user agrees, re-launch the request. |
| Permanently denied (`shouldShowRequestPermissionRationale == false` *after* a denial) | Show the in-app dialog stating the camera is unavailable. **Only a Close/Dismiss button.** No "Open Settings" button. No `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` intent. Do not open the camera. |

#### Camera tap flow (Android) — exact sequence

When the user taps the "open camera" action, follow this two-launcher sequence:

1. **First tap → show the camera permission launcher.** If permission is not already `PERMISSION_GRANTED`, launch the system permission request via the `ActivityResultContracts.RequestPermission()` `ActivityResultLauncher`. (If it is already granted, skip straight to step 3 — open the camera preview.)
2. **Not allowed → close the launcher and show the second launcher (info dialog).** When the permission result comes back denied, the system launcher dismisses itself; in response, show the **second launcher** — the in-app info dialog (the existing project dialog component) with text explaining the camera can't be used without permission. This dialog is **Close-only** — no "Open Settings" button, no `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` intent. Do not open the camera.
3. **Allowed → open the camera preview, then make the photo.** When the result comes back granted, open the CameraX preview (bind `Preview` + `ImageCapture`, see the Camera Preview & Capture — Android section) and let the user capture. Capture via `imageCapture.takePicture(...)` and return the saved `Uri` to the caller.

Keep all branching off the single permission helper (see cross-cutting rule 2) — the screen only reacts to the launcher result; it does not duplicate the `checkSelfPermission` branching.

> **Why no Settings deep-link?** It is an explicit project rule. Do not add it back "as a courtesy" — it is intentionally excluded across both platforms.

---

## Permission Flow — Gallery / Photo Library

### iOS — Gallery

**Do not** perform any in-app photo-library permission check or request before showing the picker. `PHPickerViewController` runs out-of-process; the system handles user-selected media access transparently and does **not** require `PHPhotoLibrary` authorization to read user-selected images. Open the picker directly when the user taps the gallery action.

- Keep `NSPhotoLibraryUsageDescription` in `Info.plist`. Do not remove it (it is still required to ship to App Store review for some configurations and is read by parts of the system even when not strictly enforced on the picker path).
- Only request `PHPhotoLibrary.requestAuthorization(for: .addOnly)` if the app **writes** images back to the library. In that case, add `NSPhotoLibraryAddUsageDescription` and handle `.denied`/`.restricted` with the in-app dialog — Close-only, no Settings deep-link.

### Android — Gallery

**Preferred path: Photo Picker API** (`ActivityResultContracts.PickVisualMedia` / `PickMultipleVisualMedia`). Available on all API levels via AndroidX. **No runtime permission required** regardless of API level — open the picker directly.

Only if the project explicitly cannot use Photo Picker (e.g. needs custom URI access patterns or targets ancient AndroidX versions), fall back to runtime permissions:
- API 33+ : `READ_MEDIA_IMAGES` (and `READ_MEDIA_VIDEO` if video is needed)
- API ≤ 32 : `READ_EXTERNAL_STORAGE`

The permission flow uses the same granted / rationale / permanently-denied branching as Camera above — including the Close-only dialog (no Settings deep-link) on permanent denial.

---

## Camera Preview & Capture — Android (CameraX)

Use CameraX. The required dependencies (always at the latest stable version, via version catalog or `latest.release` — never a hardcoded literal):

- `androidx.camera:camera-core`
- `androidx.camera:camera-camera2`
- `androidx.camera:camera-lifecycle`
- `androidx.camera:camera-view`
- `androidx.camera:camera-extensions` (only if extensions are actually used)

### Wiring

1. **Do not** inflate or render `PreviewView` until camera permission has been confirmed granted *in the current execution path*. Gate the composable / view behind the permission check.
2. Obtain the provider with `ProcessCameraProvider.getInstance(context)` and attach a `ListenableFuture` listener on the main executor (`ContextCompat.getMainExecutor(context)`).
3. Build the use-cases:
   - `Preview` — bind `preview.setSurfaceProvider(previewView.surfaceProvider)`.
   - `ImageCapture` — keep a reference for capture calls.
4. Bind **both** use-cases in a single `cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)` call. Re-binding inside callbacks fragments the lifecycle and causes flicker / black frames.
5. Default `CameraSelector.DEFAULT_BACK_CAMERA`. Expose a front/back toggle **only** if the design requires it — do not invent one.
6. Capture via `imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback { ... })`. Use a file-based `OutputFileOptions` and return the resulting `Uri` to the caller.

### Lifecycle and teardown

- Bind to the `LifecycleOwner` of the host (Activity / Fragment / Composable `LocalLifecycleOwner.current`). CameraX automatically releases when the lifecycle stops — do **not** manually `unbindAll()` unless you have a specific cross-screen reason. Manual unbinding tends to fight the lifecycle and produces hard-to-reproduce "camera busy" errors.
- Handle cancellation (user backs out / closes the screen without capturing): no crash, no leaked `ListenableFuture`, no stale `ImageCapture` reference held in a ViewModel that outlives the screen.

### Return contract

The camera screen returns **only** the saved file `Uri`. It must not embed unrelated business logic (no uploads, no API calls, no analytics tagging beyond a single capture event). The caller decides what to do with the URI.

---

## Camera Preview & Capture — iOS

- Do **not** instantiate `AVCaptureSession`, `AVCaptureVideoPreviewLayer`, or any preview view until `.authorized` is confirmed *in the current execution path*. Do not rely on a previous check.
- Build the session off the main thread (`AVCaptureSession.startRunning()` is blocking), but configure UI on the main thread.
- Return the capture result (file URL or `UIImage`) to the caller. Do not embed unrelated business logic in the camera screen.
- Handle dismissal cleanly: stop the session, release the preview layer, and ensure no `AVCaptureDeviceInput`/`Output` is retained beyond the screen.

---

## Gallery Picker

### iOS

Use `PHPickerViewController` with `PHPickerConfiguration`. Set `selectionLimit` (1 for single-image flows; >1 only if multi-select is required). Present directly on user tap — **no** permission prompt from the app side.

Only fall back to `UIImagePickerController` if the project is already using it elsewhere and rewriting it is out of scope. Do not introduce `UIImagePickerController` in new code — it is deprecated for library access.

### Android

Prefer `ActivityResultContracts.PickVisualMedia()` (single) or `PickMultipleVisualMedia()` (multi). Launch with `PickVisualMediaRequest(PickVisualMedia.ImageOnly)` (or `VideoOnly` / `ImageAndVideo` as needed). No permission required.

Only fall back to `Intent(Intent.ACTION_PICK)` / `Intent.ACTION_OPEN_DOCUMENT` if the host's `minSdk` predates Photo Picker support and the AndroidX backport is unavailable.

---

## Cross-cutting rules

These apply everywhere in the camera/gallery scope:

1. **Threading.** All permission checks and UI updates run on the main thread. All heavy work (image compression, EXIF stripping, disk writes, network uploads) runs off the main thread — `Dispatchers.IO` on Android, a background `DispatchQueue` or `Task.detached` on iOS.
2. **Single source of truth for permissions.** Permission logic lives in **one** manager/helper. Screens call into it; they do not duplicate `checkSelfPermission` / `authorizationStatus` branching.
3. **Reuse the existing dialog component.** Find it during the pre-flight checks above and use it. Do not introduce a second alert system just for permission denials.
4. **Close-only denial dialogs.** Every "permission denied / unavailable" dialog has exactly one button: Close/Dismiss. No "Open Settings" button. No Settings deep-link (`UIApplication.openSettingsURLString` on iOS; `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` intent on Android). This is a hard project rule across both platforms.
5. **Scope discipline.** Do not modify code outside the camera, gallery, and permission-handling files. If a refactor *seems* tempting (e.g. cleaning up an unrelated ViewModel), don't — keep the diff focused.
6. **No half-finished implementations.** If you're asked to wire up the camera, wire it up end-to-end: permission → preview → capture → return URI → dismiss. Don't leave a TODO at the capture step.
7. **The user must be able to delete a photo.** Wherever a captured or picked photo is shown to the user, provide an affordance to delete/remove it (e.g. a delete/remove button on the thumbnail or preview). Deleting clears the photo from the UI state and removes any app-owned file the capture wrote to disk (delete the saved `Uri`/file URL the camera returned); it must not leave orphaned files or stale references behind. Never make a selected/captured photo permanent with no way to discard it. This applies on both platforms.

---

## iOS `Info.plist` keys

Required when the corresponding capability is used:

| Key | When required |
|---|---|
| `NSCameraUsageDescription` | Always, if the app ever opens the camera. |
| `NSPhotoLibraryUsageDescription` | Keep present in `Info.plist` even when using `PHPickerViewController` (do not remove). |
| `NSPhotoLibraryAddUsageDescription` | Only if the app writes images back to the photo library. |

Write user-facing, accurate strings — the rationale must match how the camera/library is actually used in the app. App Store review rejects generic strings.

---

## Android `AndroidManifest.xml`

Add only what is actually needed:

```xml
<!-- Camera -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />

<!-- Gallery — ONLY if NOT using Photo Picker. Photo Picker requires no permission. -->
<!-- API 33+ -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<!-- API ≤ 32 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

Set `android:required="false"` on the camera feature unless the app genuinely cannot function without a camera — otherwise Google Play will hide the app from devices without one.

---

## Kotlin Multiplatform / Compose Multiplatform

When the project is KMP with Compose Multiplatform (`composeApp/`, `commonMain` + `androidMain` + `iosMain`, an `iosApp/` Xcode project), there is no shared camera/picker API — wire one with `expect`/`actual` and keep all UI in `commonMain`.

**Shared contract (`commonMain`).** Define a single entry point and a decoder:

```kotlin
interface MediaController { fun pickFromGallery(); fun captureFromCamera() }

@Composable expect fun rememberMediaController(
    onImagePicked: (ByteArray) -> Unit,   // raw JPEG bytes — storable & cross-platform
    onCameraDenied: () -> Unit,            // caller shows the Close-only dialog
): MediaController

expect fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap?
```

Return **bytes**, not a platform `Uri`/`UIImage` — bytes display via `decodeToImageBitmap` and persist directly (e.g. Base64). `decodeToImageBitmap` actuals: Android `BitmapFactory.decodeByteArray(...).asImageBitmap()`; iOS `org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()`.

**Android actual.** The host is usually a single `ComponentActivity` driving a custom (non-Fragment) navigator, so there's no Activity/Fragment to push a camera screen onto.
- **Use an in-app CameraX preview, NOT `ActivityResultContracts.TakePicturePreview`/`ACTION_IMAGE_CAPTURE`.** The system-camera intent throws `ActivityNotFoundException` when no camera app is visible, and a `SecurityException` when `CAMERA` is declared in the manifest but not granted — both crash the app. CameraX needs no external app and sidesteps the permission quirk. (The CameraX deps are the ones listed in the Android section above.)
- Render the camera as a full-screen `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` containing `AndroidView { PreviewView }`, gated behind a `mutableStateOf` that `rememberMediaController` flips on only after permission is granted.
- Always route the tap through the `RequestPermission` launcher and open the camera **only from the granted callback** — never branch to "already granted → open" in a way that opens before a result. (Reading `checkSelfPermission` to *skip* the prompt is fine, but the open must still happen via a single code path you control.)
- Capture **in memory** with `ImageCapture.OnImageCapturedCallback` (no temp files → nothing to clean up on delete). Copy `planes[0].buffer` to a `ByteArray`, `image.close()`, then rotate by `imageInfo.rotationDegrees` off the main thread before returning.
- Wrap `bindToLifecycle(...)` and `takePicture(...)` in `runCatching { } .onFailure { onCameraDenied() }` so a device/emulator with no usable camera shows the dialog instead of crashing. Gallery stays `PickVisualMedia` (no permission).

**iOS actual.**
- **Gallery = `PHPickerViewController`** (never `UIImagePickerController` for library access). Present directly, no permission check. Load the image with the provider's **own registered type identifier**, not a hardcoded `"public.image"`, then normalize through `UIImage(data=)` → `UIImageJPEGRepresentation` so HEIC/PNG/JPEG all come back as decodable JPEG:
  ```kotlin
  val typeId = (provider.registeredTypeIdentifiers.firstOrNull() as? String) ?: "public.image"
  provider.loadDataRepresentationForTypeIdentifier(typeId) { data, _ -> /* UIImage(data=) -> JPEG */ }
  ```
- **Camera = `UIImagePickerController`** gated by `AVCaptureDevice.authorizationStatus(.video)` (request on `notDetermined`, re-enter on the main thread), and guard `UIImagePickerController.isSourceTypeAvailable(.camera)` so the **simulator (no camera)** shows the dialog instead of crashing.
- Retain both delegates with `remember { }` (UIKit holds `delegate` weakly).

**The Compose-`Dialog`-on-iOS trap (most important).** A Compose `Dialog` (your chooser) is hosted in its **own transient `UIWindow`** on iOS. Dismissing the dialog and presenting a UIKit picker in the *same frame* makes the picker present from a window that's being torn down — it **silently fails**. The classic symptom: gallery never opens and the camera works exactly once. Fix: **defer presentation to the next runloop** and resolve the top view controller *then*:
  ```kotlin
  private fun present(vc: UIViewController) = dispatch_async(dispatch_get_main_queue()) {
      topViewController()?.presentViewController(vc, animated = true, completion = null)
  }
  ```
  Resolve `topViewController()` from `keyWindow` (fallback to `windows.first()`), walking `presentedViewController` to the top.

**Info.plist still applies.** The iOS usage-description keys live in the iOS app target's `Info.plist` (commonly `iosApp/iosApp/Info.plist`), not in the shared module. Add them there; missing `NSCameraUsageDescription` hard-crashes the app the instant the camera is touched.

---

## When the user asks for something this skill doesn't cover

This skill is the contract for camera/gallery/permission flows. It is **not** the contract for:

- Image editing / cropping (use the project's existing image-editing flow if any)
- Uploading to a backend (return the URI/UIImage to the caller; the caller handles upload)
- Video recording specifics beyond what CameraX `VideoCapture` / `AVCaptureMovieFileOutput` provide by default
- Custom barcode/ML pipelines (use ML Kit / Vision separately)

If the request strays into those areas, do the camera/gallery part to the contract above and ask the user how they want the downstream piece handled.
