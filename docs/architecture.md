# Initial architecture

```text
FireRemoteController (.NET MAUI, landscape)
  UI / PreviewCoordinateMapper
             |
             | command JSON over WebSocket
             v
FireRemoteServer (Kotlin, landscape)
  CommandWebSocketServer
             |
             v
  CommandParser -> CommandDispatcher
             |
             v
  CommandExecutor
             |
             v
  AccessibilityServiceBridge
             |
             v
  FireRemoteAccessibilityService
  (`back` / `home` / `recents` global actions and single-point tap are implemented)
```

## Boundaries

- `protocol/README.md` is the implementation-neutral contract. Kotlin and C# define their own small representations.
- WebSocket classes transport complete JSON messages; they do not know preview sizes or transform coordinates.
- `CommandParser` validates wire input. `CommandDispatcher` invokes a replaceable `CommandExecutor`.
- `AndroidCommandExecutor` depends on small action and gesture gateway boundaries. The process-local `AccessibilityServiceBridge` registers the system-created service while connected and clears the exact instance on unbind/destroy.
- `ping` does not use AccessibilityService. `back`, `home`, and `recents` use their matching Android global actions. `tap` uses `dispatchGesture`; `longPress` remains unimplemented.
- The Controller keeps command JSON creation separate from `ClientWebSocket`. `PreviewCoordinateMapper` owns AspectFit bounds checking and maps preview-local positions to Fire source pixels.
- `tools/MockWebSocketServer` lets the Controller connect without a Fire Tablet or Server APK.

## Preview path

```text
Controller Preview Loop (one request in flight)
             |
             | previewRequest
             v
CommandWebSocketServer -> CommandDispatcher
             |
             v
AccessibilityScreenshotProvider
             |
             v
AccessibilityScreenshotGateway
             |
             v
FireRemoteAccessibilityService.takeScreenshot
             |
             v
HardwareBuffer -> software Bitmap -> max 640 px -> JPEG/Base64
             |
             | previewFrame / previewError
             v
Controller Image (AspectFit)
```

- Screenshot acquisition and image encoding are outside the WebSocket server.
- The screenshot gateway owns the AccessibilityService reference and clears only the matching instance on disconnect/destroy.
- Hardware buffers and temporary bitmaps are released after each frame.
- The Controller waits for `previewFrame` or `previewError`, then waits about one second before sending the next request. Disconnecting or hiding the page cancels the loop.

## Preview tap path

```text
Preview-local tap
        |
        v
AspectFit displayed-image bounds check
        |
        v
PreviewCoordinateMapper -> Fire source pixel
        |
        | tap command
        v
AndroidCommandExecutor -> AccessibilityService.dispatchGesture
        |
        v
Gesture completed / cancelled result
```

- Taps in AspectFit letterbox/pillarbox padding are ignored by the Controller.
- `previewFrame.sourceWidth` and `sourceHeight` identify the Fire coordinate space; encoded JPEG dimensions remain separate.
- The Server replies with success only from `GestureResultCallback.onCompleted`.

## Current security boundary

Protocol v1 has no authentication or encryption. Do not expose port 8080 to the internet or an untrusted network.
