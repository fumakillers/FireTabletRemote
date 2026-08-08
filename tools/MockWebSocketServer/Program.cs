using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();
app.UseWebSockets();

app.Run(async context =>
{
    if (context.Request.Path != "/ws" || !context.WebSockets.IsWebSocketRequest)
    {
        context.Response.StatusCode = StatusCodes.Status400BadRequest;
        await context.Response.WriteAsync("Connect a WebSocket client to /ws.");
        return;
    }

    using var socket = await context.WebSockets.AcceptWebSocketAsync();
    var buffer = new byte[4096];
    while (socket.State == WebSocketState.Open)
    {
        using var message = new MemoryStream();
        WebSocketReceiveResult result;
        do
        {
            result = await socket.ReceiveAsync(buffer, context.RequestAborted);
            if (result.MessageType == WebSocketMessageType.Close)
            {
                await socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Mock server closing", context.RequestAborted);
                return;
            }
            message.Write(buffer, 0, result.Count);
        } while (!result.EndOfMessage);

        var input = Encoding.UTF8.GetString(message.ToArray());
        Console.WriteLine($"Received: {input}");
        var response = BuildResponse(input);
        await socket.SendAsync(
            Encoding.UTF8.GetBytes(response),
            WebSocketMessageType.Text,
            true,
            context.RequestAborted);
    }
});

app.Run();

static string BuildResponse(string input)
{
    try
    {
        using var document = JsonDocument.Parse(input);
        var root = document.RootElement;
        var type = root.TryGetProperty("type", out var typeElement) ? typeElement.GetString() : "unknown";
        var requestId = root.TryGetProperty("requestId", out var idElement) ? idElement.GetString() : null;
        return JsonSerializer.Serialize(new
        {
            version = 1,
            type = "result",
            requestId,
            success = true,
            message = $"mock received {type}"
        });
    }
    catch (JsonException error)
    {
        return JsonSerializer.Serialize(new
        {
            version = 1,
            type = "result",
            success = false,
            message = $"invalid JSON: {error.Message}"
        });
    }
}
