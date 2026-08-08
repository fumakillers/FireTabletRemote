# Fire Tablet Remote WebSocket Protocol v1

## Transport

- WebSocket text frames containing one UTF-8 JSON object
- Server endpoint: `ws://<fire-tablet-ip>:8080/ws`
- One command per frame and one `result` response per command
- Intended only for a trusted LAN in v1; authentication, TLS, discovery, and batching are out of scope

## Common command fields

| Field | Type | Required | Description |
|---|---|---:|---|
| `version` | integer | yes | Protocol version. The initial value is `1`. |
| `type` | string | yes | `ping`, `tap`, `back`, `home`, `recents`, or `longPress` |
| `requestId` | string | no | Caller-generated correlation ID. Returned unchanged when accepted by the parser. |

Unknown command types or unsupported versions are rejected. Additional unknown fields may be ignored so a compatible sender can be extended later.

## Commands

### ping

```json
{"version":1,"type":"ping","requestId":"check-1"}
```

### tap

`x` and `y` are non-negative integer pixels in the Fire Tablet's current landscape screen coordinate system. Coordinate conversion belongs to the Controller presentation/domain layer, not to WebSocket transport.

```json
{"version":1,"type":"tap","requestId":"tap-1","x":500,"y":300}
```

### back

```json
{"version":1,"type":"back","requestId":"back-1"}
```

### home

```json
{"version":1,"type":"home","requestId":"home-1"}
```

### recents

```json
{"version":1,"type":"recents","requestId":"recents-1"}
```

### longPress

`durationMs` is an integer from `100` through `60000` milliseconds.

```json
{"version":1,"type":"longPress","requestId":"hold-1","x":500,"y":300,"durationMs":1000}
```

## Result

The server responds after parsing and dispatching a command. A `success` value of `false` may mean either invalid input or an operation that is not implemented/available.

```json
{"version":1,"type":"result","requestId":"check-1","success":true,"message":"pong"}
```

The current Server executes `ping` and, while its AccessibilityService is connected, `back`, `home`, and `recents`. It parses `tap` and `longPress`, then returns `success: false` because gesture execution is intentionally not implemented yet.
