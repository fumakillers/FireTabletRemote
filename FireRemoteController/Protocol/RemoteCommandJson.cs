using System.Text.Json;

namespace FireRemoteController.Protocol;

public static class RemoteCommandJson
{
	public static string CreatePing(string requestId) => JsonSerializer.Serialize(new
	{
		version = 1,
		type = "ping",
		requestId
	});

	public static string CreateTap(int x, int y, string? requestId = null) => JsonSerializer.Serialize(new
	{
		version = 1,
		type = "tap",
		requestId,
		x,
		y
	});

	public static string CreateBack(string? requestId = null) => JsonSerializer.Serialize(new
	{
		version = 1,
		type = "back",
		requestId
	});

	public static string CreateHome(string? requestId = null) => JsonSerializer.Serialize(new
	{
		version = 1,
		type = "home",
		requestId
	});

	public static string CreateRecents(string? requestId = null) => JsonSerializer.Serialize(new
	{
		version = 1,
		type = "recents",
		requestId
	});

	public static string CreateLongPress(int x, int y, int durationMs, string? requestId = null) =>
		JsonSerializer.Serialize(new
		{
			version = 1,
			type = "longPress",
			requestId,
			x,
			y,
			durationMs
		});
}
