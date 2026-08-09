using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using FireRemoteController.Services;
using Xunit;

namespace FireRemoteController.Tests;

public sealed class RemoteWebSocketClientTests
{
	[Fact]
	public async Task ConnectAsyncTimesOutWhenHandshakeDoesNotComplete()
	{
		using var listener = new TcpListener(IPAddress.Loopback, 0);
		listener.Start();
		var port = ((IPEndPoint)listener.LocalEndpoint).Port;
		var acceptTask = listener.AcceptTcpClientAsync();
		await using var client = new RemoteWebSocketClient();
		var stopwatch = Stopwatch.StartNew();

		await Assert.ThrowsAsync<TimeoutException>(() =>
			client.ConnectAsync("127.0.0.1", port));

		stopwatch.Stop();
		using var acceptedClient = await acceptTask.WaitAsync(TimeSpan.FromSeconds(1));
		Assert.InRange(stopwatch.Elapsed, TimeSpan.FromSeconds(4), TimeSpan.FromSeconds(8));
		Assert.False(client.IsConnected);
	}

	[Fact]
	public async Task ConnectAsyncPreservesCallerCancellation()
	{
		using var listener = new TcpListener(IPAddress.Loopback, 0);
		listener.Start();
		var port = ((IPEndPoint)listener.LocalEndpoint).Port;
		var acceptTask = listener.AcceptTcpClientAsync();
		await using var client = new RemoteWebSocketClient();
		using var cancellation = new CancellationTokenSource(TimeSpan.FromMilliseconds(100));

		var error = await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
			client.ConnectAsync("127.0.0.1", port, cancellation.Token));

		using var acceptedClient = await acceptTask.WaitAsync(TimeSpan.FromSeconds(1));
		Assert.IsNotType<TimeoutException>(error);
		Assert.False(client.IsConnected);
	}
}
