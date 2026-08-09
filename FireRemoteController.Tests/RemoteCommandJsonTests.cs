using System.Text.Json;
using FireRemoteController.Protocol;
using Xunit;

namespace FireRemoteController.Tests;

public sealed class RemoteCommandJsonTests
{
	[Fact]
	public void CreatesLongPressCommand()
	{
		using var json = JsonDocument.Parse(RemoteCommandJson.CreateLongPress(10, 20, 600, "hold-1"));
		var root = json.RootElement;

		Assert.Equal(1, root.GetProperty("version").GetInt32());
		Assert.Equal("longPress", root.GetProperty("type").GetString());
		Assert.Equal("hold-1", root.GetProperty("requestId").GetString());
		Assert.Equal(10, root.GetProperty("x").GetInt32());
		Assert.Equal(20, root.GetProperty("y").GetInt32());
		Assert.Equal(600, root.GetProperty("durationMs").GetInt32());
	}

	[Fact]
	public void CreatesSwipeCommand()
	{
		using var json = JsonDocument.Parse(
			RemoteCommandJson.CreateSwipe(10, 20, 30, 40, 300, "swipe-1"));
		var root = json.RootElement;

		Assert.Equal(1, root.GetProperty("version").GetInt32());
		Assert.Equal("swipe", root.GetProperty("type").GetString());
		Assert.Equal("swipe-1", root.GetProperty("requestId").GetString());
		Assert.Equal(10, root.GetProperty("startX").GetInt32());
		Assert.Equal(20, root.GetProperty("startY").GetInt32());
		Assert.Equal(30, root.GetProperty("endX").GetInt32());
		Assert.Equal(40, root.GetProperty("endY").GetInt32());
		Assert.Equal(300, root.GetProperty("durationMs").GetInt32());
	}
}
