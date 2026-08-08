# Initial architecture

```text
FireRemoteController (.NET MAUI, landscape)
  UI / future preview coordinate conversion
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
  (`back` / `home` / `recents` global actions are implemented; gestures are not)
```

## Boundaries

- `protocol/README.md` is the implementation-neutral contract. Kotlin and C# define their own small representations.
- WebSocket classes transport complete JSON messages; they do not know preview sizes or transform coordinates.
- `CommandParser` validates wire input. `CommandDispatcher` invokes a replaceable `CommandExecutor`.
- `AndroidCommandExecutor` depends on the small `AndroidActionGateway` boundary. The process-local `AccessibilityServiceBridge` registers the system-created service while connected and clears the exact instance on unbind/destroy.
- `ping` does not use AccessibilityService. `back`, `home`, and `recents` use their matching Android global actions; `tap` and `longPress` remain unimplemented.
- The Controller keeps command JSON creation separate from `ClientWebSocket`. The preview input reports MAUI device-independent coordinates local to its top-left; future Fire-screen mapping remains outside transport.
- `tools/MockWebSocketServer` lets the Controller connect without a Fire Tablet or Server APK.

## Current security boundary

Protocol v1 has no authentication or encryption. Do not expose port 8080 to the internet or an untrusted network.
