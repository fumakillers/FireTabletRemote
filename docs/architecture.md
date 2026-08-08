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
  FireRemoteAccessibilityService
  (gesture/global action wiring is not implemented yet)
```

## Boundaries

- `protocol/README.md` is the implementation-neutral contract. Kotlin and C# define their own small representations.
- WebSocket classes transport complete JSON messages; they do not know preview sizes or transform coordinates.
- `CommandParser` validates wire input. `CommandDispatcher` invokes a replaceable `CommandExecutor`.
- `AndroidCommandExecutor` is the future seam for gestures and global actions. It currently logs parsed commands and only answers `ping`.
- The Controller keeps command JSON creation separate from `ClientWebSocket`, leaving future preview mapping outside both.
- `tools/MockWebSocketServer` lets the Controller connect without a Fire Tablet or Server APK.

## Current security boundary

Protocol v1 has no authentication or encryption. Do not expose port 8080 to the internet or an untrusted network.
