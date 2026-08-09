# Fire Tablet Remote WebSocket Protocol v1

## Transport

- WebSocket text frames containing one UTF-8 JSON object
- Server endpoint: `ws://<fire-tablet-ip>:8080/ws`
- One request per frame and one correlated response per request
- Intended only for a trusted LAN in v1; authentication, TLS, discovery, and batching are out of scope

## Common command fields

| Field | Type | Required | Description |
|---|---|---:|---|
| `version` | integer | yes | Protocol version. The initial value is `1`. |
| `type` | string | yes | `ping`, `tap`, `back`, `home`, `recents`, `longPress`, `swipe`, or `previewRequest` |
| `requestId` | string | no | Caller-generated correlation ID. Returned unchanged when accepted by the parser. |

Unknown command types or unsupported versions are rejected. Additional unknown fields may be ignored so a compatible sender can be extended later.

## Commands

### ping

```json
{"version":1,"type":"ping","requestId":"check-1"}
```

### tap

`x` and `y` are integer pixels in the Fire Tablet's current landscape screen coordinate system, with `0 <= x < sourceWidth` and `0 <= y < sourceHeight`. AspectFit coordinate conversion belongs to the Controller presentation/domain layer, not to WebSocket transport.

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

`x` and `y` are Fire screen pixel coordinates. `durationMs` is an integer from `100` through `60000` milliseconds.

```json
{"version":1,"type":"longPress","requestId":"hold-1","x":500,"y":300,"durationMs":1000}
```

### swipe

All coordinates are Fire screen pixels. `durationMs` uses the same `100` through `60000` millisecond protocol range as `longPress`.

```json
{"version":1,"type":"swipe","requestId":"swipe-1","startX":800,"startY":800,"endX":800,"endY":200,"durationMs":300}
```

### previewRequest

Requests one low-resolution screenshot. The Controller sends a new request only after the previous request has completed and the preview interval has elapsed.

```json
{"version":1,"type":"previewRequest","requestId":"preview-123"}
```

## Result

The server responds after parsing and dispatching a command. A `success` value of `false` may mean either invalid input or an operation that is not implemented/available.

```json
{"version":1,"type":"result","requestId":"check-1","success":true,"message":"pong"}
```

The current Server executes `ping` and, while its AccessibilityService is connected, `back`, `home`, `recents`, `tap`, `longPress`, and `swipe`. Gesture results are sent only after Android reports completion, cancellation, or rejection.

## Preview responses

Successful `previewRequest` calls return a JPEG Base64-encoded in a JSON text frame. `width` and `height` are the encoded image dimensions; `sourceWidth` and `sourceHeight` are the Fire display dimensions of the captured source used for tap mapping. Aspect ratio is preserved and the longest encoded edge is at most 640 pixels.

```json
{
  "version": 1,
  "type": "previewFrame",
  "requestId": "preview-123",
  "mimeType": "image/jpeg",
  "width": 640,
  "height": 400,
  "sourceWidth": 1920,
  "sourceHeight": 1200,
  "data": "<base64>"
}
```

Capture failures use a dedicated response so they are not confused with command results.

```json
{
  "version": 1,
  "type": "previewError",
  "requestId": "preview-123",
  "message": "Accessibility service is not connected"
}
```

Protocol v1 uses WebSocket text frames and Base64 for simplicity. Binary frames, streaming, and secure-content bypasses are out of scope.
