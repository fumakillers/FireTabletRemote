using System.Net.WebSockets;
using System.Text;

namespace FireRemoteController.Services;

public sealed class RemoteWebSocketClient : IRemoteWebSocketClient
{
	private static readonly TimeSpan ConnectTimeout = TimeSpan.FromSeconds(5);
	private readonly SemaphoreSlim gate = new(1, 1);
	private readonly SemaphoreSlim sendGate = new(1, 1);
	private ClientWebSocket? socket;
	private CancellationTokenSource? receiveCancellation;

	public bool IsConnected => socket?.State == WebSocketState.Open;
	public event EventHandler<bool>? ConnectionChanged;
	public event EventHandler<string>? MessageReceived;

	public async Task ConnectAsync(string host, int port, CancellationToken cancellationToken = default)
	{
		if (string.IsNullOrWhiteSpace(host))
			throw new ArgumentException("Server IP is required.", nameof(host));

		await gate.WaitAsync(cancellationToken);
		try
		{
			await DisconnectCoreAsync(CancellationToken.None);
			var newSocket = new ClientWebSocket();
			var endpoint = new UriBuilder("ws", host, port, "ws").Uri;
			using var connectCancellation =
				CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
			connectCancellation.CancelAfter(ConnectTimeout);
			try
			{
				await newSocket.ConnectAsync(endpoint, connectCancellation.Token);
			}
			catch (OperationCanceledException) when (
				!cancellationToken.IsCancellationRequested
				&& connectCancellation.IsCancellationRequested)
			{
				newSocket.Dispose();
				throw new TimeoutException("Connection timed out.");
			}
			catch
			{
				newSocket.Dispose();
				throw;
			}

			socket = newSocket;
			receiveCancellation = new CancellationTokenSource();
			ConnectionChanged?.Invoke(this, true);
			_ = ReceiveLoopAsync(newSocket, receiveCancellation.Token);
		}
		finally
		{
			gate.Release();
		}
	}

	public async Task SendAsync(string message, CancellationToken cancellationToken = default)
	{
		await sendGate.WaitAsync(cancellationToken);
		try
		{
			var activeSocket = socket;
			if (activeSocket?.State != WebSocketState.Open)
				throw new InvalidOperationException("WebSocket is not connected.");

			var bytes = Encoding.UTF8.GetBytes(message);
			await activeSocket.SendAsync(bytes, WebSocketMessageType.Text, true, cancellationToken);
		}
		finally
		{
			sendGate.Release();
		}
	}

	public async Task DisconnectAsync(CancellationToken cancellationToken = default)
	{
		await gate.WaitAsync(cancellationToken);
		try
		{
			await DisconnectCoreAsync(cancellationToken);
		}
		finally
		{
			gate.Release();
		}
	}

	private async Task DisconnectCoreAsync(CancellationToken cancellationToken)
	{
		var oldSocket = socket;
		socket = null;
		receiveCancellation?.Cancel();
		receiveCancellation?.Dispose();
		receiveCancellation = null;

		if (oldSocket is not null)
		{
			if (oldSocket.State == WebSocketState.Open)
				await oldSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Client disconnect", cancellationToken);
			oldSocket.Dispose();
			ConnectionChanged?.Invoke(this, false);
		}
	}

	private async Task ReceiveLoopAsync(ClientWebSocket activeSocket, CancellationToken cancellationToken)
	{
		var buffer = new byte[4096];
		try
		{
			while (!cancellationToken.IsCancellationRequested && activeSocket.State == WebSocketState.Open)
			{
				using var message = new MemoryStream();
				WebSocketReceiveResult result;
				do
				{
					result = await activeSocket.ReceiveAsync(buffer, cancellationToken);
					if (result.MessageType == WebSocketMessageType.Close)
						return;
					message.Write(buffer, 0, result.Count);
				} while (!result.EndOfMessage);

				MessageReceived?.Invoke(this, Encoding.UTF8.GetString(message.ToArray()));
			}
		}
		catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
		{
		}
		catch (Exception error)
		{
			MessageReceived?.Invoke(this, $"WebSocket error: {error.Message}");
		}
		finally
		{
			if (ReferenceEquals(socket, activeSocket))
			{
				socket = null;
				activeSocket.Dispose();
				ConnectionChanged?.Invoke(this, false);
			}
		}
	}

	public async ValueTask DisposeAsync()
	{
		await DisconnectAsync();
		gate.Dispose();
		sendGate.Dispose();
	}
}
